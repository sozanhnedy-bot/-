# Video engine migration plan

## Verified baseline (2026-11-11)

- Android Gradle Plugin 8.9.0, Gradle 8.11.1, Kotlin 2.1.20.
- VideoStudioActivity currently contains both the screen UI and the Media3 export pipeline.
- The existing exporter assembles images and video clips, crops to portrait 9:16 or landscape 16:9, draws the headline/lower-third/ticker overlay, optionally adds narration and a lower-volume music track, and writes H.264/AAC MP4 locally.
- Media3 dependencies are declared in the app module.
- Existing unit tests cover transition, ticker, and music-gain calculations. No export-engine integration tests were found.
- No FFmpeg native engine or native FFmpeg libraries are currently integrated.

## Safety rules

1. Keep the current Media3 exporter intact as a fallback until the replacement passes real-device tests.
2. Extract an export request/progress/result contract so the Activity handles UI only.
3. Add FFmpeg as a separate backend rather than rewriting the Activity in place.
4. Test URI access, codec compatibility, durations, portrait and landscape output, Arabic text, audio mixing, cancellation, low-storage failures, and playback.
5. Do not remove Media3 until FFmpeg has passed device tests and the fallback decision is explicit.
6. Review native binary provenance, ABI coverage, LGPL/GPL configuration, and redistribution obligations before bundling FFmpeg.

## Staged implementation

- Stage 1: extract a stable export-engine contract while preserving current behavior.
- Stage 2: implement the FFmpeg native backend behind that contract.
- Stage 3: compare output and failure handling on real Android devices; retain Media3 fallback.
- Stage 4: switch the default only after tests pass; remove unused dependencies only in a later cleanup.

This file records the migration plan only. It does not claim FFmpeg has been integrated or that an APK has been built.
