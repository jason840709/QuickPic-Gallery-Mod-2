# 快圖 · QuickPic Gallery Mod II

[繁體中文](README.md)

QuickPic Gallery Mod II is an Android gallery maintained by Jason Chen. It builds on [WSTxda’s QuickPic Gallery Mod](https://github.com/WSTxda/QP-Gallery-Releases), adding viewing controls for e-readers, E-Ink screens and modern Android devices. It works on phones and tablets; its installed name is **快圖**.

## Project features

### E-readers and E-Ink screens

- Choose None for image transitions. New installations also default to no slideshow transition, reducing visual changes between images.
- Tap the lower-left region for the previous image or the lower-right region for the next image.
- Edit included and excluded folder paths directly to choose where the gallery looks for images.
- Hidden items are shown by default when the app opens; existing password protection still applies.

### Slideshows and display intervals

Start a slideshow directly from the viewer. The phone toolbar shows delete and play; tablets also provide an interval button. Sharing is in the three-dot menu.

New installations display each image for 3 seconds by default. The interval control opens a dialog with 3, 5, 10, 30 and 60 seconds, plus a custom value from 1 to 86400 seconds. It sets how long each image stays on screen. Slideshows use the current image list and offer sequential or shuffled playback.

### Screensaver images

Open the image’s three-dot menu, choose Set as, then select the screensaver action. The app creates a **1860 × 2480 pixel PNG** in `QuickPic-Screensaver`.

The app first tries XPhoto. If XPhoto is unavailable or cannot open the image, you can choose another image app. Finish assigning the PNG as the device screensaver in XPhoto or your e-reader’s system settings.

### Modern Android and animated images

The app supports devices that run only 64-bit apps. Animated WebP and GIF play automatically, while static WebP remains a still image. Turning off image transitions keeps animated-image playback available.

## Installation and use

Download the stable APK from [GitHub Releases](https://github.com/jason840709/QuickPic-Gallery-Mod-2/releases/latest) and open it in your phone or tablet’s file manager. Grant image or file access when Android requests it, then choose a folder to browse.

Updates to the same app package keep preferences. Older clones use separate packages and need their own setup. Leave off the trailing `/` when editing a folder path.

## Developer and sources

Developer: **Jason Chen**.

The upstream project is [WSTxda / QP-Gallery-Releases](https://github.com/WSTxda/QP-Gallery-Releases). This repository provides newly written code, patches, build scripts and tests. The complete historical app source is unavailable. See [LICENSING](LICENSING.md) and [CONTRIBUTORS](CONTRIBUTORS.md) for licensing and credits.

See [FAQ](FAQ.md) for usage, [CHANGELOG](CHANGELOG.md) for releases, and [BUILDING](BUILDING.md) and [VALIDATION](VALIDATION.md) for development and testing.
