// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolated, opt-in bridge to the verified OEM single-image import method.
 * No private-file writes, sleep commands, retries or claimed commit acknowledgement.
 */
public final class BigmeScreensaver {
    private static final String PACKAGE = "com.xrz.standby";
    private static final String AUTHORITY = "com.xrz.LogoProvider";
    private static final String PROVIDER = "com.xrz.settings.provider.LogoProvider";
    private static final String CERT = "ec4bb6c0f4c281829f7ec09a20d3350fd0d9486fa896b2aa8f27e01ebc85bdf4";
    private static final String APK = "d1cb4a72d337e55e3f523a6760d9017db11dd7ae58adb301e5f15172549ba215";
    private static final int WIDTH = 1860, HEIGHT = 2480;
    private static final long MAX_BYTES = 24L * 1024 * 1024;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private BigmeScreensaver() {}

    /** Main-thread menu entry; metadata checks do not acquire/start the OEM provider. */
    public static void confirm(final Context context, final String source) {
        if (context == null) return;
        if (ACTIVE.get()) { message(context, "正在處理屏保圖片，請稍候"); return; }
        try {
            target(context, false);
            if (source == null || !new File(source).isFile() || !new File(source).canRead()) {
                throw new IOException("來源圖片無法讀取");
            }
            new AlertDialog.Builder(context)
                .setTitle("設置為 B1051C 屏保")
                .setMessage("將另存為 1860×2480 PNG，再交給系統套用。\n\n"
                    + "此功能尚待實機驗證；系統沒有提供寫入確認與復原功能。快圖不會自動關閉螢幕。")
                .setNegativeButton("取消", null)
                .setPositiveButton("交給系統套用", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        start(context.getApplicationContext(), source);
                    }
                }).show();
        } catch (Exception error) {
            message(context, error.getMessage() == null ? "無法開啟屏保設定" : error.getMessage());
        }
    }

    private static void start(final Context context, final String source) {
        if (!ACTIVE.compareAndSet(false, true)) {
            message(context, "正在處理屏保圖片，請稍候"); return;
        }
        try {
            Thread worker = new Thread(new Runnable() {
                @Override public void run() { apply(context, source); }
            }, "QuickPic-StandbyImport");
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
            message(context, "正在準備並檢查屏保 PNG…");
        } catch (Exception error) {
            ACTIVE.set(false); message(context, "無法啟動圖片處理");
        }
    }

    private static void apply(Context context, String source) {
        boolean submitted = false;
        Uri media = null;
        ContentProviderClient client = null;
        try {
            target(context, true);
            if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                throw new IOException("內部共用儲存空間尚未就緒");
            }
            // Separate from the legacy .nomedia export folder; never pass the original.
            File directory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "QuickPic-Standby-Import-" + context.getPackageName());
            noHiddenAncestors(directory);
            File picture = ScreensaverImage.prepare(source, WIDTH, HEIGHT, directory, false);
            validateImage(picture);
            media = publish(context, picture);
            context.grantUriPermission(PACKAGE, media, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // The importer needs a raw path. Media visibility/grant improves compatibility,
            // but does NOT prove another UID's raw-path access or atomic destination writes.
            target(context, false);
            validateImage(picture);
            if (new StatFs(Environment.getDataDirectory().getAbsolutePath()).getAvailableBytes()
                    < picture.length() + 64L * 1024 * 1024) {
                throw new IOException("系統儲存空間不足，已停止套用");
            }
            client = context.getContentResolver().acquireContentProviderClient(
                    Uri.parse("content://" + AUTHORITY));
            if (client == null) throw new IOException("無法連接原廠屏保元件");
            // One call only. OEM code deletes this disposable input after copying.
            // Any result/exception after this boundary is an UNKNOWN commit outcome.
            submitted = true;
            client.call("set_screensaver", picture.getAbsolutePath(), null);
            message(context, "設定請求已送出；系統未提供結果確認，尚未驗證套用成功");
        } catch (OutOfMemoryError error) {
            message(context, submitted ? "系統套用結果不明，請勿連續重試"
                    : "記憶體不足，已停止套用屏保");
        } catch (Exception error) {
            message(context, submitted ? "系統套用結果不明，請勿連續重試"
                    : (error.getMessage() == null ? "檢查未通過，尚未送出屏保設定" : error.getMessage()));
        } finally {
            if (client != null) try { client.release(); } catch (Exception ignored) {}
            if (media != null) try {
                context.revokeUriPermission(media, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            ACTIVE.set(false);
        }
    }

    private static void target(Context context, boolean verifyApk) throws Exception {
        // Build.MODEL is generic on this firmware; use the same read-only properties as OEM.
        if (!"B1051C".equals(property("ro.build.versionCode"))
                || !"B1051C_R_4.9.0".equals(property("ro.build.versionName"))) {
            throw new IOException("此入口目前只支援已核對的 B1051C 4.9.0 系統");
        }
        PackageManager pm = context.getPackageManager();
        ProviderInfo provider = pm.resolveContentProvider(AUTHORITY, 0);
        if (provider == null || !provider.enabled || !provider.exported
                || !PACKAGE.equals(provider.packageName) || !PROVIDER.equals(provider.name)
                || !AUTHORITY.equals(provider.authority) || provider.applicationInfo == null
                || !provider.applicationInfo.enabled || provider.applicationInfo.uid != 1000
                || (provider.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                || provider.readPermission != null || provider.writePermission != null) {
            throw new IOException("原廠屏保元件與已核對版本不符，已停止套用");
        }
        PackageInfo info = pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNATURES);
        if (info.versionCode != 11115 || info.signatures == null || info.signatures.length != 1
                || !CERT.equals(hex(MessageDigest.getInstance("SHA-256")
                        .digest(info.signatures[0].toByteArray())))) {
            throw new IOException("原廠屏保版本或簽章不符，已停止套用");
        }
        if (verifyApk && !APK.equals(digest(new File(provider.applicationInfo.sourceDir)))) {
            throw new IOException("原廠屏保程式內容已變更，需要重新核對相容性");
        }
    }

    private static String property(String key) throws Exception {
        return (String)Class.forName("android.os.SystemProperties")
                .getMethod("get", String.class, String.class).invoke(null, key, "");
    }

    private static void noHiddenAncestors(File directory) throws IOException {
        for (File f = directory; f != null; f = f.getParentFile()) {
            if (new File(f, ".nomedia").exists()) {
                throw new IOException("圖片匯入資料夾被 .nomedia 隱藏，已停止套用");
            }
        }
    }

    private static void validateImage(File picture) throws IOException {
        ScreensaverPng.verify(picture, WIDTH, HEIGHT, MAX_BYTES);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(picture.getAbsolutePath(), options);
        try {
            if (bitmap == null || bitmap.getWidth() != WIDTH || bitmap.getHeight() != HEIGHT
                    || bitmap.hasAlpha()) throw new IOException("屏保 PNG 無法完整解碼");
        } finally { if (bitmap != null) bitmap.recycle(); }
    }

    private static Uri publish(Context context, final File picture) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final Uri[] result = new Uri[1];
        MediaScannerConnection.scanFile(context, new String[]{picture.getAbsolutePath()},
                new String[]{"image/png"}, new MediaScannerConnection.OnScanCompletedListener() {
                    @Override public void onScanCompleted(String path, Uri uri) {
                        if (picture.getAbsolutePath().equals(path)) result[0] = uri;
                        done.countDown();
                    }
                });
        if (!done.await(10, TimeUnit.SECONDS) || result[0] == null
                || !"content".equals(result[0].getScheme())
                || !"media".equals(result[0].getAuthority())) {
            throw new IOException("系統尚未確認圖片可用，未送出屏保設定");
        }
        return result[0];
    }

    private static String digest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        try (FileInputStream input = new FileInputStream(file)) {
            int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) { text.append(Character.forDigit((b >>> 4)&15,16));
            text.append(Character.forDigit(b&15,16)); }
        return text.toString();
    }
    private static void message(final Context context, final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }
}
