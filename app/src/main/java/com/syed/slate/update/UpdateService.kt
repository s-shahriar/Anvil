package com.syed.slate.update

import android.content.Context
import com.syed.slate.BuildConfig
import com.syed.slate.backend.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val available: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String?,
    val assetName: String?,
    val notes: String?,
)

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
) {
    val fraction: Float get() = totalBytes?.takeIf { it > 0 }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
}

/**
 * Checks GitHub Releases for a newer APK and downloads it.
 *
 * Reads the public releases Atom feed rather than api.github.com, whose unauthenticated limit is 60 requests an
 * hour. The repo must therefore stay public and each release must carry an asset named `Slate-vX.Y.Z.apk`.
 * If a `Slate-vX.Y.Z.apk.sha256` asset sits next to it, the download is verified against it.
 */
class UpdateService(private val context: Context) {

    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        val r = Http.request("GET", FEED, mapOf("Accept" to "application/atom+xml", "User-Agent" to "Slate"), timeoutMs = 15_000)
        if (!r.ok) throw IllegalStateException("Could not reach GitHub (HTTP ${r.code})")
        val entry = r.body.split("<entry>").getOrNull(1) ?: throw IllegalStateException("No releases published yet")
        val tag = TAG_PATTERN.find(entry)?.groupValues?.get(1)?.let(::decodeEntities)
            ?: throw IllegalStateException("Could not read the latest version")
        val latest = tag.removePrefix("v")
        val current = BuildConfig.VERSION_NAME
        val asset = "$ASSET_PREFIX-$tag.apk"
        UpdateInfo(
            available = compareVersions(latest, current) > 0,
            latestVersion = latest,
            currentVersion = current,
            downloadUrl = "$DOWNLOAD_BASE/$tag/$asset",
            assetName = asset,
            notes = NOTES_PATTERN.find(entry)?.groupValues?.get(1)?.let(::htmlToPlainText),
        )
    }

    /** Resumes a partial file where it can, verifies size (and checksum when published), returns the APK. */
    suspend fun download(info: UpdateInfo, onProgress: (DownloadProgress) -> Unit): File = withContext(Dispatchers.IO) {
        val url = info.downloadUrl ?: throw IllegalStateException("No download URL")
        val target = File(context.cacheDir, info.assetName ?: "$ASSET_PREFIX-update.apk")
        val have = if (target.exists()) target.length() else 0L
        var c = open(url, have)
        if (c.responseCode == 416) { // the cached file is already complete
            c.disconnect(); target.delete(); c = open(url, 0L)
        }
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("Download failed (HTTP ${c.responseCode})")
            val resuming = c.responseCode == HttpURLConnection.HTTP_PARTIAL
            val start = if (resuming) have else 0L
            val total = c.contentLengthLong.takeIf { it > 0 }?.plus(start)
            var done = start
            var windowStart = System.currentTimeMillis(); var windowBytes = 0L; var speed = 0L; var lastReport = 0L
            c.inputStream.use { input ->
                FileOutputStream(target, resuming).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n; windowBytes += n
                        val now = System.currentTimeMillis()
                        if (now - windowStart >= 1000) { speed = windowBytes * 1000 / (now - windowStart); windowStart = now; windowBytes = 0 }
                        if (now - lastReport >= 250) { lastReport = now; onProgress(DownloadProgress(done, total, speed)) }
                    }
                }
            }
            if (total != null && target.length() < total) throw IllegalStateException("Download incomplete — try again")
            onProgress(DownloadProgress(done, total, speed))
        } finally { c.disconnect() }
        verifyChecksum(url, target)
        target
    }

    private fun verifyChecksum(apkUrl: String, file: File) {
        val r = runCatching { Http.request("GET", "$apkUrl.sha256", mapOf("User-Agent" to "Slate"), timeoutMs = 10_000) }.getOrNull()
        if (r == null || !r.ok) return // not published for this release
        val expected = r.body.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) { file.delete(); throw IllegalStateException("Checksum mismatch — the download was corrupted") }
    }

    /** Removes downloaded APKs left behind by earlier updates. Call once the app is running the new version. */
    fun clearStaleDownloads() {
        context.cacheDir.listFiles { f -> f.name.startsWith("$ASSET_PREFIX-") && f.name.endsWith(".apk") }?.forEach { it.delete() }
    }

    private fun open(url: String, resumeFrom: Long): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 15_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "Slate")
        if (resumeFrom > 0) c.setRequestProperty("Range", "bytes=$resumeFrom-")
        return c
    }

    companion object {
        const val REPO = "s-shahriar/Slate"
        const val ASSET_PREFIX = "Slate"
        private const val FEED = "https://github.com/$REPO/releases.atom"
        private const val DOWNLOAD_BASE = "https://github.com/$REPO/releases/download"
        private val TAG_PATTERN = Regex("""href="[^"]*/releases/tag/([^"]+)"""")
        private val NOTES_PATTERN = Regex("""<content[^>]*>([\s\S]*?)</content>""")

        /** Dotted-integer comparison; missing or non-numeric parts count as 0. */
        fun compareVersions(a: String, b: String): Int {
            val l = a.split(".").map { it.toIntOrNull() ?: 0 }
            val r = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(l.size, r.size)) {
                val d = (l.getOrNull(i) ?: 0) - (r.getOrNull(i) ?: 0)
                if (d != 0) return if (d > 0) 1 else -1
            }
            return 0
        }

        /** `&amp;` last, so `&amp;lt;` is not double-decoded. */
        fun decodeEntities(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&amp;", "&")

        fun htmlToPlainText(html: String): String = decodeEntities(html)
            .replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("(?i)</(p|li|h\\d|div)>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "• ").replace(Regex("<[^>]+>"), "")
            .let(::decodeEntities).replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
