package online.morda.mordatg.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import android.util.Log
import online.morda.mordatg.core.ProxyConstants
import online.morda.mordatg.upstream.TelegramRoute
import online.morda.mordatg.upstream.UpstreamConnector
import java.net.InetSocketAddress
import java.net.Socket

data class HealthCheckResult(
    val localSocksAvailable: Boolean,
    val upstreamAvailable: Boolean,
    val detail: String,
)

object HealthChecks {
    suspend fun run(port: Int): HealthCheckResult {
        val local = checkLocal(port)
        val upstream = checkUpstream()
        val detail = when {
            !local -> "Прокси не запущен"
            !upstream -> "Нет соединения с Telegram"
            else -> "В порядке"
        }
        return HealthCheckResult(local, upstream, detail)
    }

    private suspend fun checkLocal(port: Int): Boolean = runInterruptible(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ProxyConstants.LISTEN_HOST, port), 1_500)
                socket.soTimeout = 1_500
                socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                socket.getOutputStream().flush()
                val reply = ByteArray(2)
                var offset = 0
                while (offset < reply.size) {
                    val count = socket.getInputStream().read(reply, offset, reply.size - offset)
                    if (count < 0) break
                    offset += count
                }
                reply.contentEquals(byteArrayOf(5, 0))
            }
        }.getOrDefault(false)
    }

    private suspend fun checkUpstream(): Boolean = try {
        UpstreamConnector(onReconnect = {}).connect(TelegramRoute(2)).use { }
        true
    } catch (error: Throwable) {
        Log.w("MordaTG", "Telegram WSS health check failed", error)
        false
    }
}
