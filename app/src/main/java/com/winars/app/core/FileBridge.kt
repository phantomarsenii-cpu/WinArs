package com.winars.app.core

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.winlator.xenvironment.RootFS
import android.os.Environment
import java.io.File

/** C: = контейнер Wine, D: = Download, Z: = вся память телефона. */
object FileBridge {

    /** C: — диск C: первого контейнера Winlator (files/root/home/xuser-N/.wine/drive_c). */
    fun cRoot(ctx: Context): File {
        val home = File(RootFS.find(ctx).rootDir, "home")
        val dir = home.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("xuser-") }
            ?.minByOrNull { it.name.removePrefix("xuser-").toIntOrNull() ?: Int.MAX_VALUE }
        if (dir != null) return File(dir, ".wine/drive_c")

        // контейнера ещё нет: временная папка-заглушка, чтобы Проводник открывался
        val root = File(ctx.filesDir, "containers/0/drive_c")
        if (!root.exists()) {
            listOf("Program Files", "Windows/System32", "Users/WinArs/Desktop", "Users/WinArs/Documents")
                .forEach { File(root, it).mkdirs() }
        }
        return root
    }

    @Suppress("DEPRECATION")
    fun dRoot(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    @Suppress("DEPRECATION")
    fun zRoot(): File = Environment.getExternalStorageDirectory()

    fun root(ctx: Context, letter: Char): File = when (letter.uppercaseChar()) {
        'C' -> cRoot(ctx)
        'D' -> dRoot()
        else -> zRoot()
    }

    /** targetSdk 28 -> классическое разрешение на память даёт доступ ко всему (D:, Z:). */
    fun hasAllFilesAccess(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** true, если путь лежит на внешней памяти (D:/Z:) и нужен MANAGE_EXTERNAL_STORAGE */
    fun needsAllFiles(ctx: Context, f: File): Boolean =
        !f.absolutePath.startsWith(ctx.filesDir.absolutePath)

    fun toWindowsPath(ctx: Context, f: File): String {
        val p = f.absolutePath
        val c = cRoot(ctx).absolutePath
        val d = dRoot().absolutePath
        val z = zRoot().absolutePath
        fun rel(base: String, letter: Char): String {
            val tail = p.removePrefix(base).trim('/').replace('/', '\\')
            return if (tail.isEmpty()) "$letter:\\" else "$letter:\\$tail"
        }
        return when {
            p.startsWith(c) -> rel(c, 'C')
            p.startsWith(d) -> rel(d, 'D')
            p.startsWith(z) -> rel(z, 'Z')
            else -> p
        }
    }

    fun list(dir: File): List<File> =
        dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

    fun isExe(f: File) = f.isFile && f.extension.equals("exe", ignoreCase = true)
}
