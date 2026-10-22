package com.winars.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.winlator.R
import com.winars.app.core.AndroidApp
import com.winars.app.core.AndroidAppLauncher
import com.winars.app.core.DesktopState
import com.winars.app.core.Shortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Pinned(val key: String, val title: String, val icon: Int)

private val PINNED = listOf(
    Pinned("explorer", "Проводник", R.drawable.wa_icon_explorer),
    Pinned("settings", "Параметры", R.drawable.wa_icon_settings),
    Pinned("chrome", "Chrome", R.drawable.wa_icon_chrome),
    Pinned("photoshop", "Photoshop", R.drawable.wa_icon_photoshop),
    Pinned("word", "Word", R.drawable.wa_icon_word),
    Pinned("steam", "Steam", R.drawable.wa_icon_steam)
)

/** slide + fade, 250 мс, по центру снизу как в Windows 11. */
@Composable
fun BoxScope.StartMenu(st: DesktopState, reserve: Dp, compact: Boolean) {
    AnimatedVisibility(
        visible = st.startOpen,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = reserve + 4.dp, start = 8.dp, end = 8.dp),
        enter = slideInVertically(tween(250)) { it / 5 } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(200)) { it / 5 } + fadeOut(tween(200))
    ) {
        StartPanel(st)
    }
}

@Composable
private fun StartPanel(st: DesktopState) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var powerOpen by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<AndroidApp>()) }
    LaunchedEffect(st.startOpen) {
        if (st.startOpen) apps = withContext(Dispatchers.IO) { AndroidAppLauncher.list(ctx) }
    }

    MicaSurface(
        Modifier.widthIn(max = 640.dp).heightIn(max = 620.dp).fillMaxWidth().fillMaxHeight(0.72f),
        RoundedCornerShape(12.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = if (maxWidth < 560.dp) 3 else 6
            Column(Modifier.fillMaxSize()) {
                // поиск
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Color(0x26FFFFFF)).padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Fluent.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Поиск приложений", color = Fluent.TextDim, fontSize = 14.sp)
                            inner()
                        }
                    )
                }

                val q = query.trim().lowercase()
                val pinnedShortcuts = st.shortcuts.filter { it.inStart }
                val recent = st.shortcuts.takeLast(4).reversed()
                val shownApps = if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Section("Закреплённые") }
                    items(PINNED.filter { q.isEmpty() || it.title.lowercase().contains(q) }) { p ->
                        AppTile(p.title, { Img(p.icon, 44.dp) }) { openPinned(st, p) }
                    }
                    items(pinnedShortcuts) { sc ->
                        AppTile(sc.name, { ShortcutIcon(sc, 44.dp) }) { st.launch(sc) }
                    }
                    if (q.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { Section("Рекомендуемые") }
                        if (recent.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Установи программу из Проводника: её ярлык появится здесь и на рабочем столе.",
                                    color = Fluent.TextDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        } else {
                            items(recent, span = { GridItemSpan(maxLineSpan) }) { sc -> RecentRow(st, sc) }
                        }
                    }
                    val installed = st.shortcuts.filter { q.isEmpty() || it.name.lowercase().contains(q) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Section("Установленные программы (${installed.size})") }
                            FluentButton("Обновить", { st.importWineShortcuts(manual = true) })
                        }
                    }
                    items(installed, key = { "inst_" + it.id }) { sc ->
                        AppTile(sc.name, { ShortcutIcon(sc, 44.dp) }) { st.launch(sc) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Section("Android Apps (${shownApps.size})") }
                    items(shownApps, key = { it.pkg }) { app ->
                        AppTile(app.label, { AndroidIcon(app.pkg, 44.dp) }) { st.launchAndroid(app.pkg) }
                    }
                }

                // профиль + питание
                if (powerOpen) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        FluentButton("Выйти из WinArs", { st.exitApp() })
                        if (st.dexActive) FluentButton("Отключить монитор", { powerOpen = false; st.exitDex() })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(Color(0x33000000)).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Fluent.Accent), contentAlignment = Alignment.Center) {
                        Text("W", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("WinArs User", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).clickable { powerOpen = !powerOpen },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.PowerSettingsNew, null, tint = Color.White) }
                }
            }
        }
    }
}

/** Системные пункты открываются сразу; Chrome/Photoshop/Word/Steam — запускают ярлык с таким именем, если он уже установлен. */
private fun openPinned(st: DesktopState, p: Pinned) {
    when (p.key) {
        "explorer" -> st.openExplorer()
        "settings" -> st.openSettings()
        else -> {
            val sc = st.shortcuts.firstOrNull {
                it.name.lowercase().contains(p.key) || it.exePath.lowercase().contains(p.key)
            }
            if (sc != null) st.launch(sc) else {
                st.toast("${p.title} не установлен. Найди установщик в Проводнике.")
                st.openExplorer()
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

@Composable
private fun AppTile(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier.padding(2.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.size(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecentRow(st: DesktopState, sc: Shortcut) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .desktopTarget(onTap = { st.launch(sc) }, menu = { shortcutMenu(st, sc) })
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShortcutIcon(sc, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(sc.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
            Text("Недавно добавлено", color = Fluent.TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
fun AndroidIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            AndroidAppLauncher.icon(ctx, pkg)?.toBitmap(96, 96)?.asImageBitmap()
        }
    }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(size)) else Box(Modifier.size(size))
}
