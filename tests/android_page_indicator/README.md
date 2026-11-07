# 頁碼 Android 檢查

這個測試程式在已安裝的完整 App 裡執行，圖片全部由測試產生。它會改動測試安裝的偏好設定，資料夾測試還會刪掉一張自己產生的圖片；請使用獨立模擬器。

使用既有 Android SDK 將 Probe.java 與 AndroidManifest.xml 建成測試 APK，並以和受測 App 相同的憑證簽署。它不需要額外套件。建置輸出與暫存請放進專案指定的測試工作目錄；共用 SDK 不複製進專案。

## 執行

預設測試包含從 11.0.27 更新保留偏好的檢查：先在 27 安裝測試 APK，使用 seed 階段保存偏好，再覆蓋安裝 28，執行預設階段。folder 階段從合成的相簿清單開圖，測試另一套控制器、滑動及實際刪除後的頁數。

    adb -s "$TEST_DEVICE" shell am instrument -w -e stage seed local.quickpic.page28/local.quickpic.page28.Probe
    # 以同一簽章的 28 覆蓋安裝 27 後：
    adb -s "$TEST_DEVICE" shell am instrument -w local.quickpic.page28/local.quickpic.page28.Probe
    adb -s "$TEST_DEVICE" shell am instrument -w -e stage folder local.quickpic.page28/local.quickpic.page28.Probe
    adb -s "$TEST_DEVICE" shell am instrument -w -e stage tablet local.quickpic.page28/local.quickpic.page28.Probe
    adb -s "$TEST_DEVICE" shell am instrument -w -e stage tablet -e landscape true local.quickpic.page28/local.quickpic.page28.Probe

平板測試先調整獨立模擬器的尺寸與密度。若模擬器固定顯示方向，先解除其固定方向，再核對實際畫面寬高；單純設定 App 的橫向要求不算完成橫向驗證。

截圖與結果存在受測 App 的外部快取 page28-results 子目錄。成功回傳 INSTRUMENTATION_CODE: -1，失敗有 failure 或 Process crashed，不能只看 shell 的結束碼。

測試涵蓋開關位置與保存、小字位置、實際頁面與總數、工具列、閱讀器待載入、投影片、單張外開與合成檔案刪除。它不測實體墨水屏刷新、記憶卡速度、百萬圖片效能或原廠屏保套用。
