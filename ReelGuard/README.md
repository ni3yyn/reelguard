# ReelGuard (Native Android — Kotlin + Jetpack Compose)

Mindful Instagram Reels blocker built with `AccessibilityService` and `SYSTEM_ALERT_WINDOW`.

## State Machine Overview

- **State A (DM-Shared Single Reel)**: Clicking a shared Reel inside an Instagram DM thread (`DirectThreadFragment` / `row_thread_composer`) opens the single Reel player with zero interstitial. Any vertical swipe (`TYPE_VIEW_SCROLLED`) inside DM Reel playback immediately executes `performGlobalAction(GLOBAL_ACTION_BACK)` to return to the DM chat and reset state.
- **State B (Algorithmic / Non-DM Reels)**: Entering the Reels tab, Explore/Search Reel, Story Reel, or full-screen Home suggested Reel without an active timer raises a full-screen non-dismissible `TYPE_APPLICATION_OVERLAY` ("Are you sure?"). Tapping **Cancel** dismisses the overlay and returns to Home.
- **State C (Conscious Time Limit)**: Tapping **Continue anyway** shows the 1–10 min limit grid. Selecting a duration starts a strict `CountDownTimer` (`MM:SS`). On `00:00`, Reel access locks immediately, displays "Time's up", and forcibly exits back to Instagram Home.

## Building & Installing

1. Open the `android/` directory in **Android Studio** (Ladybug or newer, JDK 17).
2. Connect your phone via USB Debugging or build a release/debug APK (`./gradlew assembleDebug`).
3. **Samsung Galaxy S21 (One UI / Android 13–14+) Sideload Setup**:
   - After installing the APK, open **Settings > Apps > ReelGuard**.
   - Tap the **⋮** (three dots) in the top-right corner and select **Allow restricted settings**.
   - Return to **ReelGuard** and enable **1. Accessibility Service** and **2. Display Over Other Apps**.
   - Open **Settings > Apps > ReelGuard > Battery** and set it to **Unrestricted** so One UI does not kill the service in the background.
4. **Refining Instagram Selectors (Debug Inspector Mode)**:
   - Enable **Debug Inspector Mode** inside ReelGuard's `MainActivity` to temporarily suspend blocking overlays and show a draggable **`Snap UI`** floating pill in the top-right corner of Instagram.
   - Navigate freely across DM Reels, Home Feed Reels, Explore Reels, and Stories, tapping **Snap UI** to capture the indented `AccessibilityNodeInfo` hierarchy into `DebugLogRepository`.
   - Return to ReelGuard's **View Captured Logs** section and tap **Copy Logs to Clipboard** to export the last 100 entries.
