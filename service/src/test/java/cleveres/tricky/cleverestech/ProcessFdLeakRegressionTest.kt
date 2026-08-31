package cleveres.tricky.cleverestech

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory
import com.sun.management.UnixOperatingSystemMXBean
import cleveres.tricky.cleverestech.RuntimeDiagnostics
import java.io.File

class ProcessFdLeakRegressionTest {

    @Test
    fun testProcessBuilderFdLeak() {
        val os = ManagementFactory.getOperatingSystemMXBean()
        if (os !is UnixOperatingSystemMXBean) {
            println("Not a Unix OS, skipping FD leak test")
            return
        }
        
        // Warm up to stabilize FD count
        val dummyRoot = File("")
        for (i in 1..5) {
            runCatching { RuntimeDiagnostics.healthJson(dummyRoot) }
        }
        System.gc()
        Thread.sleep(100)

        val initialFdCount = os.openFileDescriptorCount
        
        for (i in 1..50) {
            runCatching {
                RuntimeDiagnostics.healthJson(dummyRoot)
            }
        }
        
        val finalFdCount = os.openFileDescriptorCount
        val diff = finalFdCount - initialFdCount
        assertTrue("FD leak detected! FDs increased by $diff", diff < 20)
    }
}
