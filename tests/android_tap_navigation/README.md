# Android 點擊翻頁檢查

`TapProbe.java` 是同簽章 instrumentation，targetPackage 為 `io.github.jason840709.quickpic`；測試包為 `local.quickpic.tapprobe`。以 Android SDK 編譯與簽署到倉庫以外的目錄，使用隔離模擬器，不能對含個人資料的裝置執行。測試會在公開 Pictures/QuickPicTapTest 產生五張合成 PNG，修改測試安裝的翻頁與單擊偏好。

可用 `am instrument -w -e mode <模式> local.quickpic.tapprobe/local.quickpic.tapprobe.TapProbe` 執行：

- `raw-state`：查看是否存在新偏好，可用在舊版。
- `state`：呼叫正式 APK 讀取模式，核對新安裝或升級預設停用。
- `inspect`：讀取真實看圖控制器、頁碼、圖片與焦點。
- `baseline`：向左滑至左下區域，記錄舊版實際頁碼變化。
- `gestures`：檢查停用／啟用時左右滑動、確認單擊、中央選單、雙擊、近距離多指、取消後恢復等情況。
- `menus`：在上方翻頁區啟用時，實際點工具列、下拉選單與設定對話框，核對不誤翻頁。

首次測試的 Android 全螢幕教學會搶走焦點。探針只確認已辨識的英文「Got it」教學；不移除正式 App 的焦點保護。若仍有系統或權限視窗，先處理測試環境，不把未取得焦點的觸控當成功能失效。

升級測試先用乾淨舊版證明 `tap_page_layout` 不存在，再更新新版。不能先由測試程式寫入新 key，然後將人工狀態當成真實舊版的預設。測試結果需附實際 APK 校驗值；模擬器結果不等於所有品牌實機驗收。


11.0.22 adds `reverse` (all six layouts in both directions), `ui-check` (preview, Save/Cancel and persistence), and `settings` (open View and verify the two screensaver preferences). Run against synthetic images in a dedicated emulator. Android switches may expose their state as part of their accessible text.

## 立即翻頁與計時

啟用的頁區放開即翻頁，快速連點會連續翻頁；其他區域雙擊仍送到原縮放處理。TapProbe的gestures檢查也涵蓋排隊歷史長按事件，防止被拒絕的長按由延後回呼重新觸發。

`TimingProbe` 在相同模擬器建立五張純色PNG，先暖機六次，再以60及160毫秒的名目按壓各測五次。它透過真實觸控注入，代理原View listener並原樣轉交，記錄UP送出、翻頁回呼、新圖片排入繪圖與影格提交。需在測試manifest另註冊`local.quickpic.tapprobe.TimingProbe`，targetPackage與簽章沿用現有測試方式。

`fast_tap_check`在五次短按中只要有一次UP到翻頁回呼達100毫秒便失敗；用於捕捉原等待路徑。它不是所有裝置必須達到的總顯示速度規格。UI等待、快取、模擬GPU及主機負載會影響其他階段；frame commit也不是實體螢幕完成刷新。測試只在隔離模擬器執行，會修改測試App偏好及寫入合成圖片。
