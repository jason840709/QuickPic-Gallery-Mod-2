// SPDX-License-Identifier: MIT
package com.alensw.jni;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
/** The legacy task enters here; all UI and crop choices are on the main thread. */
public final class ScreensaverExport {
    private ScreensaverExport() {}
    public static boolean prepareAndOpen(final Context c, final String path) {
        if(c==null)return false;
        new Handler(Looper.getMainLooper()).post(()->ScreensaverFlow.choose(c,path,0));
        return true;
    }
}
