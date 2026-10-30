# 屏保選項與圖片開啟測試

`Probe.java` 是針對已安裝快圖的 Android instrumentation。以與目標 App 相同金鑰簽署；測試 App 的 package 為 `local.quickpic.optionsprobe`，instrumentation 名稱為 `local.quickpic.optionsprobe.Probe`，targetPackage 為 `io.github.jason840709.quickpic`。

使用 Android SDK 的 javac、D8、aapt2、zipalign 與 apksigner 建立測試 APK。`receiver` 為獨立套件，沒有儲存或全部檔案權限，請另行建置安裝。簽署與建置輸出留在倉庫外。

先啟動完整快圖，再執行 `am instrument -w -e mode state` 查看正式 DEX 讀到的偏好與選單動作。模式 `off`、`generic` 會修改此測試安裝的選項；`open` 會產生合成 PNG 並呼叫實際 opener，`menu` 會呼叫實際設為選單。這些模式只用在隔離測試裝置。

在 `open` 顯示的系統選擇器選獨立接收器，核對 URI、MIME、ClipData、讀取授權、完整資料流與解碼結果。停止接收器後，以相同 URI、不加授權旗標再次開啟，應收到 SecurityException。測試用的接收器須與快圖有不同 UID。

首次預設需先移除再全新安裝測試 App；單純清除資料不會重設 PackageManager 的安裝／更新時間。更新保存則先在前版手動修改選項，再覆蓋安裝新版。以上流程不呼叫原廠屏保 API，不代表實機套用或休眠測試。
