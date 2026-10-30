// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/** Maps a fixed visible-menu snapshot onto the unchanged original action listener. */
public final class ScreensaverMenu {
    private ScreensaverMenu() {}

    public static void show(Context context, int title, CharSequence[] allItems,
            final DialogInterface.OnClickListener original) {
        if (allItems == null || allItems.length != 4 || original == null) {
            throw new IllegalArgumentException("Invalid screensaver menu contract");
        }
        final int[] actions = ScreensaverOptions.visibleActions(context);
        CharSequence[] items = new CharSequence[actions.length];
        for (int i = 0; i < actions.length; i++) items[i] = allItems[actions[i]];
        new AlertDialog.Builder(context).setTitle(title).setItems(items,
            new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    if (which >= 0 && which < actions.length) {
                        original.onClick(dialog, actions[which]);
                    }
                }
            }).show();
    }
}
