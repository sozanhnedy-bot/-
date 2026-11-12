# FFmpeg native runtime and licenses

The Android app integrates the official **FFmpegKitNext v9.0.2** source from https://github.com/arthenica/ffmpeg-kit-next. This repository's GitHub Actions workflow builds the Android AAR from the pinned `v9.0.2` tag for **arm64-v8a**, then publishes the generated Maven repository as a workflow artifact for Gradle.

## Configured codec and license profile

The Android build enables **GPL** and **libx264** explicitly. The video export engine must therefore use FFmpeg's `libx264` encoder for both intermediate video segments and the final MP4. Android MediaCodec is not the selected encoder in this profile.

Enabling GPL libraries changes the resulting FFmpeg build from the default LGPL configuration to a **GPL-compatible distribution profile**. Do not describe or distribute this AAR/APK as LGPL-only. Anyone distributing the resulting application must comply with the applicable GPL terms for the exact FFmpegKitNext/FFmpeg source and modifications, provide the corresponding source and notices as required, and review all bundled component licenses and codec patent obligations in each distribution territory.

Keep the generated AAR's license and component notices with any distributed APK. Before public distribution, inspect the exact AAR produced by the pinned workflow, verify its enabled libraries and notices, and obtain appropriate legal review. This file is a technical configuration record, not legal advice.

## Runtime behavior

FFmpeg and FFprobe execute locally on the device. Input media is not sent to an application server. Maven repository access is required at build time to resolve the locally generated AAR. The app currently restricts native packaging to `arm64-v8a`; other Android ABIs are not covered by this build.

## Pinned sources

- Official FFmpegKitNext source: https://github.com/arthenica/ffmpeg-kit-next
- Pinned release: https://github.com/arthenica/ffmpeg-kit-next/releases/tag/v9.0.2
- Android build and integration instructions: https://github.com/arthenica/ffmpeg-kit-next/blob/v9.0.2/android/README.md
- FFmpeg legal information: https://ffmpeg.org/legal.html

