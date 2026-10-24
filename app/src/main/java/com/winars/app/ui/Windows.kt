package com.winars.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.R
import com.winars.app.core.AppWindow
import kotlin.math.roundToInt

/**
 * Окно рабочего стола. На узком экране (телефон, портрет) — на всю рабочую область;
 * на широком (ландшафт/монитор) — перетаскиваемое плавающее окно, двойной тап по заголовку = развернуть.
 */
@Composable
fun BoxScope.WindowHost(w: AppWindow, compact: Boolean, area: DpSize, reserve: Dp) {
    val st = LocalDesktop.current
    var offset by remember { mutableStateOf(Offset(((w.id % 5) * 36).toFloat(), ((w.id % 5) * 36).toFloat())) }
    var maximized by remember { mutableStateOf(false) }

    val dialog = w is AppWindow.Install || w is AppWindow.Properties
    val pref = when (w) {
        is AppWindow.Explorer -> DpSize(780.dp, 500.dp)
        is AppWindow.Settings -> DpSize(720.dp, 540.dp)
        is AppWindow.Install -> DpSize(460.dp, 460.dp)
        is AppWindow.Properties -> DpSize(420.dp, 400.dp)
        is AppWindow.WineApp -> {
            // 1 пиксель окна Wine = 1 физический пиксель экрана, плюс моя шапка 40dp
            val info = st.wine.xWindows.firstOrNull { it.id == w.xid }
            val d = LocalDensity.current.density
            if (info != null) {
                val r = st.wineRegion(info)
                DpSize((r[2] / d).dp, (r[3] / d).dp + 40.dp)
            } else DpSize(640.dp, 480.dp)
        }
    }
    val icon = when (w) {
        is AppWindow.Explorer -> R.drawable.wa_icon_explorer
        is AppWindow.Settings -> R.drawable.wa_icon_settings
        is AppWindow.WineApp -> R.drawable.wa_generic_exe
        else -> R.drawable.wa_icon_launcher
    }
    // окно программы Wine всегда плавающее и точно по размеру программы (без чёрных полей)
    val full = !dialog && w !is AppWindow.WineApp && (compact || maximized)
    val width: Dp
    val height: Dp
    if (w is AppWindow.WineApp) {
        val contentW = pref.width
        val contentH = pref.height - 40.dp
        val k = minOf(1f, (area.width - 16.dp) / contentW, (area.height - reserve - 80.dp) / contentH)
        width = contentW * k
        height = contentH * k + 40.dp
    } else {
        width = minOf(pref.width, area.width - 16.dp)
        height = minOf(pref.height, area.height - reserve - 40.dp)
    }

    val place = if (full) {
        Modifier.align(Alignment.TopStart).fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 28.dp, bottom = reserve)
    } else {
        Modifier.align(Alignment.Center)
            .padding(bottom = reserve / 2)
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(width, height)
    }

    WindowFrame(
        title = w.title, icon = icon, dialog = dialog, full = full,
        onDrag = { if (!full) offset += it },
        onMinimize = { w.minimized = true },
        onMaximize = {
            maximized = !maximized
            // для программы Wine «развернуть» = развернуть её средствами Wine; окно WinArs подстроится под новый размер
            if (w is AppWindow.WineApp) st.wine.session?.showXWindow(w.xid, if (maximized) 3 else 9)
        },
        onClose = { st.close(w) },
        modifier = place
            // свёрнутое окно остаётся в композиции (состояние не теряется), но не занимает места и не принимает ввод
            .layout { m, c ->
                if (w.minimized) layout(0, 0) {} else {
                    val p = m.measure(c)
                    layout(p.width, p.height) { p.place(0, 0) }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press) st.focus(w)
                    }
                }
            }
    ) {
        when (w) {
            is AppWindow.Explorer -> ExplorerContent()
            is AppWindow.Settings -> SettingsContent()
            is AppWindow.Install -> InstallContent(w)
            is AppWindow.Properties -> PropertiesContent(w)
            is AppWindow.WineApp -> WineAppContent(w)
        }
    }
}

@Composable
private fun WindowFrame(
    title: String,
    icon: Int,
    dialog: Boolean,
    full: Boolean,
    onDrag: (Offset) -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    MicaSurface(modifier.shadow(if (full) 0.dp else 24.dp, shape), shape) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(40.dp)
                    .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag) } }
                    .pointerInput(dialog) { detectTapGestures(onDoubleTap = { if (!dialog) onMaximize() }) }
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Img(icon, 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!dialog) {
                    CaptionButton(Icons.Default.Remove, onMinimize)
                    CaptionButton(Icons.Default.CropSquare, onMaximize)
                }
                CaptionButton(Icons.Default.Close, onClose, danger = true)
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xCC202020)), content = content)
        }
    }
}

@Composable
private fun CaptionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, danger: Boolean = false) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier.width(46.dp).fillMaxSize()
            .background(if (hovered) (if (danger) Color(0xFFC42B1C) else Fluent.Hover) else Color.Transparent)
            .hoverable(hover)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
}
