package online.morda.mordatg.morda

import kotlinx.serialization.Serializable
import online.morda.mordatg.updates.UpdateManifest

@Serializable
data class MordaContent(
    val title: String,
    val text: String,
    val imageUrl: String? = null,
    val buttonLabel: String,
    val url: String,
) {
    fun validate(): List<String> = buildList {
        if (title.isBlank() || title.length > 100) add("title is invalid")
        if (text.isBlank() || text.length > 500) add("text is invalid")
        if (buttonLabel.isBlank() || buttonLabel.length > 40) add("buttonLabel is invalid")
        if (!UpdateManifest.isHttps(url)) add("url must use HTTPS")
        if (imageUrl != null && !UpdateManifest.isHttps(imageUrl)) add("imageUrl must use HTTPS")
    }

    companion object {
        val FALLBACK = MordaContent(
            title = "Нужен не только Telegram?",
            text = "VPN morda.online — спонсор прокси.",
            buttonLabel = "Открыть бота",
            url = "https://t.me/morda_online_bot",
        )
    }
}

