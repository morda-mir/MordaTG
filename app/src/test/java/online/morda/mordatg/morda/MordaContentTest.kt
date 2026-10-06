package online.morda.mordatg.morda

import org.junit.Assert.assertTrue
import org.junit.Test

class MordaContentTest {
    @Test
    fun `fallback bot block is valid`() {
        assertTrue(MordaContent.FALLBACK.validate().isEmpty())
    }

    @Test
    fun `remote block rejects non HTTPS destinations`() {
        val content = MordaContent(
            title = "Заголовок",
            text = "Текст",
            imageUrl = "http://example.test/image.png",
            buttonLabel = "Открыть",
            url = "javascript:alert(1)",
        )
        val errors = content.validate()
        assertTrue(errors.any { it.contains("url must use HTTPS") })
        assertTrue(errors.any { it.contains("imageUrl must use HTTPS") })
    }
}
