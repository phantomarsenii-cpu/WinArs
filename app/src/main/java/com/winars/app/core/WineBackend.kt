package com.winars.app.core

import android.content.Context
import android.content.Intent
import android.os.Environment
import com.winlator.XServerDisplayActivity
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.container.GraphicsDrivers
import com.winlator.xenvironment.RootFS
import com.winlator.xenvironment.RootFSInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/** Интерфейс запуска Windows-программ. Реализация — [WinlatorBackend] (Box64 + Wine из Winlator 11.2). */
interface WineBackend {
    val available: Boolean
    val status: String

    /** Аналог `wine start /unix <exe>` в контейнере Winlator. */
    suspend fun launch(exe: File, asAdmin: Boolean): Result<Unit>
}

object WineBackends {
    lateinit var current: WineBackend
        private set

    fun init(ctx: Context) {
        if (!::current.isInitialized) current = WinlatorBackend(ctx.applicationContext)
    }
}

/**
 * Запуск идёт тем же путём, что и в самом Winlator: XServerDisplayActivity получает
 * container_id + exec_path, поднимает XServer, Box64, Wine, PulseAudio и Vortek/Gladio/Turnip/VirGL,
 * после чего Wine запускает exe. Пока Wine работает, на экране окно X-сервера Winlator.
 */
class WinlatorBackend(private val app: Context) : WineBackend {

    private fun rootReady(): Boolean {
        val rfs = RootFS.find(app)
        return rfs.isValid && rfs.version >= RootFSInstaller.LATEST_VERSION
    }

    override val available: Boolean get() = rootReady()

    override val status: String
        get() = if (!rootReady()) {
            "Система Wine ещё не установлена (установится при первом запуске .exe)"
        } else {
            val n = ContainerManager(app).containers.size
            "Wine + Box64 готовы · контейнеров: $n"
        }

    override suspend fun launch(exe: File, asAdmin: Boolean): Result<Unit> = withContext(Dispatchers.Main) {
        if (!exe.exists()) return@withContext Result.failure(IllegalStateException("Файл не найден: ${exe.name}"))

        if (!rootReady()) {
            // первичная установка rootfs.tzst/box64 — штатный экран Winlator с прогрессом
            app.startActivity(Intent(app, com.winlator.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return@withContext Result.failure(
                IllegalStateException("Устанавливаю систему Wine. Дождись конца установки и запусти программу снова")
            )
        }

        val manager = ContainerManager(app)
        val container = manager.containers.firstOrNull() ?: createDefaultContainer(manager)
            ?: return@withContext Result.failure(IllegalStateException("Не удалось создать контейнер Wine"))

        ensureDrive(container, exe)

        app.startActivity(
            Intent(app, XServerDisplayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("container_id", container.id)
                .putExtra("exec_path", exe.absolutePath)
        )
        Result.success(Unit)
    }

    /** Контейнер по умолчанию: драйвер по GPU (Adreno -> Turnip, иначе Vortek), D: = Download, F: = вся память. */
    private suspend fun createDefaultContainer(manager: ContainerManager): Container? =
        suspendCancellableCoroutine { cont ->
            val data = JSONObject()
                .put("name", "WinArs")
                .put("graphicsDriver", GraphicsDrivers.getDefaultDriver(app))
                .put("drives", Container.DEFAULT_DRIVES + "F:" + Environment.getExternalStorageDirectory().path)
            manager.createContainerAsync(data) { container -> if (cont.isActive) cont.resume(container) }
        }

    /** Если exe лежит вне подключённых дисков контейнера — добавляем диск с буквой под нужную папку. */
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
