package cleveres.tricky.cleverestech

import org.json.JSONObject

internal class CompatibilityPreflightException(cause: Throwable) :
    IllegalStateException("Identity compatibility state is unsafe", cause)

internal enum class CompatibilitySyncStatus {
    OK,
    PENDING,
}

internal data class PolicyMutationResult(
    val state: JSONObject,
    val compatibilitySync: CompatibilitySyncStatus,
    val compatibilityError: Throwable? = null,
    val previousState: JSONObject? = null,
    val runtimeTransition: IdentityCoordinator.TransitionOutcome? = null,
    val runtimeTransitionError: Throwable? = null,
)

/**
 * Extends PolicyState's own monitor across canonical state reads/mutations and
 * legacy-marker synchronization so no compatibility writer can publish markers
 * out of order relative to the V2 state that produced them.
 */
internal object PolicyMutationCoordinator {
    /* Serializes complete policy-to-runtime transitions without holding PolicyState's monitor over I/O. */
    private val transitionLock = Any()

    fun mutate(
        preflight: () -> Unit,
        mutation: () -> Result<JSONObject>,
        synchronizeCompatibility: (JSONObject) -> Result<Unit>,
        captureBefore: (() -> JSONObject)? = null,
        reconcileRuntime:
            ((previous: JSONObject, resulting: JSONObject) -> Result<IdentityCoordinator.TransitionOutcome>)? = null,
    ): Result<PolicyMutationResult> =
        synchronized(transitionLock) {
            val committed =
                synchronized(PolicyState) {
                    val previous =
                        try {
                            // A policy mutation may publish a new snapshot by mutating an object returned
                            // from a test or compatibility adapter. Keep the transition's "before" value
                            // detached from that object while the canonical PolicyState monitor is held.
                            // The runtime transition happens later, after this monitor is released.
                            captureBefore?.invoke()?.let { JSONObject(it.toString()) }
                        } catch (error: Throwable) {
                            return@synchronized Result.failure(error)
                        }
                    try {
                        preflight()
                    } catch (error: Throwable) {
                        return@synchronized Result.failure(CompatibilityPreflightException(error))
                    }

                    runCatching { mutation().getOrThrow() }.map { state ->
                        val compatibilityResult = runCatching { synchronizeCompatibility(state).getOrThrow() }
                        PolicyMutationResult(
                            state = state,
                            compatibilitySync =
                                if (compatibilityResult.isSuccess) {
                                    CompatibilitySyncStatus.OK
                                } else {
                                    CompatibilitySyncStatus.PENDING
                                },
                            compatibilityError = compatibilityResult.exceptionOrNull(),
                            previousState = previous,
                        )
                    }
                }
            committed.map { result ->
                val previous = result.previousState
                if (previous == null || reconcileRuntime == null) {
                    result
                } else {
                    reconcileRuntime(previous, result.state).fold(
                        onSuccess = { transition -> result.copy(runtimeTransition = transition) },
                        onFailure = { error -> result.copy(runtimeTransitionError = error) },
                    )
                }
            }
        }

    fun synchronizeCurrentCompatibility(
        stateProvider: () -> JSONObject = { PolicyState.stateJson() },
        synchronizeCompatibility: (JSONObject) -> Result<Unit>,
    ): Result<Unit> =
        synchronized(PolicyState) {
            runCatching {
                val state = stateProvider()
                synchronizeCompatibility(state).getOrThrow()
            }
        }
}
