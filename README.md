# 快圖 · QuickPic Gallery Mod II

[English](README.en.md)

快圖是由 Jason Chen 維護的 Android 圖片瀏覽器，延續 [WSTxda 的 QuickPic Gallery Mod](https://github.com/WSTxda/QP-Gallery-Releases)，加入適合閱讀器、電子墨水螢幕和新版 Android 裝置的看圖操作。手機和平板都可以使用，安裝後的名稱為「快圖」。

## 本專案的功能

### 閱讀器與電子墨水螢幕

- 圖片轉場可選「無」，首次安裝的投影片也預設無轉場，減少看圖時的畫面變化。
- 點擊圖片下半部左側看上一張、右側看下一張，單手操作時不用一直滑動。
- 可直接編輯包含或排除的資料夾路徑，決定要瀏覽哪些位置的圖片。
- 開啟 App 時預設顯示隱藏項目；已設定的隱藏項目密碼保護照常生效。

### 投影片與播放間隔

看圖時可以直接開始投影片播放。手機工具列顯示垃圾桶和播放按鈕，平板另外提供「顯示間隔」；分享放在三點選單。

首次安裝時，每張圖片預設停留 3 秒。「顯示間隔」會開啟浮動視窗，提供 3、5、10、30、60 秒及自訂秒數，讓你直接調整每張圖片停留的時間。自訂可設 1～86400 秒。投影片使用目前開啟的圖片清單，也保留順序或隨機播放的選擇。

### 螢幕保護圖片

在圖片的三點選單中選「設為」，再選螢幕保護功能，快圖會產生 **1860 × 2480 像素的 PNG**，保存到 `QuickPic-Screensaver`。

快圖優先用 XPhoto 開啟產生的圖片。裝置沒有 XPhoto、或 XPhoto 無法開啟時，可以選擇其他圖片 App。把 PNG 指定為裝置螢幕保護圖片的最後一步，請在 XPhoto 或閱讀器的系統設定中完成。

### 新版 Android 與動畫圖片

快圖支援僅能執行 64 位元 App 的裝置。動畫 WebP 和 GIF 可自動播放，靜態 WebP 照常顯示；關閉圖片轉場也會保留動畫圖片本身的播放。

## 安裝與使用

到 [GitHub Releases](https://github.com/jason840709/QuickPic-Gallery-Mod-2/releases/latest) 下載正式 APK，用手機或平板的檔案管理員點選安裝。首次開啟時，依 Android 提示授予圖片或檔案存取權限，再選擇資料夾開始看圖。

更新本專案的同套件 App 可保留設定。更早的分身使用不同套件，需各自設定。編輯資料夾路徑時，末尾不要加 `/`。

## 來源與開發者

開發者：**Jason Chen**。

上游來源為 [WSTxda／QP-Gallery-Releases](https://github.com/WSTxda/QP-Gallery-Releases)。本倉庫公開新增程式、補丁、建置腳本與測試；完整歷史 App 原始碼目前無法取得。授權與歷史署名見 [LICENSING](LICENSING.md) 和 [CONTRIBUTORS](CONTRIBUTORS.md)。

操作問題見 [FAQ](FAQ.md)，版本記錄見 [CHANGELOG](CHANGELOG.md)，開發與測試資料見 [BUILDING](BUILDING.md) 和 [VALIDATION](VALIDATION.md)。
