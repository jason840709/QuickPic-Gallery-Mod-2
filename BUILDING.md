# 在本機建置

建置腳本會解開你提供的 Android 安裝檔（APK）基底，把新增 Java 程式編譯成 Android 可執行程式，再併入 App，修補功能、重建資源並簽署。完整歷史 App 的畫面與主要程式由基底 APK 提供；公開來源的範圍與授權見 [LICENSING.md](LICENSING.md)。

## 取得基底 APK

從 [11.0.0 發行頁](https://github.com/jason840709/QuickPic-Gallery-Mod-2/releases/tag/v11.0.0) 下載 `QuickPic-Gallery-Mod-2-11.0.0.apk`。建置腳本以 APK 裡的既有圖庫程式與資源為基底，再把本倉庫的 Java 相容層和功能程式編譯進去。

11.0.0 發行 APK 的 SHA-256 是：

```text
1eb348eab476fda8f4be42b64c438767d60a65daa61aebc9b6c818c0bdf10405
```

SHA-256 是檔案內容的校驗值。腳本會先核對固定基底，結構不符時停止，不將補丁套用到其他同名 APK。

## 所需工具

- Python 3.9 或更新版本。
- JDK 17 或更新版本，`java` 與 `javac` 可由命令列執行。
- Android SDK Platform 36 的 `android.jar`。
- Android SDK Build Tools 34.0.0 的 `apksigner.jar` 與 `zipalign`，以及 36.0.0 的 `d8.jar`。
- Apktool 2.11.1，可執行檔或 JAR。
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
APKSIGNER_JAR="$SDK_DIR/build-tools/34.0.0/lib/apksigner.jar"
ZIPALIGN="$SDK_DIR/build-tools/34.0.0/zipalign"
APKTOOL="$(command -v apktool)"
SIGNING_KEYSTORE="$HOME/.quickpic-signing/local.p12"
SIGNING_PASSWORD_FILE="$HOME/.quickpic-signing/password.txt"
OUTPUT_APK="$HOME/Downloads/QuickPic-local.apk"
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

建置產物的 App 名稱為「快圖」；版本與版本代碼由 `scripts/build.py` 的 `VERSION_NAME`、`VERSION_CODE` 決定。預設套件名稱為 `io.github.jason840709.quickpic`，可用 `--package` 指定自己的測試套件。不同套件的偏好設定各自保存，舊 App 資料不會自動移入。

## 每一步修改的位置

| 檔案 | 工作 |
| --- | --- |
| `src/com/alensw/jni/JniUtils.java` | 替代舊原生介面（JNI），處理掃描與 GIF 等功能 |
| `src/com/alensw/jni/QuickPicExif.java` | 讀寫圖片的拍攝中繼資料（EXIF），並在寫入前建立備份 |
| `src/com/alensw/jni/CompatIntents.java` | 處理傳給外部 App 或回傳給呼叫者的圖片位址（URI）與讀取權限 |
| `src/com/alensw/jni/ViewerControls.java` | 工具列、播放間隔與秒數浮窗 |
| `src/com/alensw/jni/ScreensaverOpener.java` | 以可授權的 content URI 將 PNG 交給系統 App 選擇器 |
| `src/com/alensw/jni/ScreensaverOptions.java`、`ScreensaverMenu.java` | 保存選項可見狀態，固定選單顯示時的動作對應 |
| `src/com/alensw/jni/ScreensaverDevice.java` | 僅在乾淨首次安裝選擇屏保預設 |
| `src/com/alensw/jni/BigmeScreensaver.java`、`BooxScreensaver.java` | 檢查支援的原廠元件，處理各自的直接屏保設定入口 |
| `scripts/patch_core.py` | 在 App 啟動時交給 EXIF 程式快取目錄，並插入 Android 開啟／分享請求（Intent）與回傳圖片的相容處理 |
| `scripts/patch_viewer.py` | 在兩個圖片檢視控制器插入工具列及秒數浮窗呼叫，調整圖片選單 |
| `scripts/patch_defaults.py` | 開啟或回到前景時恢復顯示隱藏項目，保留原密碼驗證；首次安裝預設無轉場 |
| `scripts/patch_animation.py` | WebP 內容辨識、預覽升級與動畫排程呼叫 |
| `scripts/patch_xphoto.py` | 更新既有 XPhoto 開啟接點 |
| `scripts/patch_screensaver.py` | 串接尺寸／選項設定、選單動作及首次初始化 |
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

圖片開啟路由測試使用 Android API 替身檢查 App 選擇器、Intent、URI 與授權內容；EXIF 測試使用合成 JPEG、TIFF 中繼資料與非 JPEG 拒絕案例檢查讀寫行為。也可用 `python3 tests/xphoto_host/run.py --android-jar "$ANDROID_JAR"` 檢查圖片開啟程式與 Android API 的編譯相容性。

主機測試完成後，仍需在實機檢查安裝、權限、資料夾瀏覽、圖片顯示、分享、播放間隔，以及 PNG 保存和開啟。實際 Android 權限與裝置廠商提供的圖片選擇器由裝置測試確認。

修改程式並發布新 APK 時，請更新版本名稱與版本代碼。重建既有來源供本機檢查時，輸出另存，保留原發行檔及其校驗值。

預設值與手動開圖的實際方法測試需自備最終 APK 的 apktool decode 及 JADX sources；反編譯 App 程式不加入公開倉庫：

```sh
python3 tests/defaults_host/run.py --sources /path/to/jadx/sources --decoded /path/to/decoded --work-dir /path/to/private/defaults-test
python3 tests/manual_open_host/run.py --sources /path/to/jadx/sources --work-dir /path/to/private/manual-test
```

建置會在封裝前解析顯示隱藏項目啟動方法的本機成員引用，核對名稱、參數及回傳型別。Android／Java 系統 API 不在這項檢查範圍內。對最後 APK 另以 apktool 解碼後可重跑：

```sh
python3 scripts/check_local_links.py --decoded /path/to/decoded --output-json /path/to/private/startup-links.json
```

這項檢查核對方法名稱、參數及回傳型別，但不執行 Android 啟動流程。修改啟動程式後，仍須安裝最終 APK 並確認完整 App 能啟動。

## 掃描與日期排序檢查

執行 `python tests/media_dates_host/run.py` 與 `python tests/date_sort_host/run.py` 驗證日期索引和排序。掃描檢查使用 `python tests/scan_priority_host/run.py --decoded <最終APK的解碼目錄> --baseline-decoded <比較基底的解碼目錄> --work-dir <工作目錄> --output-json <報告>`，涵蓋實際控制流與佇列案例。主機 API 替身、DEX 成員核對及手機操作各自記錄；正式發布前仍須確認同一 APK 在 Android 正常啟動。

## 看圖與設定介面檢查

`python tests/viewer_controls_host/run.py` 使用實際輔助程式測試工具列、主題與自訂秒數；`python tests/folder_path_host/run.py --decoded <原APK解碼目錄> --work-dir <工作目錄>`核對路徑編輯補丁；`python tests/about_history_host/run.py --decoded <原APK解碼目錄>`檢查離線歷史頁、最新／完整紀錄兩部分，以及將舊E-Ink內容歸入11.0.0後的功能保全。最終APK仍須核對原有public資源ID、新layout連結與DEX方法簽名；手機排版和鍵盤需另外驗收。

投影片間隔視窗使用可捲動的單一內容區。選項按鈕直接套用已保存的秒數，輸入欄的新值只由「套用自訂」保存；主機測試分別核對這兩種操作及1～86400秒範圍。輸入欄的左邊界取自實際主題單選按鈕的文字內距，工具列的計時器向量沿用原資源ID。實際螢幕、字體放大與鍵盤顯示仍需裝置確認。

## 刪除流程檢查

`tests/folder_delete_host/run.py`核對整樹、授權範圍、取消、失敗、連結與MediaStore批次範圍；`tests/file_delete_host/run.py`核對raw／SAF旗標、原方法回退與實際smali組譯。兩者以`--decoded`指定正確基底，完整參數見各入口`--help`。最終APK需核對新增方法完整DEX描述符，以及普通MediaStore支路保留。Android測試應確認實體檔案、媒體索引和相鄰資料夾，不能僅看列表消失。方法說明見[DELETION](DELETION.md)。


## 刪除範圍與停用診斷

正式版保留`DeleteTiming`的35個既有公開方法，讓原worker與wrapper接線不變；ACTIVE／fileOpen仍限制單檔`MediaCleanup`派發。診斷常數固定關閉，`complete`移除scope後回null，`finish`與`save`沒有顯示或寫檔。不能直接移除begin呼叫，否則會連同本機ID清理一起停用。

`tests/delete_timing_host/run.py --help`列出production靜默模式的主機、真SDK與D8參數。它驗IO回傳／例外、正常／取消／throw的scope、沒有報告UI或保存，以及薄MediaCleanup替身的派發；演算法本身由`tests/media_cleanup_host/run.py`與`tests/fast_media_delete_host/run.py`驗證。兩個入口使用`ANDROID_HOME`或`ANDROID_SDK_ROOT`尋找SDK，也能用參數明確指定既有android.jar及D8。

完整刪除機制見[DELETION](DELETION.md)。最終APK仍需驗證本機單筆收尾、記憶卡快速路徑、混合內容整夾刪除、未選檔案保留、沒有診斷視窗，以及舊私有報告沒有被更新。


## 閱讀器模式

`patch_reader.py` 在既有功能補丁後、套件正規化之前，將 `ReaderMode.Host` 接到兩套看圖控制器，並為圖片結果加入 `Frame` 介面。新增 Java 程式仍使用相同 SDK／D8 建置；不需要新的圖片框架或原生函式庫。載入請求、引用與取消規則見 [READER_MODE](READER_MODE.md)。

```sh
python3 -B tests/reader_mode_host/run.py --output-json /tmp/reader-mode.json
```

補丁只支援已核對的固定基底。它會先檢查所有切點，再寫入解碼工作副本；組譯前可重套，重組譯後 label 會改名，不能把重套到最終重新解碼檔的結果當成相同檢查。

屏保尺寸與裁切設計見 [SCREENSAVER.md](SCREENSAVER.md)。裁切、EXIF、PNG 與失敗清理的主機檢查：`python3 tests/screensaver_host/renderer/run.py --android-jar "$ANDROID_JAR"`；使用 Java2D 替身，實際 Android 解碼另驗證。


## 屏保選項與首次安裝

```sh
python3 -B tests/screensaver_options_host/run.py
```

這組主機測試執行實際選項與選單輔助程式，核對可見項目的動作對應、全部隱藏、取消不保存、更新保留設定，以及僅乾淨首次安裝選擇預設。Android 介面、偏好與裝置判斷使用測試替身；原廠套用結果需另在對應裝置確認。
