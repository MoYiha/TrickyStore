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
                            if (!releaseFirstSync.await(2, TimeUnit.SECONDS)) {
                                return@mutate Result.failure(AssertionError("Timed out waiting to release first sync"))
                            }
                            markers.set(state.getString("generation"))
                            Result.success(Unit)
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
            assertFalse("second canonical mutation must wait for first marker sync", secondMutationEntered.await(150, TimeUnit.MILLISECONDS))

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
}
