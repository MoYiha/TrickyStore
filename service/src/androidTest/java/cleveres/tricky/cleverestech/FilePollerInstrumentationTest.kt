package cleveres.tricky.cleverestech

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FilePollerInstrumentationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun benchmarkDetectionLatency() {
        val testFile = tempFolder.newFile("benchmark_poller.txt")
        testFile.writeText("initial")

        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()

        // Use default interval (5000ms) to show that efficient polling (FileObserver) works
        // If it falls back to polling, this test will likely fail (timeout > 200ms)
        val poller = FilePoller(testFile) {
            latch.countDown()
        }
        poller.start()

        // Wait a bit to ensure poller is ready
        Thread.sleep(100)

        // Modify file
        testFile.writeText("modified")
        testFile.setLastModified(System.currentTimeMillis())

        // Expect detection within 200ms (should fail with 5s polling, pass with FileObserver)
        val detected = latch.await(200, TimeUnit.MILLISECONDS)
        val duration = System.currentTimeMillis() - startTime

        poller.stop()

        if (!detected) {
            println("Benchmark: Detection timed out (latency > 200ms). Expected if falling back to polling.")
        } else {
            println("Benchmark: Detection took ${duration}ms")
        }

        assertTrue("Detection took too long: ${duration}ms. Expected near-instant detection with FileObserver.", detected)
    }
}
