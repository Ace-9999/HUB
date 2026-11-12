package dev.personalhub.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.personalhub.vtop.VtopProbeView

// Dedicated native host avoids Compose/WebView paint lifecycle coupling.
// This is debug UI; the connector contains no screen layout or domain persistence.
class PortalProbeActivity : Activity() {
    lateinit var portal: VtopProbeView
        private set
    var pageLoaded = false
        private set
    var inspectionCompleted = false
        private set
    private lateinit var statusText: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Connected owner login is always protected from screenshots/recording.
        val publicPaintTest = BuildConfig.DEMO && intent.getBooleanExtra("publicPaintProbe", false)
        if (!publicPaintTest) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        statusText = TextView(this).apply { setTextColor(Color.DKGRAY); text = "Opening official VTOP portal…"; textSize = 14f }
        val controls = LinearLayout(this)
        controls.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        controls.addView(Button(this).apply { text = "Retry"; setOnClickListener { portal.openPortal() } })
        controls.addView(Button(this).apply { text = "Inspect"; setOnClickListener { inspectPage() } })
        root.addView(controls)
        root.addView(statusText)
        portal = VtopProbeView(this) { message ->
            pageLoaded = message.startsWith("Page loaded.")
            statusText.text = message
        }
        root.addView(portal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        portal.post { portal.openPortal() }
    }
    fun inspectPage() {
        portal.inspect {
            inspectionCompleted = it != null
            statusText.text = if (it == null) "Shape unavailable. No data changed." else "${it.page}: ${it.tableCount} tables, ${it.semesterOptions} semester options. Dataset validation pending."
        }
    }
    override fun onDestroy() {
        (portal.parent as? ViewGroup)?.removeView(portal)
        portal.closeProbe()
        super.onDestroy()
    }
}
