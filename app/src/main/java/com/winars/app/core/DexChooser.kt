package com.winars.app.core

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.winars.app.ui.FluentButton
import com.winars.app.ui.MicaSurface

/**
 * Следит за подключением внешнего монитора и ведёт Presentation.
 * Используем DISPLAY_CATEGORY_PRESENTATION: туда не попадают виртуальные дисплеи записи/трансляции.
 */
class DexController(
    private val activity: ComponentActivity,
    private val state: DesktopState
) {
    private val dm = activity.getSystemService(DisplayManager::class.java)
    var presentation: DexPresentation? = null
        private set
    private var suppressed = false

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { check() }
        override fun onDisplayChanged(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {
            if (presentation?.display?.displayId == displayId) stopWinArs()
            if (state.dexPrompt?.displayId == displayId) state.dexPrompt = null
            if (external() == null) suppressed = false
        }
    }

    fun start() {
        dm.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        check()
    }

    fun stop() = dm.unregisterDisplayListener(listener)

    fun destroy() { stop(); stopWinArs() }

    private fun external(): Display? =
        dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

    private fun check() {
        if (presentation != null || state.dexPrompt != null || suppressed) return
        state.dexPrompt = external()
    }

    fun startWinArs(display: Display) {
        state.dexPrompt = null
        suppressed = true
        presentation?.destroy()
        presentation = DexPresentation(activity, display, state).also { it.show() }
        state.targetDisplayId = display.displayId
        state.dexActive = true
    }

    fun stopWinArs() {
        presentation?.destroy()
        presentation = null
        state.dexActive = false
        state.targetDisplayId = Display.DEFAULT_DISPLAY
    }

    fun dismissPrompt() { state.dexPrompt = null; suppressed = true }
}

/** Диалог "Обнаружен внешний монитор". Подписи кнопок — строго в одну строку. */
@Composable
fun DexChooserDialog(
    onSamsungDex: () -> Unit,
    onWinArs: () -> Unit,
    onMirror: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        MicaSurface(shape = RoundedCornerShape(12.dp), modifier = Modifier.width(340.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Обнаружен внешний монитор", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Text("Что показать на мониторе?", color = Color(0xFFC5C5C5), fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                FluentButton("Запустить Samsung DeX", onSamsungDex, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                FluentButton("Запустить WinArs", onWinArs, Modifier.fillMaxWidth(), primary = true)
                Spacer(Modifier.height(8.dp))
                FluentButton("Зеркалить экран", onMirror, Modifier.fillMaxWidth())
            }
        }
    }
}
