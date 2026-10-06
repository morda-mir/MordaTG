package online.morda.mordatg.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import online.morda.mordatg.core.ProxyConstants

enum class ProxyStatus {
    STOPPED,
    STARTING,
    LISTENING,
    CONNECTED,
    WAITING_FOR_NETWORK,
    ERROR,
}

data class ProxySnapshot(
    val status: ProxyStatus = ProxyStatus.STOPPED,
    val activeConnections: Int = 0,
    val reconnects: Long = 0,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val startedAtEpochMillis: Long? = null,
    val lastError: String? = null,
    val lastDc: Int? = null,
    val networkAvailable: Boolean = true,
    val listenPort: Int = ProxyConstants.DEFAULT_LISTEN_PORT,
    val portChangedFrom: Int? = null,
)

object ProxyRuntimeState {
    private val mutable = MutableStateFlow(ProxySnapshot())
    val state: StateFlow<ProxySnapshot> = mutable.asStateFlow()

    fun starting(preferredPort: Int) = mutable.update {
        ProxySnapshot(
            status = ProxyStatus.STARTING,
            startedAtEpochMillis = System.currentTimeMillis(),
            listenPort = preferredPort,
        )
    }

    fun listening(port: Int) = mutable.update {
        it.copy(
            status = ProxyStatus.LISTENING,
            lastError = null,
            listenPort = port,
        )
    }

    fun portChanged(from: Int) = mutable.update { it.copy(portChangedFrom = from) }

    fun portSharedWithTelegram() = mutable.update { it.copy(portChangedFrom = null) }

    fun connected(dc: Int) = mutable.update {
        it.copy(status = ProxyStatus.CONNECTED, lastError = null, lastDc = dc)
    }

    fun connectionOpened() = mutable.update { it.copy(activeConnections = it.activeConnections + 1) }

    fun connectionClosed() = mutable.update {
        val remaining = (it.activeConnections - 1).coerceAtLeast(0)
        it.copy(
            activeConnections = remaining,
            status = when {
                !it.networkAvailable -> ProxyStatus.WAITING_FOR_NETWORK
                remaining == 0 && it.status == ProxyStatus.CONNECTED -> ProxyStatus.LISTENING
                else -> it.status
            },
        )
    }

    fun reconnect() = mutable.update { it.copy(reconnects = it.reconnects + 1) }

    fun addSent(bytes: Int) = mutable.update { it.copy(bytesSent = it.bytesSent + bytes) }

    fun addReceived(bytes: Int) = mutable.update { it.copy(bytesReceived = it.bytesReceived + bytes) }

    fun networkAvailable(available: Boolean) = mutable.update {
        it.copy(
            networkAvailable = available,
            status = when {
                !available -> ProxyStatus.WAITING_FOR_NETWORK
                it.status == ProxyStatus.WAITING_FOR_NETWORK -> ProxyStatus.LISTENING
                else -> it.status
            },
        )
    }

    fun error(message: String) = mutable.update {
        it.copy(status = ProxyStatus.ERROR, lastError = sanitize(message))
    }

    fun stopped() {
        val previous = mutable.value
        mutable.value = ProxySnapshot(
            reconnects = previous.reconnects,
            bytesSent = previous.bytesSent,
            bytesReceived = previous.bytesReceived,
            listenPort = previous.listenPort,
        )
    }

    private fun sanitize(message: String): String = message
        .replace(Regex("(?i)(secret|token|password|key)\\s*[:=]\\s*\\S+"), "\$1=<hidden>")
        .take(240)
}

