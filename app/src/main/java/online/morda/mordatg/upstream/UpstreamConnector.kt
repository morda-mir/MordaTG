package online.morda.mordatg.upstream

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import java.io.IOException

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
        val attempts = endpointPool.candidates(route, candidateEndpoints(route), MAX_ENDPOINT_ATTEMPTS)
        var lastError: Throwable? = null
        for ((index, endpoint) in attempts.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (index > 0) {
                onReconnect()
                delay(backoff.delayMs((index - 1).coerceAtMost(4)))
            }
            var opened: RawWebSocket? = null
            try {
                val socket = runInterruptible(Dispatchers.IO) {
                    RawWebSocket.connect(
                        endpoint.connectHost,
                        endpoint.tlsHost,
                        endpoint.path,
                        connectTimeoutMs = 3_000,
                        handshakeTimeoutMs = 3_000,
                        secure = endpoint.secure,
                    ).also { opened = it }
                }
                endpointPool.succeeded(route, endpoint)
                Log.i("MordaTG", "DC${route.dcId} -> ${if (endpoint.secure) "WSS" else "WS"} ${endpoint.tlsHost}")
                return socket
            } catch (cancelled: CancellationException) {
                opened?.close()
                throw cancelled
            } catch (error: IOException) {
                endpointPool.failed(route, endpoint)
                lastError = error
                Log.w(
                    "MordaTG",
                    "${if (endpoint.secure) "WSS" else "WS"} ${endpoint.tlsHost} failed: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}",
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
                add(Endpoint(domain, domain, "/apiws", secure = false))
            }
        }
        route.webSocketDomains.forEach { domain ->
            add(Endpoint(directTargets[route.dcId] ?: domain, domain, "/apiws"))
        }
    }

    internal data class Endpoint(
        val connectHost: String,
        val tlsHost: String,
        val path: String,
        val secure: Boolean = true,
    )

    companion object {
        private const val MAX_ENDPOINT_ATTEMPTS = 4
        private val endpointPool = EndpointPool()

        fun resetRoutes() = endpointPool.clear()

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
