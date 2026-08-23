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
)

/**
 * Extends PolicyState's own monitor across canonical state reads/mutations and
 * legacy-marker synchronization so no compatibility writer can publish markers
 * out of order relative to the V2 state that produced them.
 */
internal object PolicyMutationCoordinator {
    fun mutate(
        preflight: () -> Unit,
        mutation: () -> Result<JSONObject>,
        synchronizeCompatibility: (JSONObject) -> Result<Unit>,
    ): Result<PolicyMutationResult> =
        synchronized(PolicyState) {
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
                        if (compatibilityResult.isSuccess) CompatibilitySyncStatus.OK else CompatibilitySyncStatus.PENDING,
                    compatibilityError = compatibilityResult.exceptionOrNull(),
                )
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
