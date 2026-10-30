// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.Context;

/** Read-only device defaults. Explicitly saved menu choices are handled elsewhere. */
public final class ScreensaverDevice {
    private ScreensaverDevice() {}

    public static int defaultAction(Context context) {
        if (context == null) return 1;
        if (BigmeScreensaver.isSupported(context)) return 2;
        if (BooxScreensaver.supports(context)) return 3;
        return 1;
    }
}
