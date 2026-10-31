# Android 圖片取樣檢查

這兩個 Instrumentation 以反射使用成品 APK 的類別，沒有另外複製處理核心。請在隔離的測試模擬器使用，目標套件為 `io.github.jason840709.quickpic`。測試包需與成品使用同一開發簽章，金鑰不包含在來源中。

- `EinkProbe` 建立公開的合成灰階圖，比較原始模式與兩種可選模式，檢查實際完整圖載入、解碼上限、快照、快取、手勢及設定保存／取消。畫面需等待重繪完成；計數增加不代表畫面已提交。
- `EinkSettingsProbe` 從真實 MainPreference 的 View 頁面進入新選項，檢查排列及選單。
- 若 `Pictures/QuickPicEinkTest/animated.gif` 存在，另檢查 GIF 持續換幀且不進入重取樣。可用兩張不同顏色的500毫秒影格製作此測試檔。

使用現有 Android SDK 的 android.jar 編譯為 DEX，manifest 註冊上述兩個類別並設定相同 targetPackage，再簽署及安裝。以 `am instrument -w` 執行；JSON 的 PASS 只代表列出的 Android 軟體行為。螢幕截圖沒有電子紙濾色陣列，不能作為實機摩爾紋測試。

測試會寫入合成圖片與截圖、暫時改動顯示模式；成功結束時恢復原始模式。不要在個人日常裝置上直接執行。
