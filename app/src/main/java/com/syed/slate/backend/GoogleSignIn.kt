package com.syed.slate.backend

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

/** Native Google sign-in through Credential Manager; the ID token is then handed to Supabase. */
object GoogleSignIn {
    class Token(val idToken: String, val rawNonce: String)

    suspend fun requestToken(activity: Activity, webClientId: String): Token {
        require(webClientId.isNotBlank()) { "Google web client ID is not configured" }
        // Google embeds the hashed nonce in the token; Supabase is told the raw one and re-hashes it.
        val raw = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val hashed = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(hashed).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        return Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, raw)
    }
}
