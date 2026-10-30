# 驗證方式

本專案提供主機測試、APK 結構檢查與 Android 測試程式。各類測試涵蓋不同範圍：主機測試檢查演算法與流程；APK 檢查核對實際封裝內容；Android 測試檢查平台解碼、權限與執行行為。這份文件說明如何重跑測試，不是歷次執行結果清單。

工具與建置環境見 [BUILDING.md](BUILDING.md)。測試使用合成圖片，原廠 APK、簽章金鑰及裝置資料不隨倉庫提供。

## 已發布版本的驗證邊界

以下保留已發布版本的測試範圍；它們不是後續版本的新增實測結果。

| 版本 | 已有證據 | 未涵蓋範圍 |
| --- | --- | --- |
| 11.0.0 | 僅支援 64 位元 App 的手機完成安裝與啟動；Android 執行環境中的 38 項相容層檢查涵蓋掃描、檔案複製、EXIF 與 GIF。 | 相容層檢查是獨立程序，不等於所有畫面與廠商功能驗收。 |
| 11.0.14 | Android 14 ARM64 模擬器完成閱讀器模式、手動翻頁、連點、投影片、刪後換頁及動畫內容檢查。 | 另一套資料夾控制器未完成所有 UI 入口；模擬器錄影不代表實體電子紙刷新或手寫效果。 |
| 11.0.16 | Android 14 ARM64 模擬器完成 15 項恢復尺寸介面檢查，包含取消、儲存、無效輸入及重開保存。 | 此版未重測實體閱讀器或真正的 XPhoto；Android 5 尺寸偵測後備也未在裝置驗證。 |

原廠屏保的單張套用、各種圖片格式、重新開機保存及長期休眠／喚醒是不同測試項目，不能用一般 Android 解碼或一次設定結果互相替代。

## 11.0.20 檢查範圍

完成 37 項單元測試、104 項設定／選單主機檢查、63 項 B1051C 版本分流及 PNG 檢查、13 項文石系統身份檢查，並通過 APK 建置、簽章及方法連結核對。主機檢查使用測試替身，沒有執行原廠服務。

Android 10／API 29 ARM64 模擬器已確認完整 App 冷啟動、由 11.0.19 更新保留手動關閉的選項，以及全新安裝只顯示一般屏保入口。一般屏保動作實際開啟系統選擇器；另一個沒有儲存權限的 App 可讀取並完整解碼 PNG，未授權時則遭系統拒絕。

B1051C 4.8.8 與文石的新入口已依原廠程式核對，但尚未在對應實機驗證套用及休眠／喚醒。11.0.20 的 B1051C 4.9.0 路徑亦未重新完成實機驗收，先前版本結果不視為本版新增實測。

## 主機測試

在倉庫根目錄執行：

```sh
python3 -B -m unittest discover -s tests -p 'test_*.py'
python3 -B tests/run_exif.py
python3 -B tests/webp_host/run.py
python3 -B tests/animation_clock_host/run.py
python3 -B tests/xphoto_host/run.py
python3 -B tests/reader_mode_host/run.py --output-json /tmp/reader-mode.json
```

測試範圍如下：

| 測試入口 | 涵蓋內容 |
| --- | --- |
| `test_*.py` | 修補腳本、套件／方法連結、版本及資源規則 |
| `run_exif.py` | 合成 JPEG／TIFF 中繼資料、旋轉與保存、非 JPEG 拒絕 |
| `webp_host`、`animation_clock_host` | WebP 畫面合成、透明混合、循環與播放時間安排 |
| `xphoto_host` | 圖片開啟路由、Intent、URI 與讀取授權內容 |
| `reader_mode_host` | 舊頁保留、載入完成後切換、取消、過期回呼及混合清單 |

WebP 主機測試所需的圖片套件與版本見 BUILDING。Android API 替身只能驗證測試所模擬的行為；不能取代平台原生解碼、跨 App 權限或實際畫面操作。

### 屏保圖片處理

```sh
python3 -B tests/screensaver_host/renderer/run.py --android-jar "$ANDROID_JAR"
python3 -B tests/bigme_host/run.py --oem-apk /path/to/audited/StandbyApp.apk
```

`ANDROID_JAR` 指向既有 Android SDK 的平台 JAR。renderer 使用 Java2D 替身，檢查置中裁切、EXIF 方向、輸出尺寸、白底合成、暫存清理與記憶體限制。

Bigme 主機測試執行正式橋接及 PNG 驗證程式，檢查確認／取消、原廠元件身份、完整 PNG、失敗處理、輸入隔離與資源釋放。套件管理、掃描器、解碼及 Provider 使用測試替身，不會呼叫原廠服務或修改裝置設定。詳見 [測試說明](tests/bigme_host/README.md)。

### 刪除、日期與掃描

相關入口位於：

- `tests/file_delete_host/`、`tests/folder_delete_host/`：檔案及完整目錄、授權範圍、取消與失敗處理。
- `tests/fast_media_delete_host/`、`tests/media_cleanup_host/`：精確媒體紀錄、缺檔確認、清理條件與後備流程。
- `tests/delete_timing_host/`：工作範圍管理，以及正式版不顯示或保存診斷報告的行為。
- `tests/media_dates_host/`、`tests/date_sort_host/`、`tests/scan_priority_host/`：日期索引、排序及掃描順序。

各入口需要的解碼目錄、SDK 或報告參數，請查該目錄的 README 或 `run.py --help`。刪除機制見 [DELETION.md](DELETION.md)。

## 需要指定 APK 的檢查

部分測試需要 APK 的 Apktool 解碼結果或 JADX 來源。這些反編譯檔不包含在公開倉庫中：

```sh
python3 -B tests/defaults_host/run.py --sources /path/to/jadx/sources --decoded /path/to/decoded --work-dir /tmp/defaults-test
python3 -B tests/manual_open_host/run.py --sources /path/to/jadx/sources --work-dir /tmp/manual-test
python3 -B scripts/check_local_links.py --decoded /path/to/decoded --output-json /tmp/startup-links.json
```

檢查時須核對實際 APK 的類別、方法名稱、參數及回傳型別，並確認套件、版本、資源 ID、簽章、ZIP 對齊與預期的原生函式庫內容。主機替身可協助檢查邏輯，但不能證明 Android 能解析每一個執行時連結；最終 APK 仍需安裝並完整啟動。

## Android 與裝置測試

[Android 圖片處理探針](tests/android_screensaver/README.md) 從已安裝 APK 的 class loader 呼叫實際產圖程式，使用合成 PNG、JPEG、靜態／動畫 WebP 及損壞輸入，檢查平台解碼與輸出。探針不呼叫原廠屏保 Provider，不能驗證原廠套用或休眠行為。

最終 APK 的裝置檢查應包含：

- 冷啟動與覆蓋更新、權限請求、資料夾瀏覽及圖片開啟。
- 靜態圖片、GIF、動畫 WebP、手動翻頁與投影片。
- 設定保存、取消、自訂尺寸與恢復預設。
- 另一個 App 透過 content URI 實際讀取產生的 PNG。
- 刪除後同時核對實檔、媒體索引、未選資料及相鄰目錄。
- 屏保原廠元件的套用、重新開機後保存，以及休眠／喚醒。

模擬器可驗證一般 Android 行為；電子紙刷新、原廠圖片匯入、手寫延遲及硬體喚醒需在對應裝置測試。各次結果應標明來源版本、APK 校驗值、環境與實際涵蓋項目，避免把某一版本或裝置的結果套用到其他版本或機型。
