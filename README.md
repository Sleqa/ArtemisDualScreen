# ArtemisDS (Artemis Dual Screen)

This is a fork of [Artemis](https://github.com/ClassicOldSong/moonlight-android) (itself a fork of Moonlight) for Android, focused on adding native support for **dual-screen Android devices** such as the AYN Odin 2 / Thor.

**Goal:** the main screen behaves exactly like stock Artemis while streaming, while the device's secondary screen becomes a dedicated companion panel with:

- A grid of compact circular macro keys (e.g. Alt+Tab, Ctrl+Shift+Esc) that you create and edit in-app, each with an optional icon from a bundled 75-icon set and an optional name shown underneath
- A reliable soft-keyboard toggle that forwards typed input straight into the stream
- A trackpad mode that turns the second screen into a mouse trackpad for the host PC
- An always-on column of ring gauges down the right edge: stream FPS, end-to-end latency (network + host processing + client decode, green under 10 ms, amber under 18 ms, then a deepening red, with the ring full at 100 ms), and the host PC's CPU, GPU and RAM load read live from a Vibepollo/Apollo host's web API
- A swipe-up-to-quit handle to end the session without touching the main screen

### Host performance gauges

The FPS gauge is measured on the device and needs no setup. The CPU, GPU and RAM gauges read the
host PC's counters from Vibepollo's (or Apollo's) web API — `GET /api/host/stats` on the web
interface port — which requires credentials. In the app's settings, under the second-screen
options, enter either:

- your Vibepollo web interface **username and password**, or
- a **scoped API token** (a token limited to `GET /api/host/stats` is enough, and is the safer
  option since it grants nothing else)

The port defaults to the stream's port + 1 (47990 on a default install) and can be overridden.
The connection is pinned to the same certificate the stream is paired with, so nothing is sent
anywhere but the paired host. Hosts without that endpoint (older Sunshine, for example) simply
leave those three gauges blank. Vibepollo only serves its web API to the local network by
default, so streaming from outside the LAN additionally needs the host's
`origin_web_ui_allowed` set to `wan` — think about whether you want that exposed before
changing it.

### Icons

The macro icons are [Material Design Icons](https://pictogrammers.com/library/mdi/) by the
Pictogrammers group, redistributed under the Apache License 2.0 — see
[THIRD_PARTY_ICONS_LICENSE.txt](THIRD_PARTY_ICONS_LICENSE.txt). Only the subset used by the icon
picker is bundled, converted to white Android vector drawables.

> **Disclaimer:** The dual-screen features in this fork were built with AI assistance (Claude, via Claude Code). They've been tested on-device, but have not been reviewed by the upstream Artemis/Moonlight maintainers, and bugs may exist. Use at your own discretion, and please report issues on [this fork's repository](https://github.com/Sleqa/ArtemisDualScreen/issues) rather than upstream.

---

# Artemis Android

Previously named Moonlight Noir

An open source client for [Apollo](https://github.com/ClassicOldSong/Apollo)/[Sunshine](https://github.com/LizardByte/Sunshine).

Artemis Android will allow you to stream your collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Artemis is currently the best fork of Moonlight with loads of optimizations for office usage.

A more seamless experience with virtual display will be Artemis paired with [Apollo](https://github.com/ClassicOldSong/Apollo).

# Features

If you switch back to the main stream version, you'll be missing the following awesome features which are very unlikely to be added there:

1. Custom virtual buttons with import and export support.
2. [Custom resolutions](https://github.com/moonlight-stream/moonlight-android/pull/1349).
3. Custom bitrates.
4. [Multiple mouse mode switching](https://github.com/moonlight-stream/moonlight-android/pull/1304) (normal mouse, [multi-touch](https://github.com/moonlight-stream/moonlight-android/pull/1364), touchpad, disabled, local cursor mode).
5. Optimized virtual gamepad skins and free joystick.
6. External monitor mode.
7. Joycon D-pad support.
8. Simplified performance information display.
9. [Game back menu](https://github.com/moonlight-stream/moonlight-android/pull/1171).
10. Custom shortcut commands.
11. Easy soft keyboard switching.
12. Portrait mode.
13. Display on top mode, useful for foldable phones.
14. [Virtual touchpad space and sensitivity adjustment](https://github.com/moonlight-stream/moonlight-android/issues/1348#issuecomment-2236344729) for playing right-click view games, such as Warcraft.
15. Force use device's own vibration motor (in case your gamepad's vibration is not effective).
16. Gamepad debugging page to view gamepad vibration and gyroscope information, as well as Android kernel version information.
17. Trackpad tap/scrolling support
18. Natural track pad mode with touch screen
19. Non-QWERTY keyboard layout support
20. Quick Meta key with physical BACK button
21. Frame rate lock fix for some devices
22. Video scale mode: Fit/Fill/Stretch
23. View pan/zoom support
24. Rotate screen in-game
25. Add option to quit app directly
26. Samsung DeX scrolling support
27. Proper click/scroll/right-click for trackpad on generic Android tablet when using local cursor
28. Virtual Display integration with [Apollo](https://github.com/ClassicOldSong/Apollo)
29. Server Command integration with [Apollo](https://github.com/ClassicOldSong/Apollo)
30. Clipboard sync (requires Apollo)
31. SBS 3D for external Displays (Using AI MiDaS v2 Lite)

# Disclaimer

This is the `go away` version of Moonlight Android.

I got kicked from Moonlight and Sunshine's Discord server literally for helping people out.

This is what I got for finding a bug, opened an issue, getting no response, troubleshoot myself, fixed the issue myself, shared it by PR to the main repo hoping my efforts can help someone else during the maintainance gap.

Yes, I'm going away. Fixes and improvements on this fork are not necessarily be merged to the main repo either. I have also started [a fork of Sunshine called Apollo](https://github.com/ClassicOldSong/Apollo) and will add useful features that will never get merged by the main repo shortly. [Apollo](https://github.com/ClassicOldSong/Apollo) and [Moonlight Noir](https://github.com/ClassicOldSong/moonlight-android) will no longer be compatible with OG Sunshine and OG Moonlight eventually, but they'll work even better with much more carefully designed features.

The main repo had stayed silent for 5 months, with nobody actually responding to issues, and people are getting totally no help besides the limited FAQ in their Discord server. I tried to answer issues and questions, solve problems within my ablilty but I got kicked out just for helping others.

**PRs for feature improvements are welcomed here unlike the main repo, your ideas are more likely to be appreciated and your efforts are actually being respected. We welcome people who can and willing to share their efforts, helping yourselves and other people in need.**

**Update**: They have contacted me and apologized for this incident, but the fact it **happened** still motivated me to start my own fork.

## Downloads (this fork - ArtemisDS)
* [Download APK directly](https://github.com/Sleqa/ArtemisDualScreen/releases)
* [Use Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.limelight.noir%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FSleqa%2FArtemisDualScreen%22%2C%22author%22%3A%22Sleqa%22%2C%22name%22%3A%22ArtemisDS%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22arm64%5C%22%2C%5C%22matchGroutToUse%5C%22%3A%5C%22%241%5C%22%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22v(.%2B)%5C%22%7D%22%7D) (recommended)

Looking for upstream Artemis instead? See [ClassicOldSong/moonlight-android](https://github.com/ClassicOldSong/moonlight-android).

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
