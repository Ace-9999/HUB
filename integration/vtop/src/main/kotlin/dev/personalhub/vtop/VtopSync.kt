package dev.personalhub.vtop

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.*
import dev.personalhub.college.api.*
import org.json.JSONObject
import java.time.Instant

sealed interface SyncOutcome {
    data class Success(val import: CollegeImport) : SyncOutcome
    /** Reason is a fixed code or short sentence — never portal text. */
    data class Failure(val reason: String) : SyncOutcome
}

/** Converts the bounded script payload into a validated import. Pure apart from org.json, JVM-testable. */
object VtopImport {
    fun build(payload: String, now: Instant, preferredSemester: String? = null): SyncOutcome {
        val root = runCatching { JSONObject(payload) }.getOrElse { return SyncOutcome.Failure("Portal response could not be read.") }
        if (root.optString("phase") == "error") return SyncOutcome.Failure(when (root.optString("reason")) {
            "not_signed_in" -> "Sign in on the portal first."
            "session_expired" -> "The portal session expired. Sign in again."
            "no_semester" -> "No semester was offered by the portal (HTTP ${root.optInt("status")})."
            else -> "The portal page changed; sync stopped safely."
        })
        val opts = root.optJSONArray("semesters")
        val semesters = VtopParser.semesters((0 until (opts?.length() ?: 0)).map { val o = opts!!.getJSONArray(it); o.optString(0) to o.optString(1) })
        val semId = root.optString("semester").ifEmpty { preferredSemester } ?: return SyncOutcome.Failure("No semester selected.")
        val semester = semesters.firstOrNull { it.id == semId } ?: Semester(semId, semId)
        val data = root.optJSONObject("datasets") ?: JSONObject()
        fun tables(name: String): Pair<List<PortalTable>?, String?> {
            val d = data.optJSONObject(name) ?: return null to "not requested"
            d.optString("error").takeIf { it.isNotEmpty() }?.let { return null to if (it == "session_expired") "session expired" else "request failed" }
            val status = d.optInt("status", 0)
            if (status !in 200..299) return null to "HTTP $status"
            return VtopScripts.tables(d.optJSONArray("tables")) to null
        }
        val statuses = mutableListOf<DatasetStatus>()
        val clean = { o: JSONObject? -> o?.toString()?.filter { it.isLetterOrDigit() || it in "_/:.,={}\" -" || it == '[' || it == ']' }?.take(12000) }
        VtopDiagnostics.last = "chosen=${clean(JSONObject().put("c", root.optString("chosen")).put("tt", root.optJSONArray("ttOptions")))} sems=${clean(root.optJSONObject("sems"))} shapes=${clean(root.optJSONObject("shapes"))} semesters=${semesters.size} " + listOf("timetable", "attendance", "marks", "exams", "calendar", "grades").joinToString(" | ") { VtopDiagnostics.describe(it, tables(it).first) }
        val (ttTables, ttErr) = tables("timetable")
        val courses = ttTables?.let { VtopParser.courses(it) }
        val meetings = ttTables?.let { VtopParser.meetings(it) }
        statuses += when {
            ttTables == null -> DatasetStatus(Dataset.TIMETABLE, ImportState.FAILED, 0, ttErr ?: "failed")
            courses!!.rows.isEmpty() && meetings!!.rows.isEmpty() -> DatasetStatus(Dataset.TIMETABLE, ImportState.EMPTY, 0, "No courses or timetable grid recognised (${ttTables.size} tables)")
            else -> DatasetStatus(Dataset.TIMETABLE, ImportState.OK, courses.rows.size, "${courses.rows.size} courses · ${meetings!!.rows.size} weekly classes")
        }
        val (atTables, atErr) = tables("attendance")
        val attendance = atTables?.let { VtopParser.attendance(it) }
        statuses += attendance?.status ?: DatasetStatus(Dataset.ATTENDANCE, ImportState.FAILED, 0, atErr ?: "failed")
        val (mkTables, mkErr) = tables("marks")
        val marks = mkTables?.let { VtopParser.marks(it) }
        statuses += marks?.status ?: DatasetStatus(Dataset.MARKS, ImportState.FAILED, 0, mkErr ?: "failed")
        val (grTables, grErr) = tables("grades")
        val grades = grTables?.let { VtopParser.grades(it) }
        statuses += grades?.first?.status ?: DatasetStatus(Dataset.GRADES, ImportState.FAILED, 0, grErr ?: "failed")
        if (statuses.all { it.state == ImportState.FAILED }) return SyncOutcome.Failure("Every dataset failed: ${statuses.joinToString { "${it.dataset.label} ${it.detail}" }}.")
        return SyncOutcome.Success(CollegeImport(semester, semesters.ifEmpty { listOf(semester) }, now,
            courses?.rows.orEmpty(), meetings?.rows.orEmpty(), attendance?.rows.orEmpty(), marks?.rows.orEmpty(),
            grades?.first?.rows.orEmpty(), grades?.second, statuses))
    }
}

