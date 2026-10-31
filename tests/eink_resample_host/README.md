# 純 Java 取樣核心檢查

以既有 JDK 將 `src/com/alensw/jni/EinkResample.java` 與本資料夾的 `ResampleProbe.java` 編譯至外部暫存目錄，執行 `ResampleProbe <合成圖片輸出目錄>`。不需要 Android runtime 或額外套件。

本測試以36組高頻灰階載波比較雙線性、面積與 Lanczos3 的殘餘混疊，也檢查等尺寸、透明度、線條貢獻與尺寸保護。RMS 是與理想均值127.5的偏差，排除8像素邊界，再對36組等權平均；它不是漫畫觀感或實體電子紙分數。
