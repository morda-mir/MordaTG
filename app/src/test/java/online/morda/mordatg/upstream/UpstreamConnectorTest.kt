package online.morda.mordatg.upstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamConnectorTest {
    @Test
    fun `cdn dc uses cloudflare routes and skips nonexistent telegram domain`() {
        val route = TelegramRoute(dcId = 203, media = true)
        val endpoints = UpstreamConnector(onReconnect = {}).candidateEndpoints(route)

        assertTrue(endpoints.isNotEmpty())
        assertTrue(endpoints.all { it.tlsHost.startsWith("kws203.") })
        assertFalse(endpoints.any { it.tlsHost.endsWith(".web.telegram.org") })
        assertEquals(20, endpoints.size)
        assertEquals("kws203.offshor.co.uk", endpoints.first().tlsHost)
        assertEquals("kws203.cakeisalie.co.uk", endpoints[1].tlsHost)
    }

    @Test
    fun `regular dc uses the same compatibility pool before telegram routes`() {
        val endpoints = UpstreamConnector(onReconnect = {}).candidateEndpoints(TelegramRoute(dcId = 2))

        assertEquals("kws2.offshor.co.uk", endpoints.first().tlsHost)
        assertEquals("kws2.cakeisalie.co.uk", endpoints[1].tlsHost)
        assertTrue(endpoints.any { it.tlsHost == "kws2.web.telegram.org" })
    }
}
