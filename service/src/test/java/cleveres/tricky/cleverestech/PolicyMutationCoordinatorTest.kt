package cleveres.tricky.cleverestech

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PolicyMutationCoordinatorTest {
    @Test
    fun `concurrent policy mutations keep canonical state and markers in commit order`() {
        val canonical = AtomicReference("none")
        val markers = AtomicReference("none")
        val firstSyncEntered = CountDownLatch(1)
        val releaseFirstSync = CountDownLatch(1)
        val secondAttempting = CountDownLatch(1)
        val secondMutationEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first =
                executor.submit<Result<PolicyMutationResult>> {
                    PolicyMutationCoordinator.mutate(
                        preflight = {},
                        mutation = {
                            canonical.set("A")
                            Result.success(JSONObject().put("generation", "A"))
                        },
                        synchronizeCompatibility = { state ->
                            firstSyncEntered.countDown()
                            if (releaseFirstSync.await(2, TimeUnit.SECONDS)) {
                                markers.set(state.getString("generation"))
                                Result.success(Unit)
                            } else {
                                Result.failure(AssertionError("Timed out waiting to release first sync"))
                            }
                        },
                    )
                }

            assertTrue(firstSyncEntered.await(2, TimeUnit.SECONDS))

            val second =
                executor.submit<Result<PolicyMutationResult>> {
                    secondAttempting.countDown()
                    PolicyMutationCoordinator.mutate(
                        preflight = {},
                        mutation = {
                            secondMutationEntered.countDown()
                            canonical.set("B")
                            Result.success(JSONObject().put("generation", "B"))
                        },
                        synchronizeCompatibility = { state ->
                            markers.set(state.getString("generation"))
                            Result.success(Unit)
                        },
                    )
                }

            assertTrue(secondAttempting.await(2, TimeUnit.SECONDS))
            assertFalse(
                "second canonical mutation must wait for first marker sync",
                secondMutationEntered.await(150, TimeUnit.MILLISECONDS),
            )

            releaseFirstSync.countDown()
            assertTrue(first.get(2, TimeUnit.SECONDS).isSuccess)
            assertTrue(second.get(2, TimeUnit.SECONDS).isSuccess)
            assertTrue(secondMutationEntered.await(2, TimeUnit.SECONDS))
            assertEquals("B", canonical.get())
            assertEquals(canonical.get(), markers.get())
        } finally {
            releaseFirstSync.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `startup compatibility heal cannot publish stale markers after a newer mutation`() {
        val canonical = AtomicReference("A")
        val markers = AtomicReference("none")
        val healSyncEntered = CountDownLatch(1)
        val releaseHealSync = CountDownLatch(1)
        val mutationEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val heal =
                executor.submit<Result<Unit>> {
                    PolicyMutationCoordinator.synchronizeCurrentCompatibility(
                        stateProvider = { JSONObject().put("generation", canonical.get()) },
                        synchronizeCompatibility = { state ->
                            healSyncEntered.countDown()
                            if (releaseHealSync.await(2, TimeUnit.SECONDS)) {
                                markers.set(state.getString("generation"))
                                Result.success(Unit)
                            } else {
                                Result.failure(AssertionError("Timed out waiting to release startup heal"))
                            }
                        },
                    )
                }

            assertTrue(healSyncEntered.await(2, TimeUnit.SECONDS))

            val mutation =
                executor.submit<Result<PolicyMutationResult>> {
                    PolicyMutationCoordinator.mutate(
                        preflight = {},
                        mutation = {
                            mutationEntered.countDown()
                            canonical.set("B")
                            Result.success(JSONObject().put("generation", "B"))
                        },
                        synchronizeCompatibility = { state ->
                            markers.set(state.getString("generation"))
                            Result.success(Unit)
                        },
                    )
                }

            assertFalse(
                "canonical mutation must wait until startup heal releases the shared policy monitor",
                mutationEntered.await(150, TimeUnit.MILLISECONDS),
            )

            releaseHealSync.countDown()
            assertTrue(heal.get(2, TimeUnit.SECONDS).isSuccess)
            assertTrue(mutation.get(2, TimeUnit.SECONDS).isSuccess)
            assertTrue(mutationEntered.await(2, TimeUnit.SECONDS))
            assertEquals("B", canonical.get())
            assertEquals("B", markers.get())
        } finally {
            releaseHealSync.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `compatibility failure preserves canonical result but marks mutation pending`() {
        val state = JSONObject().put("generation", 42)

        val result =
            PolicyMutationCoordinator.mutate(
                preflight = {},
                mutation = { Result.success(state) },
                synchronizeCompatibility = { Result.failure(IOException("marker write failed")) },
            )

        assertTrue(result.isSuccess)
        val value = result.getOrThrow()
        assertEquals(42, value.state.getInt("generation"))
        assertEquals(CompatibilitySyncStatus.PENDING, value.compatibilitySync)
        assertTrue(value.compatibilityError is IOException)
    }

    @Test
    fun `thrown mutation failure stays inside Result and skips compatibility sync`() {
        var synchronized = false

        val result =
            PolicyMutationCoordinator.mutate(
                preflight = {},
                mutation = { throw IOException("mutation threw") },
                synchronizeCompatibility = {
                    synchronized = true
                    Result.success(Unit)
                },
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(synchronized)
    }

    @Test
    fun `startup state provider failure stays inside Result`() {
        var synchronized = false

        val result =
            PolicyMutationCoordinator.synchronizeCurrentCompatibility(
                stateProvider = { throw IOException("state read failed") },
                synchronizeCompatibility = {
                    synchronized = true
                    Result.success(Unit)
                },
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(synchronized)
    }

    @Test
    fun `pending compatibility response preserves canonical state and exposes retry warning`() {
        val result =
            PolicyMutationResult(
                state = JSONObject().put("generation", 42).put("features", JSONObject().put("buildIdentity", true)),
                compatibilitySync = CompatibilitySyncStatus.PENDING,
                compatibilityError = IOException("marker write failed"),
            )

        val response = PolicyApi.mutationResponse(result)

        assertEquals(42, response.getInt("generation"))
        assertTrue(response.getJSONObject("features").getBoolean("buildIdentity"))
        assertEquals("pending", response.getString("compatibilitySync"))
        assertTrue(response.getString("compatibilityWarning").contains("Retry before reboot"))
    }

    @Test
    fun `successful compatibility response reports ok without warning`() {
        val result =
            PolicyMutationResult(
                state = JSONObject().put("generation", 43),
                compatibilitySync = CompatibilitySyncStatus.OK,
            )

        val response = PolicyApi.mutationResponse(result)

        assertEquals(43, response.getInt("generation"))
        assertEquals("ok", response.getString("compatibilitySync"))
        assertFalse(response.has("compatibilityWarning"))
    }

    @Test
    fun `preflight failure rejects mutation before canonical persistence`() {
        var mutated = false

        val result =
            PolicyMutationCoordinator.mutate(
                preflight = { throw IOException("unsafe marker") },
                mutation = {
                    mutated = true
                    Result.success(JSONObject())
                },
                synchronizeCompatibility = { Result.success(Unit) },
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CompatibilityPreflightException)
        assertFalse(mutated)
    }

    @Test
    fun `enable transition captures its group before applying live identity`() {
        val calls = mutableListOf<String>()
        val result =
            IdentityCoordinator.reconcilePolicyTransition(
                root = java.io.File("."),
                before = policyState(build = false, region = false),
                after = policyState(build = true, region = false),
                capture = { _, build, region ->
                    calls += "capture:$build:$region"
                    Result.success(snapshot())
                },
                applyRuntime = {
                    calls += "apply"
                    runtimeResult()
                },
            )

        assertTrue(result.isSuccess)
        assertEquals(listOf("capture:true:false", "apply"), calls)
    }

    @Test
    fun `region enable captures only the region group before applying live identity`() {
        val calls = mutableListOf<String>()
        val result =
            IdentityCoordinator.reconcilePolicyTransition(
                root = java.io.File("."),
                before = policyState(build = false, region = false),
                after = policyState(build = false, region = true),
                capture = { _, build, region ->
                    calls += "capture:$build:$region"
                    Result.success(snapshot())
                },
                applyRuntime = {
                    calls += "apply"
                    runtimeResult()
                },
            )

        assertTrue(result.isSuccess)
        assertEquals(listOf("capture:false:true", "apply"), calls)
    }

    @Test
    fun `disable transition restores only its group`() {
        val calls = mutableListOf<String>()
        val result =
            IdentityCoordinator.reconcilePolicyTransition(
                root = java.io.File("."),
                before = policyState(build = true, region = true),
                after = policyState(build = false, region = true),
                restoreRuntime = { _, build, region ->
                    calls += "restore:$build:$region"
                    runtimeResult()
                },
                applyRuntime = {
                    calls += "apply"
                    runtimeResult()
                },
            )

        assertTrue(result.isSuccess)
        assertEquals(listOf("restore:true:false", "apply"), calls)
    }

    @Test
    fun `snapshot capture failure skips live apply and preserves committed compatibility response`() {
        var liveApplyCalled = false
        val result =
            PolicyMutationCoordinator.mutate(
                preflight = {},
                captureBefore = { policyState(build = false, region = false) },
                mutation = { Result.success(policyState(build = true, region = false)) },
                synchronizeCompatibility = { Result.success(Unit) },
                reconcileRuntime = { before, after ->
                    IdentityCoordinator.reconcilePolicyTransition(
                        java.io.File("."),
                        before,
                        after,
                        capture = { _, _, _ -> Result.failure(IOException("snapshot failed")) },
                        applyRuntime = {
                            liveApplyCalled = true
                            runtimeResult()
                        },
                    )
                },
            )

        assertTrue(result.isSuccess)
        assertFalse(liveApplyCalled)
        val response = PolicyApi.mutationResponse(result.getOrThrow())
        assertEquals("ok", response.getString("compatibilitySync"))
        assertEquals("snapshot_unavailable", response.getJSONObject("runtimeTransition").getString("error"))
        assertTrue(response.getJSONObject("runtimeTransition").getBoolean("rebootRequired"))
        assertTrue(response.getString("runtimeWarning").contains("Policy is saved"))
    }

    @Test
    fun `runtime transition receives an immutable pre-mutation policy snapshot`() {
        val captured = policyState(build = false, region = false)
        var transitionBefore: JSONObject? = null

        val result =
            PolicyMutationCoordinator.mutate(
                preflight = {},
                captureBefore = { captured },
                mutation = {
                    captured.getJSONObject("features").put("buildIdentity", true)
                    Result.success(captured)
                },
                synchronizeCompatibility = { Result.success(Unit) },
                reconcileRuntime = { before, _ ->
                    transitionBefore = before
                    Result.success(IdentityCoordinator.TransitionOutcome(true, null, null))
                },
            )

        assertTrue(result.isSuccess)
        assertFalse(requireNotNull(transitionBefore).getJSONObject("features").getBoolean("buildIdentity"))
        assertTrue(result.getOrThrow().state.getJSONObject("features").getBoolean("buildIdentity"))
    }

    @Test
    fun `non-snapshot runtime errors retain the committed policy with an accurate failure code`() {
        val response =
            PolicyApi.mutationResponse(
                PolicyMutationResult(
                    state = policyState(build = true, region = false),
                    compatibilitySync = CompatibilitySyncStatus.OK,
                    runtimeTransitionError = IOException("shell unavailable"),
                ),
            )

        assertTrue(response.getJSONObject("features").getBoolean("buildIdentity"))
        assertEquals("ok", response.getString("compatibilitySync"))
        assertEquals("runtime_transition_failed", response.getJSONObject("runtimeTransition").getString("error"))
        assertTrue(response.getJSONObject("runtimeTransition").getBoolean("rebootRequired"))
    }

    @Test
    fun `compatibility refresh failure after persistence does not turn committed mutation into failure`() {
        val result =
            PolicyMutationCoordinator.mutate(
                preflight = {},
                captureBefore = { policyState(build = false, region = false) },
                mutation = { Result.success(policyState(build = true, region = false)) },
                synchronizeCompatibility = { Result.failure(IOException("presentation refresh failed")) },
                reconcileRuntime = { _, _ ->
                    Result.success(IdentityCoordinator.TransitionOutcome(true, null, null))
                },
            )

        assertTrue(result.isSuccess)
        val response = PolicyApi.mutationResponse(result.getOrThrow())
        assertTrue(response.getJSONObject("features").getBoolean("buildIdentity"))
        assertEquals("pending", response.getString("compatibilitySync"))
    }

    @Test
    fun `runtime reconciliation does not retain the policy monitor`() {
        val runtimeEntered = CountDownLatch(1)
        val releaseRuntime = CountDownLatch(1)
        val policyMonitorAvailable = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val mutation =
                executor.submit<Result<PolicyMutationResult>> {
                    PolicyMutationCoordinator.mutate(
                        preflight = {},
                        captureBefore = { policyState(build = false, region = false) },
                        mutation = { Result.success(policyState(build = true, region = false)) },
                        synchronizeCompatibility = { Result.success(Unit) },
                        reconcileRuntime = { _, _ ->
                            runtimeEntered.countDown()
                            releaseRuntime.await(2, TimeUnit.SECONDS)
                            Result.success(IdentityCoordinator.TransitionOutcome(true, null, null))
                        },
                    )
                }
            assertTrue(runtimeEntered.await(2, TimeUnit.SECONDS))

            executor.submit {
                synchronized(PolicyState) { policyMonitorAvailable.countDown() }
            }
            assertTrue(
                "runtime I/O must run outside the PolicyState monitor",
                policyMonitorAvailable.await(2, TimeUnit.SECONDS),
            )

            releaseRuntime.countDown()
            assertTrue(mutation.get(2, TimeUnit.SECONDS).isSuccess)
        } finally {
            releaseRuntime.countDown()
            executor.shutdownNow()
        }
    }

    private fun policyState(build: Boolean, region: Boolean): JSONObject =
        JSONObject().put("features", JSONObject().put("buildIdentity", build).put("regionIdentity", region))

    private fun runtimeResult(): IdentityRuntimeApplier.Result =
        IdentityRuntimeApplier.Result(true, false, false, "test")

    private fun snapshot(): IdentityRuntimeSnapshot.Snapshot =
        IdentityRuntimeSnapshot.Snapshot("00000000-0000-0000-0000-000000000000", true, false, emptyMap())
}
