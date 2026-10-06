package online.morda.mordatg.upstream

import online.morda.mordatg.core.ProxyConstants
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class MtProtoTransport(val tag: ByteArray) {
    ABRIDGED(byteArrayOf(0xef.toByte(), 0xef.toByte(), 0xef.toByte(), 0xef.toByte())),
    INTERMEDIATE(byteArrayOf(0xee.toByte(), 0xee.toByte(), 0xee.toByte(), 0xee.toByte())),
    PADDED_INTERMEDIATE(byteArrayOf(0xdd.toByte(), 0xdd.toByte(), 0xdd.toByte(), 0xdd.toByte())),
}

data class MtProtoInit(
    val transport: MtProtoTransport,
    val hintedDc: Int?,
    internal val decryptor: Cipher,
)

object MtProtoObfuscation {
    const val INIT_SIZE = 64

    fun parseClientInit(init: ByteArray): MtProtoInit? {
        if (init.size != INIT_SIZE) return null
        val key = init.copyOfRange(8, 40)
        val iv = init.copyOfRange(40, 56)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val plain = cipher.update(init)
        val tag = plain.copyOfRange(56, 60)
        val transport = MtProtoTransport.entries.firstOrNull { it.tag.contentEquals(tag) } ?: return null
        val dcRaw = ByteBuffer.wrap(plain, 60, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val hintedDc = kotlin.math.abs(dcRaw).takeIf { it in 1..5 || it == 203 }
        return MtProtoInit(transport, hintedDc, cipher)
    }
}

class MtProtoMessageSplitter(private val init: MtProtoInit) {
    private val encrypted = ByteArrayBuffer()
    private val plain = ByteArrayBuffer()
    private var disabled = false

    fun split(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()
        if (disabled) return listOf(chunk)
        if (encrypted.size.toLong() + chunk.size > ProxyConstants.MAX_WS_MESSAGE_BYTES) {
            throw IOException("MTProto message exceeds safety limit")
        }
        encrypted.append(chunk)
        plain.append(init.decryptor.update(chunk))

        val parts = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < encrypted.size) {
            val length = packetLength(offset, encrypted.size - offset) ?: break
            if (length <= 0) {
                parts += encrypted.slice(offset, encrypted.size)
                offset = encrypted.size
                disabled = true
                break
            }
            parts += encrypted.slice(offset, offset + length)
            offset += length
        }
        if (offset > 0) {
            encrypted.discard(offset)
            plain.discard(offset)
        }
        return parts
    }

    fun flush(): List<ByteArray> {
        if (encrypted.size == 0) return emptyList()
        val tail = encrypted.slice(0, encrypted.size)
        encrypted.clear()
        plain.clear()
        return listOf(tail)
    }

    private fun packetLength(offset: Int, available: Int): Int? = when (init.transport) {
        MtProtoTransport.ABRIDGED -> {
            val first = plain[offset].toInt() and 0xff
            val headerLength: Int
            val payloadLength: Int
            if (first == 0x7f || first == 0xff) {
                if (available < 4) return null
                payloadLength = ((plain[offset + 1].toInt() and 0xff) or
                    ((plain[offset + 2].toInt() and 0xff) shl 8) or
                    ((plain[offset + 3].toInt() and 0xff) shl 16)) * 4
                headerLength = 4
            } else {
                payloadLength = (first and 0x7f) * 4
                headerLength = 1
            }
            if (payloadLength <= 0) 0 else (headerLength + payloadLength).takeIf { available >= it }
        }
        MtProtoTransport.INTERMEDIATE, MtProtoTransport.PADDED_INTERMEDIATE -> {
            if (available < 4) return null
            val payloadLength = ByteBuffer.wrap(plain.slice(offset, offset + 4))
                .order(ByteOrder.LITTLE_ENDIAN).int and 0x7fffffff
            if (payloadLength <= 0) 0 else (4 + payloadLength).takeIf { available >= it }
        }
    }
}

private class ByteArrayBuffer(initialCapacity: Int = 4096) {
    private var data = ByteArray(initialCapacity)
    var size: Int = 0
        private set

    operator fun get(index: Int): Byte {
        require(index in 0 until size)
        return data[index]
    }

    fun append(bytes: ByteArray) {
        ensure(size + bytes.size)
        bytes.copyInto(data, size)
        size += bytes.size
    }

    fun slice(from: Int, until: Int): ByteArray = data.copyOfRange(from, until)

    fun discard(count: Int) {
        require(count in 0..size)
        data.copyInto(data, 0, count, size)
        size -= count
    }

    fun clear() {
        size = 0
    }

    private fun ensure(required: Int) {
        if (required <= data.size) return
        var newSize = data.size
        while (newSize < required) newSize = (newSize * 2).coerceAtLeast(1)
        data = data.copyOf(newSize)
    }
}
