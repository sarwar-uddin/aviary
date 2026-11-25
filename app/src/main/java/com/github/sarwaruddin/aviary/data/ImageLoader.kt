package com.github.sarwaruddin.aviary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.github.sarwaruddin.aviary.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Loads [ImageRef]s: bundled asset first, then URL via a ~150 MB LRU disk cache, plus a memory cache. */
object ImageLoader {
    private const val TAG = "ImageLoader"
    private const val USER_AGENT = "Aviary/0.1 (Android bird field guide)"
    private const val DISK_CAP = 150L * 1024 * 1024

    private lateinit var app: Context
    private lateinit var dir: File
    private val downloads = Semaphore(4)
    private val diskLock = Any()

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun init(context: Context) {
        app = context.applicationContext
        dir = File(app.cacheDir, "img").apply { mkdirs() }
    }

    /** Memory-cache hit only; never blocks. Handy for initial composition state. */
    fun cached(ref: ImageRef, maxPx: Int = 1024): ImageBitmap? =
        memory.get(memKey(ref, maxPx))?.asImageBitmap()

    suspend fun load(ref: ImageRef, maxPx: Int = 1024): ImageBitmap? {
        if (!::app.isInitialized) return null
        val key = memKey(ref, maxPx)
        memory.get(key)?.let { return it.asImageBitmap() }
        return withContext(Dispatchers.IO) {
            val bmp = ref.asset?.let { fromAsset(it, maxPx) } ?: ref.url?.let { fromUrl(it, maxPx) }
            bmp?.also { memory.put(key, it) }?.asImageBitmap()
        }
    }

    private fun memKey(ref: ImageRef, maxPx: Int) = "${ref.asset}|${ref.url}|$maxPx"

    private fun fromAsset(path: String, maxPx: Int): Bitmap? = try {
        decode(maxPx) { app.assets.open(path) }
    } catch (e: Exception) {
        null   // not bundled (or unreadable): fall back to the URL
    }

    private suspend fun fromUrl(url: String, maxPx: Int): Bitmap? {
        val file = File(dir, sha1(url))
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            decodeFile(file, maxPx)?.let { return it }
            file.delete()   // corrupt
        }
        val ok = downloads.withPermit { file.exists() || download(url, file) }
        return if (ok) decodeFile(file, maxPx) else null
    }

    private fun download(url: String, file: File): Boolean {
        val tmp = File(dir, "${file.name}.${System.nanoTime()}.part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode} for $url")
                return false
            }
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            trimDisk()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Download failed: $url (${e.message})")
            tmp.delete()
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun trimDisk() {
        synchronized(diskLock) {
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= DISK_CAP) return
            val target = DISK_CAP * 9 / 10
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= target) break
                val len = f.length()
                if (f.delete()) total -= len
            }
        }
    }

    private fun decodeFile(file: File, maxPx: Int): Bitmap? =
        runCatching { decode(maxPx) { file.inputStream() } }.getOrNull()

    /** Two passes: read bounds, then decode with a power-of-two inSampleSize down to ~[maxPx]. */
    private fun decode(maxPx: Int, open: () -> InputStream): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx) }
        return open().use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Largest power of two that keeps the longer side >= [maxPx]. */
    internal fun sampleSize(w: Int, h: Int, maxPx: Int): Int {
        val longest = maxOf(w, h)
        var s = 1
        while (maxPx > 0 && longest / (s * 2) >= maxPx) s *= 2
        return s
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
