# FFmpeg Native runtime and licenses

The Android editor pins `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`. This is an independently maintained Android fork; it is **not** the retired upstream `FFmpegKit` repository and is **not** the original author's source-only `FFmpegKitNext`. The selected `full` artifact is the fork's LGPL-3.0 variant. Do not change it to a `-gpl` artifact or an unpinned `+` version without review and approval.

The app requests H.264 output through Android's `MediaCodec` encoder (`h264_mediacodec`) and AAC audio through FFmpeg; it does not request the GPL `libx264` encoder. The AAR contains the component license notices as Android raw resources. Keep those resources in the packaged APK and retain the in-app licensing notice.

The FFmpeg export and media probing run on the device. Input media is not sent to an application server. Maven Central access is needed only when building the app to resolve the dependency.

Sources:

- Artifact and maintainer documentation: https://github.com/ffmpegkit-maintained/ffmpeg-kit
- Pinned artifact: https://central.sonatype.com/artifact/dev.ffmpegkit-maintained/ffmpeg-kit-full/8.1.7
- FFmpegKitNext (official source-only continuation): https://github.com/arthenica/ffmpeg-kit-next
- FFmpeg license and patent notes: https://ffmpeg.org/legal.html

This file is a dependency record, not legal advice. Before distributing the application, review the LGPL-3.0 terms and the individual notices bundled in the AAR for the exact build, as well as any codec patent obligations applicable in the distribution territories.