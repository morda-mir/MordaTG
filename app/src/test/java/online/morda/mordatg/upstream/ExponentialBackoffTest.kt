package online.morda.mordatg.upstream

import org.junit.Assert.assertEquals
import org.junit.Test

class ExponentialBackoffTest {
    @Test
    fun `backoff doubles and caps`() {
        val backoff = ExponentialBackoff(initialMs = 100, maximumMs = 800, jitterRatio = 0.0)
        assertEquals(100, backoff.delayMs(0))
        assertEquals(200, backoff.delayMs(1))
        assertEquals(400, backoff.delayMs(2))
        assertEquals(800, backoff.delayMs(3))
        assertEquals(800, backoff.delayMs(20))
    }
}

