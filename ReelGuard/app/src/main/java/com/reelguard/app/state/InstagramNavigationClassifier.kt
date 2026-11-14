package com.reelguard.app.state

import java.util.Locale

/**
 * Stable Instagram Android view-ID relationships used to infer how a Reel was opened.
 *
 * Instagram reuses the same clips viewer for feed, Reels-tab and Direct entry. In the
 * DM-origin hierarchy, direct_thread_toolbar is retained as a hidden direct child of
 * the content host alongside clips_viewer_fragment_container. Feed/Reels-tab viewer
 * trees do not contain that DM back-stack anchor.
 */
object InstagramNavigationClassifier {
    const val DM_THREAD_TOOLBAR_ID = "direct_thread_toolbar"
    const val DM_SHARED_REEL_PREVIEW_ID = "clips_share_preview_container"
    const val REELS_VIEWER_FRAGMENT_CONTAINER_ID = "clips_viewer_fragment_container"
    const val REELS_VIEWER_PAGER_ID = "clips_viewer_view_pager"

    fun isDmOriginReel(
        contentChildViewIds: Collection<String>,
        isReelsViewer: Boolean
    ): Boolean {
        if (!isReelsViewer) return false

        val ids = contentChildViewIds.map(::normalizeId).toSet()
        val hasDmBackStackAnchor = DM_THREAD_TOOLBAR_ID in ids
        val hasViewerContainer = REELS_VIEWER_FRAGMENT_CONTAINER_ID in ids ||
            REELS_VIEWER_PAGER_ID in ids

        return hasDmBackStackAnchor && hasViewerContainer
    }

    fun isDmSharedReelPreview(viewId: String?): Boolean =
        normalizeId(viewId) == DM_SHARED_REEL_PREVIEW_ID

    private fun normalizeId(viewId: String?): String =
        viewId.orEmpty().substringAfterLast('/').lowercase(Locale.ROOT)
}
