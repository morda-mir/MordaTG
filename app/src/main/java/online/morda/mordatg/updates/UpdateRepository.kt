package online.morda.mordatg.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class UpdateRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchManifest(endpoint: String): UpdateManifest = runInterruptible(Dispatchers.IO) {
        requireHttps(endpoint)
        val bytes = getHttps(endpoint, MAX_MANIFEST_BYTES)
        val manifest = json.decodeFromString<UpdateManifest>(bytes.toString(Charsets.UTF_8))
        val errors = manifest.validate()
        if (errors.isNotEmpty()) throw IOException(errors.joinToString("; "))
        manifest
    }

    suspend fun downloadAndVerify(manifest: UpdateManifest): File = runInterruptible(Dispatchers.IO) {
        val errors = manifest.validate()
        if (errors.isNotEmpty()) throw IOException(errors.joinToString("; "))
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { if (it.name.endsWith(".part") || it.name.endsWith(".apk")) it.delete() }
        val temporary = File(directory, "mordatg-${manifest.versionCode}.apk.part")
        val destination = File(directory, "mordatg-${manifest.versionCode}.apk")
        val digest = java.security.MessageDigest.getInstance("SHA-256")

        openHttps(manifest.apkUrl).useConnection { connection ->
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_APK_BYTES) throw IOException("APK is larger than the safety limit")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IOException("APK is larger than the safety limit")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(manifest.sha256, ignoreCase = true)) {
            temporary.delete()
            throw IOException("SHA-256 downloaded APK does not match the update manifest")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        destination
    }

    fun launchInstaller(apk: File): InstallResult {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            return InstallResult.PermissionRequired(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return InstallResult.Launched
    }

    sealed interface InstallResult {
        data object Launched : InstallResult
        data class PermissionRequired(val intent: Intent) : InstallResult
    }

    private fun getHttps(url: String, maximumBytes: Int): ByteArray {
        openHttps(url).useConnection { connection ->
            val declaredLength = connection.contentLengthLong
            if (declaredLength > maximumBytes) throw IOException("Response is too large")
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maximumBytes) throw IOException("Response is too large")
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        }
    }

    private fun openHttps(value: String): HttpsURLConnection {
        requireHttps(value)
        var current = URL(value)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (current.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
                setRequestProperty("User-Agent", "MordaTG/${online.morda.mordatg.BuildConfig.VERSION_NAME}")
                connect()
            }
            if (connection.responseCode in 200..299) return connection
            if (connection.responseCode in 300..399 && redirectCount < MAX_REDIRECTS) {
                val location = connection.getHeaderField("Location")
                    ?: run {
                        connection.disconnect()
                        throw IOException("HTTPS redirect has no Location header")
                    }
                val next = URL(current, location)
                connection.disconnect()
                requireHttps(next.toString())
                current = next
            } else {
                val code = connection.responseCode
                connection.disconnect()
                throw IOException("HTTPS endpoint returned HTTP $code")
            }
        }
        throw IOException("Too many HTTPS redirects")
    }

    private fun requireHttps(value: String) {
        if (!UpdateManifest.isHttps(value)) throw IOException("Only HTTPS endpoints are allowed")
    }

    private inline fun <T> HttpsURLConnection.useConnection(block: (HttpsURLConnection) -> T): T =
        try { block(this) } finally { disconnect() }

    companion object {
        private const val MAX_MANIFEST_BYTES = 512 * 1024
        private const val MAX_APK_BYTES = 250L * 1024 * 1024
        private const val MAX_REDIRECTS = 5
    }
}

