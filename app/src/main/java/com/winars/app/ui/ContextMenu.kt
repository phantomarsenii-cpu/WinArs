package com.winars.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winars.app.core.DesktopState
import com.winars.app.core.MenuItem
import com.winars.app.core.Shortcut
import kotlin.math.roundToInt

/** Меню ярлыка (рабочий стол / Пуск / панель задач). */
fun shortcutMenu(st: DesktopState, sc: Shortcut): List<MenuItem> = listOf(
    MenuItem("Запуск", Icons.Default.PlayArrow) { st.launch(sc) },
    MenuItem("Запуск от имени администратора", Icons.Default.AdminPanelSettings) { st.launch(sc, asAdmin = true) },
    MenuItem("Свойства", Icons.Default.Info) { st.openProperties(sc.id) },
    MenuItem("Сменить иконку", Icons.Default.Image) { st.pickIconFor(sc) },
    MenuItem(if (sc.onTaskbar) "Открепить от панели задач" else "Закрепить на панели задач", Icons.Default.PushPin) {
        st.updateShortcut(sc.copy(onTaskbar = !sc.onTaskbar))
    },
    MenuItem(if (sc.inStart) "Открепить от меню Пуск" else "Закрепить в меню Пуск", Icons.Default.PushPin) {
        st.updateShortcut(sc.copy(inStart = !sc.inStart))
    },
    MenuItem("Удалить ярлык", Icons.Default.Delete) { st.removeShortcut(sc.id) }
)

@Composable
fun BoxScope.ContextMenuLayer(st: DesktopState) {
    val m = st.menu ?: return
    val root = LocalRootSize.current.value
    val d = LocalDensity.current

    // перехват нажатий вне меню
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { st.menu = null }) })

    val menuW = 290.dp
    val rowH = 40.dp
    val wPx = with(d) { menuW.toPx() }
    val hPx = with(d) { (rowH * m.items.size + 12.dp).toPx() }
    val x = m.position.x.coerceIn(8f, (root.width - wPx - 8f).coerceAtLeast(8f))
    val y = m.position.y.coerceIn(8f, (root.height - hPx - 8f).coerceAtLeast(8f))

    MicaSurface(
        Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(menuW),
        RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(6.dp)) {
            m.items.forEach { item ->
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(4.dp))
                        .background(if (hovered) Fluent.Hover else Color.Transparent)
                        .hoverable(hover)
                        .clickable { st.menu = null; item.action() }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(item.title, color = Color.White, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
