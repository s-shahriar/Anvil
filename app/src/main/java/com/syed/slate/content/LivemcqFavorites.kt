package com.syed.slate.content

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The owner's LiveMCQ favourites, read straight off livemcq.com for the admin importer (moved here from Magpie,
 * which used to write them to Downloads/live_fav for the web /admin to pick up).
 *
 * No token is needed: the favourites endpoint also accepts a plain Django session (`Vary: Cookie` only appears
 * when no `Authorization` header is sent), and the phone-number + OTP login form is server-side Django that works
 * in a WebView. So signing in through [com.syed.slate.ui.screen.LivemcqLogin] fills the app's cookie jar and that
 * is the whole credential. Nothing else in Slate uses cookies, so signing out may clear the jar.
 */
object LivemcqFavorites {
    const val LOGIN_URL = "https://livemcq.com/login/?next=/app/"
    private const val SITE = "https://livemcq.com/"
    private const val API = "https://livemcq.com/api/v1/central-favorite-list/"
    private const val FOLDER = "live_fav"

    sealed interface Scope {
        /**
         * Everything not yet [stored] — the usual run. Pages are read until one holds nothing new; a max-id baseline
         * would not do, since the DB has hand-made rows with placeholder favorite_ids (999000xxx) above every real one.
         */
        data class Since(val stored: Set<String>) : Scope

        /** The newest [count] favourites, stored or not. */
        data class Newest(val count: Int) : Scope
    }

    data class Progress(val page: Int, val pages: Int, val questions: Int)

    /** What the account holds right now, from page 1 alone. */
    data class Peek(val total: Int, val pages: Int, val newest: String?)

    class NotSignedIn : IOException("Sign in to LiveMCQ first")

    private fun cookie(): String = CookieManager.getInstance().getCookie(SITE).orEmpty()

    fun isSignedIn(): Boolean = cookie().contains("sessionid=")

    fun signOut(done: () -> Unit = {}) {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies { cm.flush(); done() }
    }

    suspend fun peek(): Peek = withContext(Dispatchers.IO) {
        val body = fetch(1)
        val list = body.optJSONArray("question_list") ?: JSONArray()
        Peek(
            total = body.optJSONObject("pagination")?.optInt("total_results", 0) ?: 0,
            pages = pages(body),
            newest = list.optJSONObject(0)?.let { str(it, "favorite_id") }?.ifBlank { null },
        )
    }

    /**
     * Walks pages newest-first until [scope] is satisfied. Favourites come back in descending favorite_id order,
     * so "everything new" stops at the first page that is entirely stored instead of reading ~100 pages.
     * Returns the raw API objects; [com.syed.slate.ui.screen.LivemcqAdmin.normalizeItem] reads them as they are.
     */
    suspend fun fetchAll(scope: Scope, onProgress: (Progress) -> Unit): List<JSONObject> = withContext(Dispatchers.IO) {
        val stored = (scope as? Scope.Since)?.stored
        val wanted = (scope as? Scope.Newest)?.count
        val taken = mutableListOf<JSONObject>()
        var page = 1
        while (true) {
            val body = fetch(page)
            val pages = pages(body)
            val list = body.optJSONArray("question_list") ?: JSONArray()
            var fresh = 0
            for (i in 0 until list.length()) {
                val q = list.optJSONObject(i) ?: continue
                if (stored != null) { if (str(q, "favorite_id") !in stored) { taken += q; fresh++ }; continue }
                taken += q
                if (wanted != null && taken.size >= wanted) break
            }
            val atBaseline = stored != null && fresh == 0
            onProgress(Progress(page, pages, taken.size))
            if (atBaseline || (wanted != null && taken.size >= wanted) || list.length() == 0 || page >= pages) break
            page++
        }
        taken
    }

    private fun fetch(page: Int): JSONObject {
        val cookie = cookie()
        if (!cookie.contains("sessionid=")) throw NotSignedIn()
        // Deliberately no Authorization header: the API tries Token auth first and stops there,
        // so an empty or stale one would mask a good session.
        val c = (URL("$API?page=$page").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Cookie", cookie)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Referer", "https://livemcq.com/app/")
            connectTimeout = 20_000
            readTimeout = 30_000
        }
        try {
            when (val code = c.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> throw NotSignedIn()
                else -> throw IOException("LiveMCQ answered HTTP $code")
            }
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    /** The field has been spelled both ways; either is better than guessing 1. */
    private fun pages(body: JSONObject): Int {
        val p = body.optJSONObject("pagination") ?: return 1
        return maxOf(p.optInt("total_pages", 0), p.optInt("num_pages", 0), 1)
    }

    /** A string field with JSON null read as empty (optString would give the four characters "null"). */
    private fun str(o: JSONObject, key: String): String {
        val v = o.opt(key)
        return if (v == null || v == JSONObject.NULL) "" else v.toString()
    }

    /**
     * One question in the livefav file shape the web /admin reads. `options` keeps its *position* (`answer` is a
     * 1-based index into option1..5), so only trailing blanks go; an interior gap stays for the importer to flag.
     */
    fun slim(q: JSONObject): JSONObject = JSONObject().apply {
        put("favorite_id", str(q, "favorite_id"))
        put("slug", str(q, "slug"))
        put("question", str(q, "question"))
        put("options", JSONArray((1..5).map { str(q, "option$it") }.dropLastWhile { it.isBlank() }))
        put("answer", q.optInt("answer", 0))
        put("explanation", str(q, "exp").ifEmpty { str(q, "explanation") })
    }

    /** Writes Downloads/live_fav/livefav_<stamp>.json (a backup, or for the web /admin); returns the file name. */
    suspend fun saveCopy(context: Context, items: List<JSONObject>): String = withContext(Dispatchers.IO) {
        val arr = JSONArray().apply { items.forEach { put(slim(it)) } }
        val name = "livefav_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Could not create the file")
        resolver.openOutputStream(uri).use { checkNotNull(it) { "Could not open the file" }.write(arr.toString().toByteArray()) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Download/$FOLDER/$name"
    }
}
