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
 * Extends PolicyState's own monitor across preflight, canonical persistence and
 * legacy-marker synchronization so concurrent mutations cannot commit markers
 * out of order relative to the published V2 state.
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

            mutation().map { state ->
                val compatibilityResult = synchronizeCompatibility(state)
                PolicyMutationResult(
                    state = state,
                    compatibilitySync =
                        if (compatibilityResult.isSuccess) CompatibilitySyncStatus.OK else CompatibilitySyncStatus.PENDING,
                    compatibilityError = compatibilityResult.exceptionOrNull(),
                )
            }
        }
}
