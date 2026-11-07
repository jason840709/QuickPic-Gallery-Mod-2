# Screensaver crop instrumentation

Use a separately signed Android instrumentation test APK targeting the maintained QuickPic package, signed with the same development key. Probe uses synthetic quadrant JPEG images only. It checks EXIF orientations, locked-aspect crop gestures for all backend editor parameters, PNG dimensions and persistent-copy isolation. No OEM calls are executed. Run only on an isolated Android emulator; the probe writes synthetic cache and Pictures fixtures.
