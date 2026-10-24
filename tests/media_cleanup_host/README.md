# MediaCleanup 主機測試

`MediaCleanup` 在目前選取檔案的原始刪除流程之後，嘗試縮小 MediaStore 索引清理的範圍。它只接受沒有 query 或 fragment 的 MediaStore Files、Images、Video 集合 URI，以及單一 `_data = ?` 絕對路徑條件。支援的 volume 名稱為 `external`、`external_primary`、十六進位 4–4 格式或標準 UUID；不認識的 URI、SQL 與參數直接交給原有 `ContentResolver.delete`，不先查詢。

helper 先確認路徑字串沒有被 `File` 正規化、canonical 與 absolute 路徑一致，再呼叫 `Os.lstat`。只有 `ENOENT` 才能進入新的查詢。第一次檢查發現檔案、資料夾或符號連結仍存在，或遇到權限不足、`ENOTDIR`、其他不確定結果時，保留原有刪除呼叫。

查詢可能阻塞，提供者也可能先刪掉舊列、再拋出例外，期間其他程式可能在同一路徑建立新檔。因此，helper 在每次新的 ID delete 或原集合精確後備之前，會再做一次相同的 canonical 與 lstat 檢查。後續檢查若發現檔案重現或無法證明缺檔，就回傳 0 並停止本次新增清理，不再把新檔交給 delete。這些重驗能攔下已發生的重建；最後一次檢查與提供者操作之間仍有時間差，無法據此宣稱兩者已具備原子性。

查詢沿用呼叫者原本的集合與 `_data = ?` 索引條件，並用 Java 字串完全相等篩選結果。只有唯一且 `_id > 0` 的精確對應列，才對原集合附加該 ID 送出一次 delete。刪除條件同時限定 ID、BINARY 路徑與原媒體類型。API 29 起再加上 `is_pending = 0`、`is_trashed = 0`，保留原集合排除這兩種列的範圍；AOSP Q 的 item URI 預設會包含它們。Images 與 Video 查詢只投影 `_id`、`_data`；Files 查詢另讀 `media_type`，因此也能處理實體已消失的 type 0 索引。這不會放寬 `FastMediaDelete` 真正刪除檔案的類型限制。

對應列不唯一、ID 無效，或查詢、讀取、關閉 cursor 失敗時，helper 仍用原集合清理，但改用 `_data = ? COLLATE BINARY`，避免 NOCASE 索引刪到只有大小寫不同的鄰居。ID delete 正常回傳 0 或負值時直接回傳，不再擴大至集合；ID delete 拋出例外後，只有重新確認缺檔才可走同一個精確路徑後備。後備的實際列數會原樣回傳，後備例外也會繼續往外拋出。

所有真正的 delete 都經過 `DeleteTiming.providerDelete`。`cleanup_verify` 量測每次 canonical 與 lstat 檢查及重驗，`cleanup_lookup` 量測查詢及 cursor 關閉，`cleanup_id_delete` 量測 ID 候選呼叫；原集合後備仍會量入 `media_delete`，並記錄固定數字的後備事件。helper 不建立或更新媒體列、不搬移檔案，也不自行 unlink；原有 delete 委派仍由提供者處理。它不操作父資料夾。

在專案根目錄使用既有 Python、JDK、SDK 36 與 D8 執行：

```sh
/opt/anaconda3/bin/python -B tests/media_cleanup_host/run.py \
  --work-dir /absolute/path/to/new-empty-work-directory \
  --output-json /absolute/path/to/private-result.json
```

`--skip-api` 只跑 host 與 SQLite，供開發時反覆檢查；完整驗證請省略這個選項。預設工具路徑沿用本機共用安裝，也能用 `--android-jar`、`--d8-jar` 指向其他已存在的版本，不需要安裝工具或啟動模擬器。

Java probe 直接編譯實際 `MediaCleanup`，在工作目錄建立暫存檔、目錄與符號連結，provider 及 lstat 錯誤由 Android 替身控制。它核對 URI、SQL、參數、缺檔證明、型別、pending／trash、cursor 關閉、provider 權限、查詢與刪除間的列變更，以及例外後部分完成。provider 替身會對匹配的暫存路徑實際 unlink；另外在查詢期間重建實體檔，以及在 ID 刪除部分完成後重建同路徑新檔與新 ID，再拋出例外，確認後續重驗保留新檔內容與索引。Mac 的磁碟可能不區分大小寫，因此大小寫鄰居以 provider 列及真實 SQLite 檢查，實體保留控制使用前綴檔與其他未選檔案。

SQLite 先重現 NOCASE 原路徑後備會刪掉三個大小寫鄰居，再驗證 BINARY 只清理精確路徑，並核對 `%`、`_`、引號、前綴、重複列、媒體類型、pending／trash 與 volume 的控制。完整命令另以真實 API 36 編譯四個相關 helper，再用最低 API 21 的 D8 轉換並核對公開方法的整數回傳簽名。

這些檢查涵蓋主機安全案例、SQL 行為與編譯整合。Android 提供者的實際行為、實卡清理時間與速度改善仍需在裝置上量測。


11.0.12：只有原集合查詢完整成功且沒有大小寫精確的對應列，才直接回0、不呼叫delete；新增`no_exact_row_noop`。空集合但query／null cursor／move／close失敗仍走原精確後備。case-only row、pending／trash列與查詢後重建的實檔保持原狀；仍有唯一列的幽靈索引繼續用ID清理，ID例外後備與11一致。這裡的「沒有列」只描述原集合的查詢快照，不表示整個媒體庫沒有紀錄。
