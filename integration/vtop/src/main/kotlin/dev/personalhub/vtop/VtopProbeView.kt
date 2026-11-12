package dev.personalhub.vtop

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import org.json.JSONObject
import org.json.JSONTokener

// Visible owner-operated WebView. Never reads credentials or sends raw DOM to native code.
@SuppressLint("SetJavaScriptEnabled")
class VtopProbeView(context: Context, private val status: (String) -> Unit) : WebView(context) {
    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val reject = !PortalPolicy.permits(request.url.toString())
                if (reject) status("External navigation blocked; challenge origin needs audit.")
                return reject
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (PortalPolicy.permits(request.url.toString())) null else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), "".byteInputStream())
            override fun onPageFinished(view: WebView, url: String) { status("Page loaded. Sign in and solve challenges on this device, then inspect shape.") }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) { handler.cancel(); status("TLS validation failed. Connection stopped.") }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) status("Portal unavailable. No saved academic data changed.")
            }
        }
        loadUrl(PortalPolicy.landing)
    }
    fun inspect(result: (ProbeResult?) -> Unit) {
        if (!PortalPolicy.permits(url.orEmpty())) { result(null); return }
        // Return only bounded structural counts/booleans. No text, HTML, credentials or grade rows.
        evaluateJavascript("""
            (() => JSON.stringify({
              login: !!document.querySelector('input[type=password]'),
              challenge: !!document.querySelector('iframe[src*=recaptcha],input[name*=captcha],input[id*=captcha]'),
              semesters: Math.min(500,document.querySelectorAll('select[id*=semester] option,select[name*=semester] option').length),
              tables: Math.min(100,document.querySelectorAll('table').length)
            }))()
        """.trimIndent()) { encoded ->
            val parsed = runCatching {
                require(encoded.length < 4096)
                val decoded = JSONTokener(encoded).nextValue() as String
                val obj = JSONObject(decoded)
                val page = when {
                    obj.getBoolean("challenge") -> PortalPage.CHALLENGE
                    obj.getBoolean("login") -> PortalPage.LOGIN
                    obj.getInt("semesters") > 0 -> PortalPage.SEMESTER_CANDIDATE
                    obj.getInt("tables") > 0 -> PortalPage.TABLE_CANDIDATE
                    else -> PortalPage.UNKNOWN
                }
                ProbeResult(page, obj.getInt("tables"), obj.getInt("semesters"))
            }.getOrNull()
            result(parsed)
        }
    }
    fun closeProbe() {
        stopLoading(); loadUrl("about:blank"); clearHistory(); clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        removeAllViews(); destroy()
    }
}
