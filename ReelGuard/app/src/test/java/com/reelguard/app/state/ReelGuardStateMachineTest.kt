package com.reelguard.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelGuardStateMachineTest {

    @Test
    fun dmSharedReel_allowsPlayback_andReturnsToDmAfterMeaningfulSwipe() {
        val machine = ReelGuardStateMachine(dmClickWindowMs = 2_000L, dmMountSettleMs = 400L)

        machine.onScreenEvaluated(ScreenSignals(isInDmThread = true), nowMs = 1_000L)
        machine.onDmReelCardClicked(nowMs = 1_200L)

        val openEffects = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 1_500L
        )
        assertTrue(openEffects.isEmpty())
        assertEquals(GuardPhase.DM_SINGLE_REEL, machine.snapshot.phase)

        val settleEffect = machine.onVerticalScrollDetected(
            nowMs = 1_700L,
            scrollDeltaY = 12,
            fromIndex = 0,
            toIndex = 0
        )
        assertEquals(GuardEffect.None, settleEffect)

        val tinyScroll = machine.onVerticalScrollDetected(
            nowMs = 2_000L,
            scrollDeltaY = 24,
            fromIndex = 0,
            toIndex = 0
        )
        assertEquals(GuardEffect.None, tinyScroll)

        val nextReel = machine.onVerticalScrollDetected(
            nowMs = 2_100L,
            scrollDeltaY = 380,
            fromIndex = 0,
            toIndex = 1
        )
        assertEquals(GuardEffect.ExecuteBackToDm, nextReel)
        assertEquals(GuardPhase.DM_THREAD, machine.snapshot.phase)
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

        val cancelEffects = machine.onCancelTapped()
        assertEquals(
            listOf(GuardEffect.DismissOverlay, GuardEffect.ExitReelsToHome),
            cancelEffects
        )
        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)
    }

    @Test
    fun timedSession_survivesLeavingInstagram_andExpiresWithoutStaleBlock() {
        val machine = ReelGuardStateMachine()
        machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true),
            nowMs = 1_000L
        )
        machine.onContinueAnywayTapped()
        val startEffects = machine.onTimeLimitSelected(minutes = 3, nowMs = 2_000L)

        assertEquals(
            listOf(GuardEffect.DismissOverlay, GuardEffect.StartCountdown(3)),
            startEffects
        )
        assertEquals(GuardPhase.TIMED_UNLOCKED, machine.snapshot.phase)

        assertTrue(machine.onAppBackgrounded().isEmpty())
        assertEquals(GuardPhase.TIMED_UNLOCKED, machine.snapshot.phase)

        val stillAllowed = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true),
            nowMs = 60_000L
        )
        assertTrue(stillAllowed.isEmpty())

        val expired = machine.onTimerExpired()
        assertEquals(
            listOf(GuardEffect.ShowTimesUpBanner, GuardEffect.ExitReelsToHome),
            expired
        )
        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)
    }

    @Test
    fun leavingInstagram_dismissesBlockingOverlay_andNextReelCanBeDetectedAgain() {
        val machine = ReelGuardStateMachine()
        machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 10_000L
        )
        assertEquals(GuardPhase.ALGORITHMIC_BLOCKED, machine.snapshot.phase)

        assertEquals(
            listOf(GuardEffect.DismissOverlay),
            machine.onAppBackgrounded()
        )
        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)

        val backInReels = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 20_000L
        )
        assertEquals(listOf(GuardEffect.ShowConfirmationOverlay), backInReels)
    }

    @Test
    fun leavingReels_dismissesAnOpenIntervention() {
        val machine = ReelGuardStateMachine()
        machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 1_000L
        )
        machine.onContinueAnywayTapped()

        val effects = machine.onScreenEvaluated(
            ScreenSignals(),
            nowMs = 1_500L
        )
        assertEquals(listOf(GuardEffect.DismissOverlay), effects)
        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)
    }

    @Test
    fun restoredTimer_preservesRemainingSession() {
        val machine = ReelGuardStateMachine()
        machine.restoreTimedSession(120_000L, nowMs = 10_000L)

        assertEquals(GuardPhase.TIMED_UNLOCKED, machine.snapshot.phase)
        assertEquals(130_000L, machine.snapshot.timerEndsAtMs)

        val effects = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 11_000L
        )
        assertTrue(effects.isEmpty())
    }

    @Test
    fun timerExpiredWhileOutsideSession_doesNotLeaveMachineUnlocked() {
        val machine = ReelGuardStateMachine()
        machine.restoreTimedSession(1_000L, nowMs = 10_000L)

        val effects = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 11_100L
        )

        assertEquals(listOf(GuardEffect.ShowConfirmationOverlay), effects)
        assertEquals(GuardPhase.ALGORITHMIC_BLOCKED, machine.snapshot.phase)
    }

    @Test
    fun overlayUnavailable_resetsBlockedState_soPermissionCanRecover() {
        val machine = ReelGuardStateMachine()
        machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 1_000L
        )
        machine.onOverlayUnavailable()

        assertEquals(GuardPhase.IDLE, machine.snapshot.phase)

        val retry = machine.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 2_000L
        )
        assertEquals(listOf(GuardEffect.ShowConfirmationOverlay), retry)
    }
}
