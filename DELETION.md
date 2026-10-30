# 刪除圖片與資料夾

快圖分別處理實體檔案刪除與媒體索引清理。單張與多選逐張執行，保留進度及取消操作；整個資料夾則以完整目錄為單位處理。

## 圖片刪除

記憶卡路徑已有對應寫入授權，且媒體庫有唯一、精確的圖片或影片紀錄時，`FastMediaDelete` 可對該筆 ID 發出刪除要求。條件同時限制原路徑與媒體類型；之後以 `lstat` 的 `ENOENT` 確認實檔消失。刪除回傳正列數或清單項目消失，都不能取代實檔確認。

Android 10 在直接檔案／SAF 後備前嘗試這條路徑；較新的 Android 保留直接檔案刪除優先。沒有合適紀錄、缺少授權或無法確認刪除時，仍採用原有方式。刪除不會更動隱藏與 `.nomedia` 設定，也不會先建立索引、改媒體類型或搬移圖片。

## 媒體索引清理

本機直接刪除成功後，`MediaCleanup` 先確認實檔已不存在，再查原來的 Images、Video 或 Files 集合。若有唯一精確紀錄，就用相同類型與 volume 的單筆 ID 清理，同時保留 ID、`BINARY` 精確路徑、媒體類型與原 pending／trashed 範圍限制。

查詢、遍歷及 cursor 關閉都完整成功，但沒有精確對應列時，直接結束清理。這可省去索引已同步移除後的重複請求。若查詢不確定、回傳 null、cursor 出錯、有重複列或無效 ID，則保留受精確路徑限制的後備流程。

單筆 ID 呼叫正常回傳 0 或負值時，不擴大重試；呼叫拋出例外後才可能走精確後備。每次新的清理操作前都重新確認缺檔；路徑已重建或無法確認時停止額外清理。檢查與系統提供者操作並非原子，因此跨程序競態仍有可能發生。

## 請求範圍與等待時間

精確單筆 URI 可讓媒體提供者縮小處理目標。[AOSP Android 10 MediaProvider](https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/tags/android-10.0.0_r1/src/com/android/providers/media/MediaProvider.java) 的刪除流程也會依 URI 執行權限檢查與查詢。實際耗時仍取決於裝置的媒體提供者、儲存空間及索引狀態，不能僅憑使用單筆 URI 就保證固定速度。

[SQLite 比較定序](https://www.sqlite.org/datatype3.html#collating_sequences) 會影響條件與索引的匹配方式。`BINARY` 精確路徑與 `NOCASE` 索引並非同一定序；以 ID 搭配精確路徑，可先縮小到主鍵目標，再核對路徑。這是條件設計的依據，不代表已取得每台裝置的實際查詢計畫。

## 整個資料夾

有對應 SAF 授權且路徑、目錄檢查通過時，快圖交給系統刪除完整目錄，減少逐張發送請求。系統仍須處理每個檔案，並非只做一次磁碟操作。普通檔案路徑則完整遞迴刪除，再以受路徑邊界限制的請求清理索引。

範圍包括非圖片、隱藏檔、子資料夾及父目錄；成功後同步清理相關子相簿與快取。未選資料及相鄰目錄保留。目錄別名不會擴大操作範圍，符號連結也不會被當成可任意跟隨的目錄。

失敗或取消時保留未完成項目；取消後不再開始下一個資料夾，已送進系統的單次要求則無法由 App 中途打斷。

整目錄操作參考 [Ehviewer 的下載刪除入口](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/dfe356244d234c91f2bf65af60903f80b407a838/app/src/main/java/com/hippo/ehviewer/ui/scene/download/part/DownloadBatchActions.kt#L280-L361)。本專案自行實作檢查與回傳處理，沒有複製其刪除程式碼。

## 正式版的提示與工作範圍

正式版保留一般刪除進度、取消、權限請求與真正失敗的提示，不產生、保存或顯示診斷報告，也不顯示成功統計。

`DeleteTiming` 的工作範圍同時控制單檔清理派發，因此仍保留既有 worker 介面及 `ACTIVE`／`fileOpen` 狀態。`complete` 清除範圍後回傳 null，`finish` 與 `save` 直接返回。這讓清理流程持續運作，而不啟用報告功能。

驗證方式見 [VALIDATION](VALIDATION.md)，建置方式見 [BUILDING](BUILDING.md)。
