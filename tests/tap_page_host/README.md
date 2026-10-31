# 點擊翻頁設定與區域檢查

```sh
python3 -B tests/tap_page_host/run.py
```

入口使用 `JAVA_HOME/bin`，未指定時使用命令列的 `javac`／`java`。不需 Android 裝置或額外套件。

`LayoutProbe` 執行正式 `TapPageLayout.action`，核對七個配置、停用、舊下方兩角的整數邊界、奇數尺寸、無效座標、中心區域、橫直向及三種動作可達性。網格檢查只確認動作範圍與區域可達性，不把範例點當成所有連續座標的完整證明。

`PreferenceProbe` 執行正式 `TapPagePreference` 的讀取及對話框回呼，檢查首次／缺設定預設停用、錯型別／非法值、讀取不寫入、未儲存選擇不生效、儲存後摘要與設定保留，以及英文、繁中、簡中標籤。Android UI 使用測試替身，不能取代實際畫面、文字排版、觸控分派或螢幕旋轉測試。

本組檢查不讀寫原廠服務，也不驗證滑動、縮放與選單事件；這些應在整合後的 APK 檢查。
