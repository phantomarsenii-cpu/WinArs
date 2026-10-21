package com.winars.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Иконка ярлыка:
 *  1) кастомная  files/icons_custom/<key>.png     (пользователь выбрал через "Сменить иконку")
 *  2) из самого exe files/icons_extracted/<key>.png (парсим PE-ресурсы RT_GROUP_ICON/RT_ICON,
 *     выбираем самую большую, как wrestool+icotool — но без зависимости от rootfs)
 *  3) null -> UI показывает generic_exe.png
 */
object ExeIconExtractor {

    fun customFile(ctx: Context, key: String) = File(ctx.filesDir, "icons_custom/$key.png")
    fun extractedFile(ctx: Context, key: String) = File(ctx.filesDir, "icons_extracted/$key.png")

    /** Быстро: только то, что уже лежит на диске. */
    fun cachedIcon(ctx: Context, key: String): File? =
        customFile(ctx, key).takeIf { it.exists() } ?: extractedFile(ctx, key).takeIf { it.exists() }

    /** Медленно (IO): вытащить иконку из exe, если её ещё нет. */
    fun getIcon(ctx: Context, exe: File, key: String): File? {
        cachedIcon(ctx, key)?.let { return it }
        val bmp = extractFromExe(exe) ?: return null
        val out = extractedFile(ctx, key)
        out.parentFile?.mkdirs()
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    fun extractFromExe(exe: File): Bitmap? = try {
        PeIcons.readIcons(exe)
            .sortedWith(compareByDescending<PeIcons.IconImage> { it.width }.thenByDescending { it.bitCount })
            .firstNotNullOfOrNull { PeIcons.decode(it) }
            ?.let { if (it.width == 256 && it.height == 256) it else Bitmap.createScaledBitmap(it, 256, 256, true) }
    } catch (e: Throwable) {
        null
    }

    /** SAF: выбранное изображение -> files/icons_custom/KEY.png */
    fun saveCustomIcon(ctx: Context, key: String, uri: Uri): File? {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scaled = if (maxOf(bmp.width, bmp.height) > 512) {
            val k = 512f / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
        } else bmp
        val out = customFile(ctx, key)
        out.parentFile?.mkdirs()
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    fun clearCustomIcon(ctx: Context, key: String) {
        customFile(ctx, key).delete()
    }
}

/** Минимальный читатель PE-ресурсов (иконки). */
internal object PeIcons {
    private const val RT_ICON = 3
    private const val RT_GROUP_ICON = 14

    class IconImage(val width: Int, val height: Int, val bitCount: Int, val data: ByteArray)

    private class Reader(val raf: RandomAccessFile) {
        private val b = ByteArray(4)
        fun bytes(off: Long, n: Int): ByteArray { val a = ByteArray(n); raf.seek(off); raf.readFully(a); return a }
        fun u16(off: Long): Int { raf.seek(off); raf.readFully(b, 0, 2); return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) }
        fun u32(off: Long): Long { raf.seek(off); raf.readFully(b, 0, 4)
            return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24) }
    }

    private class Section(val va: Long, val vsize: Long, val rawPtr: Long, val rawSize: Long)
    private class Entry(val id: Long, val isNamed: Boolean, val isDir: Boolean, val offset: Long)

