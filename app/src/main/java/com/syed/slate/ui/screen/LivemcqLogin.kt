package com.syed.slate.ui.screen

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.syed.slate.content.LivemcqFavorites
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateLinearLoader
import com.syed.slate.ui.component.SlateTopBar
import com.syed.slate.ui.theme.LocalPalette

/**
 * livemcq.com sign-in, in the app's own WebView so the session cookie lands in the jar [LivemcqFavorites] reads.
 * Use the phone number + OTP form: the Google/Facebook buttons beside it open a Firebase popup a WebView blocks.
 * The `; wv` token is dropped from the user agent so the site doesn't treat this as an embedded browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivemcqLogin(onDone: () -> Unit) {
    val p = LocalPalette.current
    var signedIn by remember { mutableStateOf(LivemcqFavorites.isSignedIn()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }

    BackHandler { onDone() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { BarTitle("Sign in to LiveMCQ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (signedIn) TextButton(onClick = onDone) { Text("Done") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) SlateLinearLoader()
            Text(
                if (signedIn) "Signed in — tap Done." else "Use the phone number + OTP form; the Google and Facebook buttons can't open here.",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = if (signedIn) p.ok else p.text3,
            )
            failure?.let {
                Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = p.bad)
            }
            AndroidView(
                // weight, not fillMaxSize: inside a Column the latter overflows past the bottom and swallows scrolls.
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = settings.userAgentString.replace("; wv)", ")").replace(" wv)", ")")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                CookieManager.getInstance().flush()
                                signedIn = LivemcqFavorites.isSignedIn()
                                loading = false
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) { failure = error?.description?.toString() ?: "Could not load"; loading = false }
                            }

                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                                if (request?.isForMainFrame == true) { failure = "LiveMCQ returned HTTP ${response?.statusCode}"; loading = false }
                            }
                        }
                        loadUrl(LivemcqFavorites.LOGIN_URL)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
