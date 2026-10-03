package com.syed.anvil.backend

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds. */
    val expiresAt: Long,
    val userId: String,
    val email: String?,
)

/** Supabase GoTrue over REST: Google ID-token sign-in, refresh, sign-out. One instance per backend. */
class SupabaseAuth(context: Context, private val config: BackendConfig) {
    private val prefs = context.getSharedPreferences("auth_${config.module.key}", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session

    private fun load(): Session? {
        val access = prefs.getString("access", null) ?: return null
        return Session(
            access,
            prefs.getString("refresh", "") ?: "",
            prefs.getLong("expiresAt", 0),
            prefs.getString("userId", "") ?: "",
            prefs.getString("email", null),
        )
    }

    private fun store(s: Session?) {
        prefs.edit().apply {
            if (s == null) clear() else {
                putString("access", s.accessToken); putString("refresh", s.refreshToken)
                putLong("expiresAt", s.expiresAt); putString("userId", s.userId); putString("email", s.email)
            }
        }.apply()
        _session.value = s
    }

    private fun parse(json: String): Session {
        val o = JSONObject(json)
        val user = o.getJSONObject("user")
        val expires = o.optLong("expires_at", System.currentTimeMillis() / 1000 + o.optLong("expires_in", 3600))
        return Session(
            o.getString("access_token"), o.getString("refresh_token"), expires,
            user.getString("id"), user.optString("email").takeIf { it.isNotEmpty() },
        )
    }

    private fun authCall(path: String, body: JSONObject): HttpResult = Http.request(
        "POST", "${config.url}/auth/v1/$path",
        mapOf("apikey" to config.publishableKey), body.toString(),
    )

    /** Exchanges a Google ID token (plus the raw nonce it was requested with) for a Supabase session. */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String) = withContext(Dispatchers.IO) {
        val r = authCall(
            "token?grant_type=id_token",
            JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", rawNonce),
        )
        if (!r.ok) throw HttpException(r.code, errorText(r))
        store(parse(r.body))
    }

    /** A valid access token, refreshed when it has under a minute left; null when signed out. */
    suspend fun accessToken(): String? = lock.withLock {
        val s = _session.value ?: return@withLock null
        if (s.expiresAt - System.currentTimeMillis() / 1000 > 60) return@withLock s.accessToken
        withContext(Dispatchers.IO) {
            val r = authCall("token?grant_type=refresh_token", JSONObject().put("refresh_token", s.refreshToken))
            when {
                r.ok -> parse(r.body).also(::store).accessToken
                // The refresh token is dead: sign out so the UI offers to sign in again.
                r.code == 400 || r.code == 401 || r.code == 403 -> { store(null); null }
                // Offline or a server hiccup: keep the session, use the old token and let the call fail.
                else -> s.accessToken
            }
        }
    }

    fun signOut() = store(null)

    private fun errorText(r: HttpResult): String =
        runCatching { JSONObject(r.body).let { it.optString("msg", it.optString("error_description", r.body)) } }
            .getOrDefault(r.body)
}
