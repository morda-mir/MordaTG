package online.morda.mordatg.updates

import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant

@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val sha256: String,
    val publishedAt: String,
    val minimumSupportedVersion: Int,
) {
    fun validate(): List<String> = buildList {
        if (versionCode <= 0) add("versionCode must be positive")
        if (versionName.isBlank() || versionName.length > 80) add("versionName is invalid")
        if (!isHttps(apkUrl)) add("apkUrl must use HTTPS")
        if (!sha256.matches(Regex("[0-9a-fA-F]{64}"))) add("sha256 must contain 64 hexadecimal characters")
        if (runCatching { Instant.parse(publishedAt) }.isFailure) add("publishedAt must be ISO-8601")
        if (minimumSupportedVersion < 0) add("minimumSupportedVersion cannot be negative")
        if (changelog.length > 20_000) add("changelog is too large")
    }

    companion object {
        fun isHttps(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}

