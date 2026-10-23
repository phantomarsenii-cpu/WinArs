package com.winars.app.core

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.winlator.WineSession
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.container.GraphicsDrivers
import com.winlator.xenvironment.RootFSInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/** Запуск Windows-программ. Реализация — [WinlatorBackend]: Winlator здесь только невидимое ядро. */
interface WineBackend {
    val available: Boolean
    val status: String

    /** Аналог `wine start /unix <exe>` в контейнере. */
    suspend fun launch(exe: File, asAdmin: Boolean): Result<Unit>
}

object WineBackends {
    private var backend: WinlatorBackend? = null

    val current: WinlatorBackend
        get() = backend ?: error("WineBackends.init не вызван")

    fun init(ctx: Context) {
        if (backend == null) backend = WinlatorBackend(ctx.applicationContext)
    }
}

/**
 * Ядро Winlator без интерфейса: RootFSInstaller распаковывает систему, ContainerManager создаёт контейнер,
 * WineSession поднимает XServer + Box64 + Wine и отдаёт X-экран как обычный View в окно WinArs.
 * Intent в какую-либо Activity Winlator не используется.
 */
class WinlatorBackend(private val app: Context) : WineBackend {
    private val ui = Handler(Looper.getMainLooper())

    /** 0..100 во время распаковки rootfs, иначе -1 */
    var installProgress by mutableIntStateOf(-1)
        private set
    var session by mutableStateOf<WineSession?>(null)
        private set
    val xWindows = mutableStateListOf<WineSession.WindowInfo>()
    var sessionReady by mutableStateOf(false)
        private set
    /** есть ли сейчас запущенные программы Wine (сессия при этом может жить и без них) */
    var guestActive by mutableStateOf(false)
        private set
    var onGuestExit: () -> Unit = {}
    var onWindowsRefreshed: () -> Unit = {}

    var onSessionReady: () -> Unit = {}
    var onSessionExit: () -> Unit = {}
    var onError: (String) -> Unit = {}

    override val available: Boolean get() = RootFSInstaller.isInstalled(app)

    override val status: String
        get() = when {
            installProgress >= 0 -> "Установка системы Wine: $installProgress%"
            !available -> "Система Wine установится при первом запуске .exe"
            session != null -> "Wine запущен · окон: ${xWindows.size}"
            else -> "Wine + Box64 готовы · контейнеров: ${ContainerManager(app).containers.size}"
        }

    override suspend fun launch(exe: File, asAdmin: Boolean): Result<Unit> = withContext(Dispatchers.Main) {
        if (!exe.exists()) return@withContext Result.failure(IllegalStateException("Файл не найден: ${exe.name}"))
        if (installProgress >= 0) return@withContext Result.failure(IllegalStateException("Идёт установка системы Wine"))

        // 1. rootfs
        if (!RootFSInstaller.isInstalled(app) && !installRootFs()) {
            return@withContext Result.failure(IllegalStateException("Не удалось установить систему Wine"))
        }

        // 2. контейнер
        val manager = ContainerManager(app)
        val container = manager.containers.firstOrNull() ?: createDefaultContainer(manager)
            ?: return@withContext Result.failure(IllegalStateException("Не удалось создать контейнер Wine"))
        ensureDrive(container, exe)

        // 3. Wine уже работает -> запускаем программу внутри него
        session?.let {
            if (!it.isGuestRunning) sessionReady = false
            guestActive = true
            it.runProgram(exe.absolutePath)
            return@withContext Result.success(Unit)
        }

        // 4. новая сессия: XEnvironment + Wine без Activity
        val s = WineSession(app)
        session = s
        sessionReady = false
        guestActive = true
        s.start(container, exe.absolutePath, object : WineSession.Listener {
            override fun onReady() { sessionReady = true; refreshWindows(); onSessionReady() }
            override fun onWindowsChanged() { refreshWindows() }
            override fun onGuestExit() {
                guestActive = false; sessionReady = false; xWindows.clear()
                this@WinlatorBackend.onGuestExit()
            }
            override fun onExit() { session = null; sessionReady = false; guestActive = false; xWindows.clear(); onSessionExit() }
            override fun onError(message: String) { this@WinlatorBackend.onError(message) }
        })
        Result.success(Unit)
    }

    fun refreshWindows() {
        val s = session ?: return
        val list = s.activeWindows
        xWindows.clear()
        xWindows.addAll(list)
        onWindowsRefreshed()
    }

    /** закрыть программы Wine, X-сервер оставить */
    fun stopGuest() { session?.stopGuest() }

    /** остановить ядро целиком (после этого система перезапускается) */
    fun stop() { session?.stop() }

    private suspend fun installRootFs(): Boolean = suspendCancellableCoroutine { cont ->
        installProgress = 0
        RootFSInstaller.install(app, object : RootFSInstaller.Listener {
            override fun onProgress(percent: Int) { installProgress = percent }
            override fun onFinished(success: Boolean) {
                ui.post { installProgress = -1; if (cont.isActive) cont.resume(success) }
            }
        })
    }

    /** Контейнер по умолчанию: драйвер по GPU (Adreno -> Turnip, иначе Vortek), D: = Download, F: = вся память. */
    private suspend fun createDefaultContainer(manager: ContainerManager): Container? =
        suspendCancellableCoroutine { cont ->
            @Suppress("DEPRECATION")
            val data = JSONObject()
                .put("name", "WinArs")
                .put("graphicsDriver", GraphicsDrivers.getDefaultDriver(app))
                .put("drives", Container.DEFAULT_DRIVES + "F:" + Environment.getExternalStorageDirectory().path)
            manager.createContainerAsync(data) { container -> if (cont.isActive) cont.resume(container) }
        }

    /** Если exe лежит вне подключённых дисков контейнера — добавляем диск под нужную папку. */
    @Suppress("DEPRECATION")
    private fun ensureDrive(container: Container, exe: File) {
        val path = exe.absolutePath
        if (path.contains("/.wine/drive_c")) return
        if (container.drivesIterator().any { path.startsWith(it.path) }) return
        val used = container.drivesIterator().map { it.letter }.toSet()
        val letter = ('F'..'Y').map { it.toString() }.firstOrNull { it !in used } ?: return
        val base = Environment.getExternalStorageDirectory().path.takeIf { path.startsWith(it) } ?: exe.parent ?: return
        container.drives = container.drives + letter + ":" + base
        container.saveData()
    }
}
