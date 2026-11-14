package com.reelguard.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelGuardStateMachineTest {

    @Test
    fun dmSharedReel_allowsPlayback_andInterceptsSwipeAfterSettle() {
        val sm = ReelGuardStateMachine(dmClickWindowMs = 2_000L, dmMountSettleMs = 400L)

        sm.onScreenEvaluated(ScreenSignals(isInDmThread = true), nowMs = 1_000L)
        sm.onDmReelCardClicked(nowMs = 1_200L)

        val openEffects = sm.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true),
            nowMs = 1_500L
        )
        assertTrue(openEffects.isEmpty())
        assertEquals(GuardPhase.DM_SINGLE_REEL, sm.snapshot.phase)

        // During initial 400ms mount settle window, layout scroll is ignored
        val settleEffect = sm.onVerticalScrollDetected(nowMs = 1_700L, scrollDeltaY = 12, fromIndex = 0, toIndex = 0)
        assertEquals(GuardEffect.None, settleEffect)

        // Swiping to next Reel after settle window triggers GLOBAL_ACTION_BACK and resets DM Reel state
        val swipeEffect = sm.onVerticalScrollDetected(nowMs = 2_100L, scrollDeltaY = 380, fromIndex = 0, toIndex = 1)
        assertEquals(GuardEffect.ExecuteBackToDm, swipeEffect)
        assertEquals(GuardPhase.DM_THREAD, sm.snapshot.phase)
    }

    @Test
    fun algorithmicReel_showsConfirmationOverlay_andCancelExitsToHome() {
        val sm = ReelGuardStateMachine()
        val effects = sm.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true),
            nowMs = 5_000L
        )
        assertEquals(listOf(GuardEffect.ShowConfirmationOverlay), effects)
        assertEquals(GuardPhase.ALGORITHMIC_BLOCKED, sm.snapshot.phase)

        val cancelEffects = sm.onCancelTapped()
        assertEquals(listOf(GuardEffect.DismissOverlay, GuardEffect.ExitReelsToHome), cancelEffects)
        assertEquals(GuardPhase.IDLE, sm.snapshot.phase)
    }

    @Test
    fun continueAnyway_toTimeLimit_unlocksUntilExpiration_thenRelocks() {
        val sm = ReelGuardStateMachine()
        sm.onScreenEvaluated(ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true), nowMs = 1_000L)

        val step2 = sm.onContinueAnywayTapped()
        assertEquals(GuardEffect.ShowTimeLimitOverlay, step2)
        assertEquals(GuardPhase.TIME_LIMIT_SELECTION, sm.snapshot.phase)

        val startEffects = sm.onTimeLimitSelected(minutes = 3, nowMs = 2_000L)
        assertEquals(listOf(GuardEffect.DismissOverlay, GuardEffect.StartCountdown(3)), startEffects)
        assertEquals(GuardPhase.TIMED_UNLOCKED, sm.snapshot.phase)

        // Re-evaluating Reels viewer while timer active stays unlocked
        val whileUnlocked = sm.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true),
            nowMs = 60_000L
        )
        assertTrue(whileUnlocked.isEmpty())

        // Expiration locks and forces exit to Home
        val expireEffects = sm.onTimerExpired()
        assertEquals(listOf(GuardEffect.ShowTimesUpBanner, GuardEffect.ExitReelsToHome), expireEffects)
        assertEquals(GuardPhase.IDLE, sm.snapshot.phase)

        // Immediate re-entry triggers "Are you sure?" again
        val reEntry = sm.onScreenEvaluated(
            ScreenSignals(isInReelsViewer = true, isReelsTabSelected = true),
            nowMs = 183_000L
        )
        assertEquals(listOf(GuardEffect.ShowConfirmationOverlay), reEntry)
    }
}
