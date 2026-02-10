package cleveres.tricky.cleverestech

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SepolicyTest {

    @Test
    fun testCleveresTrickyDataFilePermissions() {
        // Locate the sepolicy.rule file relative to the project root
        // Assuming the test runs from service/
        val sepolicyFile = File("../module/template/sepolicy.rule")
        assertTrue("sepolicy.rule file does not exist at ${sepolicyFile.absolutePath}", sepolicyFile.exists())

        val content = sepolicyFile.readText()

        // Check for directory permissions
        val dirPermissions = "allow cleverestricky_daemon cleverestricky_data_file:dir { search read open getattr write create setattr unlink add_name remove_name };"
        assertTrue("sepolicy.rule missing directory write permissions", content.contains(dirPermissions))

        // Check for file permissions
        val filePermissions = "allow cleverestricky_daemon cleverestricky_data_file:file { read open getattr write create setattr unlink };"
        assertTrue("sepolicy.rule missing file write permissions", content.contains(filePermissions))

        // Check for network permissions
        assertTrue("sepolicy.rule missing tcp_socket permissions", content.contains("allow cleverestricky_daemon self:tcp_socket"))
        assertTrue("sepolicy.rule missing udp_socket permissions", content.contains("allow cleverestricky_daemon self:udp_socket"))
        assertTrue("sepolicy.rule missing netd permissions", content.contains("allow cleverestricky_daemon netd:unix_stream_socket connectto;"))
    }
}
