package com.syed.anvil.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.syed.anvil.ui.theme.Palette
import com.syed.anvil.ui.theme.TopicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Which pre-rendered page, and which wrapper its stylesheet expects. */
enum class PageKind(val css: List<String>, val open: String, val close: String) {
    MATH(listOf("mathformulas.css"), """<div class="mf-root"><div class="mf-wrap mf-cover-on">""", "</div></div>"),
    EQUATION(listOf("equation.css"), """<div class="eq-page">""", "</div>"),
}

private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
private fun rgba(c: Color, a: Float) = "rgba(${(c.red * 255).toInt()},${(c.green * 255).toInt()},${(c.blue * 255).toInt()},$a)"

/** The web apps' design tokens, filled from the palette of the part of Anvil the page sits in. */
internal fun themeVariables(p: Palette, dark: Boolean): String = buildString {
    append(":root{")
    fun v(name: String, value: String) = append("--$name:$value;")
    v("bg", hex(p.bg)); v("surface", hex(p.surface)); v("card", hex(p.surface)); v("elevated", hex(p.elevated))
    v("text", hex(p.text)); v("text-2", hex(p.text2)); v("text-3", hex(p.text3)); v("text-dim", rgba(p.text3, .7f))
    v("border", rgba(p.text, if (dark) .08f else .07f)); v("border-md", rgba(p.text, if (dark) .13f else .10f)); v("border-bright", rgba(p.primary, .45f))
    v("accent", hex(p.primary)); v("accent-2", hex(p.primary)); v("accent-light", rgba(p.primary, .14f)); v("on-accent", hex(p.onPrimary))
    v("imp", hex(p.imp)); v("imp-tint", rgba(p.imp, .12f)); v("ok", hex(p.ok)); v("ok-tint", rgba(p.ok, .12f))
    v("warn", hex(p.warn)); v("bad", hex(p.bad)); v("info", hex(p.info))
    v("hover-bg", rgba(p.primary, .07f)); v("surface-glass", rgba(p.surface, .97f)); v("shadow-sm", "0 1px 2px rgba(0,0,0,.05)")
    v("font-body", "'Plus Jakarta Sans','Noto Sans Bengali',system-ui,sans-serif"); v("font-display", "'Plus Jakarta Sans','Noto Sans Bengali',system-ui,sans-serif")
    for (n in 1..12) v("topic-$n", hex(TopicColors.of(n, dark)))
    append("}")
}

private fun Context.assetText(path: String) = assets.open(path).bufferedReader().use { it.readText() }

/** Joins the stylesheets, the pre-rendered body and the start-up state into one page. */
private fun buildPage(ctx: Context, kind: PageKind, bodyAsset: String, p: Palette, dark: Boolean, cover: Boolean, important: Set<String>, importantOnly: Boolean): String {
    val body = ctx.assetText(bodyAsset)
    val init = """window.__init={cover:$cover,importantOnly:$importantOnly,important:${JSONArray(important.toList())}};"""
    return buildString(body.length + 4096) {
        append("""<!doctype html><html data-theme="${if (dark) "dark" else "light"}"><head><meta charset="utf-8">""")
        append("""<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">""")
        append("""<link rel="stylesheet" href="katex/katex.min.css"><style>${themeVariables(p, dark)}</style><link rel="stylesheet" href="base.css">""")
        kind.css.forEach { append("""<link rel="stylesheet" href="$it">""") }
        append("</head><body>").append(kind.open).append(body).append(kind.close)
        append("<script>$init</script><script src=\"controller.js\"></script></body></html>")
    }
}

/** What the page can tell Anvil. Called from the WebView's thread, so each hop to the UI thread is explicit. */
private class Bridge(
    private val onToggle: () -> ((String) -> Unit),
    private val onSection: () -> ((String) -> Unit),
    private val onReady: () -> Unit,
) {
    private val ui = Handler(Looper.getMainLooper())
    @JavascriptInterface fun toggleImportant(uid: String) { ui.post { onToggle()(uid) } }
    @JavascriptInterface fun onSection(id: String) { ui.post { onSection()(id) } }
    @JavascriptInterface fun onReady() { ui.post { onReady.invoke() } }
}

/**
 * A pre-rendered formula page (KaTeX and diagrams already turned into HTML at development time) in a WebView. It is
 * entirely local: no network, no file access beyond the app's own assets. The page scrolls itself.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FormulaPage(
    kind: PageKind,
    bodyAsset: String,
    palette: Palette,
    dark: Boolean,
    cover: Boolean,
    modifier: Modifier = Modifier,
    importantOnly: Boolean = false,
    important: Set<String> = emptySet(),
    scrollTo: String? = null,
    scrollNonce: Int = 0,
    onToggleImportant: (String) -> Unit = {},
    onSection: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    // Rebuilt only when the theme changes; cover and stars are switched live through the page's own script.
    val initial = remember { mutableStateOf(Triple(cover, importantOnly, important)) }
    val html by produceState<String?>(null, kind, bodyAsset, palette, dark) {
        value = withContext(Dispatchers.IO) { buildPage(ctx, kind, bodyAsset, palette, dark, initial.value.first, initial.value.third, initial.value.second) }
    }
    var ready by remember(html) { mutableStateOf(false) }
    val toggle = rememberUpdatedState(onToggleImportant)
    val section = rememberUpdatedState(onSection)
    var web by remember { mutableStateOf<WebView?>(null) }

    val page = html
    if (page != null) AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true // only our own page, and only its controller script
                settings.allowFileAccess = false; settings.allowContentAccess = false
                settings.blockNetworkLoads = true; settings.domStorageEnabled = false
                settings.setSupportZoom(false); settings.textZoom = 100
                overScrollMode = WebView.OVER_SCROLL_NEVER
                setBackgroundColor(palette.bg.toArgb())
                addJavascriptInterface(Bridge({ toggle.value }, { section.value }, { ready = true }), "Anvil")
                // Nothing in these pages links anywhere; refuse any navigation rather than leave the page.
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                }
                web = this
            }
        },
        update = { w ->
            if (w.tag != page) {
                w.tag = page
                w.setBackgroundColor(palette.bg.toArgb())
                w.loadDataWithBaseURL("file:///android_asset/web/", page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )

    val w = web
    LaunchedEffect(w, ready, cover) { if (ready) w?.evaluateJavascript("setCover($cover)", null) }
    LaunchedEffect(w, ready, importantOnly) { if (ready) w?.evaluateJavascript("setImportantOnly($importantOnly)", null) }
    LaunchedEffect(w, ready, important) { if (ready) w?.evaluateJavascript("setImportant(${JSONArray(important.toList())})", null) }
    LaunchedEffect(w, ready, scrollNonce) { if (ready && scrollTo != null) w?.evaluateJavascript("scrollToId('${scrollTo.replace("'", "")}')", null) }
    DisposableEffect(Unit) { onDispose { web?.stopLoading() } }
}
