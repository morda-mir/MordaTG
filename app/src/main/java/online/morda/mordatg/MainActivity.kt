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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
import online.morda.mordatg.updates.UpdateRepository
import online.morda.mordatg.updates.AppUpdateManager
import online.morda.mordatg.updates.AppUpdateState
import java.io.File
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val updates get() = (application as MordaApplication).updates
    private var pendingInstallPath: String? = null
    private val installerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingInstallPath = null
        updates.installerFinished()
    }
    private val installPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val path = pendingInstallPath
        if (path != null && packageManager.canRequestPackageInstalls()) {
            openInstaller(File(path))
        } else {
            pendingInstallPath = null
            updates.installerFinished("Разреши установку обновлений")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInstallPath = savedInstanceState?.getString("pending_update_path")
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updates.state.map { it.readyApk }.distinctUntilChanged().filterNotNull().collect {
                    updates.takeReadyApk()?.let { path -> openInstaller(File(path)) }
                }
            }
        }
        setContent {
            MordaTheme { MordaTgApp() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppSettings(this).desiredEnabled && ProxyRuntimeState.state.value.status == ProxyStatus.STOPPED) {
            runCatching { ProxyService.start(this) }
        }
        lifecycleScope.launch { updates.check() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_update_path", pendingInstallPath)
        super.onSaveInstanceState(outState)
    }

    private fun openInstaller(apk: File) {
        try {
            if (!apk.isFile) throw java.io.IOException("Update file is missing")
            pendingInstallPath = apk.absolutePath
            when (val result = UpdateRepository(this).installerIntent(apk)) {
                is UpdateRepository.InstallResult.PermissionRequired -> installPermissionLauncher.launch(result.intent)
                is UpdateRepository.InstallResult.Ready -> installerLauncher.launch(result.intent)
            }
        } catch (_: Exception) {
            pendingInstallPath = null
            updates.installerFinished("Не удалось открыть установку")
        }
    }
}

private enum class AppScreen { MAIN, UPDATES, BACKGROUND_HELP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MordaTgApp() {
    AppWallpaper { MordaAppContent() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MordaAppContent() {
    val context = LocalContext.current
    val snapshot by ProxyRuntimeState.state.collectAsStateWithLifecycle()
    val settings = remember { AppSettings(context) }
    val snackbar = remember { SnackbarHostState() }
    val updates = remember { (context.applicationContext as MordaApplication).updates }
    val updateState by updates.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = screen != AppScreen.MAIN) { screen = AppScreen.MAIN }
    LaunchedEffect(updateState.message) {
        updateState.message?.let { message ->
            snackbar.showSnackbar(message)
            updates.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (screen) {
                                AppScreen.MAIN -> "MordaTG"
                                AppScreen.UPDATES -> "Обновления"
                                AppScreen.BACKGROUND_HELP -> "Фоновая работа"
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        if (screen == AppScreen.MAIN && updateState.available) {
                            Spacer(Modifier.width(10.dp))
                            NeonUpdateButton(updateState, updates::download)
                        }
                    }
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                ),
            )
        },
    ) { padding ->
        when (screen) {
            AppScreen.MAIN -> MainScreen(snapshot, settings, snackbar, padding)
            AppScreen.UPDATES -> UpdateScreen(updates, updateState, padding)
            AppScreen.BACKGROUND_HELP -> BackgroundHelpScreen(padding)
        }
    }
}

@Composable
private fun NeonUpdateButton(state: AppUpdateState, onClick: () -> Unit) {
    val color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF82FFF0) else Color(0xFF00665B)
    TextButton(enabled = !state.busy, onClick = onClick) {
        Text(
            when {
                state.downloading -> state.progress?.let { "$it%" } ?: "Загрузка…"
                state.installing -> "Установка…"
                else -> "Обновить"
            },
            color = color,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            style = MaterialTheme.typography.labelLarge.copy(shadow = Shadow(color.copy(alpha = 0.65f), blurRadius = 10f)),
        )
    }
}

@Composable
private fun AppWallpaper(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Image(
            painter = painterResource(R.drawable.app_wallpaper),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f,
        )
        content()
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
    val listenPort = if (snapshot.status == ProxyStatus.STOPPED) {
        settings.telegramPort.takeIf { it != 0 } ?: settings.listenPort
    } else {
        snapshot.listenPort
    }

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
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                        val opened = openTelegramProxy(context, listenPort)
                        if (opened) {
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
    val description = snapshot.lastError.takeIf { snapshot.status == ProxyStatus.ERROR }
    val container = if (snapshot.status == ProxyStatus.ERROR) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        Color.Transparent
    }
    val content = if (snapshot.status == ProxyStatus.ERROR) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(color = container, contentColor = content) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.header_face),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit,
                alpha = 0.12f,
            )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = content.copy(alpha = 0.12f),
                        contentColor = content,
                        disabledContainerColor = content.copy(alpha = 0.06f),
                        disabledContentColor = content.copy(alpha = 0.5f),
                    ),
                    border = BorderStroke(1.dp, content.copy(alpha = 0.28f)),
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
            OutlinedButton(
                enabled = enabled,
                onClick = onOpenTelegram,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            ) {
                Text(
                    when {
                        configuredPort > 0 && needsUpdate -> "Обновить"
                        else -> "Подключить"
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
private fun UpdateScreen(updates: AppUpdateManager, state: AppUpdateState, padding: PaddingValues) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            enabled = !state.busy && !state.checking,
            onClick = { scope.launch { updates.check(force = true) } },
        ) {
            if (state.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Проверить обновления")
        }
        state.manifest?.let { update ->
            Text(if (state.available) "Доступна версия ${update.versionName}" else "Установлена актуальная версия")
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Версия ${update.versionName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Опубликована: ${formatPublishedAt(update.publishedAt)}")
                    Text(update.changelog)
                    if (state.available) {
                        Button(
                            enabled = !state.busy,
                            onClick = updates::download,
                        ) { Text(if (state.downloading) "Загрузка…" else "Установить обновление") }
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
    var web = Uri.parse(url)
    val telegramUsername = web.pathSegments.firstOrNull()
        ?.takeIf { web.host.equals("t.me", ignoreCase = true) && it.matches(Regex("[A-Za-z0-9_]{5,32}")) }
    if (telegramUsername != null) {
        val start = web.getQueryParameter("start")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
            ?: "mordatg".takeIf { telegramUsername.equals("morda_online_bot", ignoreCase = true) }
        val telegram = Uri.Builder().scheme("tg").authority("resolve")
            .appendQueryParameter("domain", telegramUsername)
        if (start != null) {
            telegram.appendQueryParameter("start", start)
            if (web.getQueryParameter("start") == null) web = web.buildUpon().appendQueryParameter("start", start).build()
        }
        openPreferred(context, telegram.build(), web)
    } else {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
    }
}

private fun openPreferred(context: Context, preferred: Uri, fallback: Uri): Boolean {
    for (uri in listOf(preferred, fallback)) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            return true
        } catch (_: android.content.ActivityNotFoundException) {
            // Try the public HTTPS link if no installed client handles tg://.
        } catch (_: SecurityException) {
            // Some vendor firmware restricts a handler; let Android resolve the fallback.
        }
    }
    return false
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
