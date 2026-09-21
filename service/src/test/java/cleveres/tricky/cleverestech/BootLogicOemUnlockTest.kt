package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class BootLogicOemUnlockTest {
    @Test
    fun `absent property succeeds without invoking delete`() {
        var deletions = 0
        BootLogic.removeOemUnlockProperty(
            currentValue = { "" },
            delete = { deletions++ },
        )
        assertEquals(0, deletions)
    }

    @Test
    fun `present property cleared by delete succeeds once`() {
        var value = "1"
        var deletions = 0
        BootLogic.removeOemUnlockProperty(
            currentValue = { value },
            delete = {
                deletions++
                value = ""
            },
        )
        assertEquals(1, deletions)
    }

    @Test
    fun `present property surviving delete fails closed`() {
        var deletions = 0
        assertThrows(IOException::class.java) {
            BootLogic.removeOemUnlockProperty(
                currentValue = { "1" },
                delete = { deletions++ },
            )
        }
        assertEquals(1, deletions)
    }
}
