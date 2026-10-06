package online.morda.mordatg.upstream

import online.morda.mordatg.core.ProxyConstants
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class WebSocketHandshakeException(
    val statusCode: Int,
    message: String,
) : IOException(message)

class RawWebSocket private constructor(
    private val socket: SSLSocket,
    private val input: BufferedInputStream,
    private val output: BufferedOutputStream,
) : Closeable {
    private val writeLock = Any()
    private val random = SecureRandom()
    @Volatile private var closed = false
    private var fragments: ByteArray? = null

    fun sendBinary(payload: ByteArray) = sendFrame(OP_BINARY, payload)

    fun receiveBinary(): ByteArray? {
        while (!closed) {
            val first = readRequired()
            val second = readRequired()
            val fin = first and 0x80 != 0
            val opcode = first and 0x0f
            val masked = second and 0x80 != 0
            var length = (second and 0x7f).toLong()
            if (length == 126L) length = readUnsigned(2)
            if (length == 127L) length = readUnsigned(8)
            if (length < 0 || length > ProxyConstants.MAX_WS_MESSAGE_BYTES || length > Int.MAX_VALUE) {
                throw IOException("WebSocket frame exceeds safety limit")
            }
            val mask = if (masked) readExact(4) else null
            val payload = readExact(length.toInt())
            if (mask != null) applyMask(payload, mask)

            when (opcode) {
                OP_CLOSE -> {
                    runCatching { sendFrame(OP_CLOSE, payload.take(2).toByteArray()) }
                    close()
                    return null
                }
                OP_PING -> {
                    if (payload.size <= 125) sendFrame(OP_PONG, payload)
                }
                OP_PONG -> Unit
                OP_BINARY, OP_TEXT, OP_CONTINUATION -> {
                    val previous = fragments
                    if (previous == null && fin) return payload
                    val combined = if (previous == null) payload else previous + payload
                    if (combined.size > ProxyConstants.MAX_WS_MESSAGE_BYTES) {
                        throw IOException("WebSocket message exceeds safety limit")
                    }
                    if (fin) {
                        fragments = null
                        return combined
                    }
                    fragments = combined
                }
                else -> throw IOException("Unsupported WebSocket opcode")
            }
        }
        return null
    }

    private fun sendFrame(opcode: Int, payload: ByteArray) {
        if (closed) throw IOException("WebSocket is closed")
        if (payload.size > ProxyConstants.MAX_WS_MESSAGE_BYTES) {
            throw IOException("WebSocket frame exceeds safety limit")
        }
        synchronized(writeLock) {
            if (closed) throw IOException("WebSocket is closed")
            val mask = ByteArray(4).also(random::nextBytes)
            output.write(0x80 or opcode)
            when {
                payload.size < 126 -> output.write(0x80 or payload.size)
                payload.size <= 0xffff -> {
                    output.write(0x80 or 126)
                    writeUnsigned(payload.size.toLong(), 2)
                }
                else -> {
                    output.write(0x80 or 127)
                    writeUnsigned(payload.size.toLong(), 8)
                }
            }
            output.write(mask)
            val masked = payload.copyOf()
            applyMask(masked, mask)
            output.write(masked)
            output.flush()
        }
    }

    override fun close() {
        if (closed) return
        synchronized(writeLock) {
            if (closed) return
            closed = true
            runCatching { socket.close() }
        }
    }

    private fun readRequired(): Int {
        val value = input.read()
        if (value < 0) throw EOFException("WebSocket closed unexpectedly")
        return value
    }

    private fun readExact(length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(result, offset, length - offset)
            if (count < 0) throw EOFException("WebSocket closed unexpectedly")
            offset += count
        }
        return result
    }

    private fun readUnsigned(bytes: Int): Long {
        var value = 0L
        repeat(bytes) { value = (value shl 8) or readRequired().toLong() }
        return value
    }

    private fun writeUnsigned(value: Long, bytes: Int) {
        for (index in bytes - 1 downTo 0) {
            output.write(((value ushr (index * 8)) and 0xff).toInt())
        }
    }

    companion object {
        private const val OP_CONTINUATION = 0x0
        private const val OP_TEXT = 0x1
        private const val OP_BINARY = 0x2
        private const val OP_CLOSE = 0x8
        private const val OP_PING = 0x9
        private const val OP_PONG = 0xa
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        fun connect(
            connectHost: String,
            tlsHost: String,
            path: String = "/apiws",
            connectTimeoutMs: Int = ProxyConstants.CONNECT_TIMEOUT_MS,
        ): RawWebSocket {
            require(path.startsWith('/'))
            val raw = Socket()
            try {
                raw.tcpNoDelay = true
                raw.keepAlive = true
                raw.connect(InetSocketAddress(connectHost, 443), connectTimeoutMs)
                raw.soTimeout = ProxyConstants.READ_TIMEOUT_MS

                val context = SSLContext.getInstance("TLS").apply { init(null, null, null) }
                val ssl = context.socketFactory.createSocket(raw, tlsHost, 443, true) as SSLSocket
                ssl.sslParameters = ssl.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    serverNames = listOf(SNIHostName(tlsHost))
                }
                ssl.startHandshake()

                val input = BufferedInputStream(ssl.inputStream, 64 * 1024)
                val output = BufferedOutputStream(ssl.outputStream, 64 * 1024)
                val key = ByteArray(16).also(SecureRandom()::nextBytes)
                    .let { Base64.getEncoder().encodeToString(it) }
                val request = buildString {
                    append("GET ").append(path).append(" HTTP/1.1\r\n")
                    append("Host: ").append(tlsHost).append("\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: ").append(key).append("\r\n")
                    append("Sec-WebSocket-Version: 13\r\n")
                    append("Sec-WebSocket-Protocol: binary\r\n\r\n")
                }
                output.write(request.toByteArray(Charsets.US_ASCII))
                output.flush()

                val statusLine = readHttpLine(input)
                val statusCode = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                val headers = linkedMapOf<String, String>()
                var headerBytes = statusLine.length
                while (true) {
                    val line = readHttpLine(input)
                    headerBytes += line.length
                    if (headerBytes > 32 * 1024) throw IOException("WebSocket response headers are too large")
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        headers[line.substring(0, separator).trim().lowercase(Locale.ROOT)] =
                            line.substring(separator + 1).trim()
                    }
                }
                if (statusCode != 101) {
                    throw WebSocketHandshakeException(statusCode, "WebSocket upgrade failed: HTTP $statusCode")
                }
                val expectedAccept = MessageDigest.getInstance("SHA-1")
                    .digest((key + WS_GUID).toByteArray(Charsets.US_ASCII))
                    .let { Base64.getEncoder().encodeToString(it) }
                if (!headers["sec-websocket-accept"].equals(expectedAccept, ignoreCase = false)) {
                    throw WebSocketHandshakeException(statusCode, "Invalid WebSocket accept header")
                }
                if (!headers["upgrade"].equals("websocket", ignoreCase = true)) {
                    throw WebSocketHandshakeException(statusCode, "Invalid WebSocket upgrade header")
                }
                // Telegram connections may legitimately remain idle for long periods.
                // Connect and handshake are bounded above; established streams block without polling.
                ssl.soTimeout = 0
                return RawWebSocket(ssl, input, output)
            } catch (error: Throwable) {
                runCatching { raw.close() }
                throw error
            }
        }

        internal fun buildMaskedFrameForTest(opcode: Int, payload: ByteArray, mask: ByteArray): ByteArray {
            require(mask.size == 4)
            require(payload.size < 126)
            val body = payload.copyOf().also { applyMask(it, mask) }
            return byteArrayOf((0x80 or opcode).toByte(), (0x80 or payload.size).toByte()) + mask + body
        }

        private fun readHttpLine(input: BufferedInputStream): String {
            val bytes = ArrayList<Byte>()
            while (bytes.size <= 8192) {
                val value = input.read()
                if (value < 0) throw EOFException("Unexpected end of HTTP response")
                if (value == '\n'.code) break
                if (value != '\r'.code) bytes += value.toByte()
            }
            if (bytes.size > 8192) throw IOException("HTTP header line is too long")
            return bytes.toByteArray().toString(Charsets.ISO_8859_1)
        }

        private fun applyMask(data: ByteArray, mask: ByteArray) {
            for (index in data.indices) {
                data[index] = (data[index].toInt() xor mask[index and 3].toInt()).toByte()
            }
        }
    }
}

