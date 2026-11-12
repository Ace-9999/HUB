package dev.personalhub.monitoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Probe keeps event metadata only; it cannot claim text/video classification or durations.
data class ProbeSignals(val connected: Boolean = false, val events: Long = 0, val scrolls: Long = 0, val lastPackage: String? = null, val lastElapsedMs: Long? = null)
object ProbeControl {
    val packages = setOf("com.instagram.android", "com.twitter.android", "com.reddit.frontpage", "com.android.chrome")
    private val mutable = MutableStateFlow(ProbeSignals())
    val signals = mutable.asStateFlow()
    var optedIn = false
    var redirectPackage: String? = null
        set(value) { require(value == null || value in packages - "com.android.chrome"); field = value }
    fun connected(value: Boolean) { mutable.value = mutable.value.copy(connected = value) }
    fun event(pkg: String?, isScroll: Boolean, elapsed: Long) {
        if (!optedIn || pkg !in packages) return
        val old = mutable.value
        mutable.value = old.copy(events = old.events + 1, scrolls = old.scrolls + if (isScroll) 1 else 0, lastPackage = pkg, lastElapsedMs = elapsed)
    }
    fun reset() { optedIn = false; redirectPackage = null; mutable.value = ProbeSignals(connected = mutable.value.connected) }
}
