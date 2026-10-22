package com.winars.app.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.R
import com.winars.app.core.AppWindow
import com.winars.app.core.DesktopState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Портрет: 62dp, скругление 16dp, отступ 8dp (не налезает на жесты).
 * Ландшафт: 72dp на всю ширину, без скруглений, справа системный трей.
 */
@Composable
fun BoxScope.Taskbar(st: DesktopState, landscape: Boolean) {
    val h = if (landscape) 72.dp else 62.dp
    Box(
        Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .then(if (landscape) Modifier else Modifier.padding(8.dp))
            .fillMaxWidth().height(h)
    ) {
        MicaSurface(Modifier.fillMaxSize(), if (landscape) RectangleShape else RoundedCornerShape(16.dp)) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskbarButton(false, { st.startOpen = !st.startOpen }) { Img(R.drawable.wa_icon_launcher, 34.dp) }
                TaskbarButton(st.windows.any { it is AppWindow.Explorer }, { toggle(st, { it is AppWindow.Explorer }) { st.openExplorer() } }) {
                    Img(R.drawable.wa_icon_explorer, 32.dp)
                }
                TaskbarButton(st.windows.any { it is AppWindow.Settings }, { toggle(st, { it is AppWindow.Settings }) { st.openSettings() } }) {
                    Img(R.drawable.wa_icon_settings, 32.dp)
                }
                // окна Wine из X-сервера: каждое — кнопка на панели задач
                if (st.wine.session != null) {
                    TaskbarButton(
                        st.windows.any { it is AppWindow.WineScreen },
                        { toggle(st, { it is AppWindow.WineScreen }) { st.openWineScreen() } }
                    ) { Img(R.drawable.wa_generic_exe, 30.dp) }
                    st.wine.xWindows.forEach { xw ->
                        TaskbarButton(!xw.iconic, {
                            st.openWineScreen()
                            st.wine.session?.bringToFront(xw)
                        }) { Img(R.drawable.wa_generic_exe, 26.dp) }
                    }
                }
                st.shortcuts.filter { it.onTaskbar }.forEach { sc ->
                    TaskbarButton(false, { st.launch(sc) }, menu = { shortcutMenu(st, sc) }) { ShortcutIcon(sc, 32.dp) }
                }
            }
            if (landscape) Tray(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
        }
    }
}

/** Клик по значку: нет окна — открыть; видимо и сверху — свернуть; иначе — показать. */
private fun toggle(st: DesktopState, match: (AppWindow) -> Boolean, open: () -> Unit) {
    val w = st.windows.firstOrNull(match)
    when {
        w == null -> open()
        !w.minimized && st.windows.last() === w -> w.minimized = true
        else -> st.focus(w)
    }
}

@Composable
private fun TaskbarButton(
    active: Boolean,
    onClick: () -> Unit,
    menu: (() -> List<com.winars.app.core.MenuItem>)? = null,
    icon: @Composable () -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
            .background(if (hovered) Fluent.Hover else Color.Transparent)
            .hoverable(hover)
            .desktopTarget(onTap = onClick, menu = menu),
        contentAlignment = Alignment.Center
    ) {
        icon()
        if (active) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).width(16.dp).height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Fluent.Accent)
            )
        }
    }
}

/** Системный трей: язык, батарея, время/дата. */
@Composable
private fun Tray(modifier: Modifier) {
    val ctx = LocalContext.current
    val tick by produceState(Triple("", "", 0)) {
        val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            val now = Date()
            value = Triple(tf.format(now), df.format(now), bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            delay(15_000)
        }
    }
    val lang = remember { Locale.getDefault().isO3Language.uppercase() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(lang, color = Color.White, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BatteryFull, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(2.dp))
            Text("${tick.third}%", color = Color.White, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(tick.first, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.End)
            Text(tick.second, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.End)
        }
    }
}
