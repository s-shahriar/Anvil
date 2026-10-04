package com.syed.slate.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Minimal PostgREST client. Sends the user's JWT when signed in, otherwise just the publishable key. */
class Postgrest(private val config: BackendConfig, private val auth: SupabaseAuth) {

    private suspend fun headers(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val h = mutableMapOf("apikey" to config.publishableKey, "Accept" to "application/json")
        auth.accessToken()?.let { h["Authorization"] = "Bearer $it" }
        return h + extra
    }

    private fun check(r: HttpResult): HttpResult =
        if (r.ok) r else throw HttpException(r.code, "HTTP ${r.code}: ${r.body.take(300)}")

    /**
     * Reads every row of a query. PostgREST silently truncates at 1000 rows, so this pages by
     * limit/offset and relies on [query] ending in a unique `order=` so pages never overlap.
     */
    suspend fun selectAll(
        table: String,
        query: String,
        pageSize: Int = 1000,
        onPage: (loaded: Int) -> Unit = {},
    ): List<org.json.JSONObject> = withContext(Dispatchers.IO) {
        val rows = ArrayList<org.json.JSONObject>()
        var offset = 0
        while (true) {
            val r = check(Http.request("GET", "${config.url}/rest/v1/$table?$query&limit=$pageSize&offset=$offset", headers()))
            val page = JSONArray(r.body)
            for (i in 0 until page.length()) rows.add(page.getJSONObject(i))
            offset += page.length()
            onPage(rows.size)
            if (page.length() < pageSize) break
        }
        rows
    }

    /** Bulk upsert; every row must carry the same keys. */
    suspend fun upsert(table: String, rows: JSONArray, onConflict: String) {
        withContext(Dispatchers.IO) {
            check(
                Http.request(
                    "POST", "${config.url}/rest/v1/$table?on_conflict=$onConflict",
                    headers(mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")),
                    rows.toString(),
                ),
            )
        }
    }

    /** Calls a database function (`/rest/v1/rpc/<name>`); returns the response body. Throws [HttpException] on failure. */
    suspend fun rpc(name: String, body: org.json.JSONObject): String = withContext(Dispatchers.IO) {
        check(Http.request("POST", "${config.url}/rest/v1/rpc/$name", headers(), body.toString())).body
    }

    /** PATCH rows matching [filter] (e.g. `id=in.(a,b)`). */
    suspend fun patch(table: String, filter: String, body: org.json.JSONObject) {
        withContext(Dispatchers.IO) {
            check(Http.request("PATCH", "${config.url}/rest/v1/$table?$filter", headers(mapOf("Prefer" to "return=minimal")), body.toString()))
        }
    }

    /** DELETE rows matching [filter]. */
    suspend fun delete(table: String, filter: String) {
        withContext(Dispatchers.IO) {
            check(Http.request("DELETE", "${config.url}/rest/v1/$table?$filter", headers(mapOf("Prefer" to "return=minimal"))))
        }
    }

    /** Insert rows and return nothing. Rows may carry their own `id`. */
    suspend fun insert(table: String, rows: JSONArray) {
        withContext(Dispatchers.IO) {
            check(Http.request("POST", "${config.url}/rest/v1/$table", headers(mapOf("Prefer" to "return=minimal")), rows.toString()))
        }
    }

    /** Row count for a filter, from the Content-Range header. */
    suspend fun count(table: String, filter: String): Int = withContext(Dispatchers.IO) {
        val r = check(
            Http.request(
                "GET", "${config.url}/rest/v1/$table?select=id&$filter&limit=1",
                headers(mapOf("Prefer" to "count=exact")),
            ),
        )
        r.header("Content-Range")?.substringAfter('/')?.toIntOrNull() ?: 0
    }
}
