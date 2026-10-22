package com.winars.app.core

import android.content.Context
import com.winlator.container.ContainerManager
import com.winlator.core.WineUtils
import com.winlator.win32.MSLink
import java.io.File
import java.util.UUID

/**
 * После установки программы в Wine: читаем .lnk с рабочего стола Wine и из меню Пуск контейнера,
 * разбираем их через MSLink (Winlator), достаём иконку из exe и создаём ярлыки в СВОЕЙ системе.
 * Ярлыки с рабочего стола Wine после импорта удаляются — рабочий стол один, наш.
 */
object WineShortcutImporter {

    private val SKIP_NAMES = Regex("(?i)(uninstall|удалить|деинсталл|readme|help|справк|website|веб-?сайт|documentation)")

    fun scan(ctx: Context, known: Collection<Shortcut>, dismissed: Set<String>): List<Shortcut> {
        val container = ContainerManager(ctx).containers.firstOrNull() ?: return emptyList()
        val driveC = File(container.rootDir, ".wine/drive_c")
        val users = File(driveC, "users").listFiles()?.filter { it.isDirectory } ?: emptyList()

        val desktopDirs = users.map { File(it, "Desktop") }
        val startDirs = listOf(container.startMenuDir) +
            users.flatMap { listOf(File(it, "AppData/Roaming/Microsoft/Windows/Start Menu"), File(it, "Start Menu")) }

        val knownPaths = known.map { it.exePath }.toMutableSet()
        val result = ArrayList<Shortcut>()

        fun handle(lnk: File, fromDesktop: Boolean) {
            if (SKIP_NAMES.containsMatchIn(lnk.nameWithoutExtension)) return
            val info = try { MSLink.extractLinkInfo(lnk) } catch (e: Throwable) { null } ?: return
            val target = info.targetPath ?: return
            if (!target.endsWith(".exe", ignoreCase = true) || info.isDirectory) return
            if (target.startsWith("C:\\windows", ignoreCase = true)) return          // системные программы Wine
            val unix = try { WineUtils.dosToUnixPath(target, container) } catch (e: Throwable) { null } ?: return
            val exe = File(unix)
            if (!exe.exists()) return
            val path = exe.absolutePath
            if (path in dismissed) { if (fromDesktop) lnk.delete(); return }
            if (path in knownPaths) { if (fromDesktop) lnk.delete(); return }

            val sc = Shortcut(
                id = UUID.randomUUID().toString(), name = lnk.nameWithoutExtension, exePath = path,
                onDesktop = true, inStart = false, containerId = container.id
            )
            val icon = ExeIconExtractor.getIcon(ctx, exe, sc.iconKey)
            result.add(sc.copy(icon = icon?.absolutePath ?: ""))
            knownPaths.add(path)
            if (fromDesktop) lnk.delete()      // не оставляем ярлык в рабочем столе Wine
        }

        desktopDirs.forEach { d -> d.listFiles { f -> f.isFile && f.extension.equals("lnk", true) }?.forEach { handle(it, true) } }
        startDirs.forEach { d ->
            d.walkTopDown().maxDepth(4).filter { it.isFile && it.extension.equals("lnk", true) }.forEach { handle(it, false) }
        }
        return result
    }
}
