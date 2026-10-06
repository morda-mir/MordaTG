package online.morda.mordatg.updates

import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    fun digest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun digest(bytes: ByteArray): String = bytes.inputStream().use(::digest)
}

