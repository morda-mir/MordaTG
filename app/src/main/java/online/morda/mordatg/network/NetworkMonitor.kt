package online.morda.mordatg.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class NetworkMonitor(
    context: Context,
    private val onAvailabilityChanged: (Boolean) -> Unit,
) : AutoCloseable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var lastAvailable: Boolean? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publishCurrent()
        override fun onLost(network: Network) = publishCurrent()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            publishCurrent()
    }

    fun start() {
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
        publishCurrent()
    }

    private fun publishCurrent() {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val available = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (lastAvailable != available) {
            lastAvailable = available
            onAvailabilityChanged(available)
        }
    }

    override fun close() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}

