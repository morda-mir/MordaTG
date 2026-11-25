package online.morda.mordatg.upstream

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class EndpointPool(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val cooldownMs: Long = 30_000,
) {
    private data class Selection(val endpoint: UpstreamConnector.Endpoint, val selectedAt: Long)
    private val preferred = ConcurrentHashMap<TelegramRoute, Selection>()
    private val retryAfter = ConcurrentHashMap<UpstreamConnector.Endpoint, Long>()
    private val cursor = ConcurrentHashMap<TelegramRoute, AtomicInteger>()

    fun candidates(
        route: TelegramRoute,
        endpoints: List<UpstreamConnector.Endpoint>,
        limit: Int,
    ): List<UpstreamConnector.Endpoint> {
        if (endpoints.isEmpty()) return emptyList()
        val start = Math.floorMod(cursor.getOrPut(route) { AtomicInteger() }.getAndAdd(limit), endpoints.size)
        val now = nowMillis()
        return buildList {
            preferred[route]?.takeIf { it.endpoint in endpoints }?.let { selected ->
                if (!selected.endpoint.secure && now - selected.selectedAt >= 300_000) {
                    endpoints.firstOrNull { it.secure && it.tlsHost == selected.endpoint.tlsHost }?.let(::add)
                }
                add(selected.endpoint)
            }
            repeat(endpoints.size) { add(endpoints[(start + it) % endpoints.size]) }
        }.distinct().filter { (retryAfter[it] ?: 0L) <= now }.take(limit)
    }

    fun succeeded(route: TelegramRoute, endpoint: UpstreamConnector.Endpoint) {
        preferred.compute(route) { _, previous ->
            previous?.takeIf { it.endpoint == endpoint } ?: Selection(endpoint, nowMillis())
        }
        retryAfter.remove(endpoint)
    }

    fun failed(route: TelegramRoute, endpoint: UpstreamConnector.Endpoint) {
        preferred.computeIfPresent(route) { _, selected -> selected.takeUnless { it.endpoint == endpoint } }
        retryAfter[endpoint] = nowMillis() + cooldownMs
    }

    fun clear() {
        preferred.clear()
        retryAfter.clear()
        cursor.clear()
    }
}
