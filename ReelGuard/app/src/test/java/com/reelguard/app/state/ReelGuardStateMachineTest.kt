package com.reelguard.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelGuardStateMachineTest {

    @Test
    fun dmSharedReel_keepsClickWindowAcrossTapEvent_thenAllowsOneReel() {
        val machine = ReelGuardStateMachine(dmClickWindowMs = 2_000L, dmMountSettleMs = 400L)

        machine.onScreenEvaluated(ScreenSignals(isInDmThread = true), nowMs = 1_000L)
        machine.onDmReelCardClicked(nowMs = 1_200L)

        // Instagram emits another content/click evaluation while the DM thread is still visible.
        val stillInThread = machine.onScreenEvaluated(
            ScreenSignals(isInDmThread = true),
            nowMs = 1_250L
        )
        assertTrue(stillInThread.isEmpty())
        assertEquals(GuardPhase.DM_THREAD, machine.snapshot.phase)
        assertEquals(3_200L, machine.snapshot.pendingDmReelClickUntilMs)

        val openEffects = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 1_500L
        )
        assertTrue(openEffects.isEmpty())
        assertEquals(GuardPhase.DM_SINGLE_REEL, machine.snapshot.phase)

        assertEquals(
            GuardEffect.None,
            machine.onVerticalScrollDetected(1_700L, 12, 0, 0)
        )
        assertEquals(
            GuardEffect.None,
            machine.onVerticalScrollDetected(2_000L, 24, 0, 0)
        )
        assertEquals(
            GuardEffect.ExecuteBackToDm,
            machine.onVerticalScrollDetected(2_100L, 380, 0, 1)
        )
        assertEquals(GuardPhase.DM_THREAD, machine.snapshot.phase)
    }

    @Test
    fun dmSharedReel_isAllowedEvenWhenInstagramMarksReelsTabSelected() {
        val machine = ReelGuardStateMachine(dmClickWindowMs = 2_000L)

        // A Reel card is explicitly opened from inside a direct-message thread.
        machine.onScreenEvaluated(ScreenSignals(isInDmThread = true), nowMs = 10_000L)
        machine.onDmReelCardClicked(nowMs = 10_100L)

        // Instagram may report the Reels tab as selected during the DM viewer transition.
        val openEffects = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true),
            nowMs = 10_350L
        )

        assertTrue(openEffects.isEmpty())
        assertEquals(GuardPhase.DM_SINGLE_REEL, machine.snapshot.phase)

        // Later tab-selection/content events must not turn this DM-origin Reel into a block.
        assertTrue(
            machine.onScreenEvaluated(
                ScreenSignals(isReelsTabSelected = true),
                nowMs = 10_500L
            ).isEmpty()
        )
        assertEquals(GuardPhase.DM_SINGLE_REEL, machine.snapshot.phase)
        assertEquals(
            GuardEffect.ExecuteBackToDm,
            machine.onVerticalScrollDetected(
                nowMs = 10_700L,
                scrollDeltaY = 360,
                fromIndex = 0,
                toIndex = 1
            )
        )
    }

    @Test
    fun algorithmicReel_showsClearIntervention_andCancelReturnsHome() {
        val machine = ReelGuardStateMachine()
        val effects = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true),
            nowMs = 5_000L
        )

        assertEquals(listOf(GuardEffect.ShowConfirmationOverlay), effects)
        assertEquals(GuardPhase.ALGORITHMIC_BLOCKED, machine.snapshot.phase)
        assertEquals(
            listOf(GuardEffect.DismissOverlay, GuardEffect.ExitReelsToHome),
            machine.onCancelTapped()
        )
        assertEquals(GuardPhase.EXITING_TO_HOME, machine.snapshot.phase)
        assertTrue(machine.onAppBackgrounded().isEmpty())
        assertEquals(GuardPhase.EXITING_TO_HOME, machine.snapshot.phase)
        assertTrue(
            machine.onScreenEvaluated(
                ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true),
                nowMs = 5_100L
            ).isEmpty()
        )
        assertEquals(GuardPhase.EXITING_TO_HOME, machine.snapshot.phase)
        assertEquals(
            listOf(GuardEffect.DismissOverlay),
            machine.onScreenEvaluated(ScreenSignals(), nowMs = 5_200L)
        )
        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)
    }

    @Test
    fun timedSession_survivesLeavingInstagram_andExpiresWithoutStaleBlock() {
        val machine = ReelGuardStateMachine()
        machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 1_000L)
        machine.onContinueAnywayTapped()
        assertEquals(
            listOf(GuardEffect.DismissOverlay, GuardEffect.StartCountdown(3)),
            machine.onTimeLimitSelected(3, 2_000L)
        )
        assertEquals(GuardPhase.TIMED_UNLOCKED, machine.snapshot.phase)
        assertTrue(machine.onAppBackgrounded().isEmpty())
        assertEquals(GuardPhase.TIMED_UNLOCKED, machine.snapshot.phase)
        assertTrue(
            machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 60_000L).isEmpty()
        )
        assertEquals(
            listOf(GuardEffect.ShowTimesUpBanner, GuardEffect.ExitReelsToHome),
            machine.onTimerExpired()
        )
        assertEquals(GuardPhase.EXITING_TO_HOME, machine.snapshot.phase)
        assertTrue(machine.onAppBackgrounded().isEmpty())
        assertEquals(GuardPhase.EXITING_TO_HOME, machine.snapshot.phase)
        assertTrue(
            machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 60_001L).isEmpty()
        )
        assertEquals(GuardPhase.EXITING_TO_HOME, machine.snapshot.phase)
    }

    @Test
    fun leavingInstagram_dismissesBlockingOverlay_andNextReelCanBeDetectedAgain() {
        val machine = ReelGuardStateMachine()
        machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 10_000L)
        assertEquals(GuardPhase.ALGORITHMIC_BLOCKED, machine.snapshot.phase)
        assertEquals(listOf(GuardEffect.DismissOverlay), machine.onAppBackgrounded())
        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)
        assertEquals(
            listOf(GuardEffect.ShowConfirmationOverlay),
            machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 20_000L)
        )
    }

    @Test
    fun leavingReels_dismissesAnOpenIntervention() {
        val machine = ReelGuardStateMachine()
        machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 1_000L)
        machine.onContinueAnywayTapped()
        assertEquals(
            listOf(GuardEffect.DismissOverlay),
            machine.onScreenEvaluated(ScreenSignals(), 1_500L)
        )
        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)
    }

    @Test
    fun restoredTimer_preservesRemainingSession() {
        val machine = ReelGuardStateMachine()
        machine.restoreTimedSession(120_000L, 10_000L)
        assertEquals(GuardPhase.TIMED_UNLOCKED, machine.snapshot.phase)
        assertEquals(130_000L, machine.snapshot.timerEndsAtMs)
        assertTrue(
            machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 11_000L).isEmpty()
        )
    }

    @Test
    fun expiredRestoredTimer_relocksWhenUserReturnsToReels() {
        val machine = ReelGuardStateMachine()
        machine.restoreTimedSession(1_000L, 10_000L)
        assertEquals(
            listOf(GuardEffect.ShowConfirmationOverlay),
            machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 11_100L)
        )
        assertEquals(GuardPhase.ALGORITHMIC_BLOCKED, machine.snapshot.phase)
    }

    @Test
    fun overlayUnavailable_resetsBlockedState_soPermissionCanRecover() {
        val machine = ReelGuardStateMachine()
        machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 1_000L)
        machine.onOverlayUnavailable()
        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)
        assertEquals(
            listOf(GuardEffect.ShowConfirmationOverlay),
            machine.onScreenEvaluated(ScreenSignals(isInReelsViewer = true), 2_000L)
        )
    }
}
