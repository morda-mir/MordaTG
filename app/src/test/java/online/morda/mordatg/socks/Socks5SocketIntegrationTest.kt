package online.morda.mordatg.socks

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class Socks5SocketIntegrationTest {
    @Test
    fun `loopback client completes negotiation and connect exchange`() {
        val observed = AtomicReference<SocksConnectRequest>()
        ServerSocket().use { server ->
            server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1)
            val worker = thread(name = "socks5-test-server") {
                server.accept().use { accepted ->
                    val input = BufferedInputStream(accepted.getInputStream())
                    val output = BufferedOutputStream(accepted.getOutputStream())
                    Socks5Protocol.negotiate(input, output)
                    observed.set(Socks5Protocol.readConnectRequest(input))
                    Socks5Protocol.writeReply(output, SocksReply.SUCCEEDED)
                }
            }

            Socket().use { client ->
                client.soTimeout = 2_000
                client.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), 2_000)
                val input = BufferedInputStream(client.getInputStream())
                val output = BufferedOutputStream(client.getOutputStream())

                output.write(byteArrayOf(5, 1, 0))
                output.flush()
                assertArrayEquals(byteArrayOf(5, 0), input.readExactForTest(2))

                val host = "telegram.org".toByteArray(Charsets.US_ASCII)
                output.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(1, 0xbb.toByte()))
                output.flush()
                val reply = input.readExactForTest(10)
                assertEquals(5, reply[0].toInt())
                assertEquals(SocksReply.SUCCEEDED.code, reply[1].toInt())
            }

            worker.join(2_000)
            check(!worker.isAlive) { "SOCKS test server did not finish" }
        }

        assertEquals("telegram.org", observed.get().host)
        assertEquals(443, observed.get().port)
    }

    private fun BufferedInputStream.readExactForTest(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = read(bytes, offset, length - offset)
            check(count >= 0) { "Unexpected end of test socket" }
            offset += count
        }
        return bytes
    }
}
