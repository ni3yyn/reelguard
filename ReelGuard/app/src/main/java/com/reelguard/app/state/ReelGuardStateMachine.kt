package com.reelguard.app.state

enum class GuardPhase {
    IDLE,
    DM_THREAD,
    DM_SINGLE_REEL,
    ALGORITHMIC_BLOCKED,
    TIME_LIMIT_SELECTION,
    EXITING_TO_HOME,
    TIMED_UNLOCKED
}

data class ScreenSignals(
    val isInDmThread: Boolean = false,
    val isInReelsViewer: Boolean = false,
    val isReelsTabSelected: Boolean = false,
    val isExploreOrStoryReel: Boolean = false,
    val isHomeSuggestedFullScreenReel: Boolean = false,
    val isDmOriginReel: Boolean = false
) {
    val isReelsSurface: Boolean
        get() = isInReelsViewer ||
            isReelsTabSelected ||
            isExploreOrStoryReel ||
            isHomeSuggestedFullScreenReel
}

sealed interface GuardEffect {
    data object None : GuardEffect
    data object ShowConfirmationOverlay : GuardEffect
    data object ShowTimeLimitOverlay : GuardEffect
    data object DismissOverlay : GuardEffect
    data object ExecuteBackToDm : GuardEffect
    data object ExitReelsToHome : GuardEffect
    data class StartCountdown(val minutes: Int) : GuardEffect
    data object ShowTimesUpBanner : GuardEffect
}

data class GuardSnapshot(
    val phase: GuardPhase = GuardPhase.IDLE,
    val pendingDmReelClickUntilMs: Long = 0L,
    val dmReelMountedAtMs: Long = 0L,
    val timerEndsAtMs: Long = 0L,
    val selectedMinutes: Int = 0
)

