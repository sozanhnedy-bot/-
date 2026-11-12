# FFmpeg native runtime and licenses

The Android app builds and integrates the official **FFmpegKitNext v9.0.2** source from https://github.com/arthenica/ffmpeg-kit-next. FFmpegKitNext does not publish ready-made Android packages; this repository's GitHub Actions workflow builds the Android AAR locally from the pinned `v9.0.2` tag for `arm64-v8a`, then exposes its generated Maven repository to Gradle.

The upstream project is licensed under **LGPL-3.0 by default** and becomes GPL-licensed when GPL libraries are enabled. The current Android build enables `--enable-gpl` and `--enable-lib-x264`; its generated configuration must also confirm that MediaCodec is disabled. Because this AAR includes a GPL-enabled encoder, distribution requires a deliberate GPL compliance and licensing review; this file is not a legal opinion and does not decide the app's overall license.

Keep the generated AAR's license and component notices with any distributed APK. Before public distribution, inspect the exact AAR produced by the pinned workflow, verify its enabled libraries and notices, and resolve GPL source and corresponding-source obligations, attribution, and any codec patent requirements in the distribution territories. Do not distribute the GPL-enabled APK until that review is complete.

FFmpeg and FFprobe execute locally on the device. Input media is not sent to an application server. Maven repository access is required at build time to resolve the locally generated AAR.

## Pinned sources

- Official FFmpegKitNext source: https://github.com/arthenica/ffmpeg-kit-next
- Pinned release: https://github.com/arthenica/ffmpeg-kit-next/releases/tag/v9.0.2
- Android build and integration instructions: https://github.com/arthenica/ffmpeg-kit-next/blob/v9.0.2/android/README.md
- FFmpeg legal information: https://ffmpeg.org/legal.html

This file records the configured dependency and is not legal advice.
