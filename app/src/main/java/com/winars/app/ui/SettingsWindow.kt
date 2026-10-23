package com.winars.app.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winars.app.core.AppWindow
import com.winars.app.core.FileBridge
import com.winars.app.core.WallpaperSpec
import com.winars.app.core.WineBackends
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF)).padding(14.dp)) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Fluent.TextDim, fontSize = 12.sp, modifier = Modifier.weight(0.4f))
        Text(value, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
    }
}

@Composable
fun SettingsContent() {
    val st = LocalDesktop.current
    val ctx = LocalContext.current
    val p = st.profile
    val am = remember { ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }
    val totalMb = remember { ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem / (1024 * 1024) }
    val maxRam = remember { minOf(8192, (totalMb * 0.75).toInt() / 256 * 256).coerceAtLeast(1024) }
    var ram by remember { mutableFloatStateOf(st.ramMb.toFloat().coerceIn(512f, maxRam.toFloat())) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card("Персонализация") {
            Line("Обои", when (val w = st.wallpaper) {
                WallpaperSpec.Bloom -> "Bloom Dark"
                is WallpaperSpec.Image -> "Изображение"
                is WallpaperSpec.Video -> "Видео (MP4)"
            })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluentButton("Выбрать обои", { st.pickWallpaper() }, primary = true)
                FluentButton("Bloom Dark", { st.resetWallpaper() })
            }
            Spacer(Modifier.height(4.dp))
            Text("JPG, PNG или MP4 (живые обои). Смена — кроссфейд 300 мс.", color = Fluent.TextDim, fontSize = 11.sp)
        }

        Card("Производительность") {
            Line("GPU", p.gpu.ifBlank { "не определён" })
            Line("Профиль", p.label)
            Line("Графика", p.d3d)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mica blur", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(st.blurEnabled, { st.setBlur(it) })
            }
            Text("ОЗУ контейнера: ${ram.toInt()} МБ", color = Color.White, fontSize = 13.sp)
            Slider(
                ram, { ram = it }, onValueChangeFinished = { st.setRam(ram.toInt()) },
                valueRange = 512f..maxRam.toFloat(), steps = ((maxRam - 512) / 256 - 1).coerceAtLeast(0)
            )
            Text("Драйвер GPU", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Turnip", "VirGL").forEach { d ->
                    FluentButton(d, { st.setDriver(d) }, primary = st.gpuDriver == d)
                }
            }
            if (p.vendor != com.winars.app.core.DeviceProfiler.Vendor.ADRENO && st.gpuDriver == "Turnip") {
                Spacer(Modifier.height(6.dp))
                Text("Turnip работает только на Adreno. На Mali выбери VirGL.", color = Color(0xFFFFB900), fontSize = 11.sp)
            }
        }

        Card("Хранилище") {
            Line("C:", FileBridge.cRoot(ctx).absolutePath)
            Line("D:", FileBridge.dRoot().absolutePath)
            Line("Z:", FileBridge.zRoot().absolutePath)
            Spacer(Modifier.height(8.dp))
            if ((st.storageRev >= 0 && FileBridge.hasAllFilesAccess(ctx))) {
                Text("Доступ ко всей памяти выдан", color = Color(0xFF6CCB5F), fontSize = 12.sp)
            } else {
                FluentButton("Разрешить доступ к памяти", { st.requestAllFiles() }, primary = true)
            }
        }

        Card("S-Pen и ввод") {
            Text("Тап пером — левый клик", color = Color.White, fontSize = 12.sp)
            Text("Двойной тап (<300 мс) — двойной клик", color = Color.White, fontSize = 12.sp)
            Text("Кнопка S-Pen + касание — правый клик", color = Color.White, fontSize = 12.sp)
            Text("Перо над экраном — движение курсора", color = Color.White, fontSize = 12.sp)
            Text("Мышь и клавиатура USB/Bluetooth работают штатно", color = Fluent.TextDim, fontSize = 12.sp)
        }

        Card("Ядро Wine") {
            Line("Состояние", WineBackends.current.status)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Разворачивать программу на весь экран Wine", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(st.autoMaximize, { st.changeAutoMaximize(it) })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluentButton("Обновить программы", { st.importWineShortcuts(manual = true) }, primary = true)
                if (st.wine.session != null) FluentButton("Перезапустить ядро", { st.wine.stop() })
            }
            Spacer(Modifier.height(4.dp))
            Text("Ярлыки установленных программ переносятся из Wine в рабочий стол и меню Пуск WinArs.", color = Fluent.TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
fun PropertiesContent(w: AppWindow.Properties) {
    val st = LocalDesktop.current
    val ctx = LocalContext.current
    val sc = st.shortcuts.firstOrNull { it.id == w.shortcutId }
    if (sc == null) { LaunchedEffect(Unit) { st.close(w) }; return }
    val f = File(sc.exePath)

    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ShortcutIcon(sc, 72.dp)
        Spacer(Modifier.height(10.dp))
        Text(sc.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Line("Путь Windows", FileBridge.toWindowsPath(ctx, f))
        Line("Путь Android", sc.exePath)
        Line("Размер", if (f.exists()) humanSize(f.length()) else "файл не найден")
        Line("Изменён", if (f.exists()) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified())) else "—")
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentButton("Сменить иконку", { st.pickIconFor(sc) })
            FluentButton("Закрыть", { st.close(w) }, primary = true)
        }
    }
}
