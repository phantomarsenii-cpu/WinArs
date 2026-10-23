package com.winars.app.core

import android.content.Context
import android.net.Uri
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import com.winlator.core.AppUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Окна внутри рабочего стола WinArs */
sealed class AppWindow {
    val id: Long = counter.incrementAndGet()
    abstract val title: String
    var minimized by mutableStateOf(false)

    class Explorer : AppWindow() { override val title = "Проводник" }
    class Settings : AppWindow() { override val title = "Параметры" }
    class Install(val exe: File) : AppWindow() { override val title = "Установка ${exe.name}" }
    class Properties(val shortcutId: String) : AppWindow() { override val title = "Свойства" }
    /** Экран Wine: X-сервер, нарисованный внутри окна WinArs */
    class WineScreen : AppWindow() { override val title = "Wine" }

    companion object { private val counter = AtomicLong(0) }
}

class MenuItem(val title: String, val icon: ImageVector, val action: () -> Unit)
class MenuState(val position: Offset, val items: List<MenuItem>)

/** Единое состояние: его рисуют и экран телефона, и Presentation на мониторе. */
class DesktopState(val app: Context) {
    private val prefs = app.getSharedPreferences("winars", Context.MODE_PRIVATE)
    /** тот же файл, что читает ядро (PreferenceManager.getDefaultSharedPreferences) */
    private val corePrefs = app.getSharedPreferences(app.packageName + "_preferences", Context.MODE_PRIVATE)
    var autoMaximize by mutableStateOf(corePrefs.getBoolean("wa_auto_maximize", true))
        private set
    fun setAutoMaximize(v: Boolean) { autoMaximize = v; corePrefs.edit().putBoolean("wa_auto_maximize", v).apply() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val profile: DeviceProfiler.Profile = DeviceProfiler.detect(app)

    // ---- ярлыки ----
    private val shortcutStore = ShortcutStore(app)
    val wine: WinlatorBackend get() = WineBackends.current
    val shortcuts = mutableStateListOf<Shortcut>().apply { addAll(shortcutStore.load()) }
    var selectedId by mutableStateOf<String?>(null)
    /** растёт при смене иконок — ключ для перезагрузки картинок */
    var iconRev by mutableIntStateOf(0)
    /** растёт после ответа на запрос разрешения на память — перерисовать Проводник/Параметры */
    var storageRev by mutableIntStateOf(0)

    fun addShortcut(s: Shortcut) { shortcuts.add(s); shortcutStore.save(shortcuts) }
    fun updateShortcut(s: Shortcut) {
        val i = shortcuts.indexOfFirst { it.id == s.id }
        if (i >= 0) { shortcuts[i] = s; shortcutStore.save(shortcuts) }
    }
    fun removeShortcut(id: String) {
        shortcuts.firstOrNull { it.id == id }?.let { shortcutStore.dismiss(it.exePath) }
        shortcuts.removeAll { it.id == id }
        shortcutStore.save(shortcuts)
    }

    /** Найти ярлыки, созданные Wine-программами (.lnk), и перенести их в рабочий стол WinArs. */
    fun importWineShortcuts(manual: Boolean = false) {
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                try { WineShortcutImporter.scan(app, shortcuts.toList(), shortcutStore.dismissed()) } catch (e: Throwable) { emptyList() }
            }
            found.forEach { addShortcut(it) }
            if (manual) toast(if (found.isEmpty()) "Новых программ не найдено" else "Добавлено программ: ${found.size}")
        }
    }
    fun newShortcutId() = UUID.randomUUID().toString()

    // ---- окна / меню ----
    val windows = mutableStateListOf<AppWindow>()
    var startOpen by mutableStateOf(false)
    var menu by mutableStateOf<MenuState?>(null)
    var toast by mutableStateOf<String?>(null)

    fun toast(msg: String) { toast = msg }

    fun open(w: AppWindow) { startOpen = false; windows.add(w) }
    fun close(w: AppWindow) {
        if (w is AppWindow.WineScreen) wine.stopGuest()   // закрыли окно Wine = закрыли программы; X-сервер живёт
        windows.remove(w)
    }
    fun openWineScreen() {
        startOpen = false
        windows.removeAll { it is AppWindow.Install }
        val e = windows.firstOrNull { it is AppWindow.WineScreen }
        if (e != null) focus(e) else windows.add(AppWindow.WineScreen())
    }
    private fun onWineExit() {
        windows.removeAll { it is AppWindow.WineScreen }
        toast("Ядро Wine остановлено. Перезапускаю систему…")
        // как и в оригинальном Winlator: чистый перезапуск процесса, чтобы не оставлять состояние XServer/GL
        scope.launch { delay(1200); AppUtils.restartApplication(app) }
    }
    fun focus(w: AppWindow) {
        w.minimized = false
        if (windows.lastOrNull() !== w) { windows.remove(w); windows.add(w) }
    }
    fun openExplorer() {
        startOpen = false
        val e = windows.firstOrNull { it is AppWindow.Explorer }
        if (e != null) focus(e) else windows.add(AppWindow.Explorer())
    }
    fun openSettings() {
        startOpen = false
        val e = windows.firstOrNull { it is AppWindow.Settings }
        if (e != null) focus(e) else windows.add(AppWindow.Settings())
    }
    fun openInstall(exe: File) { open(AppWindow.Install(exe)) }
    fun openProperties(id: String) { open(AppWindow.Properties(id)) }

    // ---- запуск ----
    fun launch(sc: Shortcut, asAdmin: Boolean = false) {
        startOpen = false
        scope.launch {
            val r = WineBackends.current.launch(File(sc.exePath), asAdmin)
            r.onFailure { toast("${sc.name}: ${it.message}") }
        }
    }

    /** Запуск .exe прямо из Проводника (например с диска C:) */
    fun runExe(exe: File) {
        startOpen = false
        scope.launch { wine.launch(exe, false).onFailure { toast(it.message ?: "Не удалось запустить") } }
    }

    private var androidSlot = 0
    fun launchAndroid(pkg: String) {
        startOpen = false
        if (!AndroidAppLauncher.launch(app, pkg, targetDisplayId, androidSlot++)) toast("Не удалось запустить приложение")
    }

    // ---- обои / blur ----
    val wallpaperStore = WallpaperStore(app)
    var wallpaper by mutableStateOf(wallpaperStore.current())
    var backdrop by mutableStateOf<ImageBitmap?>(null)
    var blurEnabled by mutableStateOf(prefs.getBoolean("blur", profile.blur))
        private set

    fun setBlur(v: Boolean) { blurEnabled = v; prefs.edit().putBoolean("blur", v).apply(); refreshBackdrop() }

    fun refreshBackdrop() {
        scope.launch {
            backdrop = if (blurEnabled) {
                withContext(Dispatchers.IO) { wallpaperStore.makeBackdrop(wallpaper)?.asImageBitmap() }
            } else null
        }
    }

    fun setWallpaperFromUri(uri: Uri) {
        scope.launch {
            val spec = withContext(Dispatchers.IO) { wallpaperStore.importFromUri(uri) }
            if (spec == null) toast("Нужен JPG, PNG или MP4") else { wallpaper = spec; refreshBackdrop() }
        }
    }

    fun resetWallpaper() { wallpaperStore.reset(); wallpaper = WallpaperSpec.Bloom; refreshBackdrop() }

    // ---- настройки производительности (читает ядро) ----
    var ramMb by mutableIntStateOf(prefs.getInt("ram_mb", profile.ramMb))
        private set
    var gpuDriver by mutableStateOf(prefs.getString("gpu_driver", profile.driver) ?: profile.driver)
        private set
    fun setRam(v: Int) { ramMb = v; prefs.edit().putInt("ram_mb", v).apply() }
    fun setDriver(v: String) { gpuDriver = v; prefs.edit().putString("gpu_driver", v).apply() }

    // ---- внешний монитор ----
    var dexPrompt by mutableStateOf<Display?>(null)
    var dexActive by mutableStateOf(false)
    var targetDisplayId = 0
    var cursor by mutableStateOf(Offset(200f, 200f))

    // ---- хуки, которые выставляет MainActivity ----
    var pickWallpaper: () -> Unit = {}
    var pickIconFor: (Shortcut) -> Unit = {}
    var requestAllFiles: () -> Unit = {}
    var exitApp: () -> Unit = {}
    var exitDex: () -> Unit = {}

    init {
        WineBackends.init(app)
        wine.onSessionReady = { openWineScreen() }
        wine.onSessionExit = { onWineExit() }
        wine.onGuestExit = { windows.removeAll { it is AppWindow.WineScreen } }
        wine.onError = { toast(it) }
        refreshBackdrop()
        importWineShortcuts()
    }

    companion object {
        @Volatile private var instance: DesktopState? = null
        fun get(ctx: Context): DesktopState =
            instance ?: synchronized(this) { instance ?: DesktopState(ctx.applicationContext).also { instance = it } }
    }
}
