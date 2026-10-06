package online.morda.mordatg.upstream

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class UpstreamConnector(
    private val onReconnect: () -> Unit,
    private val backoff: ExponentialBackoff = ExponentialBackoff(
        initialMs = 100,
        maximumMs = 500,
        jitterRatio = 0.2,
    ),
) {
    private val directTargets = mapOf(
        2 to "149.154.167.220",
        4 to "149.154.167.220",
    )

    suspend fun connect(route: TelegramRoute): RawWebSocket {
        val routeKey = "${route.dcId}:${route.media}"
        val candidates = buildList {
            lastSuccessfulEndpoints[routeKey]?.let(::add)
            addAll(candidateEndpoints(route))
        }.distinctBy { it.tlsHost }

        var lastError: Throwable? = null
        val attempts = candidates.take(MAX_ENDPOINT_ATTEMPTS)
        for ((index, endpoint) in attempts.withIndex()) {
            if (index > 0) {
                onReconnect()
                delay(backoff.delayMs((index - 1).coerceAtMost(4)))
            }
            try {
                val socket = runInterruptible(Dispatchers.IO) {
                    RawWebSocket.connect(endpoint.connectHost, endpoint.tlsHost, endpoint.path)
                }
                lastSuccessfulEndpoints[routeKey] = endpoint
                return socket
            } catch (error: Throwable) {
                lastError = error
                Log.w(
                    "MordaTG",
                    "WSS ${endpoint.tlsHost} failed: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}",
                )
            }
        }
        throw IOException("No Telegram WSS route is reachable", lastError)
    }

    internal fun candidateEndpoints(route: TelegramRoute): List<Endpoint> = buildList {
        if (route.dcId in 1..5 || route.dcId == 203) {
            CF_PROXY_DOMAINS.forEach { base ->
                val domain = "kws${route.dcId}.$base"
                add(Endpoint(domain, domain, "/apiws"))
            }
        }
        route.webSocketDomains.forEach { domain ->
            add(Endpoint(directTargets[route.dcId] ?: domain, domain, "/apiws"))
        }
    }

    internal data class Endpoint(val connectHost: String, val tlsHost: String, val path: String)

    companion object {
        private const val MAX_ENDPOINT_ATTEMPTS = 8
        private val lastSuccessfulEndpoints = ConcurrentHashMap<String, Endpoint>()

        // Compatibility pool embedded by Flowseal/tg-ws-proxy v1.10.4.
        private val CF_PROXY_DOMAINS = listOf(
            "offshor.co.uk",
            "cakeisalie.co.uk",
            "pclead.co.uk",
            "noskomnadzor.co.uk",
            "lovetrue.co.uk",
            "sorokdva.co.uk",
            "pyatdesyatdva.co.uk",
            "kartoshka.co.uk",
            "sorokodin.co.uk",
            "pyatdesyatodin.co.uk",
            "notelega.co.uk",
            "ebally.co.uk",
            "nebally.co.uk",
            "havegreatday.co.uk",
            "pomogite.co.uk",
            "fixtelega.co.uk",
            "sadnews.co.uk",
            "onedaychamp.co.uk",
            "stopblocking.co.uk",
            "nothingthere.co.uk",
        )
    }
}
