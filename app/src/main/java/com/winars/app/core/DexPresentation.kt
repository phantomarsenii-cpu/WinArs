package com.winars.app.core

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winars.app.ui.DesktopRoot
import com.winars.app.ui.LocalDesktop
import com.winars.app.ui.WinArsTheme

/**
 * Рабочий стол WinArs на внешнем мониторе. Телефон в это время — тачпад (см. ui/Touchpad.kt),
 * который через [moveBy]/[click]/[scroll] подаёт в ComposeView синтетические события мыши.
 */
class DexPresentation(
    outer: Context,
    display: Display,
    private val state: DesktopState
) : Presentation(outer, display), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private lateinit var composeView: ComposeView

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val decor = window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)

        composeView = ComposeView(context).apply {
            setContent {
                CompositionLocalProvider(LocalDesktop provides state) {
                    WinArsTheme { DesktopRoot(state, external = true) }
                }
            }
        }
        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    fun destroy() {
        runCatching { dismiss() }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    // ---------------- виртуальная мышь ----------------

    private fun ev(action: Int, downTime: Long, buttons: Int, x: Float, y: Float): MotionEvent {
        val props = arrayOf(MotionEvent.PointerProperties().also { it.id = 0; it.toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().also { it.x = x; it.y = y; it.pressure = if (buttons != 0) 1f else 0f; it.size = 1f })
        return MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, 1, props, coords,
            0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
        )
    }

    fun moveBy(dx: Float, dy: Float) {
        if (!::composeView.isInitialized || composeView.width == 0) return
        val x = (state.cursor.x + dx).coerceIn(0f, (composeView.width - 1).toFloat())
        val y = (state.cursor.y + dy).coerceIn(0f, (composeView.height - 1).toFloat())
        state.cursor = Offset(x, y)
        if (dragDownTime != 0L) {
            val m = ev(MotionEvent.ACTION_MOVE, dragDownTime, MotionEvent.BUTTON_PRIMARY, x, y)
            composeView.dispatchTouchEvent(m); m.recycle()
        } else {
            val e = ev(MotionEvent.ACTION_HOVER_MOVE, SystemClock.uptimeMillis(), 0, x, y)
            composeView.dispatchGenericMotionEvent(e)
            e.recycle()
        }
    }

    private var dragDownTime = 0L

    /** Удержание левой кнопки (перетаскивание окон/иконок): buttonDown -> moveBy... -> buttonUp */
    fun buttonDown() {
        if (!::composeView.isInitialized || dragDownTime != 0L) return
        dragDownTime = SystemClock.uptimeMillis()
        val e = ev(MotionEvent.ACTION_DOWN, dragDownTime, MotionEvent.BUTTON_PRIMARY, state.cursor.x, state.cursor.y)
        composeView.dispatchTouchEvent(e); e.recycle()
    }

    fun buttonUp() {
        if (dragDownTime == 0L) return
        val e = ev(MotionEvent.ACTION_UP, dragDownTime, 0, state.cursor.x, state.cursor.y)
        composeView.dispatchTouchEvent(e); e.recycle()
        dragDownTime = 0L
    }

    fun click(right: Boolean = false) {
        if (!::composeView.isInitialized) return
        val btn = if (right) MotionEvent.BUTTON_SECONDARY else MotionEvent.BUTTON_PRIMARY
        val t = SystemClock.uptimeMillis()
        val (x, y) = state.cursor.x to state.cursor.y
        val down = ev(MotionEvent.ACTION_DOWN, t, btn, x, y)
        composeView.dispatchTouchEvent(down); down.recycle()
        val up = ev(MotionEvent.ACTION_UP, t, 0, x, y)
        composeView.dispatchTouchEvent(up); up.recycle()
    }

    fun scroll(dy: Float) {
        if (!::composeView.isInitialized) return
        val props = arrayOf(MotionEvent.PointerProperties().also { it.id = 0; it.toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().also {
            it.x = state.cursor.x; it.y = state.cursor.y
            it.setAxisValue(MotionEvent.AXIS_VSCROLL, dy)
        })
        val now = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        composeView.dispatchGenericMotionEvent(e); e.recycle()
    }

    // ---------------- клавиатура ----------------

    fun forwardKey(e: KeyEvent): Boolean = window?.decorView?.dispatchKeyEvent(e) ?: false

    /** Текст с экранной клавиатуры телефона. Символы вне раскладки KeyCharacterMap (кириллица, ą ł) пропускаются. */
    fun typeText(text: String) {
        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray()) ?: return
        events.forEach { forwardKey(it) }
    }
}
