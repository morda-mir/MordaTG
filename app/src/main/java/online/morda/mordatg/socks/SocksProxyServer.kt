package online.morda.mordatg.socks

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import online.morda.mordatg.core.ProxyConstants
import online.morda.mordatg.diagnostics.ProxyRuntimeState
import online.morda.mordatg.upstream.MtProtoMessageSplitter
import online.morda.mordatg.upstream.MtProtoObfuscation
import online.morda.mordatg.upstream.RawWebSocket
import online.morda.mordatg.upstream.TelegramDcResolver
import online.morda.mordatg.upstream.UpstreamConnector
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class SocksProxyServer(
    private val preferredPort: Int = ProxyConstants.DEFAULT_LISTEN_PORT,
    private val maxConnections: Int = ProxyConstants.MAX_CONNECTIONS,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val sockets = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val connectionJobs = Collections.synchronizedSet(mutableSetOf<Job>())
    private val permits = Semaphore(maxConnections)
    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null

    suspend fun start(): Int {
        if (!running.compareAndSet(false, true)) {
            return serverSocket?.localPort ?: preferredPort
        }
        ProxyRuntimeState.starting(preferredPort)
        try {
            val server = bindFirstAvailablePort()
            serverSocket = server
            val localScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = localScope
            ProxyRuntimeState.listening(server.localPort)
            localScope.launch { acceptLoop(server) }
            return server.localPort
        } catch (error: BindException) {
            running.set(false)
            ProxyRuntimeState.error("Нет свободного локального порта")
            throw error
        } catch (error: Throwable) {
            running.set(false)
            ProxyRuntimeState.error(error.message ?: "Не удалось запустить локальный прокси")
            throw error
        }
    }

    private fun bindFirstAvailablePort(): ServerSocket {
        var lastError: BindException? = null
        val loopback = InetAddress.getByName(ProxyConstants.LISTEN_HOST)
        for (port in ProxyConstants.candidatePorts(preferredPort)) {
            val candidate = ServerSocket()
            try {
                candidate.reuseAddress = false
                candidate.bind(java.net.InetSocketAddress(loopback, port), 32)
                return candidate
            } catch (error: BindException) {
                lastError = error
                runCatching { candidate.close() }
            } catch (error: Throwable) {
                runCatching { candidate.close() }
                throw error
            }
        }
        throw lastError ?: BindException("No local port is available")
    }

    fun resetUpstreams() {
        val snapshot = synchronized(sockets) { sockets.toList() }
        snapshot.forEach { runCatching { it.close() } }
        if (snapshot.isNotEmpty()) ProxyRuntimeState.reconnect()
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        try {
            while (running.get()) {
                val client = runInterruptible { server.accept() }
                client.tcpNoDelay = true
                client.keepAlive = true
                if (!permits.tryAcquire()) {
                    rejectBusy(client)
                    continue
                }
                sockets += client
                lateinit var job: Job
                job = scope!!.launch {
                    ProxyRuntimeState.connectionOpened()
                    try {
                        handleClient(client)
                    } finally {
                        sockets -= client
                        runCatching { client.close() }
                        permits.release()
                        ProxyRuntimeState.connectionClosed()
                        connectionJobs -= job
                    }
                }
                connectionJobs += job
            }
        } catch (_: SocketException) {
            if (running.get()) ProxyRuntimeState.error("Локальный SOCKS5 неожиданно остановлен")
        } catch (_: CancellationException) {
            // Normal service shutdown.
        } catch (error: Throwable) {
            if (running.get()) ProxyRuntimeState.error(error.message ?: "Ошибка локального SOCKS5")
        }
    }

    private fun rejectBusy(client: Socket) {
        runCatching {
            client.soTimeout = 1_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            Socks5Protocol.negotiate(input, output)
            runCatching { Socks5Protocol.readConnectRequest(input) }
            Socks5Protocol.writeReply(output, SocksReply.GENERAL_FAILURE)
        }
        runCatching { client.close() }
    }

    private suspend fun handleClient(client: Socket) {
        client.soTimeout = ProxyConstants.HANDSHAKE_TIMEOUT_MS
        val input = BufferedInputStream(client.getInputStream(), 64 * 1024)
        val output = BufferedOutputStream(client.getOutputStream(), 64 * 1024)
        var requestRead = false
        var upstream: RawWebSocket? = null
        try {
            Socks5Protocol.negotiate(input, output)
            val request = Socks5Protocol.readConnectRequest(input)
            requestRead = true
            val route = TelegramDcResolver.resolve(request.host, request.port)
                ?: throw SocksProtocolException(
                    SocksReply.CONNECTION_NOT_ALLOWED,
                    "Разрешены только известные адреса Telegram",
                )

            val connector = UpstreamConnector(ProxyRuntimeState::reconnect)
            upstream = connector.connect(route)
            Socks5Protocol.writeReply(
                output,
                SocksReply.SUCCEEDED,
                client.localAddress,
                client.localPort,
            )
            val initBytes = runInterruptible { input.readNBytesExact(MtProtoObfuscation.INIT_SIZE) }
            val init = MtProtoObfuscation.parseClientInit(initBytes)
                ?: throw IOException("Unsupported Telegram MTProto transport")
            upstream.sendBinary(initBytes)
            ProxyRuntimeState.addSent(initBytes.size)
            ProxyRuntimeState.connected(route.dcId)
            client.soTimeout = 0

            val splitter = MtProtoMessageSplitter(init)
            bridge(input, output, upstream, splitter)
        } catch (error: SocksProtocolException) {
            if (requestRead) runCatching { Socks5Protocol.writeReply(output, error.reply) }
        } catch (error: java.net.UnknownHostException) {
            if (requestRead) runCatching { Socks5Protocol.writeReply(output, SocksReply.HOST_UNREACHABLE) }
        } catch (error: java.net.ConnectException) {
            if (requestRead) runCatching { Socks5Protocol.writeReply(output, SocksReply.CONNECTION_REFUSED) }
        } catch (error: java.net.SocketTimeoutException) {
            if (requestRead) runCatching { Socks5Protocol.writeReply(output, SocksReply.TTL_EXPIRED) }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Throwable) {
            if (requestRead && upstream == null) {
                runCatching { Socks5Protocol.writeReply(output, SocksReply.HOST_UNREACHABLE) }
            }
        } finally {
            runCatching { upstream?.close() }
        }
    }

    private suspend fun bridge(
        input: BufferedInputStream,
        output: BufferedOutputStream,
        upstream: RawWebSocket,
        splitter: MtProtoMessageSplitter,
    ) = coroutineScope {
        val clientToWs = launch(Dispatchers.IO) {
            try {
                val buffer = ByteArray(64 * 1024)
                while (isActive) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    val chunk = buffer.copyOf(count)
                    val messages = splitter.split(chunk)
                    messages.forEach {
                        upstream.sendBinary(it)
                        ProxyRuntimeState.addSent(it.size)
                    }
                }
                splitter.flush().forEach {
                    upstream.sendBinary(it)
                    ProxyRuntimeState.addSent(it.size)
                }
            } finally {
                runCatching { upstream.close() }
            }
        }
        val wsToClient = launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val message = upstream.receiveBinary() ?: break
                    output.write(message)
                    output.flush()
                    ProxyRuntimeState.addReceived(message.size)
                }
            } finally {
                runCatching { upstream.close() }
            }
        }
        listOf(clientToWs, wsToClient).joinAll()
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        val active = synchronized(sockets) { sockets.toList() }
        active.forEach { runCatching { it.close() } }
        scope?.cancel()
        scope = null
        connectionJobs.clear()
        ProxyRuntimeState.stopped()
    }

    private fun BufferedInputStream.readNBytesExact(length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = read(result, offset, length - offset)
            if (count < 0) throw IOException("Telegram disconnected during MTProto init")
            offset += count
        }
        return result
    }
}
