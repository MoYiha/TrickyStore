package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraVisibilityTest {
    @Test
    fun `camera runtime requires both explicit flag and configured count`() {
        assertFalse(shouldRunCameraVisibility(false, null))
        assertFalse(shouldRunCameraVisibility(false, 2))
        assertFalse(shouldRunCameraVisibility(true, null))
        assertTrue(shouldRunCameraVisibility(true, 2))
    }

    @Test
    fun `camera count only reduces real hardware`() {
        assertEquals(4, boundedVisibleCameraCount(4, null))
        assertEquals(2, boundedVisibleCameraCount(4, 2))
        assertEquals(4, boundedVisibleCameraCount(4, 16))
        assertEquals(0, boundedVisibleCameraCount(4, 0))
        assertEquals(0, boundedVisibleCameraCount(-1, 2))
    }
}
