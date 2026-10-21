# 在本機建置

建置腳本會解開你提供的 Android 安裝檔（APK）基底，把新增 Java 程式編譯成 Android 可執行程式，再併入 App，修補功能、重建資源並簽署。完整歷史 App 的畫面與主要程式由基底 APK 提供；公開來源的範圍與授權見 [LICENSING.md](LICENSING.md)。

## 取得基底 APK

從 [11.0.0 發行頁](https://github.com/jason840709/QuickPic-Gallery-Mod-2/releases/tag/v11.0.0) 下載 `QuickPic-Gallery-Mod-2-11.0.0.apk`。建置腳本以 APK 裡的既有圖庫程式與資源為基底，再把本倉庫的 Java 相容層和功能程式編譯進去。

11.0.0 發行 APK 的 SHA-256 是：

```text
1eb348eab476fda8f4be42b64c438767d60a65daa61aebc9b6c818c0bdf10405
```

SHA-256 是檔案內容的校驗值，腳本會先核對，避免把補丁套在不同的 App 上。腳本也保留開發時使用的 `8.8.5-XPhotoPNG-0.0.4-Clone1` 衍生基底支援，其校驗值為 `8a707e4285e22e668d50305864d05eb968ea584a56ec2d89e1ce50336fd5c605`；該歷史基底未放在倉庫內，也不是上游官方 APK 的通用替代名稱。

## 所需工具

- Python 3.9 或更新版本。
- JDK 17 或更新版本，`java` 與 `javac` 可由命令列執行。
- Android SDK Platform 36 的 `android.jar`。
- Android SDK Build Tools 36.0.0 的 `d8.jar`、`apksigner.jar` 與 `zipalign`。
- Apktool 2.12.1，可執行檔或 JAR。
- 你自己的單一金鑰 keystore，以及只含 keystore 密碼的本機文字檔。

keystore 是保存 Android 簽署金鑰的檔案。腳本支援檔案內只有一組金鑰，私鑰密碼與 keystore 密碼必須相同；建議使用 PKCS12 格式。工具與簽署金鑰由使用者自行提供。把 keystore 和密碼檔保存在倉庫以外，密碼檔只允許自己讀取，例如執行 `chmod 600`。後續更新同一套件時需保留相同簽署金鑰。

## 建置命令

先切換到下載或 clone 的倉庫根目錄。以下範例以 macOS／Linux 的 Android SDK 目錄為例；請把 `BASE_APK`、SDK 和簽署檔路徑換成你自己的位置。

```sh
cd QuickPic-Gallery-Mod-2
BASE_APK="$HOME/Downloads/QuickPic-Gallery-Mod-2-11.0.0.apk"
SDK_DIR="$HOME/Library/Android/sdk"
ANDROID_JAR="$SDK_DIR/platforms/android-36/android.jar"
D8_JAR="$SDK_DIR/build-tools/36.0.0/lib/d8.jar"
APKSIGNER_JAR="$SDK_DIR/build-tools/36.0.0/lib/apksigner.jar"
ZIPALIGN="$SDK_DIR/build-tools/36.0.0/zipalign"
APKTOOL="$(command -v apktool)"
SIGNING_KEYSTORE="$HOME/.quickpic-signing/local.p12"
SIGNING_PASSWORD_FILE="$HOME/.quickpic-signing/password.txt"
OUTPUT_APK="$HOME/Downloads/QuickPic-local-11.0.3.apk"
```

自行簽署的測試版使用不同套件名稱，可與正式版共存。執行：

```sh
python3 scripts/build.py \
  --base-apk "$BASE_APK" \
  --android-jar "$ANDROID_JAR" \
  --d8-jar "$D8_JAR" \
  --apksigner-jar "$APKSIGNER_JAR" \
  --zipalign "$ZIPALIGN" \
  --apktool "$APKTOOL" \
  --keystore "$SIGNING_KEYSTORE" \
  --password-file "$SIGNING_PASSWORD_FILE" \
  --out "$OUTPUT_APK" \
  --package com.example.quickpic.local
```

`--out` 指定新的 APK 檔案路徑，必須在倉庫以外，腳本也會拒絕覆寫現有檔案。`--apktool` 可指向 Apktool 可執行檔或 `.jar`。所有中間檔放在暫存目錄，結束時清除。完成後輸出 APK 的 SHA-256，並驗證簽章及 ZIP 對齊。

建置產物的 App 名稱為「快圖」，版本為 `11.0.3`，版本代碼為 `11003001`，預設套件名稱為 `io.github.jason840709.quickpic`，可用 `--package` 改成自己的測試套件名稱。新套件可與舊 Clone 共存；首次安裝需重新設定偏好，舊版 App 的資料目錄不會自動移入。

## 每一步修改的位置

| 檔案 | 工作 |
| --- | --- |
| `src/com/alensw/jni/JniUtils.java` | 替代舊原生介面（JNI），處理掃描與 GIF 等功能 |
| `src/com/alensw/jni/QuickPicExif.java` | 讀寫圖片的拍攝中繼資料（EXIF），並在寫入前建立備份 |
| `src/com/alensw/jni/CompatIntents.java` | 處理傳給外部 App 或回傳給呼叫者的圖片位址（URI）與讀取權限 |
| `src/com/alensw/jni/ViewerControls.java` | 工具列、播放間隔與秒數浮窗 |
| `src/com/alensw/jni/ScreensaverOpener.java` | 依序嘗試 XPhoto 與系統圖片開啟選擇器 |
| `scripts/patch_core.py` | 在 App 啟動時交給 EXIF 程式快取目錄，並插入 Android 開啟／分享請求（Intent）與回傳圖片的相容處理 |
| `scripts/patch_viewer.py` | 在兩個圖片檢視控制器插入工具列及秒數浮窗呼叫，調整圖片選單 |
| `scripts/patch_defaults.py` | 開啟或回到前景時恢復顯示隱藏項目，保留原密碼驗證；首次安裝預設無轉場 |
| `scripts/patch_animation.py` | WebP 候選辨識、預覽升級與動畫排程呼叫 |
| `scripts/patch_xphoto.py` | 只替換螢幕保護圖片的開啟方法，保留既有 PNG 產生程式 |
| `scripts/patch_about.py` | 整理關於頁、貢獻者、GitHub 與 FAQ |
| `scripts/patch_normalize.py` | 統一套件、App 名稱及版本，移除原生函式庫，更新輸出目錄與更新連結 |
| `scripts/build.py` | 校驗基底 APK、編譯 Java、以 D8 轉換程式、併入修補、重建及簽署 |

D8 把 Java 編譯結果轉成 Android 的 DEX 格式；Apktool 再把新增程式轉成可合併的 smali 文字。修補腳本依已核對的基底 APK 方法與資源結構工作，遇到結構不符會停止。

## 主機測試

WebP 測試另需 Pillow 12 或更新版本（含 WebP codec），不使用使用者的私人圖片。新增時間軸與 WebP 檢查：

```sh
python3 tests/webp_host/run.py
python3 tests/animation_clock_host/run.py
```

載入路徑測試 `tests/animation_route_host/run.py` 的 `--loader-java` 需另行提供由指定 APK 反編譯的 `com/alensw/a/at.java`；原 App 方法不放在來源倉庫內。


以下測試只使用合成資料，不需要 APK、簽署金鑰或裝置：

```sh
python3 -m unittest discover -s tests -p 'test_*.py'
python3 tests/run_exif.py
python3 tests/xphoto_host/run.py
```

XPhoto 測試使用假的 Android API 檢查 15 種開啟情境與 Intent 內容；EXIF 測試使用合成 JPEG、TIFF 中繼資料與非 JPEG 拒絕案例檢查讀寫行為。也可用 `python3 tests/xphoto_host/run.py --android-jar "$ANDROID_JAR"` 檢查圖片開啟程式與 Android API 的編譯相容性。

主機測試完成後，仍需在實機檢查安裝、權限、資料夾瀏覽、圖片顯示、分享、播放間隔，以及 PNG 保存和開啟。實際 Android 權限與裝置廠商提供的圖片選擇器由裝置測試確認。

每次交付修改過的 APK 都使用未交付的版本號。已交付候選可在檔案及 SHA-256 完全相同時轉為同版本正式發行；修改 APK 後必須升版。重新建置既有來源供本機檢查時，輸出另存，不替換歷史發行檔。

預設值與手動開圖的實際方法測試需自備最終 APK 的 apktool decode 及 JADX sources；反編譯 App 程式不加入公開倉庫：

```sh
python3 tests/defaults_host/run.py --sources /path/to/jadx/sources --decoded /path/to/decoded --work-dir /path/to/private/defaults-test
python3 tests/manual_open_host/run.py --sources /path/to/jadx/sources --work-dir /path/to/private/manual-test
```

建置會在封裝前解析顯示隱藏項目啟動方法的本機成員引用，核對名稱、參數及回傳型別。Android／Java 系統 API 不在這項檢查範圍內。對最後 APK 另以 apktool 解碼後可重跑：

```sh
python3 scripts/check_local_links.py --decoded /path/to/decoded --output-json /path/to/private/startup-links.json
```

這項檢查能攔下11.0.2的錯誤呼叫；它不執行Android啟動。修改啟動流程後，正式APK仍須確認完整App能在Android啟動，不能以主機測試或JNI裝置檢查代替。

## 掃描與日期排序檢查

執行 `python tests/media_dates_host/run.py` 與 `python tests/date_sort_host/run.py` 驗證日期索引和排序。掃描檢查使用 `python tests/scan_priority_host/run.py --decoded <最終APK的解碼目錄> --baseline-decoded <11.0.3解碼目錄> --work-dir <工作目錄> --output-json <報告>`，涵蓋實際控制流與佇列案例。主機 API 替身、DEX 成員核對及手機操作各自記錄；正式發布前仍須確認同一 APK 在 Android 正常啟動。

## 看圖與設定介面檢查

`python tests/viewer_controls_host/run.py` 使用實際輔助程式測試工具列、主題與自訂秒數；`python tests/folder_path_host/run.py --decoded <原APK解碼目錄> --work-dir <工作目錄>`核對路徑編輯補丁；`python tests/about_history_host/run.py --decoded <原APK解碼目錄>`檢查離線歷史頁與完整舊文字。最終APK仍須核對原有public資源ID、新layout連結與DEX方法簽名；手機排版和鍵盤需另外驗收。
