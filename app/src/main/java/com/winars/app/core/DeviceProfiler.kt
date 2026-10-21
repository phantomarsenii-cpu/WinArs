package com.winars.app.core

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build

/**
 * Профили по GPU:
 *  Adreno 7xx -> 8 ГБ, Turnip, DXVK 2.4, blur ON
 *  Adreno 640 -> 6 ГБ, Turnip, DXVK 2.3, blur ON
 *  Mali/прочее -> VirGL + WineD3D, blur OFF
 * Версии DXVK вынесены в константы ниже — меняются в одном месте.
 */
object DeviceProfiler {

    const val DXVK_ADRENO_7XX = "2.4"
    const val DXVK_ADRENO_640 = "2.3"

    enum class Vendor { ADRENO, MALI, OTHER }

    data class Profile(
        val gpu: String,
        val vendor: Vendor,
        val driver: String,       // "Turnip" | "VirGL"
        val d3d: String,          // "DXVK 2.4" | "WineD3D"
        val ramMb: Int,
        val blur: Boolean,
        val label: String
    )

    fun detect(ctx: Context): Profile {
        val prefs = ctx.getSharedPreferences("winars", Context.MODE_PRIVATE)
        var renderer = prefs.getString("gl_renderer", null)
        if (renderer.isNullOrBlank()) {
            renderer = glRenderer() ?: ""
            if (renderer.isNotBlank()) prefs.edit().putString("gl_renderer", renderer).apply()
        }
        val r = renderer.ifBlank { fallbackName() }

        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = (mi.totalMem / (1024 * 1024)).toInt()
        fun cap(target: Int) = minOf(target, (totalMb * 0.6).toInt())   // не больше 60% реальной RAM

        val adreno = Regex("Adreno.*?(\\d{3})", RegexOption.IGNORE_CASE).find(r)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            adreno != null && adreno / 100 == 7 ->
                Profile(r, Vendor.ADRENO, "Turnip", "DXVK $DXVK_ADRENO_7XX", cap(8192), true, "Adreno $adreno · Turnip")
            adreno == 640 ->
                Profile(r, Vendor.ADRENO, "Turnip", "DXVK $DXVK_ADRENO_640", cap(6144), true, "Adreno 640 · Turnip")
            adreno != null ->
                Profile(r, Vendor.ADRENO, "Turnip", "DXVK $DXVK_ADRENO_640", cap(4096), true, "Adreno $adreno · Turnip")
            r.contains("Mali", ignoreCase = true) ->
                Profile(r, Vendor.MALI, "VirGL", "WineD3D", cap(3072), false, "Mali · VirGL (blur выкл.)")
            else ->
                Profile(r, Vendor.OTHER, "VirGL", "WineD3D", cap(3072), false, "GPU не определён · VirGL")
        }
    }

    private fun fallbackName(): String {
        val hw = (Build.HARDWARE + " " + Build.BOARD).lowercase()
        return when {
            "qcom" in hw || "kona" in hw || "msm" in hw -> "Adreno (unknown)"
            "exynos" in hw || "samsungexynos" in hw || "mt" in hw || "kirin" in hw -> "Mali (unknown)"
            else -> "unknown"
        }
    }

    /** GL_RENDERER через временный EGL pbuffer — без Activity и без окна. */
    private fun glRenderer(): String? = try {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(dpy, ver, 0, ver, 1)
        val cfgAttr = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        EGL14.eglChooseConfig(dpy, cfgAttr, 0, cfgs, 0, 1, n, 0)
        val ctx = EGL14.eglCreateContext(
            dpy, cfgs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        val surf = EGL14.eglCreatePbufferSurface(
            dpy, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
        val name = GLES20.glGetString(GLES20.GL_RENDERER)
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(dpy, surf)
        EGL14.eglDestroyContext(dpy, ctx)
        EGL14.eglTerminate(dpy)
        name
    } catch (e: Throwable) {
        null
    }
}
