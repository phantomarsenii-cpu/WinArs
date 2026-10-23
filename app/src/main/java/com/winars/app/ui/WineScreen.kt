package com.winars.app.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * X-экран Wine внутри окна WinArs. View создаёт WineSession (XServerView + сенсорный ввод),
 * здесь он встраивается с пропорциями X-экрана, поэтому программа, развёрнутая на весь экран Wine,
 * заполняет окно целиком (без чёрных полей).
 */
@Composable
fun WineScreenContent() {
    val st = LocalDesktop.current
    val session = st.wine.session ?: return
    val screen = session.view ?: return
    val info = session.screenSizeInfo
    val ratio = if (info.height > 0) info.width.toFloat() / info.height.toFloat() else 16f / 9f

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.aspectRatio(ratio),
            factory = {
                (screen.parent as? ViewGroup)?.removeView(screen)
                screen
            },
            onRelease = { (it.parent as? ViewGroup)?.removeView(it) }
        )
    }
}
