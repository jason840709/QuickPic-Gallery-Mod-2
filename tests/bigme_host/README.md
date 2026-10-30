# B1051C 屏保橋接的主機拒絕路徑測試

執行方式（沿用既有 Python／JDK，不安裝套件）：

```sh
python3 -B tests/bigme_host/run.py --oem-apk /path/to/audited/StandbyApp.apk --legacy-oem-apk /path/to/audited/XrzSettings.apk
```

測試執行正式的 `BigmeScreensaver.java` 與 `ScreensaverPng.java`；Android 的套件管理、對話框、掃描器、Bitmap 解碼、Provider 呼叫均以窄 stub 取代。固定 APK／公開憑證的雜湊使用已核對原廠檔案，測試不改寫該檔，也不將它納入儲存庫。

依韌體選擇 4.8.8／4.9.0 元件，確認同一操作不混用 Provider，並保留舊元件正常回傳 status=-1 的行為。

涵蓋確認前只讀 metadata、取消不執行工作、陌生版本／元件／憑證／APK 拒絕、PNG 與掃描失敗拒絕、送出後未知結果不重試、授權與 client 清理，以及原始圖片不傳給 importer。PNG 測試包括完整 RGB fixture 與壞簽頭、尺寸、CRC、截斷、額外資料、區段順序、空資料、未知主要區段與長度溢位。

另以實際 `ScreensaverImage` 和既有 Java2D Android doubles 執行 overload 隔離測試：舊入口保留 `.nomedia`，新入口不建立；輸出使用不同路徑，重複或失敗處理保留原圖及先前輸出，失敗不留下半成品。Java2D 的 `setHasAlpha(false)` 以轉 RGB 模擬 Android opaque PNG。

這些檢查不等於真機驗證；未涵蓋 Android Skia、MediaStore 非同步時序／十秒 timeout、跨 UID 原始路徑讀取、真實原廠 Provider 的檔案寫入／休眠行為、競爭條件及實際 UI 取消事件。測試不建置／簽署 APK，也不連接裝置或寫入 OEM 狀態。
