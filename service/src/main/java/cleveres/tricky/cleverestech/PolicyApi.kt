package cleveres.tricky.cleverestech

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

internal object PolicyApi {
    fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response? {
        val uri = session.uri
        val method = session.method
        if (uri == "/api/policy_state" && method == NanoHTTPD.Method.GET) {
            return json(NanoHTTPD.Response.Status.OK, currentPolicyResponse())
        }
        if (uri == "/api/policy_state" && method == NanoHTTPD.Method.POST) {
            val data = parameter(session, "data")
                ?: return text(NanoHTTPD.Response.Status.BAD_REQUEST, "Missing policy state")
            return mutatePolicy("Invalid policy state") { PolicyState.replaceFromJson(data) }
        }
        if (uri == "/api/policy_compatibility" && method == NanoHTTPD.Method.POST) {
            return retryCompatibilitySync()
        }
        if (uri == "/api/effective_state" && method == NanoHTTPD.Method.GET) {
            val packageName = parameter(session, "package")
                ?: return text(NanoHTTPD.Response.Status.BAD_REQUEST, "Missing package")
            return runCatching { PolicyState.effectiveStateJson(packageName) }.fold(
                onSuccess = { json(NanoHTTPD.Response.Status.OK, it) },
                onFailure = { text(NanoHTTPD.Response.Status.BAD_REQUEST, it.message ?: "Invalid package") },
            )
        }
        if (uri == "/api/profile_v2" && method == NanoHTTPD.Method.POST) {
            val action = parameter(session, "action")
                ?: return text(NanoHTTPD.Response.Status.BAD_REQUEST, "Missing profile action")
            val data = parameter(session, "data") ?: "{}"
            return runCatching { JSONObject(data) }.fold(
                onSuccess = { payload ->
                    mutatePolicy("Invalid profile request") { PolicyState.profileAction(action, payload) }
                },
                onFailure = { text(NanoHTTPD.Response.Status.BAD_REQUEST, "Invalid profile request") },
            )
        }
        return null
    }

    private fun mutatePolicy(
        invalidMessage: String,
        mutation: () -> Result<JSONObject>,
    ): NanoHTTPD.Response =
        PolicyMutationCoordinator.mutate(
            preflight = { LegacyIdentityMarkers.preflight(Config.getConfigRoot()) },
            mutation = mutation,
            synchronizeCompatibility = { state ->
                LegacyIdentityMarkers.syncFromPolicyState(Config.getConfigRoot(), state)
            },
        ).fold(
            onSuccess = { result ->
                if (result.compatibilitySync == CompatibilitySyncStatus.PENDING) {
                    result.compatibilityError?.let { error ->
                        Logger.e("Policy state saved but early-boot identity markers could not be synchronized", error)
                    }
                }
                json(NanoHTTPD.Response.Status.OK, mutationResponse(result))
            },
            onFailure = { error ->
                if (error is CompatibilityPreflightException) {
                    Logger.e("Refusing policy mutation because identity compatibility markers are unsafe", error.cause ?: error)
                    text(NanoHTTPD.Response.Status.BAD_REQUEST, "Identity compatibility state is unsafe")
                } else {
                    text(NanoHTTPD.Response.Status.BAD_REQUEST, error.message ?: invalidMessage)
                }
            },
        )

    private fun currentPolicyResponse(): JSONObject =
        synchronized(PolicyState) {
            val state = PolicyState.stateJson()
            val root = Config.getConfigRoot()
            val synchronizedResult = LegacyIdentityMarkers.isSynchronized(root, state)
            val result =
                synchronizedResult.fold(
                    onSuccess = { synchronized ->
                        PolicyMutationResult(
                            state = state,
                            compatibilitySync = if (synchronized) CompatibilitySyncStatus.OK else CompatibilitySyncStatus.PENDING,
                        )
                    },
                    onFailure = { error ->
                        PolicyMutationResult(
                            state = state,
                            compatibilitySync = CompatibilitySyncStatus.PENDING,
                            compatibilityError = error,
                        )
                    },
                )
            mutationResponse(result)
        }

    private fun retryCompatibilitySync(): NanoHTTPD.Response =
        synchronized(PolicyState) {
            val state = PolicyState.stateJson()
            val root = Config.getConfigRoot()
            val compatibilityResult =
                if (!state.optString("source").equals("v2", true)) {
                    Result.success(Unit)
                } else {
                    runCatching {
                        LegacyIdentityMarkers.preflight(root)
                        LegacyIdentityMarkers.syncFromPolicyState(root, state).getOrThrow()
                    }
                }
            val result =
                PolicyMutationResult(
                    state = state,
                    compatibilitySync =
                        if (compatibilityResult.isSuccess) CompatibilitySyncStatus.OK else CompatibilitySyncStatus.PENDING,
                    compatibilityError = compatibilityResult.exceptionOrNull(),
                )
            if (result.compatibilitySync == CompatibilitySyncStatus.PENDING) {
                result.compatibilityError?.let { error ->
                    Logger.e("Retrying early-boot identity compatibility synchronization failed", error)
                }
            }
            json(NanoHTTPD.Response.Status.OK, mutationResponse(result))
        }

    internal fun mutationResponse(result: PolicyMutationResult): JSONObject {
        val response = JSONObject(result.state.toString())
        val pending = result.compatibilitySync == CompatibilitySyncStatus.PENDING
        response.put("compatibilitySync", if (pending) "pending" else "ok")
        if (pending) {
            response.put(
                "compatibilityWarning",
                "Policy is saved, but early-boot compatibility markers are not synchronized. Retry before reboot.",
            )
        }
        return response
    }

    private fun parameter(session: NanoHTTPD.IHTTPSession, name: String): String? =
        session.parameters[name]?.singleOrNull()?.takeIf { it.length <= 1024 * 1024 }

    private fun json(status: NanoHTTPD.Response.Status, value: JSONObject): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(status, "application/json", value.toString())

    private fun text(status: NanoHTTPD.Response.Status, value: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, value)
}
