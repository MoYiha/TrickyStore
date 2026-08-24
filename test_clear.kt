import cleveres.tricky.cleverestech.util.KeyboxVerifier
import java.io.File
fun main() {
    println("Start")
    val f = File("/data/adb/test")
    KeyboxVerifier.setCacheRootForTesting(f)
    KeyboxVerifier.clearMemoryCacheForTesting()
    println("End")
}
