// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;

/** Called on the existing ScreenSaverTask worker; PNG work stays off the UI thread. */
public final class ScreensaverExport {
    private ScreensaverExport() {}
    public static boolean prepareAndOpen(final Context context, String path) {
        if (context == null) return false;
        try {
            int[] size = ScreensaverPreference.dimensions(context);
            File directory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "QuickPic-Screensaver");
            final File output = ScreensaverImage.prepare(path, size[0], size[1], directory);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() { ScreensaverOpener.open(context, output); }
            });
            return true;
        } catch (Exception error) {
            failure(context, "無法產生屏保 PNG，請檢查圖片與儲存空間");
        } catch (OutOfMemoryError error) {
            failure(context, "記憶體不足，請調小屏保圖尺寸後重試");
        }
        return false;
    }
    private static void failure(final Context context, final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }
}
