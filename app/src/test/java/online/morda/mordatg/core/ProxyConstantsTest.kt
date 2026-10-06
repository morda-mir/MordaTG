package online.morda.mordatg.core

import kotlinx.coroutines.runBlocking
import online.morda.mordatg.socks.SocksProxyServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class ProxyConstantsTest {
    @Test
    fun `preferred port is tried first and candidates are unique`() {
        val ports = ProxyConstants.candidatePorts(52741)

        assertEquals(52741, ports.first())
        assertEquals(ports.size, ports.distinct().size)
        assertEquals(ProxyConstants.FALLBACK_ATTEMPTS + 1, ports.size)
        assertTrue(ports.all { it in ProxyConstants.MIN_PRIVATE_PORT..ProxyConstants.MAX_PRIVATE_PORT })
    }

    @Test
    fun `invalid preferred port falls back to default range`() {
        val ports = ProxyConstants.candidatePorts(0)

        assertEquals(ProxyConstants.DEFAULT_LISTEN_PORT, ports.first())
        assertEquals(ProxyConstants.FALLBACK_ATTEMPTS + 1, ports.size)
    }

    @Test
    fun `legacy preferred port keeps only itself outside private range`() {
        val ports = ProxyConstants.candidatePorts(10808)

        assertEquals(10808, ports.first())
        assertTrue(ports.drop(1).all { it in ProxyConstants.MIN_PRIVATE_PORT..ProxyConstants.MAX_PRIVATE_PORT })
    }

    @Test
    fun `server binds another loopback port when preferred is occupied`() = runBlocking {
        val loopback = InetAddress.getByName(ProxyConstants.LISTEN_HOST)
        ServerSocket(0, 1, loopback).use { occupied ->
            val proxy = SocksProxyServer(preferredPort = occupied.localPort, maxConnections = 1)
            try {
                val selected = proxy.start()

                assertNotEquals(occupied.localPort, selected)
                Socket(ProxyConstants.LISTEN_HOST, selected).use { assertTrue(it.isConnected) }
            } finally {
                proxy.close()
            }
        }
    }

    @Test
    fun `server keeps configured port when it is occupied`() = runBlocking {
        val loopback = InetAddress.getByName(ProxyConstants.LISTEN_HOST)
        ServerSocket(0, 1, loopback).use { occupied ->
            val proxy = SocksProxyServer(
                preferredPort = occupied.localPort,
                maxConnections = 1,
                allowPortFallback = false,
            )
            try {
                val error = runCatching { proxy.start() }.exceptionOrNull()

                assertTrue(error is java.net.BindException)
            } finally {
                proxy.close()
            }
        }
    }
}
