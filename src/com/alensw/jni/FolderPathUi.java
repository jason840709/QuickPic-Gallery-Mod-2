// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.widget.ImageView;
import java.util.Locale;

/** Presentation only: the historical include/exclude path callbacks stay intact. */
public final class FolderPathUi {
    // colorActionIcon (0x7f010001) belongs to the toolbar. In the light Color
    // theme it is white over a colored bar, while this row has a light surface.
    private static final int MENU_ICON_COLOR = 0x7f01001a;

    private FolderPathUi() { }

    public static String editTitle(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        if (locale == null) locale = Locale.getDefault();
        if (!"zh".equals(locale.getLanguage())) return "Edit folder path";
        String country = locale.getCountry();
        boolean traditional = "TW".equals(country) || "HK".equals(country)
                || "MO".equals(country) || locale.toString().contains("Hant");
        return traditional ? "編輯資料夾路徑" : "编辑文件夹路径";
    }

    /** Reapply on every bind, including recycled rows, using the selected App theme. */
    public static void tintEditIcon(ImageView view) {
        Drawable icon = view.getDrawable();
        if (icon == null) return;
        Context context = view.getContext();
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true)
                && !context.getTheme().resolveAttribute(MENU_ICON_COLOR, value, true)) return;
        int color = value.resourceId != 0
                ? context.getResources().getColor(value.resourceId) : value.data;
        // mutate prevents a shared drawable state from tinting other rows/icons.
        Drawable tinted = icon.mutate();
        tinted.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        view.setImageDrawable(tinted);
    }
}
