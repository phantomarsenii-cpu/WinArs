package com.winars.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winars.app.core.DesktopState
import com.winars.app.core.MenuItem
import com.winars.app.core.MenuState
import kotlin.math.roundToInt

object Fluent {
    val Accent = Color(0xFF0078D4)
    val AccentHover = Color(0xFF1A86D9)
    val Mica = Color(0xE51E1E1E)          // 0xE51E1E1E по ТЗ
    val Surface = Color(0xFF202020)
    val Stroke = Color(0x33FFFFFF)
    val Hover = Color(0x1AFFFFFF)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFC5C5C5)
    val Folder = Color(0xFFFFC83D)
}

val LocalDesktop = staticCompositionLocalOf<DesktopState> { error("DesktopState не передан") }

/** Размер корня текущего рабочего стола (телефон или монитор) — нужен для Mica-подложки. */
val LocalRootSize = compositionLocalOf<MutableState<IntSize>> { mutableStateOf(IntSize.Zero) }

@Composable
fun WinArsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Fluent.Accent, background = Color.Black, surface = Fluent.Surface),
        content = content
    )
}

/**
 * Mica: полупрозрачная заливка поверх размытых обоев.
 * Размытие = крошечная копия обоев (64 px), растянутая с билинейной фильтрацией и выровненная
 * по положению панели на экране — выглядит как Mica и работает на любом Android без RenderEffect.
 * При выключенном blur (профиль Mali) остаётся только тонированная заливка.
 */
@Composable
fun MicaSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val st = LocalDesktop.current
    val rootSize = LocalRootSize.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { pos = it.positionInRoot() }
            .drawBehind {
                val bd = st.backdrop
                if (bd != null && rootSize.value.width > 0) {
                    drawImage(
                        bd,
                        dstOffset = IntOffset(-pos.x.roundToInt(), -pos.y.roundToInt()),
                        dstSize = rootSize.value,
                        filterQuality = FilterQuality.Low
                    )
                }
                drawRect(if (bd != null) Fluent.Mica else Color(0xF21E1E1E))
            }
            .border(1.dp, Fluent.Stroke, shape),
        content = content
    )
}

/** Кнопка Fluent. Подпись всегда в одну строку, без переноса и дефисов посреди слова. */
@Composable
fun FluentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    !enabled -> Color(0x14FFFFFF)
                    primary -> Fluent.Accent
                    else -> Color(0x1FFFFFFF)
                }
            )
            .border(1.dp, if (primary && enabled) Color.Transparent else Fluent.Stroke, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (enabled) Color.White else Color(0x66FFFFFF), fontSize = 13.sp,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
        )
    }
}

@Composable
fun FluentCheckbox(checked: Boolean, label: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                .background(if (checked) Fluent.Accent else Color.Transparent)
                .border(1.dp, if (checked) Fluent.Accent else Color(0x99FFFFFF), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.size(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

/** Fluent-прогресс: fraction 0..1, либо null — бегущая полоска. */
@Composable
fun FluentProgress(fraction: Float?, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))
    ) {
        if (fraction != null) {
            val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(200), label = "progress")
            Box(Modifier.fillMaxWidth(f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Fluent.Accent))
        } else {
            val t = rememberInfiniteTransition(label = "indeterminate")
            val x by t.animateFloat(
                -0.3f, 1f,
                infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x"
            )
            Box(
                Modifier.offset(x = maxWidth * x).width(maxWidth * 0.3f).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Fluent.Accent)
            )
        }
    }
}

/**
 * Единая обработка ввода для иконок, строк проводника и т.п.:
 *  тап, двойной тап (≤300 мс), правая кнопка мыши / кнопка S-Pen (приходит как SECONDARY),
 *  долгое нажатие пальцем -> контекстное меню в координатах корня рабочего стола.
 */
fun Modifier.desktopTarget(
    onTap: () -> Unit = {},
    onDoubleTap: (() -> Unit)? = null,
    menu: (() -> List<MenuItem>)? = null
): Modifier = composed {
    val st = LocalDesktop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tapS by rememberUpdatedState(onTap)
    val dblS by rememberUpdatedState(onDoubleTap)
    val menuS by rememberUpdatedState(menu)
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                        val m = menuS
                        if (m != null) {
                            val c = e.changes.first()
                            st.menu = MenuState(origin + c.position, m())
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
            }
        }
        .pointerInput(onDoubleTap != null) {
            detectTapGestures(
                onTap = { tapS() },
                onDoubleTap = if (onDoubleTap != null) ({ _: Offset -> dblS?.invoke(); Unit }) else null,
                onLongPress = { p -> menuS?.let { st.menu = MenuState(origin + p, it()) } }
            )
        }
}
