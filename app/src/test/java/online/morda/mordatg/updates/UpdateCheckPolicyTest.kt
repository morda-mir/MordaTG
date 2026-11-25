package online.morda.mordatg.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    @Test
    fun `first check runs immediately and further automatic checks wait a full day`() {
        val previous = 1_000_000L
        assertTrue(UpdateCheckPolicy.isDue(0, previous))
        assertFalse(UpdateCheckPolicy.isDue(previous, previous + 23 * 60 * 60 * 1_000L))
        assertFalse(UpdateCheckPolicy.isDue(previous, previous + UpdateCheckPolicy.INTERVAL_MS - 1))
        assertTrue(UpdateCheckPolicy.isDue(previous, previous + UpdateCheckPolicy.INTERVAL_MS))
    }

    @Test
    fun `moving the device clock backwards does not postpone checks indefinitely`() {
        assertTrue(UpdateCheckPolicy.isDue(5_000, 4_000))
    }
}
