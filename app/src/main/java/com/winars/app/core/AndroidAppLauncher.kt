package com.winars.app.core

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager

data class AndroidApp(val pkg: String, val label: String)

/**
 * Android-приложения в окнах как в DeX.
 * Freeform-окна работают только там, где система их поддерживает: Samsung DeX/Good Lock MultiStar
 * или "Параметры разработчика -> Enable freeform windows" (+ Force activities to be resizable).
 * Если не поддерживается — приложение откроется обычным (полноэкранным) способом.
 */
object AndroidAppLauncher {

    @Suppress("DEPRECATION")
    fun list(ctx: Context): List<AndroidApp> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AndroidApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun icon(ctx: Context, pkg: String): Drawable? = try {
        ctx.packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    fun launch(ctx: Context, pkg: String, displayId: Int, slot: Int): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(displayId) ?: dm.getDisplay(0)
        val size = Point().also { display.getRealSize(it) }
        val w = (size.x * 0.62f).toInt()
        val h = (size.y * 0.72f).toInt()
        val step = (minOf(size.x, size.y) * 0.05f).toInt()
        val left = (size.x * 0.12f).toInt() + (slot % 6) * step
        val top = (size.y * 0.08f).toInt() + (slot % 6) * step

        return try {
            val opts = ActivityOptions.makeBasic()
            opts.setLaunchBounds(Rect(left, top, left + w, top + h))
            if (displayId != 0) opts.setLaunchDisplayId(displayId)
            ctx.startActivity(intent, opts.toBundle())
            true
        } catch (e: Exception) {
            try { ctx.startActivity(intent); true } catch (e2: Exception) { false }
        }
    }
}