    fun readIcons(file: File): List<IconImage> = RandomAccessFile(file, "r").use { raf ->
        val r = Reader(raf)
        if (r.u16(0) != 0x5A4D) return emptyList()
        val pe = r.u32(0x3C)
        if (r.u32(pe) != 0x00004550L) return emptyList()
        val numSections = r.u16(pe + 6)
        val optSize = r.u16(pe + 20)
        val opt = pe + 24
        val dd = opt + if (r.u16(opt) == 0x20B) 112 else 96
        val resRva = r.u32(dd + 16)
        if (resRva == 0L) return emptyList()

        val secs = (0 until numSections).map { i ->
            val o = opt + optSize + i * 40L
            Section(r.u32(o + 12), r.u32(o + 8), r.u32(o + 20), r.u32(o + 16))
        }
        fun rvaToOff(rva: Long): Long {
            for (s in secs) if (rva >= s.va && rva < s.va + maxOf(s.vsize, s.rawSize)) return rva - s.va + s.rawPtr
            return -1
        }
        val resBase = rvaToOff(resRva)
        if (resBase < 0) return emptyList()

        fun entries(dirOff: Long): List<Entry> {
            val n = r.u16(dirOff + 12) + r.u16(dirOff + 14)
            return (0 until n).map { i ->
                val o = dirOff + 16 + i * 8L
                val name = r.u32(o); val data = r.u32(o + 4)
                Entry(name and 0x7FFFFFFFL, (name and 0x80000000L) != 0L, (data and 0x80000000L) != 0L, data and 0x7FFFFFFFL)
            }
        }
        fun typeDir(type: Int): List<Entry> {
            val e = entries(resBase).firstOrNull { !it.isNamed && it.id == type.toLong() && it.isDir } ?: return emptyList()
            return entries(resBase + e.offset)
        }
        fun leaf(start: Entry): ByteArray? {
            var cur = start
            var guard = 0
            while (cur.isDir && guard++ < 4) cur = entries(resBase + cur.offset).firstOrNull() ?: return null
            if (cur.isDir) return null
            val de = resBase + cur.offset
            val off = rvaToOff(r.u32(de))
            val size = r.u32(de + 4)
            if (off < 0 || size <= 0 || size > 8_000_000) return null
            return r.bytes(off, size.toInt())
        }

        val iconById = typeDir(RT_ICON).filter { !it.isNamed }.associateBy { it.id }
        val group = typeDir(RT_GROUP_ICON).asSequence().mapNotNull { leaf(it) }.firstOrNull() ?: return emptyList()
        val gb = ByteBuffer.wrap(group).order(ByteOrder.LITTLE_ENDIAN)
        val count = gb.getShort(4).toInt() and 0xFFFF
        val out = ArrayList<IconImage>()
        for (i in 0 until count) {
            val p = 6 + i * 14
            if (p + 14 > group.size) break
            val w = (group[p].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val h = (group[p + 1].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val bits = gb.getShort(p + 6).toInt() and 0xFFFF
            val id = (gb.getShort(p + 12).toInt() and 0xFFFF).toLong()
            val data = iconById[id]?.let { leaf(it) } ?: continue
            out.add(IconImage(w, h, bits, data))
        }
        out
    }

    private fun isPng(d: ByteArray) =
        d.size > 8 && d[0] == 0x89.toByte() && d[1] == 'P'.code.toByte() && d[2] == 'N'.code.toByte() && d[3] == 'G'.code.toByte()

    fun decode(img: IconImage): Bitmap? {
        val d = img.data
        if (isPng(d)) return BitmapFactory.decodeByteArray(d, 0, d.size)

        // Оборачиваем DIB в одноэлементный .ico — Android декодирует ICO штатно
        val ico = ByteBuffer.allocate(22 + d.size).order(ByteOrder.LITTLE_ENDIAN)
        ico.putShort(0).putShort(1).putShort(1)
        ico.put((if (img.width >= 256) 0 else img.width).toByte())
        ico.put((if (img.height >= 256) 0 else img.height).toByte())
        ico.put(0).put(0)
        ico.putShort(1).putShort(img.bitCount.toShort())
        ico.putInt(d.size).putInt(22)
        ico.put(d)
        BitmapFactory.decodeByteArray(ico.array(), 0, ico.array().size)?.let { return it }
        return decodeDib32(d)
    }

    /** Запасной разбор 32-bit BMP без заголовка файла (bottom-up BGRA). */
    private fun decodeDib32(d: ByteArray): Bitmap? {
        if (d.size < 44) return null
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        val hdr = b.getInt(0)
        val w = b.getInt(4)
        val h = b.getInt(8) / 2
        val bits = b.getShort(14).toInt()
        if (hdr != 40 || bits != 32 || w <= 0 || h <= 0 || d.size < hdr + w * h * 4) return null
        val px = IntArray(w * h)
        var anyAlpha = false
        for (y in 0 until h) {
            val row = hdr + (h - 1 - y) * w * 4
            for (x in 0 until w) {
                val o = row + x * 4
                val a = d[o + 3].toInt() and 0xFF
                if (a != 0) anyAlpha = true
                px[y * w + x] = (a shl 24) or ((d[o + 2].toInt() and 0xFF) shl 16) or ((d[o + 1].toInt() and 0xFF) shl 8) or (d[o].toInt() and 0xFF)
            }
        }
        if (!anyAlpha) for (i in px.indices) px[i] = px[i] or (0xFF shl 24)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
