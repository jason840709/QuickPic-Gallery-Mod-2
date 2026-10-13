package com.alensw.jni;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Opens an already generated PNG. This class never creates, changes, or deletes files. */
public final class ScreensaverOpener {
    private static final String TAG = "QP885-XPhotoBridge";
    private static final String XPHOTO_PACKAGE = "com.ebook.imagemanager";
    private static final String XPHOTO_ACTIVITY = "com.xrz.photo.ImageFolderSubAt";

    private ScreensaverOpener() {}

    public static void open(Context context, File png) {
        if (context == null) return;
        if (png == null || !png.isFile() || !png.canRead()) {
            status(context, "準備完成的 PNG 無法讀取，請重新產生圖片");
            return;
        }

        // Preserve the original XPhoto protocol, including the path list and position.
        ArrayList<String> paths = new ArrayList<String>(1);
        paths.add(png.getAbsolutePath());
        Intent original = new Intent(Intent.ACTION_VIEW);
        original.setComponent(new ComponentName(XPHOTO_PACKAGE, XPHOTO_ACTIVITY));
        original.putExtra("list", (Serializable) paths);
        original.putExtra("position", 0);
        original.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(
                    original.getComponent(), PackageManager.MATCH_DISABLED_COMPONENTS);
            if (available(context, info) && launch(context, original)) {
                xphotoStatus(context);
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "XPhoto's original activity is unavailable");
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot inspect XPhoto's original activity", e);
        }

        final Uri content;
        try {
            Class<?> provider = context.getClassLoader().loadClass(
                    context.getPackageName() + ".FileProvider");
            Object result = provider.getMethod("a", String.class).invoke(
                    null, png.getAbsolutePath());
            if (!(result instanceof Uri) || !"content".equals(((Uri) result).getScheme())) {
                throw new IllegalStateException("FileProvider did not return a content URI");
            }
            content = (Uri) result;
        } catch (Exception e) {
            Log.e(TAG, "Cannot share the saved PNG through FileProvider", e);
            savedFailure(context, png, "無法分享圖片");
            return;
        } catch (LinkageError e) {
            Log.e(TAG, "FileProvider is unavailable", e);
            savedFailure(context, png, "無法分享圖片");
            return;
        }

        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(content, "image/png");
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        view.setClipData(ClipData.newRawUri("PNG", content));

        // XPhoto versions may expose a different activity that handles ordinary PNG VIEW.
        Intent samePackage = new Intent(view).setPackage(XPHOTO_PACKAGE);
        try {
            ResolveInfo resolved = context.getPackageManager().resolveActivity(
                    samePackage, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && available(context, resolved.activityInfo)
                    && launch(context, samePackage)) {
                xphotoStatus(context);
                return;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot resolve XPhoto's PNG viewer", e);
        }

        try {
            // Let the system resolve the chooser even when package visibility
            // limits the query results visible to this process.
            Intent chooser = Intent.createChooser(view, "選擇開啟 PNG 的 App");
            // Carry the grant on both chooser and payload for Android/OEM chooser variants.
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            chooser.setClipData(view.getClipData());
            if (launch(context, chooser)) {
                status(context, "已保存 1860×2480 PNG，請選擇 App 開啟");
                return;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot resolve the PNG chooser", e);
        }
        savedFailure(context, png, "無法開啟圖片選擇器");
    }

    private static boolean hasViewer(Context context, Intent view) {
        List<ResolveInfo> matches = context.getPackageManager().queryIntentActivities(
                view, PackageManager.MATCH_DEFAULT_ONLY);
        if (matches != null) {
            for (ResolveInfo match : matches) {
                if (match != null && available(context, match.activityInfo)) return true;
            }
        }
        return false;
    }

    private static boolean available(Context context, ActivityInfo info) {
        if (info == null || info.applicationInfo == null || !info.exported) return false;
        PackageManager pm = context.getPackageManager();
        int appState = pm.getApplicationEnabledSetting(info.packageName);
        if (!enabled(appState, info.applicationInfo.enabled)) return false;
        int activityState = pm.getComponentEnabledSetting(
                new ComponentName(info.packageName, info.name));
        return enabled(activityState, info.enabled);
    }

    private static boolean enabled(int state, boolean manifestEnabled) {
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && manifestEnabled);
    }

    private static boolean launch(Context context, Intent intent) {
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to launch " + intent, e);
            return false;
        }
    }

    private static void xphotoStatus(Context context) {
        status(context, "已產生 1860×2480 PNG，請在 XPhoto 內點「設置為屏保」");
    }

    private static void savedFailure(Context context, File png, String reason) {
        // Put the result before the potentially long path because modern toasts have two lines.
        status(context, "PNG 已保存；" + reason + "，尚未設定螢幕保護。檔案：" + png.getAbsolutePath());
    }

    private static void status(final Context context, final String text) {
        final Context toastContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        Runnable show = new Runnable() {
            @Override public void run() {
                Toast.makeText(toastContext, text, Toast.LENGTH_LONG).show();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) show.run();
        else new Handler(Looper.getMainLooper()).post(show);
    }
}
