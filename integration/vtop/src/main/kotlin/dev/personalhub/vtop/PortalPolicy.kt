package dev.personalhub.vtop

import java.net.URI

// Debug feasibility policy: only the verified Chennai origin, no external bridge.
object PortalPolicy {
    const val landing = "https://vtopcc.vit.ac.in/vtop/"
    fun permits(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "vtopcc.vit.ac.in" &&
            uri.userInfo == null && uri.port in setOf(-1, 443)
    }.getOrDefault(false)
}
enum class PortalPage { LOGIN, CHALLENGE, SEMESTER_CANDIDATE, TABLE_CANDIDATE, UNKNOWN }
data class ProbeResult(val page: PortalPage, val tableCount: Int, val semesterOptions: Int) {
    init { require(tableCount in 0..100); require(semesterOptions in 0..500) }
    val validatedDataset: Boolean get() = false // Shape inspection is not a validated academic read.
}
