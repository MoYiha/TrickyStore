package cleveres.tricky.cleverestech

import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBackendLockOrderTest {
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
    }
}
