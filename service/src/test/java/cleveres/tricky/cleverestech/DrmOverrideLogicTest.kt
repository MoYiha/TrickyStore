package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrmOverrideLogicTest {

    @Test
    fun testFindTrackedPropertyNamePrefersKnownValues() {
        val propertyName = DrmOverrideLogic.findTrackedPropertyName(
            listOf("android.hardware.drm.IDrmFactory", DrmOverrideLogic.SECURITY_LEVEL_PROPERTY)
        )

        assertEquals(DrmOverrideLogic.SECURITY_LEVEL_PROPERTY, propertyName)
    }

    @Test
    fun testFindTrackedPropertyNameReturnsNullForUnknownValues() {
        assertNull(
            DrmOverrideLogic.findTrackedPropertyName(listOf("android.hardware.drm.IDrmFactory", "vendor"))
        )
    }

    @Test
    fun testSpoofSecurityLevelOnlyForTrackedProperty() {
        assertEquals(
            "L1",
            DrmOverrideLogic.spoofSecurityLevel(DrmOverrideLogic.SECURITY_LEVEL_PROPERTY, "L3", "1")
        )
        assertNull(
            DrmOverrideLogic.spoofSecurityLevel(DrmOverrideLogic.DEVICE_UNIQUE_ID_PROPERTY, "L3", "1")
        )
    }

    @Test
    fun testSpoofSecurityLevelFallsBackToL1ForInvalidConfig() {
        assertEquals(
            "L1",
            DrmOverrideLogic.spoofSecurityLevel(DrmOverrideLogic.SECURITY_LEVEL_PROPERTY, "L2", "not-a-number")
        )
        assertEquals(
            "L2",
            DrmOverrideLogic.spoofSecurityLevel(DrmOverrideLogic.SECURITY_LEVEL_PROPERTY, "L3", "L2")
        )
    }

    @Test
    fun testShouldSpoofDeviceUniqueIdRequiresMatchingPropertyAndToggle() {
        assertTrue(
            DrmOverrideLogic.shouldSpoofDeviceUniqueId(DrmOverrideLogic.DEVICE_UNIQUE_ID_PROPERTY, true)
        )
        assertFalse(
            DrmOverrideLogic.shouldSpoofDeviceUniqueId(DrmOverrideLogic.SECURITY_LEVEL_PROPERTY, true)
        )
        assertFalse(
            DrmOverrideLogic.shouldSpoofDeviceUniqueId(DrmOverrideLogic.DEVICE_UNIQUE_ID_PROPERTY, false)
        )
    }
}
