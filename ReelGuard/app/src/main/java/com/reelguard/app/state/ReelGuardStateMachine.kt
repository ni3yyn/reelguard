package com.reelguard.app.state

/**
 * Pure, deterministic state machine governing ReelGuard's detection and enforcement logic.
 * Decoupled from Android framework types so transitions can be unit tested directly.
 */
enum class GuardPhase {
    IDLE,
    DM_THREAD,
    DM_SINGLE_REEL,
    ALGORITHMIC_BLOCKED,
    TIME_LIMIT_SELECTION,
    TIMED_UNLOCKED
}

data class ScreenSignals(
    val isInDmThread: Boolean = false,
    val isInReelsViewer: Boolean = false,
    val isReelsTabSelected: Boolean = false,
    val isExploreOrStoryReel: Boolean = false,
    val isHomeSuggestedFullScreenReel: Boolean = false
)

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
    private val dmMountSettleMs: Long = 400L
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

    fun onScreenEvaluated(signals: ScreenSignals, nowMs: Long): List<GuardEffect> {
        val effects = mutableListOf<GuardEffect>()
        val timerActive = snapshot.phase == GuardPhase.TIMED_UNLOCKED && nowMs < snapshot.timerEndsAtMs

        if (snapshot.phase == GuardPhase.TIMED_UNLOCKED && nowMs >= snapshot.timerEndsAtMs) {
            snapshot = snapshot.copy(
                phase = GuardPhase.IDLE,
                timerEndsAtMs = 0L,
                selectedMinutes = 0
            )
        }

        // If user is already inside an active blocking overlay, keep it anchored unless they exited Reels.
        if (snapshot.phase == GuardPhase.ALGORITHMIC_BLOCKED || snapshot.phase == GuardPhase.TIME_LIMIT_SELECTION) {
            return effects
        }

        // Handle DM Thread presence
        if (signals.isInDmThread && !signals.isInReelsViewer) {
            snapshot = snapshot.copy(
                phase = GuardPhase.DM_THREAD,
                dmReelMountedAtMs = 0L
            )
            return effects
        }

        val anyReelsSurface = signals.isInReelsViewer ||
            signals.isReelsTabSelected ||
            signals.isExploreOrStoryReel ||
            signals.isHomeSuggestedFullScreenReel

        if (!anyReelsSurface) {
            if (snapshot.phase == GuardPhase.DM_SINGLE_REEL) {
                snapshot = snapshot.copy(
                    phase = GuardPhase.IDLE,
                    dmReelMountedAtMs = 0L,
                    pendingDmReelClickUntilMs = 0L
                )
            } else if (!timerActive && snapshot.phase == GuardPhase.DM_THREAD) {
                snapshot = snapshot.copy(phase = GuardPhase.IDLE)
            }
            return effects
        }

        // Inside a Reel viewer while already in DM_SINGLE_REEL -> remain unblocked
        if (snapshot.phase == GuardPhase.DM_SINGLE_REEL && signals.isInReelsViewer) {
            return effects
        }

        // State A: Opening a single DM-shared Reel within the click window
        val hasPendingDmClick = nowMs <= snapshot.pendingDmReelClickUntilMs
        if (signals.isInReelsViewer && hasPendingDmClick && !signals.isReelsTabSelected) {
            snapshot = snapshot.copy(
                phase = GuardPhase.DM_SINGLE_REEL,
                pendingDmReelClickUntilMs = 0L,
                dmReelMountedAtMs = nowMs
            )
            return effects
        }

        // State C active timer: allow unrestricted Reels browsing until timer hits 00:00
        if (timerActive) {
            return effects
        }

        // State B: Algorithmic / Non-DM Reel without active timer -> immediately block
        snapshot = snapshot.copy(
            phase = GuardPhase.ALGORITHMIC_BLOCKED,
            pendingDmReelClickUntilMs = 0L,
            dmReelMountedAtMs = 0L
        )
        effects += GuardEffect.ShowConfirmationOverlay
        return effects
    }

    fun onVerticalScrollDetected(
        nowMs: Long,
        scrollDeltaY: Int,
        fromIndex: Int,
        toIndex: Int
    ): GuardEffect {
        if (snapshot.phase != GuardPhase.DM_SINGLE_REEL) {
            return GuardEffect.None
        }

        // Ignore initial layout/inflation scroll events during the first 400ms of player mount
        if (nowMs - snapshot.dmReelMountedAtMs < dmMountSettleMs) {
            return GuardEffect.None
        }

        val indexChanged = fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex
        val verticalDelta = scrollDeltaY != 0

        if (indexChanged || verticalDelta) {
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
        if (snapshot.phase != GuardPhase.ALGORITHMIC_BLOCKED) {
            return GuardEffect.None
        }
        snapshot = snapshot.copy(phase = GuardPhase.TIME_LIMIT_SELECTION)
        return GuardEffect.ShowTimeLimitOverlay
    }

    fun onCancelTapped(): List<GuardEffect> {
        snapshot = snapshot.copy(
            phase = GuardPhase.IDLE,
            pendingDmReelClickUntilMs = 0L,
            dmReelMountedAtMs = 0L
        )
        return listOf(GuardEffect.DismissOverlay, GuardEffect.ExitReelsToHome)
    }

    fun onTimeLimitSelected(minutes: Int, nowMs: Long): List<GuardEffect> {
        val clamped = minutes.coerceIn(1, 10)
        snapshot = snapshot.copy(
            phase = GuardPhase.TIMED_UNLOCKED,
            selectedMinutes = clamped,
            timerEndsAtMs = nowMs + clamped * 60_000L
        )
        return listOf(
            GuardEffect.DismissOverlay,
            GuardEffect.StartCountdown(clamped)
        )
    }

    fun onTimerExpired(): List<GuardEffect> {
        snapshot = snapshot.copy(
            phase = GuardPhase.IDLE,
            timerEndsAtMs = 0L,
            selectedMinutes = 0,
            pendingDmReelClickUntilMs = 0L,
            dmReelMountedAtMs = 0L
        )
        return listOf(
            GuardEffect.ShowTimesUpBanner,
            GuardEffect.ExitReelsToHome
        )
    }
}
