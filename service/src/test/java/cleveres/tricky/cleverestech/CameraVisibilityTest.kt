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

    @Test
    fun `visibility quota is independent for each virtual device context`() {
        val statuses =
            linkedMapOf(
                CameraVisibilityKey("0", 0) to 1,
                CameraVisibilityKey("1", 0) to 1,
                CameraVisibilityKey("virtual-0", 7) to 1,
                CameraVisibilityKey("virtual-1", 7) to 1,
            )

        assertEquals(
            linkedSetOf(CameraVisibilityKey("0", 0), CameraVisibilityKey("virtual-0", 7)),
            selectVisibleCameraKeys(statuses, 1),
        )
    }

    @Test
    fun `non discoverable status does not consume a device quota`() {
        val statuses =
            linkedMapOf(
                CameraVisibilityKey("enumerating") to CAMERA_STATUS_ENUMERATING,
                CameraVisibilityKey("missing") to CAMERA_STATUS_NOT_PRESENT,
                CameraVisibilityKey("0") to 1,
                CameraVisibilityKey("1") to 1,
            )

        assertEquals(
            linkedSetOf(CameraVisibilityKey("0")),
            selectVisibleCameraKeys(statuses, 1),
        )
    }

    @Test
    fun `runtime limit changes produce hide and promote deltas`() {
        val ledger = CameraVisibilityLedger()
        ledger.initialize(
            listOf(
                CameraVisibilityStatus(CameraVisibilityKey("0"), 1),
                CameraVisibilityStatus(CameraVisibilityKey("1"), 1),
                CameraVisibilityStatus(CameraVisibilityKey("2"), 1),
            ),
            2,
        )

        val reduced = ledger.updateLimit(1)
        assertEquals(setOf(CameraVisibilityKey("1")), reduced.hidden)
        assertTrue(reduced.shown.isEmpty())
        assertEquals(setOf(CameraVisibilityKey("0")), reduced.visible)

        val expanded = ledger.updateLimit(2)
        assertTrue(expanded.hidden.isEmpty())
        assertEquals(listOf(CameraVisibilityStatus(CameraVisibilityKey("1"), 1)), expanded.shown)
        assertEquals(setOf(CameraVisibilityKey("0"), CameraVisibilityKey("1")), expanded.visible)
    }

    @Test
    fun `unplugging a visible camera promotes the next real camera`() {
        val ledger = CameraVisibilityLedger()
        ledger.initialize(
            listOf(
                CameraVisibilityStatus(CameraVisibilityKey("0"), 1),
                CameraVisibilityStatus(CameraVisibilityKey("1"), 1),
            ),
            1,
        )

        val delta = ledger.updateStatus(CameraVisibilityKey("0"), CAMERA_STATUS_NOT_PRESENT)
        assertEquals(setOf(CameraVisibilityKey("0")), delta.hidden)
        assertEquals(listOf(CameraVisibilityStatus(CameraVisibilityKey("1"), 1)), delta.shown)
        assertEquals(setOf(CameraVisibilityKey("1")), delta.visible)
    }

    @Test
    fun `disabling filtering restores every discoverable camera without inventing hardware`() {
        val ledger = CameraVisibilityLedger()
        ledger.initialize(
            listOf(
                CameraVisibilityStatus(CameraVisibilityKey("0"), 1),
                CameraVisibilityStatus(CameraVisibilityKey("1"), -2),
                CameraVisibilityStatus(CameraVisibilityKey("gone"), CAMERA_STATUS_NOT_PRESENT),
            ),
            1,
        )

        val delta = ledger.updateLimit(null)
        assertTrue(delta.hidden.isEmpty())
        assertEquals(listOf(CameraVisibilityStatus(CameraVisibilityKey("1"), -2)), delta.shown)
        assertEquals(setOf(CameraVisibilityKey("0"), CameraVisibilityKey("1")), delta.visible)
    }

    @Test
    fun `re enabling a drained listener reapplies the current limit`() {
        val ledger = CameraVisibilityLedger()
        ledger.initialize(
            listOf(
                CameraVisibilityStatus(CameraVisibilityKey("0"), 1),
                CameraVisibilityStatus(CameraVisibilityKey("1"), 1),
            ),
            1,
        )
        ledger.updateLimit(null)

        val delta = ledger.updateLimit(1)
        assertEquals(setOf(CameraVisibilityKey("1")), delta.hidden)
        assertTrue(delta.shown.isEmpty())
        assertEquals(setOf(CameraVisibilityKey("0")), delta.visible)
    }
}
