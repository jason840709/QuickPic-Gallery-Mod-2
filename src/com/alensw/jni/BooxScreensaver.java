// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/** Calls the OEM's documented-in-DEX image import; never writes OEM private state. */
public final class BooxScreensaver {
    private static final String TAG = "QuickPic-BooxScreensaver";
    private static final String PACKAGE = "com.onyx";
    private static final String CERT_SHA256 =
            "92a5d646b797817fe1ebf9cd57734a320d71834e68bbe74b2e09e8be7c7668ec";
    private static final String ACTION = "onyx.action.SCREENSAVER";
    // AOSP's hidden Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND, also used by OEM SDK.
    private static final int INCLUDE_BACKGROUND = 0x01000000;
    private static final long MAX_PNG_BYTES = ScreensaverImage.MAX_PIXELS * 4L + 1048576L;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static Toast currentToast;
    private static int toastEpoch;

    private BooxScreensaver() {}

    /** Read-only detection. A sideloaded reader cannot satisfy this system identity. */
    @SuppressWarnings("deprecation")
    public static boolean supports(Context context) {
        if (context == null || !isOnyxManufacturer(Build.MANUFACTURER)) return false;
        try {
            PackageManager manager = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info = manager.getPackageInfo(PACKAGE, flags);
            ApplicationInfo app = info.applicationInfo;
            if (!PACKAGE.equals(info.packageName) || app == null
                    || !PACKAGE.equals(app.packageName)) return false;
            int state = manager.getApplicationEnabledSetting(PACKAGE);
            boolean enabled = app.enabled
                    && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
            Signature[] signers = Build.VERSION.SDK_INT >= 28
                    ? Api28.signers(info) : info.signatures;
            if (signers == null || signers.length != 1 || signers[0] == null) return false;
            String fingerprint = hex(MessageDigest.getInstance("SHA-256")
                    .digest(signers[0].toByteArray()));
            return matchesMetadata(Build.MANUFACTURER, app.uid, app.flags, enabled, fingerprint);
        } catch (Exception unavailable) {
            return false;
        }
    }

    /** Kept independent of Android runtime calls so the identity boundary is host-testable. */
    static boolean matchesMetadata(String manufacturer, int uid, int flags,
            boolean enabled, String certificate) {
        return isOnyxManufacturer(manufacturer) && uid == 1000 && enabled
                && (flags & (ApplicationInfo.FLAG_SYSTEM
                        | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                && CERT_SHA256.equals(certificate);
    }

    private static boolean isOnyxManufacturer(String manufacturer) {
        return manufacturer != null && "Onyx".equalsIgnoreCase(manufacturer.trim());
    }

    private static final class Api28 {
        static Signature[] signers(PackageInfo info) {
            return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        }
    }

    public static void confirm(final Context context, final String source) {
        if (context == null) return;
        if (!supports(context)) {
            message(context, "此裝置未提供相容的文石屏保服務");
            return;
        }
        if (ACTIVE.get()) return;
        if (source == null || source.length() == 0) {
            message(context, "無法讀取這張圖片");
            return;
        }
        try {
            new AlertDialog.Builder(context)
                    .setTitle("設置為文石屏保")
                    .setMessage("將以這張圖片取代目前的屏保圖片。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("確認", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            Context app = context.getApplicationContext();
                            start(app == null ? context : app, source);
                        }
                    }).show();
        } catch (RuntimeException unavailable) {
            Log.e(TAG, "Cannot open screensaver confirmation", unavailable);
            message(context, "無法開啟屏保設定");
        }
    }

