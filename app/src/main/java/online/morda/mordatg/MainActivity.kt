package online.morda.mordatg

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.morda.mordatg.diagnostics.HealthCheckResult
import online.morda.mordatg.diagnostics.HealthChecks
import online.morda.mordatg.diagnostics.ProxyRuntimeState
import online.morda.mordatg.diagnostics.ProxySnapshot
import online.morda.mordatg.diagnostics.ProxyStatus
import online.morda.mordatg.morda.MordaContent
import online.morda.mordatg.morda.MordaRepository
import online.morda.mordatg.service.ProxyService
import online.morda.mordatg.storage.AppSettings
import online.morda.mordatg.ui.theme.MordaTheme
import online.morda.mordatg.updates.UpdateManifest
import online.morda.mordatg.updates.UpdateRepository
import java.io.File
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MordaTheme { MordaTgApp() }
        }
    }
}

private enum class AppScreen { MAIN, UPDATES, BACKGROUND_HELP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MordaTgApp() {
    val context = LocalContext.current
    val snapshot by ProxyRuntimeState.state.collectAsStateWithLifecycle()
    val settings = remember { AppSettings(context) }
    val snackbar = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = screen != AppScreen.MAIN) { screen = AppScreen.MAIN }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            AppScreen.MAIN -> "MordaTG"
                            AppScreen.UPDATES -> "Обновления"
                            AppScreen.BACKGROUND_HELP -> "Фоновая работа"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (screen != AppScreen.MAIN) {
                        IconButton(onClick = { screen = AppScreen.MAIN }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (screen == AppScreen.MAIN) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Обновления") },
                                    onClick = { menuOpen = false; screen = AppScreen.UPDATES },
                                )
                                DropdownMenuItem(
                                    text = { Text("Фоновая работа") },
                                    onClick = { menuOpen = false; screen = AppScreen.BACKGROUND_HELP },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when (screen) {
            AppScreen.MAIN -> MainScreen(snapshot, settings, snackbar, padding)
            AppScreen.UPDATES -> UpdateScreen(settings, snackbar, padding)
            AppScreen.BACKGROUND_HELP -> BackgroundHelpScreen(padding)
        }
    }
}

@Composable
private fun MainScreen(
    snapshot: ProxySnapshot,
    settings: AppSettings,
    snackbar: SnackbarHostState,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoStart by rememberSaveable { mutableStateOf(settings.autoStart) }
    var healthChecking by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<HealthCheckResult?>(null) }
    var morda by remember { mutableStateOf(MordaContent.FALLBACK) }
    var telegramPort by rememberSaveable { mutableStateOf(settings.telegramPort) }
    val listenPort = if (snapshot.status == ProxyStatus.STOPPED) settings.listenPort else snapshot.listenPort
    val telegramNeedsUpdate = telegramPort != listenPort

    LaunchedEffect(settings.mordaEndpoint) {
        morda = runCatching { MordaRepository().fetch(settings.mordaEndpoint) }
            .getOrDefault(MordaContent.FALLBACK)
    }

    val startProxy = remember(context) {
        { ProxyService.start(context) }
    }
    val backgroundPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (canRunInBackground(context)) {
            startProxy()
        } else {
            scope.launch { snackbar.showSnackbar("Разрешите фоновую работу") }
        }
    }

    fun requireBackgroundThenStart() {
        if (canRunInBackground(context)) {
            startProxy()
            return
        }
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        runCatching { backgroundPermission.launch(request) }
            .onFailure {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            requireBackgroundThenStart()
        } else {
            scope.launch { snackbar.showSnackbar("Разрешите уведомления") }
            val activity = context as? Activity
            if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openNotificationSettings(context)
            }
        }
    }

    fun toggleProxy() {
        if (snapshot.isRunning()) {
            ProxyService.stop(context)
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requireBackgroundThenStart()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        StatusField(snapshot = snapshot, onToggle = ::toggleProxy)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                TelegramSetupRow(
                    enabled = snapshot.isRunning(),
                    port = listenPort,
                    configuredPort = telegramPort,
                    onCopy = {
                        copyProxy(context, listenPort)
                        scope.launch { snackbar.showSnackbar("Адрес и порт скопированы") }
                    },
                    onOpenTelegram = {
                        val opened = if (telegramNeedsUpdate) {
                            openTelegramProxy(context, listenPort)
                        } else {
                            openTelegram(context)
                        }
                        if (opened && telegramNeedsUpdate) {
                            telegramPort = listenPort
                            settings.telegramPort = listenPort
                            settings.pendingPortChangeFrom = 0
                            ProxyRuntimeState.portSharedWithTelegram()
                        } else if (!opened) {
                            scope.launch { snackbar.showSnackbar("Telegram не найден") }
                        }
                    },
                )
                HorizontalDivider()
                SettingRow(
                    title = "Автозапуск",
                    description = "После перезагрузки",
                    trailing = {
                        Switch(
                            checked = autoStart,
                            onCheckedChange = {
                                autoStart = it
                                settings.autoStart = it
                            },
                        )
                    },
                )
                HorizontalDivider()
                SettingRow(
                    title = "Проверка",
                    description = healthResult?.detail ?: "Проверить работу прокси",
                    trailing = {
                        if (healthChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        } else {
                            TextButton(
                                enabled = snapshot.isRunning(),
                                onClick = {
                                    healthChecking = true
                                    scope.launch {
                                        healthResult = HealthChecks.run(listenPort)
                                        healthChecking = false
                                    }
                                },
                            ) { Text("Проверить") }
                        }
                    },
                )
                HorizontalDivider()
                Diagnostics(snapshot)
                HorizontalDivider()
                MordaRow(morda) { openMorda(context, morda.url) }
            }
        }
    }
}

