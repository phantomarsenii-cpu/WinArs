package com.winars.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

sealed interface WallpaperSpec {
    /** Встроенные обои Bloom Dark (рисуются программно) */
    data object Bloom : WallpaperSpec
    data class Image(val path: String) : WallpaperSpec
    data class Video(val path: String) : WallpaperSpec
}

class WallpaperStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("winars", Context.MODE_PRIVATE)
    private val dir = File(ctx.filesDir, "wallpapers")

    fun current(): WallpaperSpec {
        val p = prefs.getString("wallpaper_path", null) ?: return WallpaperSpec.Bloom
        val f = File(p)
        if (!f.exists()) return WallpaperSpec.Bloom
        return if (prefs.getString("wallpaper_kind", "image") == "video") WallpaperSpec.Video(p) else WallpaperSpec.Image(p)
    }

    fun reset() {
        prefs.edit().remove("wallpaper_path").remove("wallpaper_kind").apply()
    }

    /** SAF uri (jpg/png/mp4) -> files/wallpapers/ */
    fun importFromUri(uri: Uri): WallpaperSpec? {
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val isVideo = mime.startsWith("video/")
        if (!isVideo && !mime.startsWith("image/")) return null
        dir.mkdirs()
        val ext = when {
            isVideo -> "mp4"
            mime.contains("png") -> "png"
            else -> "jpg"
        }
        val out = File(dir, "wallpaper_${System.currentTimeMillis()}.$ext")
        ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } } ?: return null
        dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
        prefs.edit().putString("wallpaper_path", out.absolutePath)
            .putString("wallpaper_kind", if (isVideo) "video" else "image").apply()
        return if (isVideo) WallpaperSpec.Video(out.absolutePath) else WallpaperSpec.Image(out.absolutePath)
    }

    /** Маленькая картинка, которая при растяжении с билинейной фильтрацией даёт эффект Mica-размытия. */
    fun makeBackdrop(spec: WallpaperSpec): Bitmap? {
        val src: Bitmap? = when (spec) {
            WallpaperSpec.Bloom -> bloom(320, 180)
            is WallpaperSpec.Image -> decodeSmall(spec.path)
            is WallpaperSpec.Video -> try {
                MediaMetadataRetriever().use { it.setDataSource(spec.path); it.getFrameAtTime(0) }
            } catch (e: Exception) { null }
        }
        src ?: return null
        val w = 64
        val h = (w * src.height.toFloat() / src.width).toInt().coerceAtLeast(8)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun decodeSmall(path: String): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / s > 512) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    companion object {
        /** Bloom Dark: тёмный фон + синие/фиолетовые радиальные «лепестки». */
        fun bloom(w: Int, h: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.parseColor("#0A0D17"))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            fun blob(cx: Float, cy: Float, r: Float, color: Int) {
                p.shader = RadialGradient(cx * w, cy * h, r * h, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            blob(0.55f, 0.55f, 0.75f, Color.argb(235, 20, 80, 230))
            blob(0.30f, 0.40f, 0.55f, Color.argb(200, 110, 50, 240))
            blob(0.75f, 0.30f, 0.45f, Color.argb(190, 0, 170, 255))
            blob(0.48f, 0.78f, 0.35f, Color.argb(170, 40, 60, 255))
            p.shader = null
            return bmp
        }
    }
}

private inline fun <R> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> R): R =
    try { block(this) } finally { release() }
