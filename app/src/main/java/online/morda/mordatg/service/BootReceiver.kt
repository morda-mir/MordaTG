package online.morda.mordatg.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import online.morda.mordatg.storage.AppSettings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val settings = AppSettings(context)
        val shouldRestore = shouldRestoreProxy(intent.action, settings.autoStart, settings.desiredEnabled)
        if (shouldRestore) {
            runCatching { ProxyService.start(context) }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

        internal fun shouldRestoreProxy(action: String?, autoStart: Boolean, desiredEnabled: Boolean): Boolean =
            when (action) {
                Intent.ACTION_MY_PACKAGE_REPLACED -> desiredEnabled
                Intent.ACTION_BOOT_COMPLETED -> autoStart && desiredEnabled
                else -> false
            }
    }
}

