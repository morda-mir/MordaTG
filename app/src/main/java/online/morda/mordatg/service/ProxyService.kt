package online.morda.mordatg.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import online.morda.mordatg.MainActivity
import online.morda.mordatg.R
import online.morda.mordatg.diagnostics.ProxyRuntimeState
import online.morda.mordatg.diagnostics.ProxyStatus
import online.morda.mordatg.network.NetworkMonitor
import online.morda.mordatg.socks.SocksProxyServer
import online.morda.mordatg.storage.AppSettings

class ProxyService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var proxyServer: SocksProxyServer? = null
    private var networkMonitor: NetworkMonitor? = null
    private var promoted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promote(buildNotification("Запуск локального прокси…"))
        observeNotificationState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopProxy(userRequested = true)
            else -> startProxy()
        }
        return START_STICKY
    }

    private fun startProxy() {
        if (proxyServer != null) return
        val settings = AppSettings(this)
        settings.desiredEnabled = true
        val telegramPort = settings.telegramPort
        val preferredPort = telegramPort.takeIf { it != 0 } ?: settings.listenPort
        if (telegramPort != 0) {
            settings.listenPort = preferredPort
            settings.pendingPortChangeFrom = 0
            ProxyRuntimeState.portSharedWithTelegram()
        }
        val server = SocksProxyServer(
            preferredPort = preferredPort,
            allowPortFallback = telegramPort == 0,
        )
        proxyServer = server
        networkMonitor = NetworkMonitor(this) { available ->
            val wasAvailable = ProxyRuntimeState.state.value.networkAvailable
            ProxyRuntimeState.networkAvailable(available)
            if (available && !wasAvailable) server.resetUpstreams()
        }.also { it.start() }
        serviceScope.launch {
            runCatching { server.start() }
                .onSuccess { selectedPort ->
                    settings.listenPort = selectedPort
                    settings.pendingPortChangeFrom.takeIf { it != 0 && it != selectedPort }
                        ?.let(ProxyRuntimeState::portChanged)
                }
                .onFailure {
                    AppSettings(this@ProxyService).desiredEnabled = false
                    stopProxy(userRequested = false, preserveError = true)
                }
        }
    }

    private fun stopProxy(userRequested: Boolean, preserveError: Boolean = false) {
        if (userRequested) AppSettings(this).desiredEnabled = false
        networkMonitor?.close()
        networkMonitor = null
        proxyServer?.close()
        proxyServer = null
        if (!preserveError) ProxyRuntimeState.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        promoted = false
        stopSelf()
    }

    private fun observeNotificationState() {
        serviceScope.launch {
            ProxyRuntimeState.state
                .map {
                    NotificationState(
                        status = it.status,
                        listenPort = it.listenPort,
                        portChangedFrom = it.portChangedFrom,
                    )
                }
                .distinctUntilChanged()
                .collect { state ->
                    val text = when {
                        state.portChangedFrom != null -> "Порт изменён на ${state.listenPort} · обновите Telegram"
                        state.status == ProxyStatus.STARTING -> "Запуск локального прокси…"
                        state.status == ProxyStatus.LISTENING -> "Telegram через прокси · ожидание"
                        state.status == ProxyStatus.CONNECTED -> "Telegram через прокси"
                        state.status == ProxyStatus.WAITING_FOR_NETWORK -> "Ожидание сети"
                        state.status == ProxyStatus.ERROR -> "Ошибка запуска прокси"
                        else -> "Прокси остановлен"
                    }
                    updateNotification(buildNotification(text))
                    if (state.portChangedFrom != null) {
                        notifyPortChanged(state.listenPort)
                    } else {
                        NotificationManagerCompat.from(this@ProxyService)
                            .cancel(PORT_CHANGED_NOTIFICATION_ID)
                    }
                }
        }
    }

    private fun notifyPortChanged(port: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, PORT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Порт изменён")
            .setContentText("Новый порт: $port. Обновите Telegram.")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(this).notify(PORT_CHANGED_NOTIFICATION_ID, notification)
    }

    private fun promote(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        promoted = true
    }

    private fun updateNotification(notification: Notification) {
        if (!promoted) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ProxyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MordaTG")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Остановить", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Работа прокси",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Состояние локального Telegram-прокси"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val portChannel = NotificationChannel(
            PORT_CHANNEL_ID,
            "Смена порта",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(portChannel)
    }

    override fun onDestroy() {
        networkMonitor?.close()
        proxyServer?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "mordatg_proxy"
        private const val PORT_CHANNEL_ID = "mordatg_port"
        private const val NOTIFICATION_ID = 41_001
        private const val PORT_CHANGED_NOTIFICATION_ID = 41_002
        private const val ACTION_START = "online.morda.mordatg.action.START"
        private const val ACTION_STOP = "online.morda.mordatg.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ProxyService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ProxyService::class.java).setAction(ACTION_STOP))
        }
    }

    private data class NotificationState(
        val status: ProxyStatus,
        val listenPort: Int,
        val portChangedFrom: Int?,
    )
}

