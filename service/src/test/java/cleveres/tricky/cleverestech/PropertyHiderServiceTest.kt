package cleveres.tricky.cleverestech

import android.os.Binder
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PropertyHiderServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        // Reset Binder calling UID
        Binder.callingUid = 0
        // Suppress logging
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) {}
            override fun i(tag: String, msg: String) {}
        })
    }

    @Test
    fun testSensitiveExposure() {
        // 1. Setup Config with sensitive property
        val buildVarsFile = tempFolder.newFile("spoof_build_vars")
        buildVarsFile.writeText("ATTESTATION_ID_IMEI=123456789012345\nro.product.model=Pixel 8")
        Config.updateBuildVars(buildVarsFile)

        // 2. Instantiate Service
        val service = PropertyHiderService()

        // 3. Prepare Mock Data
        val data = Parcel.obtain()
        // My mock enforceInterface does nothing, so we don't need to write the token.
        data.writeString("ATTESTATION_ID_IMEI")

        val reply = Parcel.obtain()

        // 4. Simulate Call from Unprivileged App
        Binder.callingUid = 10000 // App UID

        val code = PropertyHiderService.GET_SPOOFED_PROPERTY_TRANSACTION_CODE

        service.transact(code, data, reply, 0)

        // 5. Verify Result
        val result = reply.readString()

        // Expect blocked access (security fix)
        assertNull("Sensitive ID should be blocked", result)
    }

    @Test
    fun testNormalPropertyAccess() {
        val buildVarsFile = tempFolder.newFile("spoof_build_vars")
        buildVarsFile.writeText("ro.product.model=Pixel 8")
        Config.updateBuildVars(buildVarsFile)

        val service = PropertyHiderService()
        val data = Parcel.obtain()
        data.writeString("ro.product.model")
        val reply = Parcel.obtain()

        Binder.callingUid = 10000
        service.transact(PropertyHiderService.GET_SPOOFED_PROPERTY_TRANSACTION_CODE, data, reply, 0)

        val result = reply.readString()
        assertEquals("Pixel 8", result)
    }
}
