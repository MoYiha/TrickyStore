package cleveres.tricky.cleverestech

import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBackendLockOrderTest {
    @Test
    fun `keybox transports share the decoder response bound`() {
        val source = nativeBackendSource()
        assertTrue(KeyboxWire.MAX_RESPONSE_BYTES > KeyboxWire.MAX_XML_BYTES)
        assertTrue(source.split("KeyboxWire.MAX_RESPONSE_BYTES").size - 1 >= 2)
        assertFalse(source.contains("private const val MAX_KEYBOX_RESPONSE_BYTES"))
    }

    @Test
    fun `recovery-capable backend entry points do not hold the object monitor`() {
        val methods = NativeBackend::class.java.declaredMethods
        for (name in RECOVERY_CAPABLE_METHODS) {
            val matches = methods.filter { it.name == name || it.name.startsWith(name + '$') }
            assertTrue("Expected NativeBackend method $name", matches.isNotEmpty())
            for (method in matches) {
                assertFalse(
                    "NativeBackend.${method.name} must not hold the object monitor across recovery-capable work",
                    Modifier.isSynchronized(method.modifiers),
                )
            }
        }
    }

    private companion object {
        val RECOVERY_CAPABLE_METHODS =
            listOf(
                "openCbox",
                "encryptBackup",
                "decryptBackup",
                "parseKeybox",
                "parseKeyboxFile",
                "awaitReady",
                "transact",
            )

        fun nativeBackendSource(): String {
            var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
            repeat(6) {
                val candidate =
                    File(
                        current,
                        "service/src/main/java/cleveres/tricky/cleverestech/NativeBackend.kt",
                    )
                if (candidate.isFile) return candidate.readText()
                current = current.parentFile ?: return@repeat
            }
            error("Repository root not found")
        }
    }
}
