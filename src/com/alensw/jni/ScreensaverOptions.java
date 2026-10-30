// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import java.util.Map;

/** Persistent menu visibility with a one-time clean-install default. */
public final class ScreensaverOptions extends Preference {
    private static final String PREFIX = "screensaver_option_";
    private static final String GENERIC = PREFIX + "generic";
    private static final String BIGME = PREFIX + "bigme";
    private static final String BOOX = PREFIX + "boox";
    private static final String INITIALIZED = "screensaver_options_initialized";
    private static final String CONFIGURED = "screensaver_options_configured";
    private static final String SCHEMA = "screensaver_options_schema";
    private static final String[] LABELS = {"設置為屏保", "設置為 B1051C 屏保", "設置為文石屏保"};

    public ScreensaverOptions(Context context, AttributeSet attributes) {
        super(context, attributes);
        super.setPersistent(false);
        refreshSummary();
    }

    private static boolean value(Map<String, ?> values, String key) {
        Object value = values.get(key);
        return value instanceof Boolean ? ((Boolean)value).booleanValue() : !BOOX.equals(key);
    }

    /** Cold-start hook. Updates and explicit preferences never run device detection. */
    public static synchronized void initialize(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Map<String, ?> values = prefs.getAll();
            if (values.containsKey(INITIALIZED)) return;
            boolean generic = value(values, GENERIC), bigme = value(values, BIGME),
                    boox = value(values, BOOX);
            boolean explicit = values.containsKey(GENERIC) || values.containsKey(BIGME)
                    || values.containsKey(BOOX) || values.containsKey(CONFIGURED);
            if (!explicit) {
                PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                if (info.firstInstallTime > 0 && info.firstInstallTime == info.lastUpdateTime) {
                    int action = 1;
                    try { action = ScreensaverDevice.defaultAction(context); }
                    catch (Exception ignored) { /* Keep the generic default. */ }
                    catch (LinkageError ignored) { /* Missing OEM APIs are not startup failures. */ }
                    generic = action != 2 && action != 3;
                    bigme = action == 2;
                    boox = action == 3;
                }
            }
            prefs.edit().putBoolean(GENERIC, generic).putBoolean(BIGME, bigme)
                .putBoolean(BOOX, boox).putBoolean(INITIALIZED, true).putInt(SCHEMA, 2).apply();
        } catch (Exception ignored) { /* Preferences/package metadata must not crash startup. */ }
        catch (LinkageError ignored) { /* Platform API differences must not crash startup. */ }
    }

    /** Read one preferences snapshot. Action 0 is wallpaper and is never hidden. */
    public static int[] visibleActions(Context context) {
        Map<String, ?> values = PreferenceManager.getDefaultSharedPreferences(context).getAll();
        boolean generic = value(values, GENERIC), bigme = value(values, BIGME), boox = value(values, BOOX);
        int[] actions = new int[1 + (generic ? 1 : 0) + (bigme ? 1 : 0) + (boox ? 1 : 0)];
        int i = 0;
        actions[i++] = 0;
        if (generic) actions[i++] = 1;
        if (bigme) actions[i++] = 2;
        if (boox) actions[i] = 3;
        return actions;
    }

    private void refreshSummary() {
        int count = visibleActions(super.getContext()).length - 1;
        super.setSummary(count == 0 ? "已隱藏屏保選項" : "顯示 " + count + " 個屏保選項");
    }

    @Override protected void onClick() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(super.getContext());
        Map<String, ?> values = prefs.getAll();
        final boolean[] pending = {value(values, GENERIC), value(values, BIGME), value(values, BOOX)};
        new AlertDialog.Builder(super.getContext()).setTitle("顯示屏保選項")
            .setMultiChoiceItems(LABELS, pending.clone(), new DialogInterface.OnMultiChoiceClickListener() {
                @Override public void onClick(DialogInterface dialog, int which, boolean selected) {
                    if (which >= 0 && which < pending.length) pending[which] = selected;
                }
            }).setNegativeButton("取消", null)
            .setPositiveButton("儲存", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    // Named keys make later options additive; do not remove unknown keys.
                    prefs.edit().putBoolean(GENERIC, pending[0]).putBoolean(BIGME, pending[1])
                        .putBoolean(BOOX, pending[2]).putBoolean(CONFIGURED, true)
                        .putBoolean(INITIALIZED, true).putInt(SCHEMA, 2).apply();
                    refreshSummary();
                }
            }).show();
    }
}
