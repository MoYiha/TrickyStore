package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InstalledPackagesCompatTest {
    @Test
    fun `package command parser accepts only canonical package records`() {
        val output =
            """
            package:com.example.alpha
            warning: ignored
            package:cleveres.tricky.cleverestech
            package:../escape
            package:com.example.with-dash
            package:com.example_beta
            
            """.trimIndent().toByteArray(Charsets.UTF_8)

        assertEquals(
            listOf(
                "com.example.alpha",
                "cleveres.tricky.cleverestech",
                "com.example_beta",
            ),
            InstalledPackagesCompat.parsePackageListOutput(output),
        )
    }

    @Test
    fun `package command parser rejects oversized output`() {
        val oversized = ByteArray(1024 * 1024 + 1) { 'a'.code.toByte() }

        assertThrows(IllegalArgumentException::class.java) {
            InstalledPackagesCompat.parsePackageListOutput(oversized)
        }
    }

    @Test
    fun `installed packages handles null slice gracefully`() {
        val mockPm =
            java.lang.reflect.Proxy.newProxyInstance(
                android.content.pm.IPackageManager::class.java.classLoader,
                arrayOf(android.content.pm.IPackageManager::class.java),
            ) { _, _, _ -> null } as android.content.pm.IPackageManager

        val result = InstalledPackagesCompat.getInstalledPackageNames(mockPm, 0)
        assertEquals(emptyList<String>(), result)
    }
}

