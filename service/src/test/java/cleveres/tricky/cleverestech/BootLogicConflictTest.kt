package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootLogicConflictTest {
    @Test
    fun `auto pif module IDs match the early boot conflict policy`() {
        assertTrue(BootLogic.isBuildIdentityProviderModuleId("auto_pif"))
        assertTrue(BootLogic.isBuildIdentityProviderModuleId("Auto_PIF_next"))
        assertTrue(BootLogic.isBuildIdentityProviderModuleId("autopif"))
        assertFalse(BootLogic.isBuildIdentityProviderModuleId("unrelated_module"))
    }
}
