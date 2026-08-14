package cleveres.tricky.cleverestech.util

import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyboxVerifierPersistentCacheTest {
    @Test
    fun freshPersistedCrlSurvivesDaemonRestartWithoutNetwork() {
        val root = Files.createTempDirectory("ct-crl-cache").toFile()
        val requestCount = AtomicInteger(0)
        val crlJson = """{"entries":{"12345":"REVOKED"}}"""
        val server = ServerSocket(0)
        val thread =
  Thread {
      try {
          val client = server.accept()
          requestCount.incrementAndGet()
          val reader = client.inputStream.bufferedReader()
              var line = reader.readLine()
              while (line != null && line.isNotEmpty()) line = reader.readLine()
          val writer = client.outputStream.bufferedWriter()
              writer.write("HTTP/1.1 200 OK\r\n")
              writer.write("Content-Type: application/json\r\n")
              writer.write("Content-Length: ${crlJson.toByteArray().size}\r\n")
              writer.write("\r\n")
              writer.write(crlJson)
          writer.flush()
          client.close()
      } catch (_: Exception) {
      }
  }
        thread.start()

        try {
  KeyboxVerifier.setCacheRootForTesting(root)
  KeyboxVerifier.setCrlUrlForTesting("http://localhost:${server.localPort}")
  val first = KeyboxVerifier.fetchCrl()
  assertNotNull(first)
  assertEquals(1, requestCount.get())

  thread.join(2_000)
  server.close()
  KeyboxVerifier.clearMemoryCacheForTesting()

  val second = KeyboxVerifier.fetchCrl()
  assertEquals(first, second)
  assertEquals(1, requestCount.get())
        } finally {
  runCatching { server.close() }
  thread.interrupt()
  KeyboxVerifier.resetCrlUrlForTesting()
  KeyboxVerifier.resetCacheRootForTesting()
  root.deleteRecursively()
        }
    }
}
