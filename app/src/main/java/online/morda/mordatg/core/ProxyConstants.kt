package online.morda.mordatg.core

object ProxyConstants {
    const val LISTEN_HOST = "127.0.0.1"
    const val DEFAULT_LISTEN_PORT = 51837
    const val MIN_PRIVATE_PORT = 49152
    const val MAX_PRIVATE_PORT = 65535
    const val FALLBACK_ATTEMPTS = 12
    const val MAX_CONNECTIONS = 64
    const val HANDSHAKE_TIMEOUT_MS = 10_000
    const val CONNECT_TIMEOUT_MS = 8_000
    const val READ_TIMEOUT_MS = 45_000
    const val MAX_WS_MESSAGE_BYTES = 16 * 1024 * 1024

    fun candidatePorts(preferredPort: Int): List<Int> = buildList {
        val first = preferredPort.takeIf { it in 1..65535 } ?: DEFAULT_LISTEN_PORT
        add(first)

        val rangeSize = MAX_PRIVATE_PORT - MIN_PRIVATE_PORT + 1
        val start = if (first in MIN_PRIVATE_PORT..MAX_PRIVATE_PORT) {
            first - MIN_PRIVATE_PORT
        } else {
            DEFAULT_LISTEN_PORT - MIN_PRIVATE_PORT
        }
        repeat(FALLBACK_ATTEMPTS) { index ->
            val port = MIN_PRIVATE_PORT + Math.floorMod(start + (index + 1) * 997, rangeSize)
            if (port != first) add(port)
        }
    }
}

