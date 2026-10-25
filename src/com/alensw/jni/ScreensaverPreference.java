// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/** Stores a pixel-size snapshot; detection never uses dp or the content viewport. */
public final class ScreensaverPreference extends Preference {
    private static final int DEFAULT_WIDTH = 1860, DEFAULT_HEIGHT = 2480;
    private static final String WIDTH = "screensaver_width";
    private static final String HEIGHT = "screensaver_height";
    private static final String AUTO = "screensaver_detected";
    private static final int CUSTOM_ID = 101, AUTO_ID = 102;

    public ScreensaverPreference(Context context, AttributeSet attributes) {
        super(context, attributes);
        super.setPersistent(false);
        refreshSummary();
    }

    public static int[] dimensions(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int w, h;
        try { w = prefs.getInt(WIDTH, DEFAULT_WIDTH); h = prefs.getInt(HEIGHT, DEFAULT_HEIGHT); }
        catch (ClassCastException bad) { return new int[] {DEFAULT_WIDTH, DEFAULT_HEIGHT}; }
        return ScreensaverImage.validSize(w, h) ? new int[] {w, h} : new int[] {DEFAULT_WIDTH, DEFAULT_HEIGHT};
    }

    private void refreshSummary() {
        int[] size = dimensions(super.getContext());
        super.setSummary(size[0] + " × " + size[1] + " px");
    }

    /** Active display mode reports physical pixels, including system-bar areas. */
    public static int[] detect(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return null;
        Display display = wm.getDefaultDisplay();
        if (display == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                int[] size = ModePixels.read(display);
                if (size[0] > 0 && size[1] > 0) return size;
            }
            Point point = new Point();
            display.getRealSize(point);
            if (point.x > 0 && point.y > 0) return new int[] {point.x, point.y};
        } catch (RuntimeException unavailable) { return null; }
        return null;
    }

    private static final class ModePixels {
        static int[] read(Display display) {
            Display.Mode mode = display.getMode();
            int w = mode.getPhysicalWidth(), h = mode.getPhysicalHeight();
            int rotation = display.getRotation();
            if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                int swap = w; w = h; h = swap;
            }
            return new int[] {w, h};
        }
    }

    @Override protected void onClick() {
        final Context context = super.getContext();
        final int[] saved = dimensions(context);
        final int[] detected = detect(context);
        final int pad = dp(20);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, dp(4), pad, 0);
        final RadioGroup choices = new RadioGroup(context);
        RadioButton custom = new RadioButton(context);
        custom.setId(CUSTOM_ID); custom.setText("自訂尺寸"); choices.addView(custom);
        RadioButton automatic = new RadioButton(context);
        automatic.setId(AUTO_ID);
        automatic.setText(detected == null ? "自動偵測（目前無法取得）" :
                "自動偵測（" + detected[0] + " × " + detected[1] + " px）");
        automatic.setEnabled(detected != null); choices.addView(automatic);
        body.addView(choices);
        LinearLayout fields = new LinearLayout(context);
        fields.setGravity(Gravity.CENTER_VERTICAL);
        final EditText width = field(context, "寬", saved[0]);
        final EditText height = field(context, "高", saved[1]);
        fields.addView(width, new LinearLayout.LayoutParams(0, -2, 1));
        TextView times = new TextView(context); times.setText(" × "); fields.addView(times);
        fields.addView(height, new LinearLayout.LayoutParams(0, -2, 1));
        TextView unit = new TextView(context); unit.setText(" px"); fields.addView(unit);
        Button reset = new Button(context, null, android.R.attr.borderlessButtonStyle);
        reset.setText("恢復預設");
        reset.setTextSize(13);
        reset.setMinWidth(0); reset.setMinimumWidth(0);
        reset.setPadding(dp(8), 0, dp(8), 0);
        reset.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View button) {
                // Switch first: the radio listener may fill the previously saved values.
                choices.check(CUSTOM_ID);
                width.setEnabled(true); height.setEnabled(true);
                width.setText(String.valueOf(DEFAULT_WIDTH));
                height.setText(String.valueOf(DEFAULT_HEIGHT));
                width.setError(null); height.setError(null);
            }
        });
        fields.addView(reset, new LinearLayout.LayoutParams(-2, -2));
        body.addView(fields);
        boolean wasAuto;
        try { wasAuto = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(AUTO, false); }
        catch (ClassCastException bad) { wasAuto = false; }
        choices.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int id) {
                boolean manual = id == CUSTOM_ID;
                width.setEnabled(manual); height.setEnabled(manual);
                int[] values = manual ? saved : detected;
                if (values != null) {
                    width.setText(String.valueOf(values[0]));
                    height.setText(String.valueOf(values[1]));
                }
            }
        });
        choices.check(wasAuto && detected != null ? AUTO_ID : CUSTOM_ID);
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("設定屏保圖尺寸").setView(body)
                .setNegativeButton("取消", null).setPositiveButton("儲存", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View button) {
                        int w = parse(width), h = parse(height);
                        if (!ScreensaverImage.validSize(w, h)) {
                            width.setError("寬高需為 1～8192，總像素不可超過 3200 萬");
                            width.requestFocus(); return;
                        }
                        PreferenceManager.getDefaultSharedPreferences(context).edit()
                                .putInt(WIDTH, w).putInt(HEIGHT, h)
                                .putBoolean(AUTO, choices.getCheckedRadioButtonId() == AUTO_ID).apply();
                        refreshSummary(); dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private int dp(int n) { return Math.round(n * super.getContext().getResources().getDisplayMetrics().density); }
    private static EditText field(Context context, String label, int value) {
        EditText field = new EditText(context);
        field.setInputType(InputType.TYPE_CLASS_NUMBER); field.setSingleLine(true);
        field.setHint(label + "（px）"); field.setContentDescription(label + "（px）");
        field.setText(String.valueOf(value)); field.setSelectAllOnFocus(true); return field;
    }
    private static int parse(EditText field) {
        try { return Integer.parseInt(field.getText().toString().trim()); }
        catch (NumberFormatException invalid) { return -1; }
    }
}
