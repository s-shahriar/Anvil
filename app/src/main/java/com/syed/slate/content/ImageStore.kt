package com.syed.slate.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Images embedded in question HTML. Each is downloaded once into app storage, so a question with a diagram still
 * shows it offline. [prefetch] is run after a module download to fetch them all while a connection exists.
 */
class ImageStore(context: Context) {
    private val dir = File(context.filesDir, "images").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun fileFor(url: String) = File(dir, MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) })

    private val _count = MutableStateFlow(dir.listFiles { f -> !f.name.endsWith(".tmp") }?.size ?: 0)
    /** How many images are saved on the device. */
    val cachedCount: StateFlow<Int> = _count
    private val prefetching = Mutex()

    fun has(url: String) = fileFor(url).exists()

    val bytes: Long get() = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** From memory, then disk, then (if [allowNetwork]) the web. Null when unavailable. */
    suspend fun get(url: String, allowNetwork: Boolean = true): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(url)?.let { return@withContext it }
        val f = fileFor(url)
        if (!f.exists() && allowNetwork) download(url, f)
        if (!f.exists()) return@withContext null
        decode(f)?.also { memory.put(url, it) } ?: run { f.delete(); null }
    }

    /**
     * Fetches whichever of [urls] are not saved yet, four at a time. Safe to call again and again: it skips what is
     * there, and does nothing while another run is in progress, so an interrupted run is simply resumed next time.
     */
    suspend fun prefetch(urls: Collection<String>) = withContext(Dispatchers.IO) {
        if (!prefetching.tryLock()) return@withContext
        try {
            urls.filter { !has(it) }.chunked(4).forEach { chunk ->
                coroutineScope { chunk.map { u -> async { download(u, fileFor(u)) } }.awaitAll() }
            }
        } finally { prefetching.unlock() }
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() }; memory.evictAll(); _count.value = 0 }

    private fun download(url: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            try {
                if (c.responseCode !in 200..299) return
                c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                if (tmp.renameTo(target)) _count.value = dir.listFiles { f -> !f.name.endsWith(".tmp") }?.size ?: 0
            } finally { c.disconnect() }
        }
        tmp.delete()
    }

    private fun decode(f: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2048) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private val img = Regex("""<img[^>]*?src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        /** Every image URL referenced by an item's question, options and explanation. */
        fun urlsIn(item: Item): List<String> {
            val texts = ArrayList<String>(8)
            texts.add(item.data.optString("question")); texts.add(item.data.optString("explanation"))
            item.data.optJSONObject("options")?.let { o -> o.keys().forEach { texts.add(o.optString(it)) } }
            return texts.filter { '<' in it }.flatMap { t -> img.findAll(t).map { it.groupValues[1] }.toList() }
        }
    }
}
