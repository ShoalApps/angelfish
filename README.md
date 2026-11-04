<p align="center"><a href="https://shoalapps.github.io/"><img src="https://raw.githubusercontent.com/ShoalApps/shoalapps.github.io/main/assets/readme/angelfish.svg" alt="Angelfish - a Shoal app, with the rest of the Shoal family" width="100%"></a></p>

*Formerly Swordfish.*

A Material 3 Expressive client for watching YouTube videos on Android, part of the **Shoal** project:
open-source apps that take on your wallpaper colours with Material You.

- Jetpack Compose UI with dynamic colour, light/dark themes and spring animations
- NewPipeExtractor backend: no Google account, no ads
- Media3 ExoPlayer with background play and picture-in-picture
- SponsorBlock, DeArrow and Return YouTube Dislike built in
- TV mode for big screens, remotes and Bluetooth/USB controllers (see below)

## TV mode

Settings → TV mode (or the app's tile on Android TV) opens YouTube's own TV interface full screen,
with [VacuumTube](https://github.com/shy1132/VacuumTube)'s modules added: ad blocking, SponsorBlock,
DeArrow, Return YouTube Dislike, controller support, speed and volume controls and its settings
page. It works with a TV remote, a keyboard, touch, or any Bluetooth/USB controller.

VacuumTube is an Electron app; its modules are vendored in [`tv/vacuumtube`](tv/vacuumtube) under
their MIT licence and bundled for Android's WebView by [`tv/build.mjs`](tv/build.mjs), with small
stand-ins for the Electron parts (`tv/shims`). Android's WebView has no Gamepad API, so the app reads
controllers itself and hands them to the page as a standard gamepad (`tv/gamepad.js`). Casting to
TV mode isn't available, since it needs network sockets a WebView can't open.

## Getting the APK

Every push to `main` builds a new APK with GitHub Actions.
Open the **Releases** page on your phone and install `Angelfish.apk` from the newest build.

Every build is also run on an Android emulator; screenshots and logs land on the `ci-shots` branch.

## Staying up to date

- The app checks GitHub once a day and offers new releases; Android asks before installing.
- Dependabot proposes library updates weekly; each is built and tested and only merged if it passes.
- Every day a workflow follows the NewPipeExtractor version the NewPipe app ships, so YouTube fixes
  arrive without anyone building anything by hand.
- Another follows VacuumTube's releases for TV mode (`tv/update-vacuumtube.sh` does the same by hand).

## Tests

- `./gradlew testDebugUnitTest`: unit tests for formatting, captions, links, stream picking,
  error messages and YouTube response parsing. Runs on the computer in a few seconds.
- `./gradlew connectedDebugAndroidTest`: UI tests that drive the real app on a phone or emulator:
  navigation, settings, links and shortcuts, the mini player, rotation, and (with a working
  connection) feeds, playback, search, comments and Shorts.

CI runs the unit tests on every push, and the UI tests that don't need YouTube itself on an emulator,
since YouTube blocks GitHub's servers. Run the full set on a home connection before a release.

## Roadmap

- v1: browse, search, channels, subscriptions, player, SponsorBlock, DeArrow
- v2: offline video downloads with yt-dlp; audio-only downloads remain planned
- v3: extras

## Licence

Angelfish is free software under the **GNU General Public License v3.0 or later**
(the same licence as NewPipeExtractor, which it is built on). You may use, study, share
and change it, as long as anything you distribute that is based on it stays under the
same licence with its source available. See [LICENSE](LICENSE).

TV mode includes VacuumTube, MIT License, Copyright (c) 2025-2026 shy; see
[tv/vacuumtube/LICENSE](tv/vacuumtube/LICENSE).

Angelfish is not affiliated with, endorsed by or sponsored by Google or YouTube.

## Privacy and legal

No ads, no tracking, nothing sent to us unless you agree to anonymous error reports. See the [privacy policy](https://shoalapps.github.io/privacy.html) and [legal notices](https://shoalapps.github.io/legal.html).

## Support

Shoal is free, open source and has no ads. If you'd like to help it keep going, you can [support Shoal on Liberapay](https://liberapay.com/ShoalApps).

## Release notes

Update `app/src/main/assets/whats-new.json` with each release. Notes ship with the APK, appear once per installed version and can be reopened from Settings. Keep them specific to changes in that release.

## Offline videos and data use

Angelfish uses on-device yt-dlp with FFmpeg. The current video and four recommendations are automatically downloaded on unmetered Wi-Fi by default. Settings can disable the current-video copy, choose 0-50 recommended copies, or explicitly allow mobile downloads. Saved videos are kept separately from automatic copies and can be played or removed in You. Only completed files are used offline; network playback switches to the complete file at the same position when it becomes available. Live streams are excluded.

Mobile playback defaults to 720p in both the main player and Shorts. Android Data Saver pauses metered downloads and background prefetching, disables new thumbnail requests and optional community lookups, and caps native playback at 720p.

New subscriptions default to All notifications. The bell offers All, Personalised (one latest upload per channel per check) and None, with a configurable daily cap. Checks run approximately every 30 minutes, subject to Android scheduling, connectivity and notification permission. The first check establishes a baseline without alerting old uploads. Signed-in bell changes sync to YouTube, with a message if sync fails.

## App language

The apps follow Android's system language by default, with an App language picker in Settings and right-to-left layout support. Translations are bundled and work offline. Coverage varies: many phrases are automatically translated, community catalogues may be partial, and missing strings fall back to British English. See `tools/README.md` and `app/src/main/assets/i18n/coverage.json` for maintenance and actual coverage.

## Audio tracks and thumbnails

The player lists audio languages and audio descriptions exposed by NewPipeExtractor for the current video. Switching a progressive track preserves position, quality and pause state; HLS and downloaded files use the audio tracks exposed by Media3. Downloaded files only offer tracks that were actually saved. Captions, chapters, speed, quality, looping, background playback and picture-in-picture remain available where supported. This does not promise full YouTube parity, premium service access, DRM playback, or tracks the upstream service does not expose. Small standard YouTube thumbnails request a 720p image and fall back to the original URL on failure.
