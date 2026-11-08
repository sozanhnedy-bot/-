# Notes: Firebase App Check and Media3 video effects

## Firebase App Check for Firebase AI Logic

Official source: https://firebase.google.com/docs/ai-logic/app-check
- Firebase AI Logic requires App Check enforcement. In Firebase Console, inspect Security → App Check → APIs → Firebase AI Logic. For baseline protection choose Enforced; keep replay protection disabled until limited-use tokens are configured in the app.
- Debug APKs use the App Check debug provider during development. The debug token must be registered under Security → App Check → Apps → World News → Manage debug tokens. Keep debug tokens private and never embed/commit them into public source or production builds.

Official Android Play Integrity source: https://firebase.google.com/docs/app-check/android/play-integrity-provider
- Play Integrity can support apps distributed outside Google Play, provided the app's distribution settings and signing certificate are registered correctly.
- Debug provider is appropriate for sideloaded development builds, but requires a registered debug token.

Official Android debug-provider source: https://firebase.google.com/docs/app-check/android/debug-provider
- Android prints the generated debug token in Logcat; the token must then be added to the Firebase Console allow-list.

## AndroidX Media3 APIs checked in the 1.9.0 cached artifacts

Dependencies: androidx.media3:media3-transformer:1.9.0, media3-effect:1.9.0, media3-common:1.9.0.
- `TextOverlay.createStaticTextOverlay(SpannableString, StaticOverlaySettings)` exists; `StaticOverlaySettings.Builder` supports `setBackgroundFrameAnchor`, `setOverlayFrameAnchor`, and `setScale`.
- `OverlayEffect(List<TextureOverlay>)` exists and can be included in `EditedMediaItem.Builder.setEffects(new Effects(audioProcessors, videoEffects))`.
- `CanvasOverlay` is a custom time-aware bitmap overlay; its `onDraw(Canvas, long presentationTimeUs)` and `setCanvasSize(width,height)` are available for animated ticker graphics and time-based fades.
- No stock public video-transition/Crossfade class was found in Media3 1.9.0 transformer/effect artifacts. A dip-to-black/fade must be implemented as a custom time-dependent overlay/effect, or another compositor approach.
- `GainProcessor(GainProvider)` exists in media3-common; `GainProvider.getGainFactorAtSamplePosition(long, int)` allows a per-sample gain curve for an audio effect.
- `Composition.Builder` accepts a list or varargs of `EditedMediaItemSequence`, allowing video, narration, and background-music tracks to be composed in parallel. Runtime behavior and duration alignment still require on-device validation.
