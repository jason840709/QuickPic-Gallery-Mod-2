# 快圖 · QuickPic Gallery Mod 2

[繁體中文](README.md)

QuickPic Gallery Mod 2 is an Android gallery maintained by Jason Chen. It builds on a derivative of WSTxda's QuickPic Gallery Mod, adding compatibility repairs for modern Android, viewer controls, and a fallback for opening screensaver images. Its installed app name is **快圖**. This version also retains Jason Chen's earlier E-Ink optimizations for an e-reader.

## Release status

**11.0.2 has a known startup crash and is not recommended for installation.** The hidden-items startup path called an internal method using the wrong return descriptor. Initialization caught the error and exited. Its original APK is preserved and the release is marked as a prerelease.

The 11.0.3 repair candidate corrects that descriptor and adds exact app-owned method and field resolution checks for the hidden-items startup method. Packaged-code and host tests pass; Android startup still awaits confirmation, and no stable 11.0.3 APK has been published. The public stable release is currently 11.0.0.

## Changes introduced in 11.0.2 and retained in the 11.0.3 candidate

Hidden items are shown when the app opens, including a return from the background. You can hide them during the current foreground session. Existing password protection still requires authentication.

New installations default to None for slideshow transitions; updates keep the existing selection. A viewer path that still faded when None was selected has also been corrected. This setting controls transitions between images; animated WebP and GIF content still plays.

This release includes the animated WebP and direct-open repairs developed in 11.0.1. It detects animation from the file contents and handles transparency, partial-frame composition and loop counts. Frame durations of 0–10 ms use 100 ms; other durations follow the file. Static WebP remains a still image. Direct opening, manual navigation and slideshow loading try to load animated content; after stopping the slideshow, the current animation can continue according to its loop count.

Host logic, packaged-code paths and signing have been checked. Android startup of the 11.0.3 candidate still awaits confirmation; earlier device checks are described in [VALIDATION](VALIDATION.md).

## Using the app

Download `QuickPic-Gallery-Mod-2-11.0.0.apk` from [GitHub Releases](https://github.com/jason840709/QuickPic-Gallery-Mod-2/releases/latest), then tap it in your phone or tablet's file manager. Follow Android's prompts to allow that file manager to install apps.

Updates to the main package keep preferences. Older Clone packages remain separate apps and need their own setup. Grant photo or file access when Android requests it, then open a folder to browse images. The existing None option in transition settings, tap-to-go-to-previous/next controls, and editable folder paths remain available.

The phone viewer toolbar shows delete and slideshow controls, with sharing in the menu. Tablets also show an interval control. A dialog in the viewer lets you enter the number of seconds each slide stays on screen.

The screensaver feature generates a **1860 × 2480 pixel PNG** in `QuickPic-Screensaver`. It first tries XPhoto; if XPhoto cannot open the image, Android offers another app through its chooser. Finish assigning the screensaver in XPhoto or your device settings after opening the image.

## Existing E-Ink optimizations

11.0.0 retains these four features from the earlier E-Ink version, with their operating instructions preserved on the About page:

- **No-animation navigation**: retain the None option in transition settings for E-Ink viewing.
- **Tap navigation**: tap the lower-left region for the previous image or the lower-right region for the next image.
- **Editable folder paths**: use the pencil beside a path in the include/exclude folder lists. Leave off the trailing `/`.
- **Screensaver images**: open the image's three-dot menu, choose Set as, then choose the screensaver action. The feature was originally intended for E-Ink devices supporting XRZ screensavers.

The following changes were added in 11.0.0.

## Changes in 11.0.0

- Replace functions that relied on an old 32-bit native library with Java, allowing installation on devices that support only 64-bit apps.
- Put delete and slideshow on the phone toolbar, add the interval control on tablets, and move share to the menu.
- Edit slideshow seconds directly in the viewer.
- Offer another viewer when XPhoto cannot open a generated screensaver image.
- Use one app name, 快圖, and package `io.github.jason840709.quickpic`, with no Clone number in the name.
- Show Jason Chen, GitHub, contributors, upstream sources, and FAQ on the About page.

The compatibility repair was installed and manually exercised on **a 64-bit phone**. Its Java replacements for the native interface (JNI) passed 38 device checks. Those checks cover the compatibility layer; see each Release's notes for validation of its final 11.0.0 APK.

## Sources and contributions

This is an independently maintained derivative of [WSTxda / QP-Gallery-Releases](https://github.com/WSTxda/QP-Gallery-Releases). The complete historical app source is unavailable. This repository contains newly written Java compatibility code, patch and build scripts, host tests, and documentation. To build, use this project's released 11.0.0 APK as the base and apply the repository's code and patches. The new contents use MIT, while the original app and third-party APK contents have separate licensing considerations described in [LICENSING.md](LICENSING.md).

See [FAQ](FAQ.md) for usage, [BUILDING](BUILDING.md) for local builds, [CONTRIBUTORS](CONTRIBUTORS.md) for credits, and [CHANGELOG](CHANGELOG.md) for version history.

Repository: [jason840709 / QuickPic-Gallery-Mod-2](https://github.com/jason840709/QuickPic-Gallery-Mod-2)