@Composable
private fun StatusField(snapshot: ProxySnapshot, onToggle: () -> Unit) {
    val title = when (snapshot.status) {
        ProxyStatus.STOPPED -> "Прокси выключен"
        ProxyStatus.STARTING -> "Запускаем прокси"
        ProxyStatus.LISTENING -> "Прокси включён"
        ProxyStatus.CONNECTED -> "Telegram подключён"
        ProxyStatus.WAITING_FOR_NETWORK -> "Ожидание сети"
        ProxyStatus.ERROR -> "Не удалось запустить"
    }
    val description = when (snapshot.status) {
        ProxyStatus.STOPPED -> null
        ProxyStatus.STARTING -> null
        ProxyStatus.LISTENING -> null
        ProxyStatus.CONNECTED -> "Активных соединений: ${snapshot.activeConnections}${snapshot.lastDc?.let { " · DC$it" }.orEmpty()}"
        ProxyStatus.WAITING_FOR_NETWORK -> null
        ProxyStatus.ERROR -> snapshot.lastError ?: "Попробуйте ещё раз"
    }
    val container = if (snapshot.status == ProxyStatus.ERROR) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (snapshot.status == ProxyStatus.ERROR) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(color = container, contentColor = content) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = snapshot.status != ProxyStatus.STARTING,
                onClick = onToggle,
            ) {
                Icon(
                    if (snapshot.isRunning()) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (snapshot.isRunning()) "Остановить" else "Включить")
            }
        }
    }
}