/**
 * Owner-operated portal WebView for production sync. Origin-restricted, no JavaScript bridge, no autofill capture.
 * The session cookie stays in the WebView store until the owner disconnects.
 */
@SuppressLint("SetJavaScriptEnabled")
class VtopSyncView(context: Context, private val status: (String) -> Unit) : WebView(context) {
    private val main = Handler(Looper.getMainLooper())
    private var mainFailure: String? = null
    private var syncing = false
    var onSignedIn: (() -> Unit)? = null
    private var signedInReported = false

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportZoom(true); settings.builtInZoomControls = true; settings.displayZoomControls = false
        settings.loadWithOverviewMode = true; settings.useWideViewPort = true
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setWebContentsDebuggingEnabled(false)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) { if (progress < 100 && !syncing) status("Loading official portal… $progress%") }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { mainFailure = null }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val reject = !PortalPolicy.permits(request.url.toString())
                if (reject) status("Navigation outside the official portal was blocked.")
                return reject
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (PortalPolicy.permits(request.url.toString())) null
                else WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), "".byteInputStream())
            override fun onPageFinished(view: WebView, url: String) {
                if (syncing) return
                status(mainFailure ?: "Sign in and complete any captcha. Sync starts automatically once you're in.")
                checkSignedIn()
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                mainFailure = "Secure connection failed (TLS code ${error.primaryError}). Nothing was sent."; handler.cancel(); status(mainFailure!!)
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) { mainFailure = "Portal HTTP error ${response.statusCode}. Saved data unchanged."; status(mainFailure!!) }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { mainFailure = "Portal unavailable (code ${error.errorCode}). Saved data unchanged."; status(mainFailure!!) }
            }
        }
    }

    fun openPortal() { signedInReported = false; loadUrl(PortalPolicy.landing) }

    private val stateCheck = object : Runnable {
        override fun run() { checkSignedIn(); if (!signedInReported && isAttachedToWindow) main.postDelayed(this, 2_000) }
    }
    fun watchSignIn() { main.removeCallbacks(stateCheck); main.postDelayed(stateCheck, 1_500) }

    private fun checkSignedIn() {
        if (!PortalPolicy.permits(url.orEmpty()) || signedInReported) return
        evaluateJavascript(VtopScripts.STATE) { if (VtopScripts.unquote(it) == "signed_in" && !signedInReported) { signedInReported = true; onSignedIn?.invoke() } }
    }

    fun sync(semesterId: String?, done: (SyncOutcome) -> Unit) {
        if (syncing) return
        if (!PortalPolicy.permits(url.orEmpty())) { done(SyncOutcome.Failure("Open the official portal first.")); return }
        syncing = true
        status("Signed in. Reading semesters…")
        evaluateJavascript(VtopScripts.sync(semesterId), null)
        val deadline = System.currentTimeMillis() + 120_000
        val poll = object : Runnable {
            override fun run() {
                evaluateJavascript(VtopScripts.POLL) { encoded ->
                    val payload = VtopScripts.unquote(encoded).orEmpty()
                    val phase = runCatching { JSONObject(payload).optString("phase") }.getOrDefault("")
                    when {
                        phase == "done" || phase == "error" -> { syncing = false; done(VtopImport.build(payload, Instant.now(), semesterId)) }
                        System.currentTimeMillis() > deadline -> { syncing = false; done(SyncOutcome.Failure("The portal took too long to respond. Saved data unchanged.")) }
                        else -> {
                            val step = runCatching { JSONObject(payload).optString("step") }.getOrDefault("")
                            if (step.isNotEmpty()) status("Downloading ${step}…")
                            main.postDelayed(this, 600)
                        }
                    }
                }
            }
        }
        main.postDelayed(poll, 600)
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        stopLoading(); loadUrl("about:blank"); clearHistory()
        removeAllViews(); destroy()
    }

    companion object {
        /** Signs out locally: clears the portal session cookie and web storage. */
        fun clearSession() { CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData() }
    }
}
