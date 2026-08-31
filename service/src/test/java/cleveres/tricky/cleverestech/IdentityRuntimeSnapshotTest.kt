package cleveres.tricky.cleverestech

import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityRuntimeSnapshotTest {
    @Test
    fun `buildProperties includes full set of serial properties for parity and rollback`() {
        val expectedSerialProps =
            listOf(
                "ro.serialno",
                "ro.boot.serialno",
                "ro.vendor.serialno",
                "ro.odm.serialno",
                "vendor.serialno",
                "vendor.boot.serialno",
                "persist.sys.serialno",
                "ro.ril.oem.sno",
                "ro.ril.oem.psno",
                "sys.serialno",
                "gsm.serial",
            )
        for (prop in expectedSerialProps) {
            assertTrue(
                "Expected buildProperties to include $prop",
                IdentityRuntimeSnapshot.buildProperties.contains(prop),
            )
        }
    }
}
