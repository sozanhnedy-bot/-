# Video export architecture — World News 1.6.5

## Current implementation

- FFmpegKitNext 9.0.2 is the app's only video-export engine. `VideoStudioActivity` invokes `FfmpegVideoExportEngine` directly.
- The Media3 exporter, fallback coordinator, and Media3 Gradle dependencies have been removed. A failed export now reports the FFmpeg error directly rather than switching to another backend.
- The in-app diagnostic dialog displays the exception chain, Android/app version, selected-media count, and optional-audio flags. It can copy a bounded, credential-redacted report to the clipboard.
- FFmpegKitNext is built for `arm64-v8a` with `libx264`; the APK workflow restores the verified AAR artifact, runs Android unit tests, builds the debug APK, verifies its signature and package version, and uploads the APK artifact.

## Validation boundaries

CI verifies dependency resolution, unit tests, APK assembly, package metadata, and APK signing. These checks do not prove that every user-selected source file can be decoded or that MP4 export works on a physical phone.

The owner has reported an on-device MP4 export failure. The diagnostic report is intended to capture the exact FFmpeg stage and native error so that the failing input or operation can be corrected. Until that phone-level export succeeds, do not describe MP4 runtime export as verified.

## Distribution note

The bundled FFmpeg configuration enables `libx264` and GPL components. Review the applicable license notices and distribution obligations before public distribution. Debug APKs are for owner preview, not production release.
