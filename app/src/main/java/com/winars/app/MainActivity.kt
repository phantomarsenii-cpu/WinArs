package com.winars.app

import android.Manifest
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.winars.app.core.AppWindow
import com.winars.app.core.DesktopState
import com.winars.app.core.DexChooserDialog
import com.winars.app.core.DexController
import com.winars.app.core.ExeIconExtractor
import com.winars.app.core.Shortcut
import com.winars.app.core.SpenInput
import com.winars.app.ui.DesktopRoot
import com.winars.app.ui.LocalDesktop
import com.winars.app.ui.TouchpadScreen
import com.winars.app.ui.WinArsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var st: DesktopState
    private lateinit var dex: DexController
    private var iconTarget: Shortcut? = null

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) st.setWallpaperFromUri(uri)
    }

    private val iconPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val sc = iconTarget
        if (uri != null && sc != null) {
            st.scope.launch {
                val f = withContext(Dispatchers.IO) { ExeIconExtractor.saveCustomIcon(this@MainActivity, sc.iconKey, uri) }
                if (f == null) st.toast("Не удалось прочитать изображение") else { st.iconRev++; st.toast("Иконка изменена") }
            }
        }
    }

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        st.storageRev++
        st.toast(if (r.values.all { it }) "Доступ к памяти выдан" else "Доступ к памяти не выдан")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        st = DesktopState.get(applicationContext)
        dex = DexController(this, st)

        st.pickWallpaper = { wallpaperPicker.launch(arrayOf("image/*", "video/mp4")) }
        st.pickIconFor = { sc -> iconTarget = sc; iconPicker.launch(arrayOf("image/*")) }
        st.requestAllFiles = { requestAllFilesAccess() }
        st.exitDex = { dex.stopWinArs() }
        st.exitApp = { dex.stopWinArs(); finishAffinity() }

        window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            CompositionLocalProvider(LocalDesktop provides st) {
                WinArsTheme {
                    if (st.dexActive) TouchpadScreen(st) { dex.presentation } else DesktopRoot(st, external = false)

                    st.dexPrompt?.let { display ->
                        DexChooserDialog(
                            onSamsungDex = {
                                dex.dismissPrompt()
                                st.toast("Samsung DeX запускается системой: открой шторку уведомлений и нажми DeX")
                            },
                            onWinArs = { dex.startWinArs(display) },
                            onMirror = { dex.dismissPrompt() },
                            onDismiss = { dex.dismissPrompt() }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); dex.start() }
    override fun onStop() { dex.stop(); super.onStop() }
    override fun onDestroy() { dex.destroy(); super.onDestroy() }

    /** S-Pen: кнопка пера + касание = правый клик */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val mapped = SpenInput.remap(ev)
        val handled = super.dispatchTouchEvent(mapped)
        if (mapped !== ev) mapped.recycle()
        return handled
    }

    /** Пока рабочий стол на мониторе, клавиатура телефона (физическая и экранная) пишет в него. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val top = st.windows.lastOrNull()
        if (top is AppWindow.WineApp && !top.minimized && event.keyCode !in PASS_THROUGH) {
            if (st.wine.session?.dispatchKey(event) == true) return true
        }
        val p = dex.presentation
        if (st.dexActive && p != null && event.keyCode !in PASS_THROUGH) {
            if (p.forwardKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun requestAllFilesAccess() {
        storagePermission.launch(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        )
    }

    private companion object {
        val PASS_THROUGH = setOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE
        )
    }
}
