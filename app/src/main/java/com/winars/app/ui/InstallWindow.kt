package com.winars.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winars.app.core.AppWindow
import com.winars.app.core.ExeIconExtractor
import com.winars.app.core.FileBridge
import com.winars.app.core.Shortcut
import com.winars.app.core.WineBackends
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Phase { Idle, Running, Done }

/**
 * Окно установки .exe: иконка 96dp (из самого exe, иначе generic_exe), имя, прогресс Fluent,
 * чекбоксы «Создать ярлык» / «Добавить в Пуск». «Установить» создаёт ярлык и запускает файл
 * через WineBackend (аналог `wine start /unix <exe>`).
 */
@Composable
fun InstallContent(w: AppWindow.Install) {
    val st = LocalDesktop.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val name = remember(w) { w.exe.nameWithoutExtension }
    val probe = remember(w) { Shortcut("", name, w.exe.absolutePath) }
    val icon by produceState<File?>(null, w) {
        value = withContext(Dispatchers.IO) { ExeIconExtractor.getIcon(ctx, w.exe, probe.iconKey) }
    }

    var desktop by remember { mutableStateOf(true) }
    var start by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    fun install() {
        phase = Phase.Running
        scope.launch {
            // ярлык: переиспользуем существующий для того же exe
            val existing = st.shortcuts.firstOrNull { it.exePath == w.exe.absolutePath }
            val sc = (existing ?: Shortcut(st.newShortcutId(), name, w.exe.absolutePath)).copy(
                onDesktop = desktop || existing?.onDesktop == true,
                inStart = start || existing?.inStart == true
            )
            if (existing == null) st.addShortcut(sc) else st.updateShortcut(sc)

            val r = WineBackends.current.launch(w.exe, false)
            r.onSuccess { message = "Запущено: ${FileBridge.toWindowsPath(ctx, w.exe)}"; error = false }
            r.onFailure { message = "Ярлык создан. Запуск невозможен: ${it.message}"; error = true }
            phase = Phase.Done
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FileIcon(icon, 96.dp)
        Spacer(Modifier.height(12.dp))
        Text(w.exe.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Издатель: неизвестен", color = Fluent.TextDim, fontSize = 12.sp)
        Text(FileBridge.toWindowsPath(ctx, w.exe), color = Fluent.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))

        Column(Modifier.fillMaxWidth()) {
            FluentCheckbox(desktop, "Создать ярлык на рабочем столе", { desktop = it })
            FluentCheckbox(start, "Добавить в меню Пуск", { start = it })
        }
        Spacer(Modifier.height(12.dp))

        when (phase) {
            Phase.Running -> { FluentProgress(null); Spacer(Modifier.height(8.dp)); Text("Выполняется…", color = Fluent.TextDim, fontSize = 12.sp) }
            Phase.Done -> {
                FluentProgress(1f)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(if (error) Color(0x33FFB900) else Color(0x3329B864)).padding(10.dp)
                ) { Text(message ?: "", color = Color.White, fontSize = 12.sp) }
            }
            Phase.Idle -> {}
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (phase == Phase.Done) {
                FluentButton("Закрыть", { st.close(w) }, primary = true)
            } else {
                FluentButton("Отмена", { st.close(w) })
                FluentButton("Установить", { install() }, primary = true, enabled = phase == Phase.Idle)
            }
        }
    }
}
