package online.morda.mordatg.socks

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets

data class SocksConnectRequest(
    val host: String,
    val port: Int,
    val addressType: AddressType,
) {
    enum class AddressType { IPV4, DOMAIN, IPV6 }
}

enum class SocksReply(val code: Int) {
    SUCCEEDED(0x00),
    GENERAL_FAILURE(0x01),
    CONNECTION_NOT_ALLOWED(0x02),
    NETWORK_UNREACHABLE(0x03),
    HOST_UNREACHABLE(0x04),
    CONNECTION_REFUSED(0x05),
    TTL_EXPIRED(0x06),
    COMMAND_NOT_SUPPORTED(0x07),
    ADDRESS_TYPE_NOT_SUPPORTED(0x08),
}

class SocksProtocolException(
    val reply: SocksReply,
    message: String,
) : Exception(message)

object Socks5Protocol {
    private const val VERSION = 0x05
    private const val NO_AUTH = 0x00
    private const val NO_ACCEPTABLE_METHODS = 0xFF
    private const val CONNECT = 0x01

    fun negotiate(input: InputStream, output: OutputStream) {
        val version = input.readByte()
        if (version != VERSION) {
            throw SocksProtocolException(SocksReply.GENERAL_FAILURE, "Unsupported SOCKS version")
        }
        val methodCount = input.readByte()
        if (methodCount == 0) {
            output.write(byteArrayOf(VERSION.toByte(), NO_ACCEPTABLE_METHODS.toByte()))
            output.flush()
            throw SocksProtocolException(SocksReply.GENERAL_FAILURE, "No authentication methods")
        }
        val methods = input.readExact(methodCount)
        if (methods.none { it.toInt() and 0xff == NO_AUTH }) {
            output.write(byteArrayOf(VERSION.toByte(), NO_ACCEPTABLE_METHODS.toByte()))
            output.flush()
            throw SocksProtocolException(SocksReply.CONNECTION_NOT_ALLOWED, "NO AUTH was not offered")
        }
        output.write(byteArrayOf(VERSION.toByte(), NO_AUTH.toByte()))
        output.flush()
    }

    fun readConnectRequest(input: InputStream): SocksConnectRequest {
        val header = input.readExact(4)
        if (header[0].toInt() and 0xff != VERSION) {
            throw SocksProtocolException(SocksReply.GENERAL_FAILURE, "Unsupported request version")
        }
        if (header[1].toInt() and 0xff != CONNECT) {
            throw SocksProtocolException(SocksReply.COMMAND_NOT_SUPPORTED, "Only TCP CONNECT is supported")
        }
        if (header[2].toInt() != 0) {
            throw SocksProtocolException(SocksReply.GENERAL_FAILURE, "Reserved byte must be zero")
        }

        val (host, type) = when (header[3].toInt() and 0xff) {
            0x01 -> (InetAddress.getByAddress(input.readExact(4)).hostAddress
                ?: throw SocksProtocolException(SocksReply.GENERAL_FAILURE, "Invalid IPv4 address")) to
                SocksConnectRequest.AddressType.IPV4
            0x03 -> {
                val length = input.readByte()
                if (length == 0) {
                    throw SocksProtocolException(SocksReply.ADDRESS_TYPE_NOT_SUPPORTED, "Empty domain")
                }
                String(input.readExact(length), StandardCharsets.US_ASCII) to
                    SocksConnectRequest.AddressType.DOMAIN
            }
            0x04 -> (InetAddress.getByAddress(input.readExact(16)).hostAddress
                ?: throw SocksProtocolException(SocksReply.GENERAL_FAILURE, "Invalid IPv6 address")) to
                SocksConnectRequest.AddressType.IPV6
            else -> throw SocksProtocolException(
                SocksReply.ADDRESS_TYPE_NOT_SUPPORTED,
                "Unsupported address type",
            )
        }

        val portBytes = input.readExact(2)
        val port = ((portBytes[0].toInt() and 0xff) shl 8) or (portBytes[1].toInt() and 0xff)
        if (port == 0) {
            throw SocksProtocolException(SocksReply.CONNECTION_NOT_ALLOWED, "Port zero is invalid")
        }
        return SocksConnectRequest(host, port, type)
    }

    fun writeReply(
        output: OutputStream,
        reply: SocksReply,
        boundAddress: InetAddress = InetAddress.getLoopbackAddress(),
        boundPort: Int = 0,
    ) {
        val address = when (boundAddress) {
            is Inet4Address -> boundAddress.address
            is Inet6Address -> boundAddress.address
            else -> byteArrayOf(127, 0, 0, 1)
        }
        val type = if (address.size == 16) 0x04 else 0x01
        output.write(
            byteArrayOf(VERSION.toByte(), reply.code.toByte(), 0, type.toByte()) +
                address +
                byteArrayOf((boundPort ushr 8).toByte(), boundPort.toByte()),
        )
        output.flush()
    }

    private fun InputStream.readByte(): Int {
        val value = read()
        if (value < 0) throw EOFException("Unexpected end of SOCKS request")
        return value
    }

    internal fun InputStream.readExact(length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = read(result, offset, length - offset)
            if (count < 0) throw EOFException("Unexpected end of stream")
            offset += count
        }
        return result
    }
}

