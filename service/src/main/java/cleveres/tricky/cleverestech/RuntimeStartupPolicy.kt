package cleveres.tricky.cleverestech

import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay

internal const val MAX_RUNTIME_STARTUP_RETRY_ATTEMPTS = 8
internal const val MAX_RUNTIME_STARTUP_RETRY_DELAY_MS = 300_000L

internal enum class RuntimeStartupFailureMode {
    FATAL,
    CONTINUE_AND_RETRY,
}

internal class RuntimeStartupTask(
    val name: String,
    val failureMode: RuntimeStartupFailureMode,
    private val attemptBlock: () -> Boolean,
    val retryDelaysMs: List<Long> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "startup task name must not be blank" }
        when (failureMode) {
            RuntimeStartupFailureMode.FATAL ->
                require(retryDelaysMs.isEmpty()) { "fatal startup tasks must not define retries" }
            RuntimeStartupFailureMode.CONTINUE_AND_RETRY -> {
                require(retryDelaysMs.isNotEmpty()) { "retryable startup tasks require a retry schedule" }
                require(retryDelaysMs.size <= MAX_RUNTIME_STARTUP_RETRY_ATTEMPTS) {
                    "startup retry schedule exceeds the attempt limit"
                }
                require(retryDelaysMs.all { it in 1L..MAX_RUNTIME_STARTUP_RETRY_DELAY_MS }) {
                    "startup retry delay is outside the supported range"
                }
            }
        }
    }

    fun attempt(): Boolean = attemptBlock()
}

internal data class RuntimeStartupResult(
    val task: RuntimeStartupTask,
    val ready: Boolean,
    val failure: Exception? = null,
)

internal object RuntimeStartupPolicy {
    fun evaluate(tasks: Iterable<RuntimeStartupTask>): List<RuntimeStartupResult> =
        tasks.map(::attempt)

    fun fatalFailures(results: Iterable<RuntimeStartupResult>): List<RuntimeStartupResult> =
        results.filter { !it.ready && it.task.failureMode == RuntimeStartupFailureMode.FATAL }

    fun retryableFailures(results: Iterable<RuntimeStartupResult>): List<RuntimeStartupResult> =
        results.filter {
            !it.ready && it.task.failureMode == RuntimeStartupFailureMode.CONTINUE_AND_RETRY
        }

    fun canEnterCoreRuntime(results: Iterable<RuntimeStartupResult>): Boolean =
        fatalFailures(results).isEmpty()

    suspend fun retryBounded(
        task: RuntimeStartupTask,
        wait: suspend (Long) -> Unit = { delay(it) },
        onAttempt: (RuntimeStartupResult) -> Unit = {},
    ): RuntimeStartupResult {
        require(task.failureMode == RuntimeStartupFailureMode.CONTINUE_AND_RETRY) {
            "only retryable startup tasks can be retried"
        }
        var result = RuntimeStartupResult(task = task, ready = false)
        for (delayMs in task.retryDelaysMs) {
            wait(delayMs)
            result = attempt(task)
            onAttempt(result)
            if (result.ready) return result
        }
        return result
    }

    private fun attempt(task: RuntimeStartupTask): RuntimeStartupResult =
        try {
            RuntimeStartupResult(task = task, ready = task.attempt())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            RuntimeStartupResult(task = task, ready = false, failure = error)
        }
}