class ReelGuardStateMachine(
    private val dmClickWindowMs: Long = 2_000L,
    private val dmMountSettleMs: Long = 400L,
    private val nextReelScrollThresholdPx: Int = 80
) {
    var snapshot: GuardSnapshot = GuardSnapshot()
        private set

    fun reset() {
        snapshot = GuardSnapshot()
    }

    fun onDmReelCardClicked(nowMs: Long): GuardEffect {
        snapshot = snapshot.copy(
            phase = GuardPhase.DM_THREAD,
            pendingDmReelClickUntilMs = nowMs + dmClickWindowMs
        )
        return GuardEffect.None
    }

    fun restoreTimedSession(remainingMs: Long, nowMs: Long) {
        if (remainingMs <= 0L) {
            snapshot = GuardSnapshot()
            return
        }
        snapshot = GuardSnapshot(
            phase = GuardPhase.TIMED_UNLOCKED,
            timerEndsAtMs = nowMs + remainingMs,
            selectedMinutes = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        )
    }

    fun onAppBackgrounded(): List<GuardEffect> {
        val hadBlockingOverlay =
            snapshot.phase == GuardPhase.ALGORITHMIC_BLOCKED ||
                snapshot.phase == GuardPhase.TIME_LIMIT_SELECTION

        snapshot = when (snapshot.phase) {
            GuardPhase.TIMED_UNLOCKED -> snapshot.copy(
                pendingDmReelClickUntilMs = 0L,
                dmReelMountedAtMs = 0L
            )
            GuardPhase.EXITING_TO_HOME -> snapshot
            else -> GuardSnapshot()
        }

        return if (hadBlockingOverlay) listOf(GuardEffect.DismissOverlay) else emptyList()
    }

    fun onOverlayUnavailable() {
        snapshot = if (snapshot.phase == GuardPhase.TIMED_UNLOCKED) {
            snapshot
        } else {
            GuardSnapshot()
        }
    }

    fun onScreenEvaluated(signals: ScreenSignals, nowMs: Long): List<GuardEffect> {
        val effects = mutableListOf<GuardEffect>()

        // Keep the intervention dismissed while the explicit Home navigation is in flight.
        // Instagram can emit several more Reel events before its navigation finishes.
        if (snapshot.phase == GuardPhase.EXITING_TO_HOME) {
            if (!signals.isReelsSurface) {
                snapshot = GuardSnapshot()
                effects += GuardEffect.DismissOverlay
            }
            return effects
        }

        // This is stronger than tab selection or a tap event: Instagram's DM viewer
        // retains a hidden direct_thread_toolbar beside its clips viewer container.
        // Trust that route provenance even when clips_tab is also reported selected.
        if (signals.isDmOriginReel && signals.isInReelsViewer) {
            val hadBlockingOverlay =
                snapshot.phase == GuardPhase.ALGORITHMIC_BLOCKED ||
                    snapshot.phase == GuardPhase.TIME_LIMIT_SELECTION
            val mountedAtMs = if (
                snapshot.phase == GuardPhase.DM_SINGLE_REEL && snapshot.dmReelMountedAtMs > 0L
            ) snapshot.dmReelMountedAtMs else nowMs

            snapshot = snapshot.copy(
                phase = GuardPhase.DM_SINGLE_REEL,
                pendingDmReelClickUntilMs = 0L,
                dmReelMountedAtMs = mountedAtMs
            )
            if (hadBlockingOverlay) effects += GuardEffect.DismissOverlay
            return effects
        }

        val wasBlocking = snapshot.phase == GuardPhase.ALGORITHMIC_BLOCKED ||
            snapshot.phase == GuardPhase.TIME_LIMIT_SELECTION
        val timerActive = snapshot.phase == GuardPhase.TIMED_UNLOCKED &&
            nowMs < snapshot.timerEndsAtMs

        if (snapshot.phase == GuardPhase.TIMED_UNLOCKED && !timerActive) {
            snapshot = GuardSnapshot()
        }

        // Preserve the DM click window while the clicked card is still in the thread.
        // Instagram often emits the click event before mounting the Reel player.
        if (
            signals.isInDmThread &&
            !signals.isInReelsViewer &&
            snapshot.phase != GuardPhase.ALGORITHMIC_BLOCKED &&
            snapshot.phase != GuardPhase.TIME_LIMIT_SELECTION &&
            snapshot.phase != GuardPhase.DM_SINGLE_REEL
        ) {
            snapshot = snapshot.copy(
                phase = GuardPhase.DM_THREAD,
                dmReelMountedAtMs = 0L
            )
            return effects
        }

        if (!signals.isReelsSurface) {
            when (snapshot.phase) {
                GuardPhase.ALGORITHMIC_BLOCKED, GuardPhase.TIME_LIMIT_SELECTION -> {
                    snapshot = GuardSnapshot()
                    effects += GuardEffect.DismissOverlay
                }
                GuardPhase.DM_SINGLE_REEL -> snapshot = GuardSnapshot()
                GuardPhase.DM_THREAD -> snapshot = GuardSnapshot()
                GuardPhase.TIMED_UNLOCKED -> Unit
                GuardPhase.EXITING_TO_HOME -> Unit
                GuardPhase.IDLE -> Unit
            }
            return effects
        }

        if (snapshot.phase == GuardPhase.ALGORITHMIC_BLOCKED ||
            snapshot.phase == GuardPhase.TIME_LIMIT_SELECTION
        ) return effects

        // The Reels tab can remain marked selected even when a Reel was opened from a DM.
        // A verified tap on a Reel inside a DM is the stronger signal: honor it for the
        // entire entry transition instead of letting Instagram's selected tab override it.
        if (snapshot.phase == GuardPhase.DM_SINGLE_REEL && signals.isReelsSurface) {
            return effects
        }

        val hasPendingDmClick = nowMs <= snapshot.pendingDmReelClickUntilMs
        if (signals.isReelsSurface && hasPendingDmClick) {
            snapshot = snapshot.copy(
                phase = GuardPhase.DM_SINGLE_REEL,
                pendingDmReelClickUntilMs = 0L,
                dmReelMountedAtMs = nowMs
            )
            return effects
        }

        if (timerActive) return effects

        snapshot = snapshot.copy(
            phase = GuardPhase.ALGORITHMIC_BLOCKED,
            pendingDmReelClickUntilMs = 0L,
            dmReelMountedAtMs = 0L,
            timerEndsAtMs = 0L,
            selectedMinutes = 0
        )
        if (!wasBlocking) effects += GuardEffect.ShowConfirmationOverlay
        return effects
    }

    fun onVerticalScrollDetected(
        nowMs: Long,
        scrollDeltaY: Int,
        fromIndex: Int,
        toIndex: Int
    ): GuardEffect {
        if (snapshot.phase != GuardPhase.DM_SINGLE_REEL) return GuardEffect.None
        if (nowMs - snapshot.dmReelMountedAtMs < dmMountSettleMs) return GuardEffect.None

        val indexChanged = fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex
        val meaningfulVerticalSwipe = kotlin.math.abs(scrollDeltaY) >= nextReelScrollThresholdPx

        if (indexChanged || meaningfulVerticalSwipe) {
            snapshot = snapshot.copy(
                phase = GuardPhase.DM_THREAD,
                dmReelMountedAtMs = 0L,
                pendingDmReelClickUntilMs = 0L
            )
            return GuardEffect.ExecuteBackToDm
        }
        return GuardEffect.None
    }

    fun onContinueAnywayTapped(): GuardEffect {
        if (snapshot.phase != GuardPhase.ALGORITHMIC_BLOCKED) return GuardEffect.None
        snapshot = snapshot.copy(phase = GuardPhase.TIME_LIMIT_SELECTION)
        return GuardEffect.ShowTimeLimitOverlay
    }

    fun onCancelTapped(): List<GuardEffect> {
        snapshot = GuardSnapshot(phase = GuardPhase.EXITING_TO_HOME)
        return listOf(GuardEffect.DismissOverlay, GuardEffect.ExitReelsToHome)
    }

    fun onTimeLimitSelected(minutes: Int, nowMs: Long): List<GuardEffect> {
        if (snapshot.phase != GuardPhase.TIME_LIMIT_SELECTION) return emptyList()
        val safeMinutes = minutes.coerceIn(1, 30)
        snapshot = snapshot.copy(
            phase = GuardPhase.TIMED_UNLOCKED,
            selectedMinutes = safeMinutes,
            timerEndsAtMs = nowMs + safeMinutes * 60_000L
        )
        return listOf(
            GuardEffect.DismissOverlay,
            GuardEffect.StartCountdown(safeMinutes)
        )
    }

    fun onTimerExpired(): List<GuardEffect> {
        snapshot = GuardSnapshot(phase = GuardPhase.EXITING_TO_HOME)
        return listOf(GuardEffect.ShowTimesUpBanner, GuardEffect.ExitReelsToHome)
    }
}
