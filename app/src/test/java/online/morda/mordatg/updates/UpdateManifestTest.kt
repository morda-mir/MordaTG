package online.morda.mordatg.updates

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun `valid manifest parses and validates`() {
        val source = """{
            "versionCode": 2,
            "versionName": "0.2.0",
            "apkUrl": "https://morda.online/mordatg.apk",
            "changelog": "Fixes",
            "sha256": "${"ab".repeat(32)}",
            "publishedAt": "2026-10-05T09:00:00Z",
            "minimumSupportedVersion": 1
        }"""
        val manifest = Json.decodeFromString<UpdateManifest>(source)
        assertEquals(2, manifest.versionCode)
        assertTrue(manifest.validate().isEmpty())
    }

    @Test
    fun `insecure and malformed manifest is rejected`() {
        val manifest = UpdateManifest(0, "", "http://example.com/a.apk", "", "bad", "yesterday", -1)
        assertTrue(manifest.validate().size >= 6)
    }
}

