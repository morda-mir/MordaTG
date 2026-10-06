package online.morda.mordatg.upstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MtProtoTransportTest {
    @Test
    fun `client init identifies intermediate transport`() {
        val init = makeInit(MtProtoTransport.INTERMEDIATE, 2)
        val parsed = MtProtoObfuscation.parseClientInit(init)
        assertNotNull(parsed)
        assertEquals(MtProtoTransport.INTERMEDIATE, parsed?.transport)
        assertEquals(2, parsed?.hintedDc)
    }

    @Test
    fun `invalid transport tag is rejected`() {
        assertNull(MtProtoObfuscation.parseClientInit(ByteArray(64)))
    }

    @Test
    fun `splitter preserves encrypted packet boundaries`() {
        val initBytes = makeInit(MtProtoTransport.INTERMEDIATE, 2)
        val parsed = MtProtoObfuscation.parseClientInit(initBytes)!!
        val splitter = MtProtoMessageSplitter(parsed)
        val encryptor = streamCipher(initBytes).also { it.update(ByteArray(64)) }
        val packetA = littleEndian(12) + ByteArray(12) { 0x11 }
        val packetB = littleEndian(20) + ByteArray(20) { 0x22 }
        val encrypted = encryptor.update(packetA + packetB)
        val pieces = mutableListOf<ByteArray>()
        pieces += splitter.split(encrypted.copyOfRange(0, 7))
        pieces += splitter.split(encrypted.copyOfRange(7, encrypted.size))
        assertEquals(2, pieces.size)
        assertTrue((pieces[0] + pieces[1]).contentEquals(encrypted))
    }

    private fun makeInit(transport: MtProtoTransport, dc: Int): ByteArray {
        val random = SecureRandom()
        val init = ByteArray(64).also(random::nextBytes)
        init[0] = 1
        val cipher = streamCipher(init)
        val encryptedFull = cipher.update(init.copyOf())
        val plainTail = transport.tag + byteArrayOf(dc.toByte(), 0, 0, 0)
        for (index in 56 until 64) {
            val keyStream = encryptedFull[index].toInt() xor init[index].toInt()
            init[index] = (plainTail[index - 56].toInt() xor keyStream).toByte()
        }
        return init
    }

    private fun streamCipher(init: ByteArray): Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(init.copyOfRange(8, 40), "AES"),
            IvParameterSpec(init.copyOfRange(40, 56)),
        )
    }

    private fun littleEndian(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value ushr 8).toByte(),
        (value ushr 16).toByte(),
        (value ushr 24).toByte(),
    )
}

