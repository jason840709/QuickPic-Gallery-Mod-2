# Android 圖片處理探針

Probe.java 是與目標 APK 同簽章的 Android instrumentation，targetPackage 為正式快圖套件。先安裝欲驗證的 APK，再以 Android SDK 的 javac/D8/aapt2/zipalign/apksigner 建立測試 APK；測試資產 assets/animation.webp 為合成兩影格 WebP。可用既有 Pillow 生成紅、藍各一影格並核對 n_frames=2，不使用私人圖片。

在本次獨立的 Android 14 ARM64 模擬器執行：

```
adb -s <本次模擬器> shell am instrument -w local.quickpic.screensaverprobe/local.quickpic.screensaverprobe.Probe
```

探針從已安裝 APK 的 class loader 反射呼叫實際 ScreensaverImage/ScreensaverPng，覆蓋 PNG、JPEG、WebP、動畫 WebP 和損壞輸入。圖片只寫入測試 cache，沒有呼叫 OEM Provider，也不能驗證真實 UI、系統檔案提交或休眠安全。私有建置回條保存實際建置腳本與執行日誌；不提交金鑰、密碼或 OEM APK。
