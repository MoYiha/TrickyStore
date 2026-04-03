package cleveres.tricky.cleverestech

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.*

class WebServerDrmResetPerfTest {

    @Test
    fun testFileWalkBaseline() {
        val testDir = File(System.getProperty("java.io.tmpdir"), "test_drm_reset_perf")
        testDir.deleteRecursively()
        testDir.mkdirs()

        for (i in 1..10) {
            val d1 = File(testDir, "dir\$i")
            d1.mkdirs()
            for (j in 1..20) {
                val d2 = File(d1, "subdir\$j")
                d2.mkdirs()
                for (k in 1..5) {
                    File(d2, "file\$k.txt").writeText("test")
                }
            }
        }

        val totalFiles = testDir.walkBottomUp().count()
        println("Created \$totalFiles files/dirs")

        val time = measureTimeMillis {
            var cleaned = 0
            testDir.walkBottomUp().forEach {
                if (it.path != testDir.path) {
                    if (it.delete()) {
                        cleaned++
                    }
                }
            }
        }

        println("Baseline synchronous deletion took: \${time}ms")
    }

    @Test
    fun testFileWalkAsync() {
        val testDir = File(System.getProperty("java.io.tmpdir"), "test_drm_reset_perf_async")
        testDir.deleteRecursively()
        testDir.mkdirs()

        for (i in 1..10) {
            val d1 = File(testDir, "dir\$i")
            d1.mkdirs()
            for (j in 1..20) {
                val d2 = File(d1, "subdir\$j")
                d2.mkdirs()
                for (k in 1..5) {
                    File(d2, "file\$k.txt").writeText("test")
                }
            }
        }

        val totalFiles = testDir.walkBottomUp().count()
        println("Created \$totalFiles files/dirs for async")

        val time = measureTimeMillis {
            GlobalScope.launch(Dispatchers.IO) {
                var cleaned = 0
                testDir.walkBottomUp().forEach {
                    if (it.path != testDir.path) {
                        if (it.delete()) {
                            cleaned++
                        }
                    }
                }
            }
        }

        println("Async deletion launch time: \${time}ms")
    }
}
