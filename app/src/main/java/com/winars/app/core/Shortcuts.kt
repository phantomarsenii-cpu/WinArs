package com.winars.app.core

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Ярлык WinArs. Хранится как files/Desktop/<id>.json (exePath, icon, containerId + флаги). */
data class Shortcut(
    val id: String,
    val name: String,
    val exePath: String,
    val onDesktop: Boolean = true,
    val inStart: Boolean = false,
    val onTaskbar: Boolean = false,
    val icon: String = "",
    val containerId: Int = 1
) {
    /** ключ для файлов иконок: files/icons_custom/<key>.png и files/icons/<key>.png */
    val iconKey: String
        get() = File(exePath).nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
}

class ShortcutStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "Desktop")
    private val dismissedFile = File(dir, "dismissed.txt")

    fun load(): List<Shortcut> {
        val files = dir.listFiles { f -> f.isFile && f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { f ->
            try {
                val o = JSONObject(f.readText())
                Shortcut(
                    id = o.getString("id"), name = o.getString("name"), exePath = o.getString("exePath"),
                    onDesktop = o.optBoolean("desktop", true), inStart = o.optBoolean("start", false),
                    onTaskbar = o.optBoolean("taskbar", false), icon = o.optString("icon", ""),
                    containerId = o.optInt("containerId", 1)
                )
            } catch (e: Exception) { null }
        }.sortedBy { it.name.lowercase() }
    }

    fun save(list: List<Shortcut>) {
        dir.mkdirs()
        val ids = list.map { it.id }.toSet()
        dir.listFiles { f -> f.isFile && f.extension == "json" }?.forEach { if (it.nameWithoutExtension !in ids) it.delete() }
        list.forEach {
            val o = JSONObject().put("id", it.id).put("name", it.name).put("exePath", it.exePath)
                .put("icon", it.icon).put("containerId", it.containerId)
                .put("desktop", it.onDesktop).put("start", it.inStart).put("taskbar", it.onTaskbar)
            File(dir, it.id + ".json").writeText(o.toString(2))
        }
    }

    /** exe, которые пользователь удалил: импорт из Wine их заново не добавляет */
    fun dismissed(): Set<String> =
        if (dismissedFile.exists()) dismissedFile.readLines().filter { it.isNotBlank() }.toSet() else emptySet()

    fun dismiss(exePath: String) {
        dir.mkdirs()
        dismissedFile.writeText((dismissed() + exePath).joinToString("\n"))
    }
}
