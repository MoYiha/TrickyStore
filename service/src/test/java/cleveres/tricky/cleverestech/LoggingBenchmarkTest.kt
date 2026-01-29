package cleveres.tricky.cleverestech

import android.os.Parcel
import android.util.Log
import org.junit.Test
import kotlin.system.measureNanoTime
import java.io.File

class LoggingBenchmarkTest {

    @Test
    fun benchmarkLogging() {
        // Setup
        Log.printToStdout = false // Disable printing to measure only string construction + method calls
        Log.isLoggableEnabled = true // Initially enabled to simulate current behavior where Logger.d is called and Log.d is called

        val target = "MockBinder"
        val callingUid = 1000
        val callingPid = 1234
        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        val iterations = 100_000

        // Warmup
        repeat(1000) {
            Logger.d("intercept post $target uid=$callingUid pid=$callingPid dataSz=${data.dataSize()} replySz=${reply.dataSize()}")
        }

        // Baseline: Current implementation (String construction always happens)
        // Note: In the real code, Logger.d("...") is called.
        // Even if Log.d returns early (if we implemented checks inside Log.d), the string construction happens BEFORE Logger.d is called.
        val baselineTime = measureNanoTime {
            repeat(iterations) {
                Logger.d("intercept post $target uid=$callingUid pid=$callingPid dataSz=${data.dataSize()} replySz=${reply.dataSize()}")
            }
        }

        println("Baseline time (100k iters): ${baselineTime / 1_000_000.0} ms")

        // Optimization: Check isDebugEnabled
        // Case 1: Logging DISABLED (Production scenario)
        Log.isLoggableEnabled = false

        val optimizedTimeDisabled = measureNanoTime {
            repeat(iterations) {
                if (Logger.isDebugEnabled()) {
                    Logger.d("intercept post $target uid=$callingUid pid=$callingPid dataSz=${data.dataSize()} replySz=${reply.dataSize()}")
                }
            }
        }

        println("Optimized time (Disabled) (100k iters): ${optimizedTimeDisabled / 1_000_000.0} ms")

        // Case 2: Logging ENABLED (Debug scenario)
        Log.isLoggableEnabled = true
        val optimizedTimeEnabled = measureNanoTime {
            repeat(iterations) {
                if (Logger.isDebugEnabled()) {
                    Logger.d("intercept post $target uid=$callingUid pid=$callingPid dataSz=${data.dataSize()} replySz=${reply.dataSize()}")
                }
            }
        }
        println("Optimized time (Enabled) (100k iters): ${optimizedTimeEnabled / 1_000_000.0} ms")

        File("benchmark_results.txt").writeText(
            "Baseline time (100k iters): ${baselineTime / 1_000_000.0} ms\n" +
            "Optimized time (Disabled) (100k iters): ${optimizedTimeDisabled / 1_000_000.0} ms\n" +
            "Optimized time (Enabled) (100k iters): ${optimizedTimeEnabled / 1_000_000.0} ms\n"
        )

        // Cleanup
        Log.printToStdout = true
    }
}
