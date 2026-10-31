// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Toast;
import java.util.Locale;

/** Opt-in image tap layout. The diagram exists only inside this settings dialog. */
public final class TapPagePreference extends Preference {
    private static final String KEY = "tap_page_layout";
    private static final String[][] LABELS = {
        {"Disabled", "Bottom corners", "Left and right edges", "Top and bottom",
         "Left edge: top / bottom", "Right edge: top / bottom", "Middle of both edges"},
        {"停用", "下方兩角", "左右兩側", "上下", "左側上下", "右側上下", "中間兩側"},
        {"停用", "下方两角", "左右两侧", "上下", "左侧上下", "右侧上下", "中间两侧"}
    };

    public TapPagePreference(Context context, AttributeSet attributes) {
        super(context, attributes);
        super.setPersistent(false);
        super.setTitle(text(context, "Tap to turn pages", "點擊翻頁", "点击翻页"));
        refreshSummary();
    }

    /** Missing/corrupt values are disabled for both clean installs and upgrades. */
    public static int mode(Context context) {
        if (context == null) return TapPageLayout.OFF;
        try {
            int result = PreferenceManager.getDefaultSharedPreferences(context).getInt(KEY,
                    TapPageLayout.OFF);
            return result >= 0 && result < TapPageLayout.COUNT ? result : TapPageLayout.OFF;
        } catch (RuntimeException ignored) {
            return TapPageLayout.OFF;
        }
    }

    private static int language(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        if (locale == null || !"zh".equals(locale.getLanguage())) return 0;
        String country = locale.getCountry();
        return "TW".equals(country) || "HK".equals(country) || "MO".equals(country)
                || "Hant".equals(locale.getScript()) ? 1 : 2;
    }

    private static String text(Context context, String english, String traditional,
            String simplified) {
        int language = language(context);
        return language == 1 ? traditional : language == 2 ? simplified : english;
    }

    private void refreshSummary() {
        super.setSummary(LABELS[language(super.getContext())][mode(super.getContext())]);
    }

    @Override protected void onClick() {
        final Context context = super.getContext();
        final int[] pending = {mode(context)};
        final LayoutPreview preview = new LayoutPreview(context, pending[0]);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int side = dp(context, 16);
        content.setPadding(side, dp(context, 8), side, 0);
        int budget = Math.min(dp(context, 520),
                (int)(context.getResources().getDisplayMetrics().heightPixels * .60f));
        int previewHeight = Math.min(dp(context, 180), Math.max(dp(context, 72), budget / 3));
        content.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, previewHeight));

        final RadioGroup choices = new RadioGroup(context);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < TapPageLayout.COUNT; i++) {
            RadioButton option = new RadioButton(context);
            option.setId(0x00f0b100 + i);
            option.setText(LABELS[language(context)][i]);
            option.setTextSize(16);
            option.setMinHeight(dp(context, 48));
            option.setPadding(dp(context, 4), dp(context, 5), dp(context, 4), dp(context, 5));
            choices.addView(option, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        choices.check(0x00f0b100 + pending[0]);
        choices.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                int selected = checkedId - 0x00f0b100;
                if (selected >= 0 && selected < TapPageLayout.COUNT) {
                    pending[0] = selected;
                    preview.select(selected);
                }
            }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(choices);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(dp(context, 96),
                budget - previewHeight - dp(context, 8))));

        new AlertDialog.Builder(context)
            .setTitle(text(context, "Tap to turn pages", "點擊翻頁", "点击翻页"))
            .setView(content)
            .setNegativeButton(text(context, "Cancel", "取消", "取消"), null)
            .setPositiveButton(text(context, "Save", "儲存", "保存"),
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                            prefs.edit().putInt(KEY, pending[0]).apply();
                            refreshSummary();
                        } catch (RuntimeException error) {
                            Toast.makeText(context, text(context, "Unable to save setting",
                                    "無法儲存設定", "无法保存设置"), Toast.LENGTH_SHORT).show();
                        }
                    }
                }).show();
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    /** Samples the same action function; no second copy of the hit-area geometry. */
    private static final class LayoutPreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int selected;
        LayoutPreview(Context context, int selected) {
            super(context);
            select(selected);
        }
        void select(int value) {
            selected = value;
            super.setContentDescription(text(super.getContext(), "Tap areas: ", "點擊區域：", "点击区域：")
                    + LABELS[language(super.getContext())][value]);
            super.invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(super.getContext(), 6), top = dp(super.getContext(), 4);
            float width = super.getWidth() - left * 2;
            float height = super.getHeight() - top - dp(super.getContext(), 8);
            if (width < 3 || height < 2) return;
            // Row runs need at most a few rectangles each and remain legible in monochrome.
            paint.setAntiAlias(false);
            final int columns = 90, rows = 60;
            for (int row = 0; row < rows; row++) {
                int start = 0;
                int action = TapPageLayout.action(selected, .5f, row + .5f, columns, rows);
                for (int column = 1; column <= columns; column++) {
                    int next = column == columns ? 2 : TapPageLayout.action(selected,
                            column + .5f, row + .5f, columns, rows);
                    if (next != action) {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(action < 0 ? 0xffdddddd : action > 0 ? 0xffaaaaaa : Color.WHITE);
                        canvas.drawRect(left + start * width / columns, top + row * height / rows,
                                left + column * width / columns, top + (row + 1) * height / rows, paint);
                        action = next;
                        start = column;
                    }
                }
            }
            paint.setAntiAlias(true);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(super.getContext(), 1));
            canvas.drawRect(left, top, left + width, top + height, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(super.getContext(), 12));
            float[][] centers = {
                {.50f,.50f,.50f,.50f,.50f,.50f},
                {.16f,.75f,.50f,.25f,.84f,.75f},
                {.15f,.50f,.50f,.50f,.85f,.50f},
                {.50f,.15f,.50f,.50f,.50f,.85f},
                {.15f,.25f,.65f,.50f,.15f,.75f},
                {.85f,.25f,.35f,.50f,.85f,.75f},
                {.15f,.50f,.50f,.18f,.85f,.50f}
            };
            float[] c = centers[selected];
            if (selected != TapPageLayout.OFF) {
                label(canvas, text(super.getContext(), "Previous", "上一頁", "上一页"),
                        left + c[0]*width, top + c[1]*height);
                label(canvas, text(super.getContext(), "Next", "下一頁", "下一页"),
                        left + c[4]*width, top + c[5]*height);
            }
            label(canvas, text(super.getContext(), "Menu", "選單", "菜单"),
                    left + c[2]*width, top + c[3]*height);
        }

        private void label(Canvas canvas, String label, float x, float y) {
            canvas.drawText(label, x, y - (paint.ascent() + paint.descent()) / 2, paint);
        }
    }
}
