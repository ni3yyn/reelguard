# ReelGuard

ReelGuard is a native Android mindful-use companion for Instagram Reels.

## What the user experience should be

1. Open ReelGuard and review the Accessibility disclosure.
2. Enable ReelGuard under Android Settings → Accessibility.
3. Allow Display over other apps.
4. Return to ReelGuard and tap **Activate protection**.
5. Open Instagram and use it normally. There is no in-Instagram accessibility button and no floating debug control.

When the user opens a Reel shared in a direct message, ReelGuard is intended to allow that single Reel. A meaningful vertical swipe to the next Reel returns to the conversation.

When the user opens a Reels, Explore, or suggested-Reel surface, ReelGuard shows a full-screen pause. The user can leave Reels or choose a 5, 10, 15, or 20 minute session. The timer continues while Instagram is in the background; it never sends a Back action to another app. When the timer ends, ReelGuard only navigates if Instagram is foreground and the current screen is still a Reel surface.

## Important limitations

- Instagram's accessibility hierarchy is not a stable public API. Selectors can change between Instagram releases, locales, devices, and experiments. Real-device regression testing is required after Instagram updates.
- ReelGuard currently targets personal/sideload testing. Google Play distribution requires a separate policy assessment. In particular, Google Play restricts non-accessibility apps that use AccessibilityService for autonomous actions, and an in-app disclosure alone does not make a prohibited use eligible.
- Accessibility and overlay permissions are user-controlled. ReelGuard must never prevent the user from disabling the service or uninstalling the app.
- This repository does not currently include a signed production release configuration.

## Development

Open this directory in Android Studio with JDK 17. Use:

\`\`\`bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
\`\`\`

The GitHub Actions workflow runs those checks and uploads a debug APK plus an unsigned release APK for inspection.

## Supported app behavior

- Setup guidance and permission status refresh when the app returns to the foreground.
- Explicit protection activation and pause.
- A separate, affirmative Accessibility disclosure before opening Android settings.
- Local-only accessibility hierarchy inspection for the core detection flow.
- A full-screen, contextual intervention; no floating Snap UI chip.
- Timer restoration after service/process restart.
- Overlay dismissal and state cleanup when Instagram is backgrounded.
