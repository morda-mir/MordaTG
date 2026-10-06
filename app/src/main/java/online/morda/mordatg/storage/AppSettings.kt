package online.morda.mordatg.storage

import android.content.Context
import online.morda.mordatg.BuildConfig
import online.morda.mordatg.core.ProxyConstants
import java.security.SecureRandom

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("mordatg_settings", Context.MODE_PRIVATE)

    var desiredEnabled: Boolean
        get() = preferences.getBoolean(KEY_DESIRED_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_DESIRED_ENABLED, value).apply()

    var autoStart: Boolean
        get() = preferences.getBoolean(KEY_AUTO_START, false)
        set(value) = preferences.edit().putBoolean(KEY_AUTO_START, value).apply()

    var listenPort: Int
        get() = synchronized(PORT_LOCK) {
            val stored = preferences.getInt(KEY_LISTEN_PORT, 0)
            if (stored in ProxyConstants.MIN_PRIVATE_PORT..ProxyConstants.MAX_PRIVATE_PORT) {
                stored
            } else {
                randomPrivatePort().also { selected ->
                    preferences.edit()
                        .putInt(KEY_LISTEN_PORT, selected)
                        .apply {
                            if (stored in 1..65535) {
                                if (!preferences.contains(KEY_TELEGRAM_PORT)) {
                                    putInt(KEY_TELEGRAM_PORT, stored)
                                }
                                putInt(KEY_PORT_CHANGE_FROM, stored)
                            }
                        }
                        .commit()
                }
            }
        }
        set(value) {
            preferences.edit().putInt(KEY_LISTEN_PORT, value).commit()
        }

    var telegramPort: Int
        get() = preferences.getInt(KEY_TELEGRAM_PORT, 0).takeIf { it in 1..65535 } ?: 0
        set(value) = preferences.edit().putInt(KEY_TELEGRAM_PORT, value).apply()

    var pendingPortChangeFrom: Int
        get() = preferences.getInt(KEY_PORT_CHANGE_FROM, 0).takeIf { it in 1..65535 } ?: 0
        set(value) = preferences.edit().putInt(KEY_PORT_CHANGE_FROM, value).apply()

    var lastUpdateCheckEpochMillis: Long
        get() = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    var updateEndpoint: String
        get() = preferences.getString(KEY_UPDATE_ENDPOINT, BuildConfig.UPDATE_ENDPOINT).orEmpty()
        set(value) = preferences.edit().putString(KEY_UPDATE_ENDPOINT, value.trim()).apply()

    var mordaEndpoint: String
        get() = preferences.getString(KEY_MORDA_ENDPOINT, BuildConfig.MORDA_ENDPOINT).orEmpty()
        set(value) = preferences.edit().putString(KEY_MORDA_ENDPOINT, value.trim()).apply()

    companion object {
        private const val KEY_DESIRED_ENABLED = "desired_enabled"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_LISTEN_PORT = "listen_port"
        private const val KEY_TELEGRAM_PORT = "telegram_port"
        private const val KEY_PORT_CHANGE_FROM = "port_change_from"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_UPDATE_ENDPOINT = "update_endpoint"
        private const val KEY_MORDA_ENDPOINT = "morda_endpoint"
        private val PORT_LOCK = Any()
        private val RANDOM = SecureRandom()

        private fun randomPrivatePort(): Int = ProxyConstants.MIN_PRIVATE_PORT +
            RANDOM.nextInt(ProxyConstants.MAX_PRIVATE_PORT - ProxyConstants.MIN_PRIVATE_PORT + 1)
    }
}

