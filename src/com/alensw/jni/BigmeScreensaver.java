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
    private static final String CERT = "ec4bb6c0f4c281829f7ec09a20d3350fd0d9486fa896b2aa8f27e01ebc85bdf4";
    private static final class Target {
        final String firmware, packageName, authority, providerClass, apkSha256;
        final int versionCode;
        Target(String firmware, String packageName, String authority,
                String providerClass, int versionCode, String apkSha256) {
            this.firmware = firmware; this.packageName = packageName;
            this.authority = authority; this.providerClass = providerClass;
            this.versionCode = versionCode; this.apkSha256 = apkSha256;
        }
    }
    private static final Target CURRENT = new Target("B1051C_R_4.9.0",
            "com.xrz.standby", "com.xrz.LogoProvider",
            "com.xrz.settings.provider.LogoProvider", 11115,
            "d1cb4a72d337e55e3f523a6760d9017db11dd7ae58adb301e5f15172549ba215");
    private static final Target LEGACY = new Target("B1051C_R_4.8.8",
            "com.xrz.settings", "com.xrz.SettingProvider",
            "com.xrz.settings.provider.SettingProvider", 40105,
            "d3eae14d99e86861c082c1d6e1b1d07de16207fb383176dd279b1fb721ada24d");
    private static final int WIDTH = 1860, HEIGHT = 2480;
    private static final long MAX_BYTES = 24L * 1024 * 1024;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static Toast currentToast;
    private BigmeScreensaver() {}

    /** Main-thread menu entry; metadata checks do not acquire/start the OEM provider. */
    public static void confirm(final Context context, final String source) {
        if (context == null) return;
        if (ACTIVE.get()) { message(context, "正在處理屏保圖片，請稍候"); return; }
        try {
            final Target selected = target(context, false);
            if (source == null || !new File(source).isFile() || !new File(source).canRead()) {
                throw new IOException("來源圖片無法讀取");
            }
            new AlertDialog.Builder(context)
                .setTitle("設置為 B1051C 屏保")
                .setMessage("將另存為 1860x2480 PNG 再交給系統套用")
                .setNegativeButton("取消", null)
                .setPositiveButton("確認", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        start(context.getApplicationContext(), source, selected);
                    }
                }).show();
        } catch (Exception error) {
            message(context, error.getMessage() == null ? "無法開啟屏保設定" : error.getMessage());
        }
    }

    private static void start(final Context context, final String source, final Target selected) {
        if (!ACTIVE.compareAndSet(false, true)) {
            message(context, "正在處理屏保圖片，請稍候"); return;
        }
        try {
            Thread worker = new Thread(new Runnable() {
                @Override public void run() { apply(context, source, selected); }
            }, "QuickPic-StandbyImport");
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
            message(context, "正在處理圖片…");
        } catch (Exception error) {
            ACTIVE.set(false); message(context, "無法啟動圖片處理");
        }
    }

    private static void apply(Context context, String source) {
        apply(context, source, null);
    }

    private static void apply(Context context, String source, Target confirmed) {
        boolean submitted = false;
        Uri media = null;
        ContentProviderClient client = null;
        try {
            // Select once; confirmation and every subsequent check stay on this target.
            Target selected = confirmed == null ? target(context, true) : confirmed;
            if (confirmed != null) target(context, selected, true);
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
            context.grantUriPermission(selected.packageName, media, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // The importer needs a raw path. Media visibility/grant improves compatibility,
            // but does NOT prove another UID's raw-path access or atomic destination writes.
            target(context, selected, false);
            validateImage(picture);
            if (new StatFs(Environment.getDataDirectory().getAbsolutePath()).getAvailableBytes()
                    < picture.length() + 64L * 1024 * 1024) {
                throw new IOException("系統儲存空間不足，已停止套用");
            }
            client = context.getContentResolver().acquireContentProviderClient(
                    Uri.parse("content://" + selected.authority));
            if (client == null) throw new IOException("無法連接原廠屏保元件");
            // One call only. OEM code deletes this disposable input after copying.
            // Any result/exception after this boundary is an UNKNOWN commit outcome.
            submitted = true;
            // Legacy normally returns status=-1; neither target provides commit acknowledgement.
            client.call("set_screensaver", picture.getAbsolutePath(), null);
            target(context, selected, false);
            // The audited importer removes its disposable input only after copying.
            // Detect a swallowed copy failure instead of displaying success on every return.
            if (picture.exists()) throw new IOException("設定屏保失敗");
            message(context, "設定成功");
        } catch (OutOfMemoryError error) {
            message(context, "記憶體不足，無法完成屏保設定");
        } catch (Exception error) {
            message(context, submitted ? "設定屏保時發生錯誤"
                    : (error.getMessage() == null ? "無法設定屏保" : error.getMessage()));
        } finally {
            if (client != null) try { client.release(); } catch (Exception ignored) {}
            if (media != null) try {
                context.revokeUriPermission(media, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            ACTIVE.set(false);
        }
    }

    /** Read-only capability check: no APK hashing, provider acquisition or OEM calls. */
    public static boolean isSupported(Context context) {
        if (context == null) return false;
        try { target(context, false); return true; }
        catch (Exception unavailable) { return false; }
        catch (LinkageError unavailable) { return false; }
    }

    private static Target target(Context context, boolean verifyApk) throws Exception {
        String firmware = property("ro.build.versionName");
        Target selected = CURRENT.firmware.equals(firmware) ? CURRENT
                : (LEGACY.firmware.equals(firmware) ? LEGACY : null);
        if (selected == null) throw new IOException("此入口目前只支援已核對的 B1051C 4.8.8／4.9.0 系統");
        target(context, selected, verifyApk);
        return selected;
    }

    private static void target(Context context, Target selected, boolean verifyApk) throws Exception {
        // Build.MODEL is generic on these firmwares. Never reselect another importer mid-operation.
        if (!"B1051C".equals(property("ro.build.versionCode"))
                || !selected.firmware.equals(property("ro.build.versionName"))) {
            throw new IOException("系統版本與已確認的屏保元件不符，已停止套用");
        }
        PackageManager pm = context.getPackageManager();
        ProviderInfo provider = pm.resolveContentProvider(selected.authority, 0);
        if (provider == null || !provider.enabled || !provider.exported
                || !selected.packageName.equals(provider.packageName)
                || !selected.providerClass.equals(provider.name)
                || !selected.authority.equals(provider.authority) || provider.applicationInfo == null
                || !provider.applicationInfo.enabled || provider.applicationInfo.uid != 1000
                || (provider.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                || provider.readPermission != null || provider.writePermission != null) {
            throw new IOException("原廠屏保元件與已核對版本不符，已停止套用");
        }
        PackageInfo info = pm.getPackageInfo(selected.packageName, PackageManager.GET_SIGNATURES);
        if (info.versionCode != selected.versionCode || info.signatures == null || info.signatures.length != 1
                || !CERT.equals(hex(MessageDigest.getInstance("SHA-256")
                        .digest(info.signatures[0].toByteArray())))) {
            throw new IOException("原廠屏保版本或簽章不符，已停止套用");
        }
        if (verifyApk && !selected.apkSha256.equals(digest(new File(provider.applicationInfo.sourceDir)))) {
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
                if (currentToast != null) currentToast.cancel();
                currentToast = Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG);
                currentToast.show();
            }
        });
    }
}
