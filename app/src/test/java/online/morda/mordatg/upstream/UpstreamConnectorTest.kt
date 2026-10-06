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
        assertEquals("kws203.pclead.co.uk", endpoints.first().tlsHost)
    }
}
