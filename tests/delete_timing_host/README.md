# 刪除流程的靜默模式測試

11.0.13 以 `DIAGNOSTICS_ENABLED = false` 關閉刪除診斷。`begin` 仍建立工作執行緒的 Session，`beginFile`／`endFile` 仍維持逐檔區段，因為 `mediaDelete` 需要這兩個狀態才能交給既有的 `MediaCleanup` 清理。`complete` 移除 Session 後回傳 `null`；`finish` 和私有 `save` 直接返回。

這組主機測試涵蓋：

- File、SAF、MediaStore 的回傳值、單次委派、參數參照，以及原本的 checked exception、SecurityException 和 Error 保持一致。
- 只有 ACTIVE Session 且逐檔區段開啟時才派發到 `MediaCleanup`；主要共用儲存路徑也保留派發。區段外、區段關閉及完成後都沿用原 provider 呼叫。
- 正常完成、取消、例外後的 finally、重新 begin 與不同執行緒，都不留下可誤用的 Session。
- 逐檔 hook 仍可呼叫 205 次，由呼叫端維持每張進度；helper 不引入批次進度。
- `complete` 一律回傳 `null`。測試另外用反射建立 synthetic Report，確認 `finish` 即使收到非空報告也不讀取 UI context、不顯示診斷視窗、不寫 clipboard、不建立 Toast。
- `complete`／`finish` 不寫私有報告；測試直接呼叫私有 `save` 防線也不會呼叫 `openFileOutput`。預先放置的舊 `delete-timing-latest.json` 內容與修改時間都維持原值；程式不自動刪除歷史檔。
- 真 SDK36 同時編譯 DeleteTiming、FolderDelete、FastMediaDelete、MediaCleanup，通過 min-api21 D8，並精確比對 35 個公開方法描述符和 SAF checked exception。額外檢查編譯後 bytecode，確認 `complete` 只有清除 scope 與回傳 null，`finish`／`save` 立即返回。

Timing host 的 `MediaCleanup` 是只呼叫 `providerDelete` 的派發替身，**不驗證 MediaCleanup 的資格判斷、查詢、實檔缺少證據或實際清理演算法**。這些行為由 `tests/media_cleanup_host` 驗證。真 SDK 編譯與 D8 使用四個實際 helper。先前的診斷輸出測例保留在 Git 歷史，不屬於目前的 production 驗收入口。

執行時提供既有 Android36 `android.jar` 與 D8 jar；沿用既有 JDK 和研究 Python，不安裝套件或工具。

```sh
/opt/anaconda3/bin/python -B tests/delete_timing_host/run.py \
  --android-jar "$ANDROID_HOME/platforms/android-36/android.jar" \
  --d8-jar /path/to/existing/d8.jar \
  --work-dir /path/to/new-empty-work-directory \
  --output-json /path/to/production-result.json
```

不指定 `--work-dir` 時使用暫存目錄，結束後清除。指定空白工作目錄時，保留 host class、SDK class、D8 結果、bytecode 與合成驗收收據。這些收據不是 App 的診斷報告。這組測試不證明最終 APK 內整合、Android 畫面、記憶卡效能或實卡加速；最終 APK 和 Android 執行需另行驗證。
