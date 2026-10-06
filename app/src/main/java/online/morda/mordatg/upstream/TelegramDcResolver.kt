package online.morda.mordatg.upstream

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class TelegramRoute(
    val dcId: Int,
    val media: Boolean = false,
) {
    val webSocketDomains: List<String>
        get() = if (media) {
            listOf("kws$dcId-1.web.telegram.org", "kws$dcId.web.telegram.org")
        } else {
            listOf("kws$dcId.web.telegram.org", "kws$dcId-1.web.telegram.org")
        }
}

object TelegramDcResolver {
    private val exactIpv4 = mapOf(
        "149.154.175.50" to 1,
        "149.154.167.51" to 2,
        "149.154.175.100" to 3,
        "149.154.167.91" to 4,
        "149.154.171.5" to 5,
        "91.105.192.100" to 203,
        "91.108.56.130" to 5,
    )

    private val exactIpv6 = mapOf(
        "2001:b28:f23d:f001::a" to 1,
        "2001:67c:4e8:f002::a" to 2,
        "2001:b28:f23d:f003::a" to 3,
        "2001:67c:4e8:f004::a" to 4,
        "2001:b28:f23f:f005::a" to 5,
    ).mapKeys { normalize(it.key) }

    fun resolve(host: String, port: Int): TelegramRoute? {
        if (port !in setOf(80, 443, 5222)) return null
        val literal = parseLiteral(host)
        if (literal != null) return resolve(literal)

        if (!host.endsWith(".telegram.org", ignoreCase = true)) return null
        return runCatching { InetAddress.getAllByName(host).firstNotNullOfOrNull(::resolve) }.getOrNull()
    }

    fun resolve(address: InetAddress): TelegramRoute? {
        val normalized = normalize(address.hostAddress ?: return null)
        val exact = when (address) {
            is Inet4Address -> exactIpv4[normalized]
            is Inet6Address -> exactIpv6[normalized]
            else -> null
        }
        if (exact != null) return TelegramRoute(exact)

        if (address is Inet4Address) {
            val bytes = address.address.map { it.toInt() and 0xff }
            val dc = when {
                bytes[0] == 149 && bytes[1] == 154 && bytes[2] == 175 && bytes[3] < 64 -> 1
                bytes[0] == 149 && bytes[1] == 154 && bytes[2] == 175 -> 3
                bytes[0] == 149 && bytes[1] == 154 && bytes[2] == 167 && bytes[3] < 64 -> 2
                bytes[0] == 149 && bytes[1] == 154 && bytes[2] == 167 -> 4
                bytes[0] == 149 && bytes[1] == 154 && bytes[2] == 171 -> 5
                bytes[0] == 91 && bytes[1] == 108 && bytes[2] == 56 -> 5
                else -> null
            }
            return dc?.let(::TelegramRoute)
        }

        if (address is Inet6Address) {
            val marker = normalized.substringBefore("::").substringAfterLast(':')
            val dc = marker.takeLast(1).toIntOrNull()
            if (dc in 1..5) return TelegramRoute(dc!!)
        }
        return null
    }

    private fun parseLiteral(host: String): InetAddress? {
        val candidate = host.removePrefix("[").removeSuffix("]")
        val looksLikeIp = candidate.contains(':') || candidate.matches(Regex("[0-9.]+"))
        return if (looksLikeIp) runCatching { InetAddress.getByName(candidate) }.getOrNull() else null
    }

    private fun normalize(value: String): String =
        runCatching { InetAddress.getByName(value).hostAddress?.lowercase()?.substringBefore('%') ?: value.lowercase() }
            .getOrElse { value.lowercase().substringBefore('%') }
}

