import kotlin.system.measureTimeMillis
import java.io.File

fun main() {
    val duration = measureTimeMillis {
        // Simulating the Runtime.exec block
        val p = Runtime.getRuntime().exec(arrayOf("sleep", "2"))
        try {
            p.inputStream.readBytes()
        } catch (_: Exception) {}
        finally {
            try { p.errorStream.readBytes() } catch (_: Exception) {}
        }
        val exitCode = p.waitFor()
        println("Exit code: $exitCode")
    }
    println("Simulated Runtime.exec duration: $duration ms")
}
