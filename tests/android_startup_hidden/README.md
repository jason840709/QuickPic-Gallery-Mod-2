# 啟動隱藏項目檢查

這個 instrumentation 程式在獨立 Android 模擬器的完整 App 內執行，使用合成偏好，不操作真實照片。它會更改密碼、隱藏、頁碼和投影片設定，請勿在主要安裝直接執行。

先用與受測 App 相同的簽章建置測試 APK。seed 階段在 28 保存測試偏好，再覆蓋安裝 29，執行 default；之後每次先 force-stop 受測 App，依序執行 off 與 on，才能核對冷啟動。default 會從原生 Security 子頁點擊新開關保存關閉；off 檢查手動選單與真實密碼回呼並寫入開啟，on 再確認冷啟動結果。

    adb -s "$TEST_DEVICE" shell am instrument -w -e stage seed local.quickpic.hidden29/local.quickpic.hidden29.Probe
    # 用同簽章29更新28後，先 force-stop 受測App，再執行：
    adb -s "$TEST_DEVICE" shell am instrument -w -e stage default local.quickpic.hidden29/local.quickpic.hidden29.Probe
    # 各階段前均先 force-stop：
    adb -s "$TEST_DEVICE" shell am instrument -w -e stage off local.quickpic.hidden29/local.quickpic.hidden29.Probe
    adb -s "$TEST_DEVICE" shell am instrument -w -e stage on local.quickpic.hidden29/local.quickpic.hidden29.Probe

成功結果回傳 INSTRUMENTATION_CODE: -1；失敗會有 failure 或 Process crashed。畫面與結果存於受測App外部快取 hidden29-results。一般命令的exit code不能代替instrumentation結果。