@Composable
private fun TelegramSetupRow(
    enabled: Boolean,
    port: Int,
    configuredPort: Int,
    onCopy: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    val needsUpdate = configuredPort != port
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Telegram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "127.0.0.1:$port",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать параметры")
            }
            Button(enabled = enabled, onClick = onOpenTelegram) {
                Text(
                    when {
                        configuredPort == 0 -> "Подключить"
                        needsUpdate -> "Обновить"
                        else -> "Открыть"
                    },
                )
            }
        }
        if (configuredPort > 0 && needsUpdate) {
            Text(
                "Порт изменён: $configuredPort → $port",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
private fun Diagnostics(snapshot: ProxySnapshot) {
    val uptime by uptimeSeconds(snapshot.startedAtEpochMillis)
    Column(modifier = Modifier.padding(vertical = 14.dp)) {
        Text("Диагностика", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Отправлено", formatBytes(snapshot.bytesSent), Modifier.weight(1f))
            Metric("Получено", formatBytes(snapshot.bytesReceived), Modifier.weight(1f))
            Metric("Время", formatDuration(uptime), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Соединений: ${snapshot.activeConnections} · переподключений: ${snapshot.reconnects}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MordaRow(content: MordaContent, onClick: () -> Unit) {
    val image by produceState<android.graphics.Bitmap?>(null, content.imageUrl) {
        value = content.imageUrl?.let { runCatching { MordaRepository().fetchImage(it) }.getOrNull() }
    }
    Column(modifier = Modifier.padding(vertical = 14.dp)) {
        Text(content.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            content.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        image?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(4.dp))
        }
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
            Text(content.buttonLabel)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateScreen(settings: AppSettings, snackbar: SnackbarHostState, padding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { UpdateRepository(context) }
    var manifest by remember { mutableStateOf<UpdateManifest?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingInstallPath by rememberSaveable { mutableStateOf<String?>(null) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        pendingInstallPath?.let { path ->
            val apk = File(path)
            if (apk.isFile && repository.launchInstaller(apk) == UpdateRepository.InstallResult.Launched) {
                pendingInstallPath = null
            }
        }
    }
    val endpoint = settings.updateEndpoint

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (endpoint.isBlank()) {
            Text("Обновления пока недоступны", style = MaterialTheme.typography.bodyLarge)
        }
        if (endpoint.isNotBlank()) {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    status = null
                    scope.launch {
                        runCatching { repository.fetchManifest(endpoint) }
                            .onSuccess {
                                manifest = it
                                settings.lastUpdateCheckEpochMillis = System.currentTimeMillis()
                                status = if (it.versionCode > BuildConfig.VERSION_CODE) {
                                    "Доступна версия ${it.versionName}"
                                } else {
                                    "Установлена актуальная версия"
                                }
                            }
                            .onFailure { status = "Не удалось проверить обновления: ${it.message.orEmpty().take(160)}" }
                        busy = false
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Проверить обновления")
            }
        }
        status?.let {
            Text(it, color = if (it.startsWith("Не удалось")) MaterialTheme.colorScheme.error else Color.Unspecified)
        }
        manifest?.let { update ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Версия ${update.versionName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Опубликована: ${formatPublishedAt(update.publishedAt)}")
                    Text(update.changelog)
                    if (update.versionCode > BuildConfig.VERSION_CODE) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { repository.downloadAndVerify(update) }
                                        .onSuccess { apk ->
                                            when (val result = repository.launchInstaller(apk)) {
                                                UpdateRepository.InstallResult.Launched -> Unit
                                                is UpdateRepository.InstallResult.PermissionRequired -> {
                                                    pendingInstallPath = apk.absolutePath
                                                    installPermissionLauncher.launch(result.intent)
                                                }
                                            }
                                        }
                                        .onFailure { snackbar.showSnackbar("Загрузка не удалась: ${it.message.orEmpty().take(140)}") }
                                    busy = false
                                }
                            },
                        ) { Text("Установить обновление") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundHelpScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("Устройство: $manufacturer", fontWeight = FontWeight.SemiBold)
                Text(
                    manufacturerGuidance(Build.MANUFACTURER),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            },
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Настройки приложения")
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        ) { Text("Настройки оптимизации батареи") }
    }
}

@Composable
private fun uptimeSeconds(startedAt: Long?) = produceState(0L, startedAt) {
    while (startedAt != null) {
        value = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
        delay(1_000)
    }
}

private fun ProxySnapshot.isRunning(): Boolean = status in setOf(
    ProxyStatus.STARTING,
    ProxyStatus.LISTENING,
    ProxyStatus.CONNECTED,
    ProxyStatus.WAITING_FOR_NETWORK,
)

private fun copyProxy(context: Context, port: Int) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(
        ClipData.newPlainText("MordaTG SOCKS5", "127.0.0.1:$port"),
    )
}

private fun openTelegramProxy(context: Context, port: Int): Boolean {
    val tg = Uri.parse("tg://socks?server=127.0.0.1&port=$port")
    val web = Uri.parse("https://t.me/socks?server=127.0.0.1&port=$port")
    return openPreferred(context, tg, web)
}

private fun openTelegram(context: Context): Boolean {
    val packages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
    val launchIntent = packages.firstNotNullOfOrNull(context.packageManager::getLaunchIntentForPackage)
    return try {
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=morda_online_bot")))
        }
        true
    } catch (_: Exception) {
        false
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
}

private fun canRunInBackground(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val power = context.getSystemService(PowerManager::class.java)
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openMorda(context: Context, url: String) {
    val web = Uri.parse(url)
    val telegramUsername = web.pathSegments.firstOrNull()
        ?.takeIf { web.host.equals("t.me", ignoreCase = true) && it.matches(Regex("[A-Za-z0-9_]{5,32}")) }
    if (telegramUsername != null) {
        openPreferred(context, Uri.parse("tg://resolve?domain=$telegramUsername"), web)
    } else {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
    }
}

private fun openPreferred(context: Context, preferred: Uri, fallback: Uri): Boolean {
    val preferredIntent = Intent(Intent.ACTION_VIEW, preferred)
    return try {
        if (preferredIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(preferredIntent)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, fallback))
        }
        true
    } catch (_: Exception) {
        false
    }
}

private fun manufacturerGuidance(value: String): String = when (value.lowercase()) {
    "samsung" -> "Если прокси останавливается: Батарея → Ограничения фонового использования → Не переводить в сон."
    "xiaomi", "redmi", "poco" -> "Если прокси останавливается: Батарея → Экономия заряда приложений → MordaTG → Без ограничений; проверьте Автозапуск."
    "huawei", "honor" -> "Если прокси останавливается: Запуск приложений → MordaTG → Управлять вручную → разрешить работу в фоне."
    "oppo", "realme", "oneplus" -> "Если прокси останавливается: Батарея → Управление расходом приложений → разрешить фоновую активность."
    else -> "Если прокси останавливается, откройте настройки приложения и разрешите фоновую работу без автоматического запуска лишних разрешений."
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${bytes} ${units[index]}" else "%.1f %s".format(value, units[index])
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, rest)
}

private fun formatPublishedAt(value: String): String = runCatching {
    Instant.parse(value).toString().replace('T', ' ').removeSuffix("Z") + " UTC"
}.getOrDefault(value)
