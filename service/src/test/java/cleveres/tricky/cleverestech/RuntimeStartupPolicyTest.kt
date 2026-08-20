package cleveres.tricky.cleverestech

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuntimeStartupPolicyTest {
    @Test
    fun `only unavailable fatal tasks block the core runtime`() {
        val readyFatal = fatalTask("ready") { true }
        val unavailableRetryable = retryableTask("degraded") { false }
        val unavailableFatal = fatalTask("required") { false }

        val allowed = RuntimeStartupPolicy.evaluate(listOf(readyFatal, unavailableRetryable))
        assertTrue(RuntimeStartupPolicy.canEnterCoreRuntime(allowed))
        assertEquals(listOf("degraded"), RuntimeStartupPolicy.retryableFailures(allowed).map { it.task.name })

        val blocked = RuntimeStartupPolicy.evaluate(listOf(readyFatal, unavailableRetryable, unavailableFatal))
        assertFalse(RuntimeStartupPolicy.canEnterCoreRuntime(blocked))
        assertEquals(listOf("required"), RuntimeStartupPolicy.fatalFailures(blocked).map { it.task.name })
    }

    @Test
    fun `task exceptions become explicit unavailable state without changing failure policy`() {
        val cause = IllegalStateException("synthetic")
        val retryable = retryableTask("degraded") { throw cause }
        val result = RuntimeStartupPolicy.evaluate(listOf(retryable)).single()

        assertFalse(result.ready)
        assertSame(cause, result.failure)
        assertTrue(RuntimeStartupPolicy.canEnterCoreRuntime(listOf(result)))
        assertEquals(listOf(result), RuntimeStartupPolicy.retryableFailures(listOf(result)))
    }

    @Test
    fun `bounded retry stops immediately after recovery`() = runBlocking {
        var attempts = 0
        val waits = mutableListOf<Long>()
        val task =
            retryableTask(
                name = "recovering",
                delays = listOf(10L, 20L, 30L),
            ) {
                attempts++
                attempts == 2
            }

        val result =
            RuntimeStartupPolicy.retryBounded(
                task = task,
                wait = { waits += it },
            )

        assertTrue(result.ready)
        assertEquals(2, attempts)
        assertEquals(listOf(10L, 20L), waits)
    }

    @Test
    fun `bounded retry exhausts a finite schedule without extra attempts`() = runBlocking {
        var attempts = 0
        val waits = mutableListOf<Long>()
        val task =
            retryableTask(
                name = "persistent",
                delays = listOf(10L, 20L, 30L),
            ) {
                attempts++
                false
            }

        val result =
            RuntimeStartupPolicy.retryBounded(
                task = task,
                wait = { waits += it },
            )

        assertFalse(result.ready)
        assertEquals(3, attempts)
        assertEquals(listOf(10L, 20L, 30L), waits)
    }

    @Test
    fun `retry cancellation propagates before another attempt`() {
        val cancellation = CancellationException("synthetic cancellation")
        var attempts = 0
        val task = retryableTask("cancelled") { attempts++ > 0 }

        try {
            runBlocking {
                RuntimeStartupPolicy.retryBounded(
                    task = task,
                    wait = { throw cancellation },
                )
            }
            fail("cancellation must propagate")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
        assertEquals(0, attempts)
    }

    @Test
    fun `retry schedules are bounded at construction`() {
        assertInvalidTask {
            RuntimeStartupTask(
                name = "missing-schedule",
                failureMode = RuntimeStartupFailureMode.CONTINUE_AND_RETRY,
                attemptBlock = { false },
            )
        }
        assertInvalidTask {
            RuntimeStartupTask(
                name = "too-many-attempts",
                failureMode = RuntimeStartupFailureMode.CONTINUE_AND_RETRY,
                attemptBlock = { false },
                retryDelaysMs = List(MAX_RUNTIME_STARTUP_RETRY_ATTEMPTS + 1) { 1L },
            )
        }
        assertInvalidTask {
            RuntimeStartupTask(
                name = "oversized-delay",
                failureMode = RuntimeStartupFailureMode.CONTINUE_AND_RETRY,
                attemptBlock = { false },
                retryDelaysMs = listOf(MAX_RUNTIME_STARTUP_RETRY_DELAY_MS + 1L),
            )
        }
        assertInvalidTask {
            RuntimeStartupTask(
                name = "fatal-with-retry",
                failureMode = RuntimeStartupFailureMode.FATAL,
                attemptBlock = { false },
                retryDelaysMs = listOf(1L),
            )
        }
    }

    private fun fatalTask(
        name: String,
        attempt: () -> Boolean,
    ): RuntimeStartupTask =
        RuntimeStartupTask(
            name = name,
            failureMode = RuntimeStartupFailureMode.FATAL,
            attemptBlock = attempt,
        )

    private fun retryableTask(
        name: String,
        delays: List<Long> = listOf(1L),
        attempt: () -> Boolean,
    ): RuntimeStartupTask =
        RuntimeStartupTask(
            name = name,
            failureMode = RuntimeStartupFailureMode.CONTINUE_AND_RETRY,
            attemptBlock = attempt,
            retryDelaysMs = delays,
        )

    private fun assertInvalidTask(block: () -> Unit) {
        val thrown =
            try {
                block()
                null
            } catch (caught: IllegalArgumentException) {
                caught
            }
        assertNotNull(thrown)
    }
}
