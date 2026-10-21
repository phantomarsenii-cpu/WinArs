package com.winars.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Shortcut(
    val id: String,
    val name: String,
    val exePath: String,
    val onDesktop: Boolean = true,
    val inStart: Boolean = false,
    val onTaskbar: Boolean = false
) {
    /** ключ для файлов иконок: files/icons_custom/<key>.png и icons_extracted/<key>.png */
    val iconKey: String
        get() = File(exePath).nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
}

class ShortcutStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("winars", Context.MODE_PRIVATE)

    fun load(): List<Shortcut> = try {
        val arr = JSONArray(prefs.getString("shortcuts", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Shortcut(
                o.getString("id"), o.getString("name"), o.getString("exe"),
                o.optBoolean("desktop", true), o.optBoolean("start", false), o.optBoolean("taskbar", false)
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Shortcut>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("exe", it.exePath)
                    .put("desktop", it.onDesktop).put("start", it.inStart).put("taskbar", it.onTaskbar)
            )
        }
        prefs.edit().putString("shortcuts", arr.toString()).apply()
    }
}
