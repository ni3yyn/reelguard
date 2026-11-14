package com.reelguard.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.reelguard.app.data.ProtectionPreferences
import com.reelguard.app.overlay.OverlayManager
import com.reelguard.app.state.GuardEffect
import com.reelguard.app.state.GuardPhase
import com.reelguard.app.state.ReelGuardStateMachine
import com.reelguard.app.state.ScreenSignals

class ReelGuardAccessibilityService : AccessibilityService() {
    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val MAX_TRAVERSAL_DEPTH = 16
        private const val MAX_TRAVERSAL_NODES = 240
        private const val OVERLAY_RETRY_DELAY_MS = 2_000L
        private const val MISSING_PERMISSION_NOTICE_INTERVAL_MS = 60_000L
    }

    private val stateMachine = ReelGuardStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayManager: OverlayManager? = null
    private var activeTimer: CountDownTimer? = null
    private var lastScreenSignals = ScreenSignals()
    private var instagramForeground = false
    private var exitNavigationPending = false
    private var exitNavigationInProgress = false
    private var overlayRetryAfterMs = 0L
    private var lastMissingPermissionNoticeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)
        stateMachine.reset()
        restoreSessionIfNeeded()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackage = event.packageName?.toString() ?: return

        if (!ProtectionPreferences.isProtectionEnabled(this)) {
            cancelActiveTimer()
            ProtectionPreferences.clearSession(this)
            stateMachine.reset()
            lastScreenSignals = ScreenSignals()
            instagramForeground = false
            overlayManager?.dismiss()
            return
        }

        if (eventPackage != INSTAGRAM_PACKAGE) {
            // The overlay belongs to ReelGuard. Ignore its own window events.
            // Any other app's window transition means Instagram is no longer foreground.
            if (
                eventPackage != packageName &&
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                instagramForeground
            ) leaveInstagram()
            return
        }

        instagramForeground = true
        if (exitNavigationPending) startExitReelsNavigation()
        val nowMs = SystemClock.elapsedRealtime()
        val eventType = event.eventType

        if (!Settings.canDrawOverlays(this)) {
            stateMachine.onOverlayUnavailable()
            if (nowMs - lastMissingPermissionNoticeMs >= MISSING_PERMISSION_NOTICE_INTERVAL_MS) {
                lastMissingPermissionNoticeMs = nowMs
                overlayManager?.showMessage("Allow ReelGuard to display over other apps to activate protection.")
            }
            return
        }

        if (nowMs < overlayRetryAfterMs) return

        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && isClickOnDmSharedReel(event)) {
            stateMachine.onDmReelCardClicked(nowMs)
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val className = event.className?.toString().orEmpty()
            val isVerticalPagerOrList =
                className.contains("ViewPager", ignoreCase = true) ||
                    className.contains("RecyclerView", ignoreCase = true) ||
                    className.contains("AbsListView", ignoreCase = true)

            if (isVerticalPagerOrList) {
                val deltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
                val effect = stateMachine.onVerticalScrollDetected(
                    nowMs = nowMs,
                    scrollDeltaY = deltaY,
                    fromIndex = event.fromIndex,
                    toIndex = event.toIndex
                )
                if (effect != GuardEffect.None) {
                    executeEffect(effect)
                    return
                }
            }
        }

        if (
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
            if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return

            lastScreenSignals = inspectScreenSignals(root, event.className?.toString().orEmpty())
            stateMachine.onScreenEvaluated(lastScreenSignals, nowMs).forEach { executeEffect(it) }
        }
    }

    override fun onInterrupt() {
        if (stateMachine.snapshot.phase == GuardPhase.EXITING_TO_HOME) {
            exitNavigationPending = true
        }
        exitNavigationInProgress = false
        stateMachine.onAppBackgrounded().forEach { executeEffect(it) }
        overlayManager?.dismissAll()
        instagramForeground = false
    }

    override fun onDestroy() {
        cancelActiveTimer()
        overlayManager?.dismissAll()
        overlayManager = null
        super.onDestroy()
    }

    private fun leaveInstagram() {
        instagramForeground = false
        if (stateMachine.snapshot.phase == GuardPhase.EXITING_TO_HOME) {
            exitNavigationPending = true
        }
        exitNavigationInProgress = false
        lastScreenSignals = ScreenSignals()
        stateMachine.onAppBackgrounded().forEach { executeEffect(it) }
        overlayManager?.dismiss()
    }

    private fun restoreSessionIfNeeded() {
        if (!ProtectionPreferences.isProtectionEnabled(this)) {
            ProtectionPreferences.clearSession(this)
            return
        }

        val endEpochMs = ProtectionPreferences.sessionEndEpochMs(this)
        if (endEpochMs <= 0L) return

        val remainingMs = endEpochMs - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            ProtectionPreferences.clearSession(this)
            stateMachine.reset()
            return
        }

        stateMachine.restoreTimedSession(remainingMs, SystemClock.elapsedRealtime())
        startCountdownFor(remainingMs)
    }

    private fun executeEffect(effect: GuardEffect) {
        when (effect) {
            GuardEffect.None -> Unit

            GuardEffect.ShowConfirmationOverlay -> {
                if (!Settings.canDrawOverlays(this)) {
                    stateMachine.onOverlayUnavailable()
                    return
                }
                overlayManager?.showConfirmationOverlay(
                    onContinueAnyway = {
                        executeEffect(stateMachine.onContinueAnywayTapped())
                    },
                    onCancel = {
                        stateMachine.onCancelTapped().forEach { executeEffect(it) }
                    },
                    onSelectMinutes = { minutes ->
                        stateMachine.onTimeLimitSelected(minutes, SystemClock.elapsedRealtime())
                            .forEach { executeEffect(it) }
                    },
                    onFailure = {
                        stateMachine.onOverlayUnavailable()
                        overlayRetryAfterMs = SystemClock.elapsedRealtime() + OVERLAY_RETRY_DELAY_MS
                    }
                )
            }

            GuardEffect.ShowTimeLimitOverlay -> overlayManager?.switchToTimeLimitOverlay()
            GuardEffect.DismissOverlay -> overlayManager?.dismiss()

            GuardEffect.ExecuteBackToDm -> {
                mainHandler.post {
                    if (isInstagramCurrentlyActive()) performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }

            GuardEffect.ExitReelsToHome -> requestExitReelsNavigation()

            is GuardEffect.StartCountdown -> {
                val durationMs = effect.minutes * 60_000L
                ProtectionPreferences.setSessionEndEpochMs(
                    this,
                    System.currentTimeMillis() + durationMs
                )
                startCountdownFor(durationMs)
            }

            GuardEffect.ShowTimesUpBanner -> {
                if (isInstagramCurrentlyActive()) {
                    overlayManager?.showMessage("Time is up. Your Reels session has ended.")
                }
            }
        }
    }

    private fun startCountdownFor(durationMs: Long) {
        cancelActiveTimer()
        activeTimer = object : CountDownTimer(durationMs.coerceAtLeast(1L), 1_000L) {
            override fun onTick(millisUntilFinished: Long) = Unit

            override fun onFinish() {
                activeTimer = null
                ProtectionPreferences.clearSession(this@ReelGuardAccessibilityService)
                stateMachine.onTimerExpired().forEach { executeEffect(it) }
            }
        }.start()
    }

    private fun cancelActiveTimer() {
        activeTimer?.cancel()
        activeTimer = null
    }

    private fun isInstagramCurrentlyActive(): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        return root.packageName?.toString() == INSTAGRAM_PACKAGE
    }

    private fun requestExitReelsNavigation() {
        exitNavigationPending = true
        startExitReelsNavigation()
    }

    private fun startExitReelsNavigation() {
        if (!instagramForeground || exitNavigationInProgress) return

        exitNavigationPending = false
        exitNavigationInProgress = true
        // Remove the full-screen overlay first. Querying rootInActiveWindow immediately can
        // still return ReelGuard's own overlay window instead of Instagram underneath it.
        overlayManager?.dismiss()
        attemptExitReelsBackToHome(attempt = 0)
    }

    private fun attemptExitReelsBackToHome(attempt: Int) {
        mainHandler.postDelayed({
            if (!instagramForeground) {
                exitNavigationInProgress = false
                exitNavigationPending = true
                return@postDelayed
            }

            val root = findInstagramRoot()
            if (root == null) {
                // The last confirmed Instagram surface is still a Reel. A guarded global Back
                // is safer than leaving the user trapped behind a dismissed overlay.
                if (lastScreenSignals.isReelsSurface) performGlobalAction(GLOBAL_ACTION_BACK)
                if (attempt < 2) {
                    attemptExitReelsBackToHome(attempt + 1)
                } else {
                    scheduleExitRetry()
                }
                return@postDelayed
            }

            val signals = inspectScreenSignals(root, "")
            lastScreenSignals = signals
            if (!signals.isReelsSurface) {
                exitNavigationInProgress = false
                exitNavigationPending = false
                stateMachine.onScreenEvaluated(signals, SystemClock.elapsedRealtime())
                    .forEach { executeEffect(it) }
                safeRecycle(root)
                return@postDelayed
            }

            val homeNode = findFirstMatchingNode(root) { node ->
                val id = node.viewIdResourceName.orEmpty().lowercase()
                val description = node.contentDescription?.toString().orEmpty().lowercase()
                val isHomeId = id.endsWith(":id/feed_tab") ||
                    id.contains("home_tab") ||
                    id.contains("feed_tab")
                val isHomeDescription = description == "home" ||
                    description.startsWith("home,") ||
                    description.startsWith("home ")
                node.isClickable && (isHomeId || isHomeDescription)
            }

            val homeSelected = homeNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            homeNode?.let(::safeRecycle)
            safeRecycle(root)

            // Prefer tapping Instagram's own Home tab; only fall back to Back when it is
            // absent or has not moved us away from a verified Reel surface.
            if (!homeSelected || attempt == 2) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            if (attempt < 2) {
                attemptExitReelsBackToHome(attempt + 1)
            } else {
                scheduleExitRetry()
            }
        }, if (attempt == 0) 220L else 420L)
    }

    private fun scheduleExitRetry() {
        exitNavigationInProgress = false
        exitNavigationPending = true
        mainHandler.postDelayed({
            if (instagramForeground && exitNavigationPending) {
                startExitReelsNavigation()
            }
        }, 900L)
    }

    private fun findInstagramRoot(): AccessibilityNodeInfo? {
        val windowRoot = runCatching {
            windows.firstNotNullOfOrNull { window ->
                val candidate = window.root ?: return@firstNotNullOfOrNull null
                if (candidate.packageName?.toString() == INSTAGRAM_PACKAGE) {
                    candidate
                } else {
                    safeRecycle(candidate)
                    null
                }
            }
        }.getOrNull()
        if (windowRoot != null) return windowRoot

        val activeRoot = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        if (activeRoot.packageName?.toString() == INSTAGRAM_PACKAGE) return activeRoot
        safeRecycle(activeRoot)
        return null
    }

    private fun isClickOnDmSharedReel(event: AccessibilityEvent): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) return false
        if (!detectDmThread(root, event.className?.toString().orEmpty())) return false

        val source = runCatching { event.source }.getOrNull()
        if (source != null) {
            val isSharedReelNode: (AccessibilityNodeInfo) -> Boolean = { node ->
                val id = node.viewIdResourceName.orEmpty().lowercase()
                val description = node.contentDescription?.toString().orEmpty().lowercase()
                val text = node.text?.toString().orEmpty().lowercase()
                id.contains("direct_reel") ||
                    id.contains("reel_share") ||
                    id.contains("clips_share") ||
                    id.contains("media_share") ||
                    description.contains("reel") ||
                    description.contains("shared video") ||
                    text.contains("watch reel")
            }

            // Instagram often reports the deepest image/text child as the click source.
            // The Reel/share identity can live on its clickable message-bubble ancestor.
            val matched = ancestorMatches(source, maxDepth = 7, predicate = isSharedReelNode) ||
                subtreeMatches(source, maxDepth = 6, maxNodes = 64, predicate = isSharedReelNode)
            safeRecycle(source)
            if (matched) return true
        }

        val eventText = event.text.joinToString(" ").lowercase()
        val eventDescription = event.contentDescription?.toString().orEmpty().lowercase()
        return eventDescription.contains("reel") || eventText.contains("reel")
    }

    private fun ancestorMatches(
        source: AccessibilityNodeInfo,
        maxDepth: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        var current: AccessibilityNodeInfo? = runCatching {
            AccessibilityNodeInfo.obtain(source)
        }.getOrNull() ?: return false

        for (depth in 0..maxDepth) {
            val node = current ?: return false
            val matched = predicate(node)
            val parent = if (!matched && depth < maxDepth) {
                runCatching { node.parent }.getOrNull()
            } else {
                null
            }
            safeRecycle(node)
            if (matched) return true
            current = parent
        }
        current?.let(::safeRecycle)
        return false
    }

    private fun inspectScreenSignals(root: AccessibilityNodeInfo, eventClass: String): ScreenSignals {
        var inDmThread = eventClass.contains("DirectThreadFragment", ignoreCase = true)
        var inReelsViewer = eventClass.contains("ClipsViewer", ignoreCase = true)
        var reelsTabSelected = false
        var exploreOrStoryReel = false
        var homeSuggestedFullScreenReel = false

        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        while (queue.isNotEmpty() && visited < MAX_TRAVERSAL_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            val id = node.viewIdResourceName.orEmpty().lowercase()
            val description = node.contentDescription?.toString().orEmpty().lowercase()
            val text = node.text?.toString().orEmpty().lowercase()

            if (
                id.contains("row_thread_composer") ||
                id.contains("direct_thread") ||
                id.contains("message_list") ||
                description.contains("message...") ||
                text == "message..."
            ) inDmThread = true

            if (
                (id.endsWith(":id/clips_tab") || id.contains("reels_tab") ||
                    description == "reels" || description.startsWith("reels,")) &&
                (node.isSelected || description.contains("selected"))
            ) reelsTabSelected = true

            if (
                id.contains("clips_viewer") ||
                id.contains("clips_video_container") ||
                id.contains("clips_author_username") ||
                id.contains("clips_ufi_container") ||
                description.contains("reels video") ||
                description.contains("original audio")
            ) inReelsViewer = true

            if (
                (id.contains("explore") && id.contains("clips")) ||
                (id.contains("story_viewer") && (description.contains("reel") || text.contains("watch full reel")))
            ) exploreOrStoryReel = true

            if (id.contains("feed_reels_viewer") || id.contains("suggested_clips_viewer")) {
                homeSuggestedFullScreenReel = true
            }

            if (depth < MAX_TRAVERSAL_DEPTH) {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    queue.add(child to depth + 1)
                }
            }

            if (node !== root) safeRecycle(node)
        }

        while (queue.isNotEmpty()) {
            val (remaining, _) = queue.removeFirst()
            if (remaining !== root) safeRecycle(remaining)
        }

        return ScreenSignals(
            isInDmThread = inDmThread,
            isInReelsViewer = inReelsViewer,
            isReelsTabSelected = reelsTabSelected,
            isExploreOrStoryReel = exploreOrStoryReel,
            isHomeSuggestedFullScreenReel = homeSuggestedFullScreenReel
        )
    }

    private fun detectDmThread(root: AccessibilityNodeInfo, eventClass: String): Boolean {
        if (eventClass.contains("DirectThreadFragment", ignoreCase = true)) return true
        return subtreeMatches(root, maxDepth = 12, maxNodes = 160) { node ->
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val text = node.text?.toString().orEmpty().lowercase()
            id.contains("row_thread_composer") ||
                id.contains("direct_thread") ||
                text == "message..."
        }
    }

    private fun findFirstMatchingNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        while (queue.isNotEmpty() && visited < MAX_TRAVERSAL_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (predicate(node)) {
                while (queue.isNotEmpty()) {
                    val (remaining, _) = queue.removeFirst()
                    if (remaining !== root) safeRecycle(remaining)
                }
                return node
            }

            if (depth < MAX_TRAVERSAL_DEPTH) {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    queue.add(child to depth + 1)
                }
            }
            if (node !== root) safeRecycle(node)
        }
        return null
    }

    private fun subtreeMatches(
        root: AccessibilityNodeInfo,
        maxDepth: Int,
        maxNodes: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        while (queue.isNotEmpty() && visited < maxNodes) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (predicate(node)) {
                while (queue.isNotEmpty()) {
                    val (remaining, _) = queue.removeFirst()
                    if (remaining !== root) safeRecycle(remaining)
                }
                if (node !== root) safeRecycle(node)
                return true
            }

            if (depth < maxDepth) {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    queue.add(child to depth + 1)
                }
            }
            if (node !== root) safeRecycle(node)
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun safeRecycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { node.recycle() }
        }
    }
}
