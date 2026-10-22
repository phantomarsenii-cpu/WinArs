package com.winars.app.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * X-экран Wine внутри окна WinArs. View создаёт WineSession (XServerView + сенсорный ввод),
 * здесь он просто встраивается — никакой Activity Winlator нет.
 */
@Composable
fun WineScreenContent() {
    val st = LocalDesktop.current
    val session = st.wine.session ?: return
    val screen = session.view ?: return

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = {
            (screen.parent as? ViewGroup)?.removeView(screen)
            screen
        },
        onRelease = { (it.parent as? ViewGroup)?.removeView(it) }
    )
}
