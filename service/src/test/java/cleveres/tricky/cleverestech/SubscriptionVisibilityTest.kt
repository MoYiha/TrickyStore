package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SubscriptionVisibilityTest {
    @Test
    fun `missing limit is a zero allocation no-op`() {
        val input = listOf("sim0", "sim1")
        assertSame(input, boundedVisibleSubscriptions(input, null))
        assertEquals(2, boundedVisibleSubscriptionCount(2, null))
    }

    @Test
    fun `configured limit can only reduce real subscriptions`() {
        val input = listOf("sim0", "sim1", "sim2")
        assertEquals(listOf("sim0"), boundedVisibleSubscriptions(input, 1))
        assertEquals(emptyList<String>(), boundedVisibleSubscriptions(input, 0))
        assertSame(input, boundedVisibleSubscriptions(input, 8))
        assertEquals(1, boundedVisibleSubscriptionCount(3, 1))
        assertEquals(3, boundedVisibleSubscriptionCount(3, 8))
        assertEquals(0, boundedVisibleSubscriptionCount(-1, 2))
    }
}
