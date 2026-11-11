# FFmpegKitNext Android build handoff

## Pinned upstream source

- Project: https://github.com/arthenica/ffmpeg-kit-next
- Tag: `v9.0.2`
- Android profile: `android-r27d`
- App minimum SDK: 24
- App ABI filter: `arm64-v8a`

The upstream v9.0.2 release explicitly states that it is source-only and publishes no prebuilt binaries. Its Android README documents the Nix profile `android-r27d`, which supplies Android NDK r27d, SDK, CMake, and build tools and produces an AAR plus a local Maven repository.

## Build in this repository

Run the GitHub Actions workflow **Build FFmpegKitNext Android AAR** manually from the Actions tab. It checks out the pinned upstream tag, builds only the app's required `arm64-v8a` ABI, verifies that the AAR contains native libraries for that ABI, and uploads the local Maven repository as a workflow artifact.

This workflow is a build preparation step only. It does not yet add the generated AAR to the app, change Gradle dependencies, switch the export engine, or remove Media3. Those integration steps must wait until the workflow succeeds and the native artifact's license/package configuration is reviewed.

## Licensing gate

The upstream build supports optional GPL libraries via `--enable-gpl`. This workflow does not enable that flag. Do not add GPL options or external libraries without a separate license review. Preserve the upstream license and notices alongside any redistributed AAR; verify the actual generated package contents before distribution.

## Current integration boundary

- No Firebase or Gemini files are changed.
- No application Gradle files or activities are changed.
- Media3 remains untouched as the existing working implementation until FFmpeg export is integrated and tested.
- A successful AAR build does not prove export correctness on a physical Android device.
