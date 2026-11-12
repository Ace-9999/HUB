package dev.personalhub.monitoring

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

// Debug-only binding in app manifest. Does not retrieve nodes, URLs or content text.
class ProbeAccessibilityService : AccessibilityService() {
    private var lastRedirect = Long.MIN_VALUE / 2
    override fun onServiceConnected() { ProbeControl.connected(true) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        val now = SystemClock.elapsedRealtime()
        ProbeControl.event(pkg, event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED, now)
        if (ProbeControl.optedIn && pkg != null && pkg == ProbeControl.redirectPackage && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && now - lastRedirect >= 750) {
            lastRedirect = now
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }
    override fun onInterrupt() { ProbeControl.connected(false) }
    override fun onDestroy() { ProbeControl.reset(); ProbeControl.connected(false); super.onDestroy() }
}
