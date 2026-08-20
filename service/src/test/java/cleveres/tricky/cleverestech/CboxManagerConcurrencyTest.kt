package cleveres.tricky.cleverestech

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CboxManagerConcurrencyTest {
    @Test
    fun `unlock waits for managed file mutation coordinator`() {
        val attempted = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        val worker =
            Thread {
                attempted.countDown()
                try {
                    result.set(CboxManager.unlock("../invalid.cbox", "password", null))
                } finally {
                    finished.countDown()
                }
            }

        synchronized(ManagedFileCoordinator.monitor) {
            worker.start()
            assertTrue(attempted.await(2, TimeUnit.SECONDS))
            assertFalse(finished.await(100, TimeUnit.MILLISECONDS))
        }

        assertTrue(finished.await(2, TimeUnit.SECONDS))
        worker.join(2_000)
        assertFalse(worker.isAlive)
        assertFalse(result.get())
    }
}