    private static void start(final Context context, final String source) {
        if (!ACTIVE.compareAndSet(false, true)) return;
        try {
            // This runs on the dialog's main thread, before the worker starts.
            cancelProgress();
            currentToast = Toast.makeText(context, "正在處理圖片…", Toast.LENGTH_SHORT);
            currentToast.show();
            Thread worker = new Thread(new Runnable() {
                @Override public void run() { prepare(context, source); }
            }, "QuickPic-BooxScreensaver");
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
        } catch (RuntimeException error) {
            ACTIVE.set(false);
            cancelProgress();
            message(context, "無法啟動圖片處理");
        } catch (OutOfMemoryError error) {
            ACTIVE.set(false);
            cancelProgress();
            message(context, "記憶體不足，無法處理圖片");
        }
    }

    private static void prepare(final Context context, String source) {
        try {
            if (!supports(context)) throw new IOException("OEM unavailable");
            if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                throw new IOException("Shared storage unavailable");
            }
            int[] size = ScreensaverPreference.dimensions(context);
            // OEM uses public Pictures + basename. Preparing there avoids another copy
            // and the OEM's skip-existing-basename branch. UUID names preserve originals.
            File directory = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
            final File picture = ScreensaverImage.prepare(source, size[0], size[1],
                    directory, false);
            validate(picture, size[0], size[1]);
            main().post(new Runnable() {
                @Override public void run() { dispatch(context, picture); }
            });
        } catch (OutOfMemoryError error) {
            failed(context, "記憶體不足，無法處理圖片", error);
        } catch (Exception error) {
            failed(context, "無法製作屏保圖片", error);
        }
    }

    private static void validate(File picture, int width, int height) throws IOException {
        ScreensaverPng.verify(picture, width, height, MAX_PNG_BYTES);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeFile(picture.getAbsolutePath(), options);
        try {
            if (decoded == null || decoded.getWidth() != width
                    || decoded.getHeight() != height || decoded.hasAlpha()) {
                throw new IOException("Prepared PNG cannot be decoded as opaque RGB");
            }
        } finally {
            if (decoded != null) decoded.recycle();
        }
    }

    private static void dispatch(Context context, File picture) {
        // Cancel before sending: no queued QuickPic progress toast may cover the OEM hint.
        cancelProgress();
        try {
            if (!supports(context)) {
                message(context, "此裝置未提供相容的文石屏保服務");
                return;
            }
            if (!picture.isFile() || !picture.canRead()) {
                message(context, "無法讀取屏保圖片");
                return;
            }
            Intent request = new Intent(ACTION).setPackage(PACKAGE);
            request.putExtra("type", 16);
            request.putExtra("file", picture.getAbsolutePath());
            request.putExtra("show_result_hint", true);
            request.addFlags(INCLUDE_BACKGROUND);
            context.sendBroadcast(request);
            // No public acknowledgement. OEM performs the operation and shows its result.
            // Retain this persistent image even if delivery/outcome is not observable here.
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to submit OEM screensaver request", error);
            message(context, "設定文石屏保失敗");
        } finally {
            ACTIVE.set(false);
        }
    }

    private static void failed(final Context context, final String text, final Throwable error) {
        Log.e(TAG, "Unable to prepare BOOX screensaver", error);
        main().post(new Runnable() {
            @Override public void run() {
                cancelProgress();
                ACTIVE.set(false);
                message(context, text);
            }
        });
    }

    private static Handler main() { return new Handler(Looper.getMainLooper()); }

    private static void cancelProgress() {
        // Also invalidate older queued messages, so none can appear after OEM dispatch.
        toastEpoch++;
        if (currentToast != null) {
            currentToast.cancel();
            currentToast = null;
        }
    }

    private static void message(final Context context, final String text) {
        final int epoch = toastEpoch;
        main().post(new Runnable() {
            @Override public void run() {
                if (epoch != toastEpoch) return;
                if (currentToast != null) currentToast.cancel();
                currentToast = Toast.makeText(context.getApplicationContext(), text,
                        Toast.LENGTH_SHORT);
                currentToast.show();
            }
        });
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            text.append(Character.forDigit((value >>> 4) & 15, 16));
            text.append(Character.forDigit(value & 15, 16));
        }
        return text.toString();
    }
}
