# FastMediaDelete 主機測試

`FastMediaDelete` 只對目前選取的單一檔案嘗試 MediaStore 刪除。它先確認次要儲存空間、非資料夾、路徑沒有別名，以及現有的持續寫入樹狀授權；不符合條件就回傳 `-1`，交回原有刪除流程。

查詢以 `_data = ?` 使用現有索引，再以 Java 字串完全相等篩選。只有唯一且 `_id > 0`、`media_type` 為圖片或影片的列可進入刪除。刪除 URI 指定該列 ID，SQL 同時要求 BINARY 路徑相等及目前的媒體類型。`media_type = 0` 或未建立索引的檔案仍走原有後備；helper 不建立索引，也不修改 `.nomedia` 或顯示設定。若系統仍留下 type 1/3 的舊索引，helper 會按相同規則處理該列，沒有額外掃描每層 `.nomedia`。

成功只認刪除嘗試後 `Os.lstat` 回報 `ENOENT`，此時回傳 `0`，讓原流程繼續清理媒體索引。刪除列數大於零、只刪索引、權限不足、`ENOTDIR` 或其他無法確認的結果均回傳 `-1`。提供者可能先刪除實體檔案、尚未刪除索引列就拋出例外，因此實體確認成功後仍保留原有索引清理；helper 不回傳跳過清理的 `1`。

在專案根目錄執行，使用既有 Python、JDK、SDK 36 與 D8；不安裝工具，也不開啟模擬器：

```sh
/opt/anaconda3/bin/python -B tests/fast_media_delete_host/run.py \
  --work-dir /absolute/path/to/new-empty-work-directory \
  --output-json /absolute/path/to/private-result.json
```

`--android-jar`、`--d8-jar` 可指定已安裝的工具。預設沿用 Jason 的共用 SDK 與既有 D8；`--skip-api` 僅供 host 測試，不會產生 API 或 D8 通過的結果。

Java probe 將合成的 `/storage` 選取路徑對應到工作目錄內的真實測試檔案，Android 及提供者部分由替身控制。它直接編譯實際 `FolderDelete` 與 `FastMediaDelete`，測試授權、別名、資料夾、API 21／29、大小寫／前綴／SQL 特殊字元對照檔、重複列、type 0、無索引、只刪索引、實體刪除失敗、例外後部分完成（包含實體已刪但索引列仍保留）、無法確認的 lstat，以及查詢到刪除間變更路徑或類型。SQLite 另外驗證 NOCASE 查詢與 ID／BINARY 路徑／媒體類型條件，確認對照列保留。計時替身只接收固定數字，並核對三個階段的開始、結束及結果。

11.0.11 只將既有 guard 分支拆成匿名原因，不改原資格或刪除行為。原 43 個情境繼續核對同一結果，另外核對 guard_skip 與原因一一對應；Timing 替身只接收固定原因碼與入口分類呼叫，不記路徑。primary 仍由原次要儲存條件擋下，沒有放寬 Fast。

完整命令另以真實 API 36 編譯 `FastMediaDelete`、`FolderDelete`、`DeleteTiming`、`MediaCleanup`，以 D8 的最低 API 21 轉換，再核對 `tryDelete(Context, File)` 的整數回傳簽名。這些結果證明主機安全案例與編譯整合；Android 提供者行為、手機實卡刪除時間及速度改善仍需分別量測。
