package com.winars.app.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.winars.app.core.AppWindow
import com.winlator.widget.WineWindowView

/**
 * Содержимое окна WinArs = ровно одна программа Wine.
 * WineWindowView показывает область X-экрана с границами этой программы и сам передаёт ей ввод.
 */
@Composable
fun WineAppContent(w: AppWindow.WineApp) {
    val st = LocalDesktop.current
    val session = st.wine.session ?: return
    val info = st.wine.xWindows.firstOrNull { it.id == w.xid } ?: return
    val crop = st.wineCropTop.coerceIn(0, (info.height - 24).coerceAtLeast(0))
    val height = (info.height - crop).coerceAtLeast(1)
    val ratio = info.width.toFloat() / height.toFloat()

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.aspectRatio(ratio),
            factory = { ctx -> WineWindowView(ctx, session, w.xid) },
            update = { it.setRegion(info.x, info.y + crop, info.width, height) }
        )
    }
}

/**
 * Мастер-вид X-сервера: крошечная поверхность, на которой живёт GL-поток рендерера.
 * Через неё рисуются все окна Wine (каждое в свою поверхность). Лежит под панелью задач и не виден.
 */
@Composable
fun BoxScope.WineMasterView() {
    val st = LocalDesktop.current
    val session = st.wine.session ?: return
    val master = session.xServerView ?: return

    AndroidView(
        modifier = Modifier.align(Alignment.BottomCenter).size(2.dp),
        factory = {
            (master.parent as? ViewGroup)?.removeView(master)
            master
        },
        onRelease = { (it.parent as? ViewGroup)?.removeView(it) }
    )
}
