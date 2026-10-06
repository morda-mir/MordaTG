package online.morda.mordatg.morda

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import online.morda.mordatg.updates.UpdateManifest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MordaRepository {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(endpoint: String): MordaContent = runInterruptible(Dispatchers.IO) {
        if (endpoint.isBlank()) return@runInterruptible MordaContent.FALLBACK
        val bytes = get(endpoint, MAX_CONFIG_BYTES, "application/json")
        val content = json.decodeFromString<MordaContent>(bytes.toString(Charsets.UTF_8))
        val errors = content.validate()
        if (errors.isNotEmpty()) throw IOException(errors.joinToString("; "))
        content
    }

    suspend fun fetchImage(url: String): Bitmap = runInterruptible(Dispatchers.IO) {
        val bytes = get(url, MAX_IMAGE_BYTES, "image/*")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > MAX_IMAGE_DIMENSION || bounds.outHeight > MAX_IMAGE_DIMENSION ||
            bounds.outWidth.toLong() * bounds.outHeight > MAX_IMAGE_PIXELS
        ) {
            throw IOException("Image dimensions exceed the safety limit")
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IOException("Unsupported image format")
    }

    private fun get(value: String, maximumBytes: Int, accept: String): ByteArray {
        if (!UpdateManifest.isHttps(value)) throw IOException("Only HTTPS endpoints are allowed")
        val connection = URL(value).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", accept)
            connection.connect()
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > maximumBytes) throw IOException("Response is too large")
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maximumBytes) throw IOException("Response is too large")
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_CONFIG_BYTES = 128 * 1024
        private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        private const val MAX_IMAGE_DIMENSION = 4_096
        private const val MAX_IMAGE_PIXELS = 8_000_000L
    }
}

