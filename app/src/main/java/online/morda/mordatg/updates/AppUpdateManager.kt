package online.morda.mordatg.updates

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import online.morda.mordatg.BuildConfig
import online.morda.mordatg.storage.AppSettings
import java.io.IOException

data class AppUpdateState(
    val manifest: UpdateManifest? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int? = null,
    val readyApk: String? = null,
    val installing: Boolean = false,
    val message: String? = null,
) {
    val available: Boolean get() = (manifest?.versionCode ?: 0) > BuildConfig.VERSION_CODE
    val busy: Boolean get() = downloading || installing || readyApk != null
}

class AppUpdateManager(context: Context) {
    private val settings = AppSettings(context.applicationContext)
    private val repository = UpdateRepository(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checkLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutable = MutableStateFlow(AppUpdateState(manifest = readCached()))
    val state: StateFlow<AppUpdateState> = mutable.asStateFlow()

    private fun readCached(): UpdateManifest? = runCatching {
        json.decodeFromString<UpdateManifest>(settings.cachedUpdateManifest)
            .takeIf { it.validate().isEmpty() }
    }.getOrNull()

    suspend fun check(force: Boolean = false) = checkLock.withLock {
        val now = System.currentTimeMillis()
        if (!force && !UpdateCheckPolicy.isDue(settings.lastUpdateAttemptEpochMillis, now)) return@withLock
        val endpoint = settings.updateEndpoint.takeIf { it.isNotBlank() } ?: return@withLock
        settings.lastUpdateAttemptEpochMillis = now
        mutable.update { it.copy(checking = true, message = null) }
        try {
            val manifest = repository.fetchManifest(endpoint)
            settings.cachedUpdateManifest = json.encodeToString(manifest)
            settings.lastUpdateCheckEpochMillis = System.currentTimeMillis()
            mutable.update { it.copy(manifest = manifest) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            if (force) mutable.update { it.copy(message = "Не удалось проверить обновления") }
        } catch (_: IllegalArgumentException) {
            if (force) mutable.update { it.copy(message = "Не удалось проверить обновления") }
        } finally {
            mutable.update { it.copy(checking = false) }
        }
    }

    fun download() {
        val current = mutable.value
        val manifest = current.manifest?.takeIf { current.available && !current.busy } ?: return
        mutable.update { it.copy(downloading = true, progress = 0, message = null) }
        scope.launch {
            try {
                val apk = repository.downloadAndVerify(manifest) { progress ->
                    mutable.update { it.copy(progress = progress) }
                }
                mutable.update { it.copy(downloading = false, readyApk = apk.absolutePath, progress = null) }
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(downloading = false, progress = null) }
                throw cancelled
            } catch (_: IOException) {
                mutable.update { it.copy(downloading = false, progress = null, message = "Не удалось загрузить обновление") }
            } catch (_: IllegalArgumentException) {
                mutable.update { it.copy(downloading = false, progress = null, message = "Обновление не прошло проверку") }
            }
        }
    }

    fun takeReadyApk(): String? {
        val path = mutable.value.readyApk ?: return null
        mutable.update { it.copy(readyApk = null, installing = true) }
        return path
    }

    fun installerFinished(message: String? = null) {
        mutable.update { it.copy(installing = false, message = message) }
    }

    fun clearMessage() = mutable.update { it.copy(message = null) }
}
