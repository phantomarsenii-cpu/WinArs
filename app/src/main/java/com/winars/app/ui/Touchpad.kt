package com.winars.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winars.app.core.DesktopState
import com.winars.app.core.DexPresentation
import kotlin.math.abs

/**
 * Телефон в режиме внешнего монитора = тачпад + клавиатура (как в DeX).
 *  1 палец: движение курсора, тап = ЛКМ (два быстрых тапа = двойной клик)
 *  2 пальца: прокрутка, тап двумя = ПКМ
 *  «Перетащить»: пока включено, касание удерживает ЛКМ (двигать окна, иконки)
 */
@Composable
fun TouchpadScreen(st: DesktopState, presentation: () -> DexPresentation?) {
    var drag by remember { mutableStateOf(false) }
    var kb by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0F1A)).statusBarsPadding().navigationBarsPadding().padding(16.dp)
    ) {
        Text("WinArs · внешний монитор", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Телефон — тачпад. Рабочий стол на мониторе.", color = Fluent.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B1F2B))
                .border(1.dp, Fluent.Stroke, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val t0 = SystemClock.uptimeMillis()
                        var maxPointers = 1
                        var dist = 0f
                        var held = false
                        if (drag) { presentation()?.buttonDown(); held = true }
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.size > maxPointers) maxPointers = pressed.size
                            if (pressed.size == 1 && maxPointers == 1) {
                                val d = pressed[0].positionChange()
                                dist += abs(d.x) + abs(d.y)
                                presentation()?.moveBy(d.x * 2.2f, d.y * 2.2f)
                            } else if (pressed.size >= 2) {
                                val dy = pressed.map { it.positionChange().y }.average().toFloat()
                                dist += abs(dy)
                                presentation()?.scroll(dy / 20f)
                            }
                            ev.changes.forEach { it.consume() }
                        } while (ev.changes.any { it.pressed })
                        if (held) presentation()?.buttonUp()
                        else if (dist < 14f && SystemClock.uptimeMillis() - t0 < 300) presentation()?.click(right = maxPointers >= 2)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Тачпад\n1 палец — курсор · тап — клик\n2 пальца — прокрутка · тап — правый клик",
                color = Color(0x66FFFFFF), fontSize = 13.sp, textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentButton("Клавиатура", {
                kb = !kb
                if (kb) { focus.requestFocus(); keyboard?.show() } else { keyboard?.hide(); focusManager.clearFocus() }
            }, primary = kb)
            FluentButton("Перетащить", { drag = !drag }, primary = drag)
            FluentButton("Выход", { st.exitDex() })
        }

        // невидимое поле: нужно только чтобы показать экранную клавиатуру и получить введённый текст
        BasicTextField(
            value = "",
            onValueChange = { if (it.isNotEmpty()) presentation()?.typeText(it) },
            modifier = Modifier.size(1.dp).focusRequester(focus)
        )
    }
}
