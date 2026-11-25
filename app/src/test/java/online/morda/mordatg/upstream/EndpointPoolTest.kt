package online.morda.mordatg.upstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPoolTest {
    private val route = TelegramRoute(2)
    private val endpoints = (1..20).map {
        UpstreamConnector.Endpoint("kws2.test$it.example", "kws2.test$it.example", "/apiws")
    }

    @Test
    fun `successive connections reach the entire pool instead of repeating the first addresses`() {
        val pool = EndpointPool()
        val attempted = (1..5).flatMap { pool.candidates(route, endpoints, 4) }

        assertEquals(endpoints.toSet(), attempted.toSet())
    }

    @Test
    fun `failed address is skipped across connections until cooldown expires`() {
        var clock = 0L
        val pool = EndpointPool(nowMillis = { clock })
        pool.failed(route, endpoints[0])

        assertFalse(pool.candidates(route, endpoints, 20).contains(endpoints[0]))
        clock = 30_001
        assertTrue(pool.candidates(route, endpoints, 20).contains(endpoints[0]))
    }

    @Test
    fun `working route is preferred until it fails`() {
        val pool = EndpointPool()
        pool.succeeded(route, endpoints.last())
        assertEquals(endpoints.last(), pool.candidates(route, endpoints, 4).first())

        pool.failed(route, endpoints.last())
        assertFalse(pool.candidates(route, endpoints, 20).contains(endpoints.last()))
    }

    @Test
    fun `fallback is reused quickly and TLS is retried after five minutes`() {
        var clock = 0L
        val pool = EndpointPool(nowMillis = { clock })
        val tls = endpoints.first()
        val fallback = tls.copy(secure = false)
        val candidates = listOf(tls, fallback)
        pool.succeeded(route, fallback)
        assertEquals(fallback, pool.candidates(route, candidates, 4).first())

        clock = 300_001
        assertEquals(tls, pool.candidates(route, candidates, 4).first())
        pool.failed(route, tls)
        assertEquals(fallback, pool.candidates(route, candidates, 4).first())
    }
}
