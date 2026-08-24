package cleveres.tricky.cleverestech

import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/** Single owner for Identity fetch, persistence, live apply/rollback and diagnostics. */
internal object IdentityCoordinator {
    data class RefreshOutcome(
        val identity: AutoIdentityManager.Result,
        val runtime: IdentityRuntimeApplier.Result?,
        val globalPersisted: Boolean,
        val profilePersisted: Boolean,
    )

    data class TransitionOutcome(
        val rollbackPrepared: Boolean,
        val restore: IdentityRuntimeApplier.Result?,
        val apply: IdentityRuntimeApplier.Result?,
    ) {
        val rebootRequired: Boolean
            get() = restore?.rebootRequired == true || apply?.rebootRequired == true

        fun toJson(): JSONObject =
            JSONObject()
                .put("rollbackPrepared", rollbackPrepared)
                .put("rebootRequired", rebootRequired)
                .put("restore", restore?.toJson() ?: JSONObject.NULL)
                .put("apply", apply?.toJson() ?: JSONObject.NULL)
    }

    private data class TopLevelIdentity(
        val build: Boolean,
        val region: Boolean,
    )

    private val fetchLock = Any()
    private var fetchFlight: CompletableFuture<AutoIdentityManager.Result>? = null

    @Volatile
    private var lastAttemptMs = 0L

    @Volatile
    private var lastSuccessMs = 0L

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var lastScope = "none"

    @Volatile
    private var lastRuntime: IdentityRuntimeApplier.Result? = null

    fun initialize(root: File) {
        runCatching { IdentityRuntimeSnapshot.read(root) }
            .onFailure {
                Logger.w("Discarding unreadable live Identity rollback metadata")
                runCatching { IdentityRuntimeSnapshot.clear(root) }
            }
    }

    fun refresh(
        root: File,
        persistGlobal: Boolean,
        persistProfile: Boolean,
        liveApplyGlobal: Boolean,
        fetcher: () -> AutoIdentityManager.Result = { AutoIdentityManager.fetchLatest() },
    ): Result<RefreshOutcome> {
        require(persistGlobal || persistProfile) { "Identity refresh has no persistence scope" }
        lastAttemptMs = System.currentTimeMillis()
        lastScope =
            when {
                persistGlobal && persistProfile -> "global+profile"
                persistGlobal -> "global"
                else -> "profile"
            }
        return runCatching {
            val resolved = fetchShared(fetcher)
            if (persistProfile) ProfileAutoIdentityStore.save(root, resolved).getOrThrow()
            if (persistGlobal) AutoIdentityPersistence.save(root, resolved).getOrThrow()
            val runtime =
                if (liveApplyGlobal && persistGlobal && PolicyState.isTopLevelFeatureEnabled(PolicyState.Feature.BUILD_IDENTITY)) {
                    IdentityRuntimeApplier.apply(root)
                } else {
                    null
                }
            lastRuntime = runtime
            lastSuccessMs = System.currentTimeMillis()
            lastError = null
            RefreshOutcome(resolved, runtime, persistGlobal, persistProfile)
        }.onFailure { error ->
            lastError = error.javaClass.simpleName
            Logger.e("Identity refresh failed", error)
        }
    }

    fun reconcilePolicyTransition(
        root: File,
        before: JSONObject,
        after: JSONObject,
    ): TransitionOutcome {
        val previous = topLevel(before)
        val current = topLevel(after)
        val enableBuild = !previous.build && current.build
        val enableRegion = !previous.region && current.region
        val disableBuild = previous.build && !current.build
        val disableRegion = previous.region && !current.region
        if (!enableBuild && !enableRegion && !disableBuild && !disableRegion) {
            return TransitionOutcome(rollbackPrepared = true, restore = null, apply = null)
        }

        val rollbackPrepared =
            if (enableBuild || enableRegion) {
                IdentityRuntimeSnapshot.capture(root, enableBuild, enableRegion).isSuccess
            } else {
                true
            }
        val restore =
            if (disableBuild || disableRegion) {
                IdentityRuntimeApplier.restore(root, disableBuild, disableRegion)
            } else {
                null
            }
        val apply =
            if (current.build || current.region) {
                IdentityRuntimeApplier.apply(root)
            } else {
                null
            }
        lastRuntime = apply ?: restore
        return TransitionOutcome(rollbackPrepared, restore, apply)
    }

    fun diagnosticsJson(): JSONObject =
        JSONObject()
            .put("lastAttemptMs", lastAttemptMs)
            .put("lastSuccessMs", lastSuccessMs)
            .put("lastError", lastError ?: JSONObject.NULL)
            .put("lastScope", lastScope)
            .put("fetchInFlight", synchronized(fetchLock) { fetchFlight?.isDone == false })
            .put("lastRuntime", lastRuntime?.toJson() ?: JSONObject.NULL)

    private fun fetchShared(fetcher: () -> AutoIdentityManager.Result): AutoIdentityManager.Result {
        var owner = false
        val flight =
            synchronized(fetchLock) {
                fetchFlight?.takeIf { !it.isDone }
                    ?: CompletableFuture<AutoIdentityManager.Result>().also {
                        fetchFlight = it
                        owner = true
                    }
            }
        if (owner) {
            try {
                flight.complete(fetcher())
            } catch (error: Throwable) {
                flight.completeExceptionally(error)
            } finally {
                synchronized(fetchLock) {
                    if (fetchFlight === flight) fetchFlight = null
                }
            }
        }
        return try {
            flight.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    private fun topLevel(state: JSONObject): TopLevelIdentity {
        val features = state.optJSONObject("features") ?: JSONObject()
        return TopLevelIdentity(
            build = features.optBoolean("buildIdentity", false),
            region = features.optBoolean("regionIdentity", false),
        )
    }
}
