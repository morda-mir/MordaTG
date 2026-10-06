package online.morda.mordatg.socks

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Socks5ProtocolTest {
    @Test
    fun `negotiation selects no auth`() {
        val output = ByteArrayOutputStream()
        Socks5Protocol.negotiate(ByteArrayInputStream(byteArrayOf(5, 2, 2, 0)), output)
        assertArrayEquals(byteArrayOf(5, 0), output.toByteArray())
    }

    @Test
    fun `negotiation rejects unsupported methods`() {
        val output = ByteArrayOutputStream()
        assertThrows(SocksProtocolException::class.java) {
            Socks5Protocol.negotiate(ByteArrayInputStream(byteArrayOf(5, 1, 2)), output)
        }
        assertArrayEquals(byteArrayOf(5, 0xff.toByte()), output.toByteArray())
    }

    @Test
    fun `domain connect request is parsed`() {
        val host = "telegram.org".toByteArray()
        val bytes = byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(1, 0xbb.toByte())
        val request = Socks5Protocol.readConnectRequest(ByteArrayInputStream(bytes))
        assertEquals("telegram.org", request.host)
        assertEquals(443, request.port)
        assertEquals(SocksConnectRequest.AddressType.DOMAIN, request.addressType)
    }

    @Test
    fun `ipv4 connect request is parsed`() {
        val bytes = byteArrayOf(5, 1, 0, 1, 149.toByte(), 154.toByte(), 167.toByte(), 51, 1, 0xbb.toByte())
        val request = Socks5Protocol.readConnectRequest(ByteArrayInputStream(bytes))
        assertEquals("149.154.167.51", request.host)
        assertEquals(443, request.port)
    }

    @Test
    fun `unsupported command returns correct error`() {
        val error = assertThrows(SocksProtocolException::class.java) {
            Socks5Protocol.readConnectRequest(ByteArrayInputStream(byteArrayOf(5, 2, 0, 1)))
        }
        assertEquals(SocksReply.COMMAND_NOT_SUPPORTED, error.reply)
    }
}

