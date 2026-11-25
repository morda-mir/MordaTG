package online.morda.mordatg.upstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class RawWebSocketTest {
    @Test
    fun `fallback validates websocket upgrade and receives binary data`() {
        val serverError = AtomicReference<Throwable>()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val worker = thread {
                runCatching {
                    server.accept().use { socket ->
                        socket.soTimeout = 2_000
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
                        var key: String? = null
                        while (true) {
                            val line = reader.readLine() ?: error("Missing upgrade request")
                            if (line.isEmpty()) break
                            if (line.startsWith("Sec-WebSocket-Key:")) key = line.substringAfter(':').trim()
                        }
                        val accept = Base64.getEncoder().encodeToString(
                            MessageDigest.getInstance("SHA-1")
                                .digest((key!! + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
                        )
                        val output = socket.getOutputStream()
                        output.write(
                            ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                                "Connection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n")
                                .toByteArray(Charsets.US_ASCII),
                        )
                        output.write(byteArrayOf(0x82.toByte(), 3, 10, 20, 30))
                        output.flush()
                    }
                }.onFailure { serverError.set(it) }
            }
            RawWebSocket.connect(
                "127.0.0.1", "localhost", secure = false, connectPort = server.localPort,
            ).use { socket -> assertArrayEquals(byteArrayOf(10, 20, 30), socket.receiveBinary()) }
            worker.join(3_000)
            assertTrue(!worker.isAlive)
            serverError.get()?.let { throw AssertionError("WebSocket fixture failed", it) }
        }
    }

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

