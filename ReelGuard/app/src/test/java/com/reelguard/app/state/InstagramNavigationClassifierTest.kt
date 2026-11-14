package com.reelguard.app.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramNavigationClassifierTest {

    @Test
    fun dmViewer_isIdentifiedByHiddenDirectToolbarAndClipsContainer() {
        assertTrue(
            InstagramNavigationClassifier.isDmOriginReel(
                contentChildViewIds = listOf(
                    "com.instagram.android:id/direct_thread_toolbar",
                    "com.instagram.android:id/clips_viewer_fragment_container"
                ),
                isReelsViewer = true
            )
        )
    }

    @Test
    fun feedViewer_isNotMistakenForDmViewer() {
        assertFalse(
            InstagramNavigationClassifier.isDmOriginReel(
                contentChildViewIds = listOf(
                    "com.instagram.android:id/clips_viewer_fragment_container"
                ),
                isReelsViewer = true
            )
        )
    }

    @Test
    fun reelsTabViewer_isNotMistakenForDmViewer() {
        assertFalse(
            InstagramNavigationClassifier.isDmOriginReel(
                contentChildViewIds = listOf(
                    "com.instagram.android:id/layout_container_main",
                    "com.instagram.android:id/clips_tab"
                ),
                isReelsViewer = true
            )
        )
    }

    @Test
    fun dmThreadPreviewIsRecognizedByExactInstagramId() {
        assertTrue(
            InstagramNavigationClassifier.isDmSharedReelPreview(
                "com.instagram.android:id/clips_share_preview_container"
            )
        )
        assertFalse(
            InstagramNavigationClassifier.isDmSharedReelPreview(
                "com.instagram.android:id/media_thumbnail"
            )
        )
    }

    @Test
    fun dmToolbarWithoutViewerDoesNotQualify() {
        assertFalse(
            InstagramNavigationClassifier.isDmOriginReel(
                contentChildViewIds = listOf(
                    "direct_thread_toolbar",
                    "message_list"
                ),
                isReelsViewer = false
            )
        )
    }
}
