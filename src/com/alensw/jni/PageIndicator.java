// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Event-driven, display-only page label. No scanning, timers or image-draw hooks. */
public final class PageIndicator {
    public static final String KEY = "show_page_number";
    private PageIndicator() { }
    public interface Host {
        ReaderMode.Host pageController();
        ReaderMode.Frame pageFrame();
        int pageBackground();
        State pageState();
    }
    public static final class State implements SharedPreferences.OnSharedPreferenceChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener {
        private final WeakReference<View> view;
        private final SharedPreferences preferences;
        private final TextView text;
        private final View scope, bottomBar, showing;
        private final Rect visibleArea = new Rect();
        private final int[] position = new int[2];
        private final float density;
        private boolean enabled, transition;
        private int index = -1, count, lastColor;
        private Uri visibleUri;
        private String label = "";

        private State(View value, View parent, TextView indicator) {
            view = new WeakReference<View>(value);
            scope = parent;
            text = indicator;
            Context context = value.getContext();
            preferences = PreferenceManager.getDefaultSharedPreferences(context);
            enabled = preferences.getBoolean(KEY, false);
            density = context.getResources().getDisplayMetrics().density;
            bottomBar = find(parent, context, "bottom_bar");
            showing = find(parent, context, "showing");
            transition = showing != null && showing.getTag() != null;
            preferences.registerOnSharedPreferenceChangeListener(this);
            scope.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }
        private ReaderMode.Host controller() {
            View value = view.get();
            return value instanceof Host ? ((Host)value).pageController() : null;
        }
        @Override public void onGlobalLayout() { refresh(controller()); }
        @Override public void onSharedPreferenceChanged(SharedPreferences source, String key) {
            if (key != null && !KEY.equals(key)) return;
            enabled = preferences.getBoolean(KEY, false);
            refresh(controller());
        }
        private void visibility(boolean show) {
            int value = show ? View.VISIBLE : View.GONE;
            if (text.getVisibility() != value) text.setVisibility(value);
        }
        private void refresh(ReaderMode.Host host) {
            try { refreshAvailable(host); }
            catch (RuntimeException unavailable) { visibility(false); }
        }
        private void refreshAvailable(ReaderMode.Host host) {
            View value = view.get();
            if (!enabled || transition || value == null || host == null
                    || !host.readerActive() || !host.readerImage(-1)) { visibility(false); return; }
            Host bridge = (Host)value;
            ReaderMode.Frame frame = bridge.pageFrame();
            Uri visible = frame == null ? null : frame.readerUri();
            if (visible == null) { visibility(false); return; }
            int nextIndex = host.readerIndex(), nextCount = host.readerCount();
            boolean single = nextCount == 0 && nextIndex == -1;
            if (single) { nextCount = 1; nextIndex = 0; }
            boolean changed = index != nextIndex || count != nextCount || !same(visibleUri, visible);
            boolean matches = same(visible, host.readerUri(-1))
                    && (!changed || single || nextIndex >= 0 && nextIndex < nextCount
                        && same(visible, host.readerUri(nextIndex)));
            if (matches && nextIndex >= 0 && nextIndex < nextCount) {
                if (changed) {
                    index = nextIndex;
                    count = nextCount;
                    visibleUri = visible;
                    String next = format(index, count);
                    if (!label.equals(next)) { label = next; text.setText(label); }
                }
            } else if (!same(visibleUri, visible)) { visibility(false); return; }
            if (label.length() == 0) { visibility(false); return; }
            int background = bridge.pageBackground();
            boolean light = Color.red(background) * 299 + Color.green(background) * 587
                    + Color.blue(background) * 114 >= 160000;
            int color = light ? 0xb3000000 : 0xb3ffffff;
            if (lastColor != color) {
                lastColor = color;
                text.setTextColor(color);
                text.setShadowLayer(density, 0, 0, light ? 0x99ffffff : 0x99000000);
            }
            value.getWindowVisibleDisplayFrame(visibleArea);
            value.getLocationOnScreen(position);
            int bottom = visibleArea.isEmpty() ? 0
                    : Math.max(0, position[1] + value.getHeight() - visibleArea.bottom);
            if (bottomBar != null && bottomBar.isShown()) bottom = Math.max(bottom, bottomBar.getHeight());
            float offset = -Math.max(bottom, value.getPaddingBottom());
            if (text.getTranslationY() != offset) text.setTranslationY(offset);
            visibility(true);
        }
        private void close() {
            preferences.unregisterOnSharedPreferenceChangeListener(this);
            ViewTreeObserver observer = scope.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            visibility(false);
            visibleUri = null;
            label = "";
        }
    }
    private static View find(View root, Context context, String name) {
        int id = context.getResources().getIdentifier(name, "id", context.getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }
    public static State attach(View view) {
        if (!(view instanceof Host)) return null;
        // Stop at the closest viewer, never bind another stacked screen's bottom_bar.
        for (ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            View scope = (View)p;
            View candidate = find(scope, view.getContext(), "quickpic_page_number");
            if (candidate instanceof TextView) {
                State result = new State(view, scope, (TextView)candidate);
                result.refresh(((Host)view).pageController());
                return result;
            }
        }
        return null;
    }
    public static void detach(State state) { if (state != null) state.close(); }
    public static void update(View view, ReaderMode.Host host) {
        if (!(view instanceof Host)) return;
        State state = ((Host)view).pageState();
        if (state != null) state.refresh(host);
    }
    public static void transition(View view, boolean running) {
        if (!(view instanceof Host)) return;
        State state = ((Host)view).pageState();
        if (state == null) return;
        state.transition = running;
        state.refresh(state.controller());
    }
    public static String format(int index, int count) {
        if (count < 1 || index < 0 || index >= count) return "";
        int digits = Math.max(2, Integer.toString(count).length());
        return padded(index + 1, digits) + "/" + padded(count, digits);
    }
    private static String padded(int value, int digits) {
        String text = Integer.toString(value);
        if (text.length() >= digits) return text;
        StringBuilder result = new StringBuilder(digits);
        for (int i = text.length(); i < digits; i++) result.append('0');
        return result.append(text).toString();
    }
    private static boolean same(Uri first, Uri second) { return first != null && first.equals(second); }
}
