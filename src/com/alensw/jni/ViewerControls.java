package com.alensw.jni;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Viewer actions and a live editor for the original slideshow preference. */
public final class ViewerControls {
    private static final String TAG = "QuickPicViewer";
    private static final String KEY = "slideshow_interval";
    private static final String CUSTOM_KEY = "slideshow_custom_interval";
    private static final int DELETE = 0x7f09004e;
    private static final int SLIDESHOW = 0x7f090052;
    private static final int SHARE = 0x7f09000a;
    private static final int EDIT = 0x7f090069;
    private static final int ROTATE = 0x7f09005a;
    // Runtime-only ID: GalleryActivity consumes R.id.settings before its viewer.
    // Keep this outside all compiled Android/app resource IDs.
    private static final int INTERVAL = 0x00f0a010;
    private static final int TITLE = 0x7f0a007b;
    private static final int ICON = 0x7f020020;
    private static final int CUSTOM = 0x7f0a0029;
    private static final int[] PRESETS = {3, 5, 10, 30, 60};

    private ViewerControls() { }

    public static void configure(Context context, Menu menu) {
        // ao.a handles both the Android action bar and the legacy TitleBar.
        // Calling setShowAsAction alone is a no-op for the legacy MenuItem.
        action(context, menu.findItem(SHARE), MenuItem.SHOW_AS_ACTION_NEVER);
        action(context, menu.findItem(EDIT), MenuItem.SHOW_AS_ACTION_NEVER);
        action(context, menu.findItem(ROTATE), MenuItem.SHOW_AS_ACTION_NEVER);
        action(context, menu.findItem(DELETE), MenuItem.SHOW_AS_ACTION_NEVER);
        MenuItem slideshow = menu.findItem(SLIDESHOW);
        action(context, slideshow, MenuItem.SHOW_AS_ACTION_ALWAYS);
        if (slideshow == null) {
            menu.removeItem(INTERVAL);
            return;
        }
        MenuItem interval = menu.findItem(INTERVAL);
        if (interval == null) interval = menu.add(Menu.NONE, INTERVAL, 1, TITLE);
        interval.setTitle(TITLE);
        Drawable icon = context.getResources().getDrawable(ICON).mutate();
        TypedValue tint = new TypedValue();
        if (context.getTheme().resolveAttribute(0x7f010001, tint, true)) {
            int color = tint.resourceId != 0 ? context.getResources().getColor(tint.resourceId) : tint.data;
            icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
        interval.setIcon(icon);
        MenuLayout.order(menu, new int[]{SLIDESHOW, INTERVAL, DELETE, EDIT, SHARE});
        action(context, interval, MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private static void action(Context context, MenuItem item, int mode) {
        if (item == null) return;
        try {
            Method method = context.getClass().getMethod("a", MenuItem.class, Integer.TYPE);
            method.invoke(context, item, mode);
        } catch (Exception exception) {
            Log.w(TAG, "Activity action dispatch unavailable", exception);
            item.setShowAsAction(mode);
        }
    }

    public static void showDialog(final Context context, final Object controller) {
        final int current = seconds(context);
        final int remembered = customSeconds(context, current);
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(Integer.toString(remembered));
        input.setHint("1–86400 s");
        CharSequence[] labels = new CharSequence[PRESETS.length + 1];
        int selected = PRESETS.length;
        for (int i = 0; i < PRESETS.length; i++) {
            labels[i] = PRESETS[i] + " s";
            if (PRESETS[i] == current) selected = i;
        }
        labels[PRESETS.length] = context.getString(CUSTOM) + " (" + remembered + " s)";
        String language = context.getResources().getConfiguration().locale.getLanguage();
        String applyLabel = "zh".equals(language) ? "套用自訂" : "Apply custom";
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle(TITLE)
                .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == PRESETS.length) {
                            input.requestFocus(); input.selectAll();
                        } else if (which >= 0 && which < PRESETS.length) {
                            // Preserve the last applied custom value before switching presets.
                            PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putString(CUSTOM_KEY, Integer.toString(remembered)).commit();
                            apply(context, controller, PRESETS[which]); dialog.dismiss();
                        }
                    }
                }).setView(input).setPositiveButton(applyLabel, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface ignored) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        try {
                            int value = Integer.parseInt(input.getText().toString().trim());
                            if (value < 1 || value > 86400) throw new NumberFormatException();
                            PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putString(CUSTOM_KEY, Integer.toString(value)).commit();
                            apply(context, controller, value); dialog.dismiss();
                        } catch (NumberFormatException exception) { input.setError("1–86400 s"); }
                    }
                });
            }
        });
        dialog.show();
    }

    private static int customSeconds(Context context, int fallback) {
        try {
            int value = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(CUSTOM_KEY, Integer.toString(fallback)));
            return value >= 1 && value <= 86400 ? value : fallback;
        } catch (RuntimeException exception) { return fallback; }
    }

    private static int seconds(Context context) {
        try {
            int value = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, "3"));
            return Math.max(1, Math.min(86400, value));
        } catch (RuntimeException exception) {
            return 3;
        }
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException exception) {
                // The controller inherits Handler b from ui.c.a.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void apply(Context context, Object controller, int seconds) {
        int millis = seconds * 1000;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(KEY, Integer.toString(seconds)).commit();
        try {
            boolean modern = controller.getClass().getName().equals("com.alensw.ui.c.as");
            field(controller, modern ? "R" : "S").setInt(controller, millis);
            Runnable next = (Runnable) field(controller, modern ? "X" : "Y").get(controller);
            Handler handler = (Handler) field(controller, "b").get(controller);
            // A nonnull next also exists while preloading. Only reschedule an
            // already queued slide, so a slow load can never advance too soon.
            if (next != null && handler != null && Build.VERSION.SDK_INT >= 29 && handler.hasCallbacks(next)) {
                long started = field(controller, modern ? "W" : "X").getLong(controller);
                long remaining = Math.max(50L, (long) millis - (System.currentTimeMillis() - started));
                handler.removeCallbacks(next);
                handler.postDelayed(next, remaining);
            }
        } catch (Exception exception) {
            Log.e(TAG, "Could not update the active slideshow", exception);
        }
    }
}
