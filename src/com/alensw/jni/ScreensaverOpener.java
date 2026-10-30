// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.File;

/** Opens a generated PNG through the existing grant-protected support provider. */
public final class ScreensaverOpener {
    private static final String TAG = "QuickPicScreensaverOpen";
    private ScreensaverOpener() {}

    public static void open(Context context, File png) {
        if (context == null) return;
        if (png == null || !png.isFile() || !png.canRead()) {
            status(context, "準備完成的 PNG 無法讀取，請重新產生圖片");
            return;
        }
        final Uri content;
        try {
            String authority = context.getPackageName() + ".provider";
            Class<?> provider = context.getClassLoader().loadClass(
                    "android.support.v4.content.FileProvider");
            Object result = provider.getMethod("getUriForFile", Context.class,
                    String.class, File.class).invoke(null, context, authority, png);
            if (!(result instanceof Uri)) throw new IllegalStateException("No content URI");
            content = (Uri) result;
            if (!"content".equals(content.getScheme()) || !authority.equals(content.getAuthority()))
                throw new IllegalStateException("Unexpected PNG provider URI");
        } catch (Exception error) {
            Log.w(TAG, "Cannot share saved PNG through the support FileProvider", error);
            savedFailure(context, png, "無法分享圖片");
            return;
        } catch (LinkageError error) {
            Log.w(TAG, "Support FileProvider is unavailable", error);
            savedFailure(context, png, "無法分享圖片");
            return;
        }

        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(content, "image/png");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        view.setClipData(ClipData.newRawUri("PNG", content));
        try {
            Intent chooser = Intent.createChooser(view, "選擇開啟 PNG 的 App");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            chooser.setClipData(view.getClipData());
            if (!(context instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Do not pre-query handlers: package visibility can hide otherwise usable apps.
            context.startActivity(chooser);
            status(context, "已儲存屏保 PNG，請選擇 App 開啟");
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot launch PNG chooser", error);
            savedFailure(context, png, "無法開啟圖片選擇器");
        }
    }

    private static void savedFailure(Context context, File png, String reason) {
        status(context, "PNG 已儲存；" + reason + "，尚未設定螢幕保護。檔案：" + png.getAbsolutePath());
    }
    private static void status(final Context context, final String text) {
        final Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        Runnable show = new Runnable() {
            @Override public void run() { Toast.makeText(app, text, Toast.LENGTH_LONG).show(); }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) show.run();
        else new Handler(Looper.getMainLooper()).post(show);
    }
}
