# 快圖 · QuickPic Gallery Mod II

[繁體中文](README.md)

QuickPic Gallery Mod II is an Android gallery maintained by Jason Chen. It builds on [WSTxda’s QuickPic Gallery Mod](https://github.com/WSTxda/QP-Gallery-Releases), adding viewing controls for e-readers, E-Ink screens and modern Android devices. It works on phones and tablets; its installed name is **快圖**.

## Project features

### E-readers and E-Ink screens

- Choose None for image transitions. New installations also default to no slideshow transition, reducing visual changes between images.
- Enable **Reader mode** in Settings → View → Image to keep the current page until the next complete image is ready, without a blurry preview or loading text. It reduces flicker but increases page-turn delay. Off by default.
- Settings → View → Controls → Tap to turn pages offers six layouts with portrait 3:4 diagrams, including an L-shaped layout. Reverse swaps previous/next actions and updates every diagram. Tap navigation is off by default.
- Screensaver size and menu visibility are grouped under Settings → View → Screensaver settings.
- Edit included and excluded folder paths directly to choose where the gallery looks for images.
- Hidden items are shown by default when the app opens; existing password protection still applies.

### Slideshows and display intervals

Start a slideshow directly from the viewer. Both phone and tablet toolbars provide slideshow playback and a Slideshow interval button. Delete, edit and share are in the three-dot menu.

New installations display each image for 3 seconds by default. The interval control opens a dialog with 3, 5, 10, 30 and 60 seconds, plus a custom value from 1 to 86400 seconds. The input remembers the last custom value even after switching to a preset. Tap Custom to apply that saved value immediately, or enter a new value and tap Apply custom to save and use it. It sets how long each image stays on screen. Slideshows use the current image list and offer sequential or shuffled playback.

### Folder scanning and date sorting

Scanning explores new or recently changed folders before revisiting known paths, so newly downloaded comics can be found sooner. Creation/added-time and modification sorting are separate choices. Existing files without a reliable creation time initially use modification time; later additions retain their first observed time.

### Deleting images and folders

Deleting a folder removes the whole directory, including non-image files, hidden files and subfolders. Access to the selected SD-card location must be granted first. Failed or cancelled items remain in the list; after a successful parent deletion, its child albums are removed too.

Individual and selected-image deletion use the available storage permissions and media records to reduce redundant cleanup. Per-image progress remains; no diagnostic report appears after deletion.

### Screensaver images

Open an image, then choose **Set as** from the three-dot menu. The screensaver actions are:

| Action | Purpose |
| --- | --- |
| Set as screensaver (設置為屏保) | Creates a PNG and opens Android’s app chooser directly. Choose an image app, then finish assigning the screensaver in that app or the device settings. |
| Set as B1051C screensaver (設置為 B1051C 屏保) | Uses the corresponding original screensaver component on B1051C firmware 4.8.8 or 4.9.0. The current image becomes a separate 1860 × 2480 PNG before being handed to the system. |
| Set as BOOX screensaver (設置為文石屏保) | Hands the current image to the original BOOX screensaver service. It requires a compatible BOOX system and original components; it is not available on ordinary Android phones. |

Under **Settings → View → Image → Show screensaver options** (顯示屏保選項), select the actions to keep and tap Save. Each of the three actions can be shown or hidden, including hiding all three. Wallpaper remains available. Cancel keeps the saved settings.

A clean first installation detects the device once to choose an initial screensaver action. Reopening or updating the app does not repeat detection or overwrite manual choices. Updates from older versions preserve existing choices; the new BOOX action is hidden until enabled manually.

The general PNG action saves files in `QuickPic-Screensaver`. Under **Settings → View → Image → Screensaver image size** (設定屏保圖尺寸), enter a width and height or detect the current screen pixels, then save. QuickPic crops the largest centered area matching the target aspect ratio and resizes it to the exact output dimensions. The default is 1860 × 2480 pixels. Restore defaults (恢復預設) fills in that size; Save applies it.

Direct actions prepare a separate image copy and keep the original. You can cancel before confirming. B1051C firmware 4.8.8 and 4.9.0 use different original components, whose identity and version are checked before use. The BOOX action also requires a supported original service; compatibility does not extend automatically to every BOOX model or firmware. See [VALIDATION](VALIDATION.md) for tested scope.

### Modern Android and animated images

The app supports devices that run only 64-bit apps. Animated WebP and GIF play automatically, while static WebP remains a still image. Turning off image transitions keeps animated-image playback available.

## Installation and use

Download the stable APK from [GitHub Releases](https://github.com/jason840709/QuickPic-Gallery-Mod-2/releases/latest) and open it in your phone or tablet’s file manager. Grant image or file access when Android requests it, then choose a folder to browse.

Updates to the same app package keep preferences. Older clones use separate packages and need their own setup. Leave off the trailing `/` when editing a folder path.

## Developer and sources

Developer: **Jason Chen**.

The upstream project is [WSTxda / QP-Gallery-Releases](https://github.com/WSTxda/QP-Gallery-Releases). This repository provides newly written code, patches, build scripts and tests. The complete historical app source is unavailable. See [LICENSING](LICENSING.md) and [CONTRIBUTORS](CONTRIBUTORS.md) for licensing and credits.

See [FAQ](FAQ.md) for usage, [CHANGELOG](CHANGELOG.md) for releases, and [BUILDING](BUILDING.md) and [VALIDATION](VALIDATION.md) for development and testing.
