package dev.personalhub.app

import androidx.test.core.app.ActivityScenario
import android.content.Intent
import org.junit.Test
import org.junit.Assert.assertTrue
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Explicit opt-in live PUBLIC-page test. Never authenticates, reads forms or extracts records.
// Not part of the offline suite; include with -PportalProbe and select this class.
class PublicPortalProbeTest {
    @Test fun officialPublicPortalLoadsInVisibleProbe() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, PortalProbeActivity::class.java).putExtra("publicPaintProbe", true)
        val scenario = ActivityScenario.launch<PortalProbeActivity>(intent)
        try {
        var activity: Activity? = null
        var web: WebView? = null
        val deadline = android.os.SystemClock.elapsedRealtime() + 45000
        var loaded = false
        while (!loaded && android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { loaded = it.pageLoaded; activity = it; web = it.portal }
            if (!loaded) Thread.sleep(100)
        }
        assertTrue("Public page must load", loaded)
        val inspected = CountDownLatch(1)
        scenario.onActivity { host -> host.portal.inspect { if (it != null) inspected.countDown() }
            host.portal.evaluateJavascript("""JSON.stringify({bodyChars:document.body?.innerText?.length||0,forms:document.forms.length,scripts:document.scripts.length,display:getComputedStyle(document.body).display,visibility:getComputedStyle(document.body).visibility,opacity:getComputedStyle(document.body).opacity,htmlChars:document.documentElement.outerHTML.length,student:!!document.getElementById('stdForm')})""") { println("PUBLIC_DOM_META=$it") }
        }
        assertTrue("Public shape inspection must respond", inspected.await(5, TimeUnit.SECONDS))
        val view = requireNotNull(web)
        instrumentation.runOnMainSync {
            println("PUBLIC_RENDER_META attached=${view.isAttachedToWindow} shown=${view.isShown} hardware=${view.isHardwareAccelerated} visibility=${view.windowVisibility} size=${view.width}x${view.height} focus=${view.hasWindowFocus()}")
        }
        assertTrue("Portal viewport must have usable height", view.height >= 200 * view.resources.displayMetrics.density)
        val rendered = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { rendered.countDown() }
            })
            view.invalidate()
        }
        val visualResponded = rendered.await(5, TimeUnit.SECONDS)
        println("PUBLIC_RENDER_CALLBACK=$visualResponded")
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var result = -1
        instrumentation.runOnMainSync {
            val location = IntArray(2).also { view.getLocationInWindow(it) }
            PixelCopy.request(activity!!.window, Rect(location[0], location[1], location[0] + view.width, location[1] + view.height), bitmap,
                { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue(copied.await(10, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS)
        var ink = 0
        var samples = 0
        for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
            val color = bitmap.getPixel(x, y)
            if (android.graphics.Color.red(color) < 235 || android.graphics.Color.green(color) < 235 || android.graphics.Color.blue(color) < 235) ink++
            samples++
        }
        println("PUBLIC_RENDER_INK=$ink/$samples")
        if (InstrumentationRegistry.getArguments().getString("retainPublicPaint") == "true") {
            java.io.File(instrumentation.targetContext.filesDir, "public-paint.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Thread.sleep(15000)
        }
        bitmap.recycle()
        assertTrue("Loaded callbacks must not hide a blank white viewport: ink=$ink/$samples", ink > samples / 100)
        } finally { scenario.close() }
    }
}
