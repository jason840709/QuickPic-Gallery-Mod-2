# 快圖 11.0.1 · QuickPic Gallery Mod 2

[繁體中文](README.md)

QuickPic Gallery Mod 2 is an Android gallery maintained by Jason Chen. It builds on a derivative of WSTxda's QuickPic Gallery Mod, adding compatibility repairs for modern Android, viewer controls, and a fallback for opening screensaver images. Its installed app name is **快圖**. This version also retains Jason Chen's earlier E-Ink optimizations for an e-reader.

## Animated WebP in 11.0.1

11.0.1 detects animation from WebP contents and plays it automatically, preserving ordinary frame durations, partial-frame composition, transparency and total loop counts. Durations of 0–10 ms use 100 ms; static WebP remains a still image.

This version is currently a local test candidate. Host and packaged-code checks passed; Android playback still awaits user testing. The latest public stable release remains 11.0.0.

## Using the app

Download `QuickPic-Gallery-Mod-2-11.0.0.apk` from [GitHub Releases](https://github.com/jason840709/QuickPic-Gallery-Mod-2/releases/latest), then tap it in your phone or tablet's file manager. Follow Android's prompts to allow that file manager to install apps.

11.0.0 can coexist with older Clone versions; set up preferences again on first use. Grant photo or file access when Android requests it, then open a folder to browse images. The existing None option in transition settings, tap-to-go-to-previous/next controls, and editable folder paths remain available.

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
