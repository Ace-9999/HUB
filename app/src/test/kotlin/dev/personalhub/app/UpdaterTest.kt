package dev.personalhub.app

import dev.personalhub.app.update.Updater
import org.junit.Assert.*
import org.junit.Test

// UPD-02: version ordering and release parsing (synthetic JSON; no network).
class UpdaterTest {
    @Test fun versionsCompareNumericallyNotAsText() {
        assertTrue(Updater.newer("1.1", "1.0")); assertTrue(Updater.newer("v1.10", "1.9")); assertTrue(Updater.newer("2.0", "1.9.9"))
        assertFalse(Updater.newer("1.1", "1.1")); assertFalse(Updater.newer("1.0", "1.1")); assertFalse(Updater.newer("1.1.0", "1.1"))
    }
    private fun release(name: String, url: String, draft: Boolean = false) =
        """{"tag_name":"v1.2","draft":$draft,"prerelease":false,"body":"notes","assets":[{"name":"$name","size":123,"browser_download_url":"$url"}]}"""

    @Test fun onlyApksFromThisRepositoryAreAccepted() {
        val ok = Updater.parseRelease(release("HUB-v1.2.apk", "https://github.com/${Updater.REPO}/releases/download/v1.2/HUB-v1.2.apk"))!!
        assertEquals("1.2", ok.version); assertEquals(123, ok.apkBytes)
        assertNull(Updater.parseRelease(release("HUB-v1.2.apk", "https://evil.example/HUB.apk")))
        assertNull(Updater.parseRelease(release("notes.txt", "https://github.com/${Updater.REPO}/releases/download/v1.2/notes.txt")))
        assertNull(Updater.parseRelease(release("HUB-v1.2.apk", "https://github.com/${Updater.REPO}/releases/download/v1.2/HUB-v1.2.apk", draft = true)))
    }
}
