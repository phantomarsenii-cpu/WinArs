package com.winars.app.ui

import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winlator.R
import com.winars.app.core.DesktopState
import com.winars.app.core.ExeIconExtractor
import com.winars.app.core.MenuItem
import com.winars.app.core.Shortcut
import com.winars.app.core.WallpaperSpec
import com.winars.app.core.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Рабочий стол. Один и тот же composable рисуется и на экране телефона, и на внешнем мониторе (external = true).
 * Панель задач: портрет — 62dp, скругление 16dp, отступ 8dp; ландшафт — 72dp на всю ширину + трей.
 * Ориентация определяется через LocalConfiguration.
 */
@Composable
fun DesktopRoot(state: DesktopState, external: Boolean) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rootSize = remember { mutableStateOf(IntSize.Zero) }

    CompositionLocalProvider(LocalDesktop provides state, LocalRootSize provides rootSize) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootSize.value = it }
        ) {
            val compact = maxWidth < 640.dp
            val tbHeight = if (landscape) 72.dp else 62.dp
            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val reserve = tbHeight + (if (landscape) 0.dp else 16.dp) + navInset
            val area = DpSize(maxWidth, maxHeight)

            WallpaperLayer(state)
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { state.selectedId = null; state.startOpen = false; state.menu = null }
            })
            DesktopIcons(state, reserve)

            for (w in state.windows) key(w.id) { WindowHost(w, compact, area, reserve) }

            StartMenu(state, reserve, compact)
            Taskbar(state, landscape)
            ContextMenuLayer(state)
            ToastHost(state)
            if (external) CursorOverlay(state)
        }
    }
}

// ---------------- обои ----------------

@Composable
private fun WallpaperLayer(state: DesktopState) {
    val bloom = remember { WallpaperStore.bloom(960, 540).asImageBitmap() }
    Crossfade(targetState = state.wallpaper, animationSpec = tween(300), label = "wallpaper") { spec ->
        when (spec) {
            WallpaperSpec.Bloom -> Image(bloom, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is WallpaperSpec.Image -> AsyncImage(File(spec.path), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is WallpaperSpec.Video -> VideoWallpaper(spec.path)
        }
    }
}

/** MP4-обои без звука, зациклены, масштаб «cover». */
@Composable
private fun VideoWallpaper(path: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val tv = TextureView(ctx)
            var mp: MediaPlayer? = null
            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    mp = MediaPlayer().apply {
                        setSurface(Surface(st))
                        setDataSource(path)
                        isLooping = true
                        setVolume(0f, 0f)
                        setOnVideoSizeChangedListener { _, vw, vh ->
                            if (vw > 0 && vh > 0 && tv.width > 0) {
                                val k = maxOf(tv.width.toFloat() / vw, tv.height.toFloat() / vh)
                                val m = Matrix()
                                m.setScale(vw * k / tv.width, vh * k / tv.height, tv.width / 2f, tv.height / 2f)
                                tv.setTransform(m)
                            }
                        }
                        setOnPreparedListener { it.start() }
                        prepareAsync()
                    }
                }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { mp?.release(); mp = null; return true }
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
            tv
        }
    )
}

// ---------------- иконки ----------------

@Composable
fun FileIcon(file: File?, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (file == null) {
        Image(painterResource(R.drawable.wa_generic_exe), null, modifier.size(size))
    } else {
        AsyncImage(
            ImageRequest.Builder(ctx).data(file).memoryCacheKey("${file.path}:${file.lastModified()}").build(),
            null, modifier.size(size)
        )
    }
}

/** Иконка ярлыка: кастомная -> из exe -> заглушка generic_exe. */
@Composable
fun ShortcutIcon(sc: Shortcut, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val st = LocalDesktop.current
    val file by produceState<File?>(null, sc.exePath, st.iconRev) {
        value = withContext(Dispatchers.IO) { ExeIconExtractor.getIcon(ctx, File(sc.exePath), sc.iconKey) }
    }
    FileIcon(file, size, modifier)
}

@Composable
fun Img(res: Int, size: Dp, modifier: Modifier = Modifier) = Image(painterResource(res), null, modifier.size(size))

@Composable
private fun DesktopIcons(state: DesktopState, reserve: Dp) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val iconSize = if (landscape) 44.dp else 56.dp
        val tileWidth = if (landscape) 76.dp else 88.dp
        val tileHeight = iconSize + 62.dp

        // раскладка как в Windows: иконки идут сверху вниз и переносятся в следующий столбец
        LazyHorizontalGrid(
            rows = GridCells.Adaptive(tileHeight),
            modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = reserve)
        ) {
            item(key = "sys_explorer") {
                DesktopTile("Проводник", state.selectedId == "sys_explorer", { state.selectedId = "sys_explorer" }, { state.openExplorer() }, null, tileWidth, iconSize) {
                    Img(R.drawable.wa_icon_explorer, iconSize)
                }
            }
            item(key = "sys_settings") {
                DesktopTile("Параметры", state.selectedId == "sys_settings", { state.selectedId = "sys_settings" }, { state.openSettings() }, null, tileWidth, iconSize) {
                    Img(R.drawable.wa_icon_settings, iconSize)
                }
            }
            val onDesktop = state.shortcuts.filter { it.onDesktop }
            items(onDesktop.size, key = { onDesktop[it].id }) { i ->
                val sc = onDesktop[i]
                DesktopTile(
                    sc.name, state.selectedId == sc.id, { state.selectedId = sc.id }, { state.launch(sc) },
                    { shortcutMenu(state, sc) }, tileWidth, iconSize
                ) { ShortcutIcon(sc, iconSize) }
            }
        }
    }
}

@Composable
private fun DesktopTile(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    menu: (() -> List<MenuItem>)?,
    tileWidth: Dp,
    iconSize: Dp,
    icon: @Composable () -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(
        Modifier.width(tileWidth).padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> Color(0x40FFFFFF)
                    hovered -> Color(0x20FFFFFF)
                    else -> Color.Transparent
                }
            )
            .hoverable(hover)
            .desktopTarget(onTap = onSelect, onDoubleTap = onOpen, menu = menu)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(4.dp))
        Text(
            label, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1f), 4f))
        )
    }
}

// ---------------- тост и курсор ----------------

@Composable
private fun BoxScope.ToastHost(state: DesktopState) {
    val msg = state.toast ?: return
    LaunchedEffect(msg) { delay(3500); if (state.toast == msg) state.toast = null }
    MicaSurface(
        Modifier.align(Alignment.TopCenter).padding(top = 36.dp, start = 16.dp, end = 16.dp),
        RoundedCornerShape(10.dp)
    ) {
        Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

/** Курсор виртуальной мыши (тачпад телефона) на внешнем мониторе. */
@Composable
private fun CursorOverlay(state: DesktopState) {
    Canvas(Modifier.fillMaxSize()) {
        val s = density * 1.3f
        val p = Path().apply {
            moveTo(0f, 0f); lineTo(0f, 17f * s); lineTo(4.5f * s, 13f * s); lineTo(8f * s, 20f * s)
            lineTo(11f * s, 18.5f * s); lineTo(7.5f * s, 12f * s); lineTo(13f * s, 12f * s); close()
        }
        val c = state.cursor
        translate(c.x, c.y) {
            drawPath(p, Color.White)
            drawPath(p, Color.Black, style = Stroke(width = 1.2f * density))
        }
    }
}
