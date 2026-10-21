package com.winars.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winars.app.core.DesktopState
import com.winars.app.core.ExeIconExtractor
import com.winars.app.core.FileBridge
import com.winars.app.core.MenuItem
import com.winars.app.core.Shortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class Place(val label: String, val icon: ImageVector, val dir: File)

@Composable
fun ExplorerContent() {
    val st = LocalDesktop.current
    val ctx = LocalContext.current

    val c = remember { FileBridge.cRoot(ctx) }
    val quick = remember {
        listOf(
            Place("Рабочий стол", Icons.Default.DesktopWindows, File(c, "Users/WinArs/Desktop")),
            Place("Загрузки", Icons.Default.Download, FileBridge.dRoot()),
            Place("Документы", Icons.Default.Description, File(c, "Users/WinArs/Documents"))
        )
    }
    val drives = remember {
        listOf(
            Place("Локальный диск (C:)", Icons.Default.Storage, c),
            Place("Загрузки (D:)", Icons.Default.Download, FileBridge.dRoot()),
            Place("Память телефона (Z:)", Icons.Default.PhoneAndroid, FileBridge.zRoot())
        )
    }

    var current by remember { mutableStateOf(c) }
    val history = remember { mutableStateListOf<File>() }
    var selected by remember { mutableStateOf<File?>(null) }
    var query by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    fun go(dir: File) { history.add(current); current = dir; selected = null; query = "" }

    val files by produceState(emptyList<File>(), current, query, refresh) {
        value = withContext(Dispatchers.IO) {
            FileBridge.list(current).filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        }
    }
    val locked = st.storageRev >= 0 && FileBridge.needsAllFiles(ctx, current) && !FileBridge.hasAllFilesAccess(ctx)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 520.dp
        Column(Modifier.fillMaxSize()) {
            // ---- панель навигации ----
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NavButton(Icons.Default.ArrowBack, history.isNotEmpty()) { current = history.removeAt(history.lastIndex); selected = null }
                NavButton(Icons.Default.ArrowUpward, current.parentFile != null && current.absolutePath != "/") { current.parentFile?.let { go(it) } }
                NavButton(Icons.Default.Refresh, true) { refresh++ }
                Box(
                    Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x1FFFFFFF))
                        .border(1.dp, Fluent.Stroke, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(FileBridge.toWindowsPath(ctx, current), color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.width(if (narrow) 110.dp else 170.dp).height(34.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x1FFFFFFF))
                        .border(1.dp, Fluent.Stroke, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Fluent.TextDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = { inner -> if (query.isEmpty()) Text("Поиск", color = Fluent.TextDim, fontSize = 13.sp); inner() }
                    )
                }
            }

            if (narrow) {
                // компактно: места — чипами сверху
                LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quick + drives) { p -> PlaceChip(p, current == p.dir) { go(p.dir) } }
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!narrow) {
                    Column(Modifier.width(190.dp).fillMaxHeight().padding(horizontal = 6.dp)) {
                        SideTitle("Быстрый доступ")
                        quick.forEach { SideItem(it, current == it.dir) { go(it.dir) } }
                        SideTitle("Этот компьютер")
                        drives.forEach { SideItem(it, current == it.dir) { go(it.dir) } }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (locked) {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFB900)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Нет доступа ко всей памяти (нужно для D: и Z:)", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            FluentButton("Разрешить", { st.requestAllFiles() })
                        }
                    }
                    if (files.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Папка пуста", color = Fluent.TextDim, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(files, key = { it.absolutePath }) { f ->
                                FileRow(st, f, selected == f, !narrow,
                                    onSelect = { selected = f },
                                    onOpen = { if (f.isDirectory) go(f) else if (FileBridge.isExe(f)) st.openInstall(f) })
                            }
                        }
                    }
                }
            }

            // ---- командная строка ----
            Row(
                Modifier.fillMaxWidth().background(Color(0x33000000)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Элементов: ${files.size}", color = Fluent.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                val sel = selected
                FluentButton("Установить", { sel?.let { st.openInstall(it) } }, primary = true, enabled = sel != null && FileBridge.isExe(sel))
            }
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (enabled) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(18.dp)) }
}

@Composable
private fun SideTitle(t: String) {
    Text(t, color = Fluent.TextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SideItem(p: Place, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(6.dp))
            .background(if (active) Color(0x26FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(p.icon, null, tint = Color(0xFF60CDFF), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(p.label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaceChip(p: Place, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (active) Fluent.Accent else Color(0x26FFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(p.label, color = Color.White, fontSize = 12.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun FileRow(st: DesktopState, f: File, selected: Boolean, wide: Boolean, onSelect: () -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val isExe = FileBridge.isExe(f)
    val menu: () -> List<MenuItem> = {
        when {
            f.isDirectory -> listOf(MenuItem("Открыть", Icons.Default.FolderOpen) { onOpen() })
            isExe -> listOf(MenuItem("Установить", Icons.Default.Download) { st.openInstall(f) })
            else -> emptyList()
        }
    }
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0x33FFFFFF) else Color.Transparent)
            .desktopTarget(onTap = onSelect, onDoubleTap = onOpen, menu = if (f.isDirectory || isExe) menu else null)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            f.isDirectory -> Icon(Icons.Default.Folder, null, tint = Fluent.Folder, modifier = Modifier.size(28.dp))
            isExe -> {
                val probe = remember(f) { Shortcut("", f.nameWithoutExtension, f.absolutePath) }
                val icon by produceState<File?>(null, f) {
                    value = withContext(Dispatchers.IO) { ExeIconExtractor.getIcon(ctx, f, probe.iconKey) }
                }
                FileIcon(icon, 28.dp)
            }
            else -> Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(f.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (wide) {
            Text(if (f.isDirectory) "" else humanSize(f.length()), color = Fluent.TextDim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
            Text(
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified())),
                color = Fluent.TextDim, fontSize = 12.sp, maxLines = 1, softWrap = false
            )
        }
    }
}

fun humanSize(b: Long): String = when {
    b < 1024 -> "$b Б"
    b < 1024 * 1024 -> "${b / 1024} КБ"
    b < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f МБ", b / 1048576.0)
    else -> String.format(Locale.getDefault(), "%.2f ГБ", b / 1073741824.0)
}
