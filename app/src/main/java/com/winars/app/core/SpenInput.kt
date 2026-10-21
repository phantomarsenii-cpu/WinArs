package com.winars.app.core

import android.view.InputDevice
import android.view.MotionEvent

/**
 * S-Pen:
 *  - тап пером            = левый клик (двойной тап <300 мс = двойной клик — это штатный таймаут Compose)
 *  - кнопка S-Pen + касание = правый клик (превращаем в событие мыши с BUTTON_SECONDARY)
 *  - hover без касания      = движение мыши (ACTION_HOVER_MOVE приходит как есть и обрабатывается Compose)
 * Мышь/клавиатуру USB/Bluetooth не трогаем.
 */
object SpenInput {

    fun remap(ev: MotionEvent): MotionEvent {
        if (ev.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return ev
        if ((ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) == 0) return ev

        val n = ev.pointerCount
        val props = Array(n) { MotionEvent.PointerProperties().also { p -> ev.getPointerProperties(it, p); p.toolType = MotionEvent.TOOL_TYPE_MOUSE } }
        val coords = Array(n) { MotionEvent.PointerCoords().also { c -> ev.getPointerCoords(it, c) } }
        return MotionEvent.obtain(
            ev.downTime, ev.eventTime, ev.action, n, props, coords,
            ev.metaState, MotionEvent.BUTTON_SECONDARY,
            ev.xPrecision, ev.yPrecision, ev.deviceId, ev.edgeFlags,
            InputDevice.SOURCE_MOUSE, ev.flags
        )
    }
}
