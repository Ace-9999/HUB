package dev.personalhub.monitoring

import dev.personalhub.usage.api.*
import dev.personalhub.usage.api.Target
import org.junit.Assert.*
import org.junit.Test

class SurfaceClassifierTest {
    @Test fun browserUsesAddressBarHostOnly() {
        assertEquals(Target.X, SurfaceClassifier.classify("com.android.chrome", setOf("url_bar"), "x.com/home")!!.target)
        assertEquals(Source.BROWSER, SurfaceClassifier.classify("com.brave.browser", emptySet(), "reddit.com")!!.source)
        assertNull(SurfaceClassifier.classify("com.android.chrome", setOf("url_bar"), "wikipedia.org"))
        assertNull(SurfaceClassifier.classify("com.android.chrome", setOf("url_bar"), null))
    }
    @Test fun appSurfacesAndUnknownFallback() {
        assertEquals(Surface.VIDEO, SurfaceClassifier.classify("com.instagram.android", setOf("clips_viewer_view_pager"), null)!!.surface)
        assertEquals(Surface.UNKNOWN, SurfaceClassifier.classify("com.instagram.android", setOf("something_new"), null)!!.surface)
        assertEquals(Surface.VIDEO, SurfaceClassifier.classify("com.google.android.youtube", setOf("reel_recycler"), null)!!.surface)
        assertNull(SurfaceClassifier.classify("com.whatsapp", setOf("x"), null))
    }
    @Test fun instagramHomeIsNotReelsJustBecauseTheReelsTabButtonExists() {
        val home = setOf("feed_tab", "clips_tab", "search_tab", "profile_tab", "selected:feed_tab", "row_feed_photo_profile_name")
        assertEquals(Surface.TEXT_FEED, SurfaceClassifier.classify("com.instagram.android", home, null)!!.surface)
        val reels = setOf("feed_tab", "clips_tab", "selected:clips_tab", "clips_viewer_view_pager")
        assertEquals(Surface.VIDEO, SurfaceClassifier.classify("com.instagram.android", reels, null)!!.surface)
        val inlineFeedReel = setOf("selected:feed_tab", "clips_tab", "clips_video_container", "row_feed_photo_profile_name")
        assertEquals(Surface.TEXT_FEED, SurfaceClassifier.classify("com.instagram.android", inlineFeedReel, null)!!.surface)
        val reelFromFeed = setOf("clips_viewer_view_pager", "clips_video_container")
        assertEquals(Surface.VIDEO, SurfaceClassifier.classify("com.instagram.android", reelFromFeed, null)!!.surface)
        assertEquals(Surface.OTHER, SurfaceClassifier.classify("com.instagram.android", setOf("clips_tab", "selected:profile_tab"), null)!!.surface)
    }
    @Test fun overlaysNeverEndSessions() {
        assertTrue(SurfaceClassifier.isOverlay("com.android.systemui")); assertTrue(SurfaceClassifier.isOverlay("com.google.android.inputmethod.latin"))
        assertFalse(SurfaceClassifier.isOverlay("com.whatsapp"))
    }
}
