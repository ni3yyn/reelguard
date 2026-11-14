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
import com.reelguard.app.data.DebugLogRepository
import com.reelguard.app.overlay.OverlayManager
import com.reelguard.app.state.GuardEffect
import com.reelguard.app.state.ReelGuardStateMachine
import com.reelguard.app.state.ScreenSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ReelGuardAccessibilityService : AccessibilityService() {

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val MAX_TRAVERSAL_DEPTH = 16
        private const val MAX_TRAVERSAL_NODES = 240
        private const val MAX_SNAPSHOT_DEPTH = 24
        private const val MAX_SNAPSHOT_NODES = 600
    }

    private val stateMachine = ReelGuardStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayManager: OverlayManager? = null
    private var activeTimer: CountDownTimer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        DebugLogRepository.init(this)
        overlayManager = OverlayManager(this)
        stateMachine.reset()

        serviceScope.launch {
            combine(
                DebugLogRepository.isDebugMode,
                DebugLogRepository.isReelGuardInForeground
            ) { debugEnabled, inReelGuard ->
                debugEnabled to inReelGuard
            }.collect { (debugEnabled, inReelGuard) ->
                if (debugEnabled) {
                    // Suspend any blocking overlays while Debug Inspector Mode is active
                    cancelActiveTimer()
                    stateMachine.reset()
                    overlayManager?.dismiss()

                    val instagramActive = !inReelGuard &&
                        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() == INSTAGRAM_PACKAGE
                    if (instagramActive && Settings.canDrawOverlays(this@ReelGuardAccessibilityService)) {
                        overlayManager?.showSnapUiFloatingButton { captureFullUiSnapshot() }
                    } else {
                        overlayManager?.hideSnapUiFloatingButton()
                    }
                } else {
                    overlayManager?.hideSnapUiFloatingButton()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != INSTAGRAM_PACKAGE) return

        DebugLogRepository.setReelGuardInForeground(false)

        val nowMs = SystemClock.elapsedRealtime()
        val eventType = event.eventType
        val debugMode = DebugLogRepository.isDebugMode.value

        if (debugMode) {
            if (Settings.canDrawOverlays(this)) {
                overlayManager?.showSnapUiFloatingButton { captureFullUiSnapshot() }
            }
            captureDebugEventLog(event)
            // Temporarily suspend blocking overlays and forced swipe-back while inspecting Instagram screens
            return
        }

        // 1. Detect clicks on DM-shared Reel preview cards while inside a DM thread
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (isClickOnDmSharedReel(event)) {
                stateMachine.onDmReelCardClicked(nowMs)
            }
        }

        // 2. Intercept vertical swipe inside State A (DM-Shared Single Reel Mode)
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val className = event.className?.toString().orEmpty()
            val isVerticalPagerOrList =
                className.contains("ViewPager", ignoreCase = true) ||
                    className.contains("RecyclerView", ignoreCase = true) ||
                    className.contains("AbsListView", ignoreCase = true)

            if (isVerticalPagerOrList) {
                val deltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    event.scrollDeltaY
                } else {
                    0
                }
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

        // 3. Evaluate active AccessibilityNodeInfo window tree on state/content/click changes
        if (
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
            val signals = inspectScreenSignals(root, event.className?.toString().orEmpty())
            val effects = stateMachine.onScreenEvaluated(signals, nowMs)
            effects.forEach { executeEffect(it) }
        }
    }

    override fun onInterrupt() {
        overlayManager?.dismissAll()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        cancelActiveTimer()
        overlayManager?.dismissAll()
        overlayManager = null
        super.onDestroy()
    }

    private fun captureFullUiSnapshot() {
        val root = runCatching { rootInActiveWindow }.getOrNull()
        if (root == null) {
            overlayManager?.showToast("No active window available")
            return
        }

        val lines = mutableListOf<String>()
        buildVisibleNodeHierarchy(root, depth = 0, lines = lines, visitedCounter = intArrayOf(0))

        val formattedTree = if (lines.isEmpty()) {
            "[0] Root | id: null | desc: null | text: (no visible labeled nodes)"
        } else {
            lines.joinToString(separator = "\n")
        }

        DebugLogRepository.recordEvent(
            eventType = "UI_SNAPSHOT",
            phase = "DEBUG_INSPECTOR",
            summary = "Manual Snap UI capture (${lines.size} visible nodes)",
            nodeTreeText = formattedTree
        )
        overlayManager?.showToast("UI Snapshot Captured")
    }

    private fun captureDebugEventLog(event: AccessibilityEvent) {
        val typeLabel = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            else -> return // Group captured node trees by the three primary interaction event types
        }

        val source = runCatching { event.source }.getOrNull()
        val sourceId = source?.viewIdResourceName
        val sourceDesc = source?.contentDescription?.toString()
        val sourceText = source?.text?.toString()
        val eventClass = event.className?.toString()?.substringAfterLast('.') ?: "Unknown"
        if (source != null) safeRecycle(source)

        val root = runCatching { rootInActiveWindow }.getOrNull()
        val lines = mutableListOf<String>()
        if (root != null) {
            buildVisibleNodeHierarchy(root, depth = 0, lines = lines, visitedCounter = intArrayOf(0))
        }

        val summary = "src=$eventClass | id=$sourceId | desc=$sourceDesc | text=$sourceText"
        DebugLogRepository.recordEvent(
            eventType = typeLabel,
            phase = "DEBUG_INSPECTOR",
            summary = summary,
            nodeTreeText = lines.take(40).joinToString(separator = "\n")
        )
    }

    private fun buildVisibleNodeHierarchy(
        node: AccessibilityNodeInfo,
        depth: Int,
        lines: MutableList<String>,
        visitedCounter: IntArray
    ) {
        if (depth > MAX_SNAPSHOT_DEPTH || visitedCounter[0] >= MAX_SNAPSHOT_NODES) return
        visitedCounter[0]++

        val visible = runCatching { node.isVisibleToUser }.getOrDefault(true)
        val viewId = node.viewIdResourceName
        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()

        val hasMetadata = !viewId.isNullOrBlank() || !desc.isNullOrBlank() || !text.isNullOrBlank()
        if (visible && hasMetadata) {
            val indent = "  ".repeat(depth)
            val simpleClass = node.className?.toString()?.substringAfterLast('.') ?: "View"
            val cleanDesc = desc?.replace("\n", " ")
            val cleanText = text?.replace("\n", " ")
            lines += "$indent[$depth] $simpleClass | id: $viewId | desc: $cleanDesc | text: $cleanText"
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (visitedCounter[0] >= MAX_SNAPSHOT_NODES) break
            val child = node.getChild(i) ?: continue
            buildVisibleNodeHierarchy(child, depth + 1, lines, visitedCounter)
            safeRecycle(child)
        }
    }

    private fun executeEffect(effect: GuardEffect) {
        when (effect) {
            GuardEffect.None -> Unit
            GuardEffect.ShowConfirmationOverlay -> {
                if (!Settings.canDrawOverlays(this)) return
                overlayManager?.showConfirmationOverlay(
                    onContinueAnyway = {
                        val next = stateMachine.onContinueAnywayTapped()
                        executeEffect(next)
                    },
                    onCancel = {
                        stateMachine.onCancelTapped().forEach { executeEffect(it) }
                    },
                    onSelectMinutes = { minutes ->
                        val now = SystemClock.elapsedRealtime()
                        stateMachine.onTimeLimitSelected(minutes, now).forEach { executeEffect(it) }
                    }
                )
            }
            GuardEffect.ShowTimeLimitOverlay -> {
                overlayManager?.switchToTimeLimitOverlay()
            }
            GuardEffect.DismissOverlay -> {
                overlayManager?.dismiss()
            }
            GuardEffect.ExecuteBackToDm -> {
                mainHandler.post {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            GuardEffect.ExitReelsToHome -> {
                exitReelsBackToHome()
            }
            is GuardEffect.StartCountdown -> {
                startStrictCountdown(effect.minutes)
            }
            GuardEffect.ShowTimesUpBanner -> {
                overlayManager?.showToast("Time's up — Reels locked")
            }
        }
    }

    private fun startStrictCountdown(minutes: Int) {
        cancelActiveTimer()
        val durationMs = minutes * 60_000L
        mainHandler.post {
            activeTimer = object : CountDownTimer(durationMs, 1_000L) {
                override fun onTick(millisUntilFinished: Long) = Unit

                override fun onFinish() {
                    activeTimer = null
                    stateMachine.onTimerExpired().forEach { executeEffect(it) }
                }
            }.start()
        }
    }

    private fun cancelActiveTimer() {
        activeTimer?.cancel()
        activeTimer = null
    }

    private fun exitReelsBackToHome() {
        mainHandler.post {
            val clickedHome = tryClickHomeTab()
            if (!clickedHome) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun tryClickHomeTab(): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val homeNode = findFirstMatchingNode(root) { node ->
            val id = node.viewIdResourceName.orEmpty().lowercase()
            val desc = node.contentDescription?.toString().orEmpty().lowercase()
            val isHomeId = id.endsWith(":id/feed_tab") || id.contains("home_tab")
            val isHomeDesc = desc == "home" || desc.startsWith("home,")
            node.isClickable && (isHomeId || isHomeDesc)
        } ?: return false

        val performed = homeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        safeRecycle(homeNode)
        return performed
    }

    private fun isClickOnDmSharedReel(event: AccessibilityEvent): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull()
        val inDmContext = root != null && detectDmThread(root, event.className?.toString().orEmpty())
        if (!inDmContext) return false

        val source = runCatching { event.source }.getOrNull()
        if (source != null) {
            val matched = subtreeMatches(source, maxDepth = 5, maxNodes = 36) { node ->
                val id = node.viewIdResourceName.orEmpty().lowercase()
                val desc = node.contentDescription?.toString().orEmpty().lowercase()
                val txt = node.text?.toString().orEmpty().lowercase()
                id.contains("direct_reel") ||
                    id.contains("reel_share") ||
                    id.contains("clips_share") ||
                    id.contains("media_share") ||
                    desc.contains("reel") ||
                    desc.contains("shared video") ||
                    txt.contains("watch reel")
            }
            safeRecycle(source)
            if (matched) return true
        }

        val eventText = event.text.joinToString(" ").lowercase()
        val eventDesc = event.contentDescription?.toString().orEmpty().lowercase()
        return eventDesc.contains("reel") || eventText.contains("reel")
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
            val desc = node.contentDescription?.toString().orEmpty().lowercase()
            val txt = node.text?.toString().orEmpty().lowercase()

            if (
                id.contains("row_thread_composer") ||
                id.contains("direct_thread") ||
                id.contains("message_list") ||
                desc.contains("message...") ||
                txt == "message..."
            ) {
                inDmThread = true
            }

            if (
                (id.endsWith(":id/clips_tab") || id.contains("reels_tab") || desc == "reels" || desc.startsWith("reels,")) &&
                (node.isSelected || desc.contains("selected"))
            ) {
                reelsTabSelected = true
            }

            if (
                id.contains("clips_viewer") ||
                id.contains("clips_video_container") ||
                id.contains("clips_author_username") ||
                id.contains("clips_ufi_container") ||
                desc.contains("reels video") ||
                desc.contains("original audio")
            ) {
                inReelsViewer = true
            }

            if (
                (id.contains("explore") && id.contains("clips")) ||
                (id.contains("story_viewer") && (desc.contains("reel") || txt.contains("watch full reel")))
            ) {
                exploreOrStoryReel = true
            }

            if (id.contains("feed_reels_viewer") || id.contains("suggested_clips_viewer")) {
                homeSuggestedFullScreenReel = true
            }

            if (depth < MAX_TRAVERSAL_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.add(child to depth + 1)
                }
            }

            if (node !== root) {
                safeRecycle(node)
            }
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
            val txt = node.text?.toString().orEmpty().lowercase()
            id.contains("row_thread_composer") ||
                id.contains("direct_thread") ||
                txt == "message..."
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
                    val (rem, _) = queue.removeFirst()
                    if (rem !== root) safeRecycle(rem)
                }
                return node
            }
            if (depth < MAX_TRAVERSAL_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
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
                    val (rem, _) = queue.removeFirst()
                    if (rem !== root) safeRecycle(rem)
                }
                if (node !== root) safeRecycle(node)
                return true
            }
            if (depth < maxDepth) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
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
