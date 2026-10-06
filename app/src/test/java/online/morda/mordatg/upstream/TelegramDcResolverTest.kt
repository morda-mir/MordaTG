package online.morda.mordatg.upstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramDcResolverTest {
    @Test
    fun `known production addresses resolve to datacenters`() {
        assertEquals(1, TelegramDcResolver.resolve("149.154.175.50", 443)?.dcId)
        assertEquals(2, TelegramDcResolver.resolve("149.154.167.51", 443)?.dcId)
        assertEquals(3, TelegramDcResolver.resolve("149.154.175.100", 443)?.dcId)
        assertEquals(4, TelegramDcResolver.resolve("149.154.167.91", 443)?.dcId)
        assertEquals(5, TelegramDcResolver.resolve("149.154.171.5", 443)?.dcId)
    }

    @Test
    fun `unknown destination and unsafe port are rejected`() {
        assertNull(TelegramDcResolver.resolve("8.8.8.8", 443))
        assertNull(TelegramDcResolver.resolve("149.154.167.51", 22))
        assertNull(TelegramDcResolver.resolve("example.com", 443))
    }
}

