package online.morda.mordatg.upstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawWebSocketTest {
    @Test
    fun `masked client frame is reversible`() {
        val payload = "hello".toByteArray()
        val mask = byteArrayOf(1, 2, 3, 4)
        val frame = RawWebSocket.buildMaskedFrameForTest(2, payload, mask)
        assertEquals(0x82, frame[0].toInt() and 0xff)
        assertTrue(frame[1].toInt() and 0x80 != 0)
        val decoded = frame.copyOfRange(6, frame.size)
        decoded.indices.forEach { decoded[it] = (decoded[it].toInt() xor mask[it and 3].toInt()).toByte() }
        assertTrue(decoded.contentEquals(payload))
    }
}

