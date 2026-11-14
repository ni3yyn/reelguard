# ReelGuard

A focused Android companion that helps people keep friend-shared Instagram Reels available while interrupting algorithmic Reels.

## Product structure

- **Protect** — protection status, one-time setup, and the single activation control.
- **Guide** — the two Reels rules and the expected Instagram experience.
- **Settings** — Android permissions, disclosure, and device-specific background settings.

The app uses a branded grey neumorphic visual system, a short animated launch screen, animated tab transitions, a persistent bottom navigation hierarchy, and a confirmation dialog when exiting.

## User journey

1. Open ReelGuard and review the Accessibility disclosure.
2. Enable ReelGuard under Android Settings → Accessibility.
3. Allow Display over other apps.
4. Return to ReelGuard and tap Activate protection.
5. Open Instagram normally. There is no in-Instagram button or floating debug control.

## Development

Open this directory in Android Studio with JDK 17.

\`\`\`bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
\`\`\`

Instagram's accessibility hierarchy is not a stable public API. Detection must be regression-tested on real devices and after Instagram UI updates. Google Play distribution also requires a separate Accessibility API policy assessment; this repository does not claim Play approval.
