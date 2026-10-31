// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.util.WeakHashMap;

/** Pages on a detector-validated release; non-page taps keep original confirmation. */
public final class TapPageNavigation {
    // Values never retain their View keys. Keep rejection through any delayed
    // confirmation; the next DOWN starts a fresh gesture.
    private static final WeakHashMap<View, Boolean> BLOCKED = new WeakHashMap<View, Boolean>();
    /** Direct DEX bridges: no controller fields or methods are guessed by reflection. */
    public interface Host {
        boolean quickpicTapReady();
        boolean quickpicTapControlsHit(float localX, float localY);
        void quickpicTapTurn(int direction);
        boolean quickpicTapToggleControls();
    }

    private TapPageNavigation() {}

    /** Observe only disqualifying events; the original detector still gets every event. */
    public static void track(View picture, MotionEvent event) {
        if (picture == null || event == null) return;
        int action = event.getActionMasked();
        synchronized (BLOCKED) {
            if (action == MotionEvent.ACTION_DOWN) {
                BLOCKED.put(picture, event.getPointerCount() > 1);
            } else if (event.getPointerCount() > 1 || action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_CANCEL) {
                BLOCKED.put(picture, true);
            }
        }
    }

    private static boolean blocked(View picture) {
        synchronized (BLOCKED) { return Boolean.TRUE.equals(BLOCKED.get(picture)); }
    }

    /** Called only by the existing detector's single-pointer, stationary UP branch. */
    public static boolean onTapRelease(View picture, Host host, MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_UP
                || event.getPointerCount() != 1) return false;
        if (event.getEventTime() - event.getDownTime() >= ViewConfiguration.getLongPressTimeout()) {
            // A queued UP can be handled before the detector's delayed callback.
            // Keep that callback from reviving the same rejected long press.
            if (picture != null) synchronized (BLOCKED) { BLOCKED.put(picture, true); }
            return false;
        }
        return route(picture, host, event.getX(), event.getY(), false);
    }

    /** Other areas retain confirmation and the original double-tap behavior. */
    public static boolean onConfirmedTap(View picture, Host host, float x, float y) {
        return route(picture, host, x, y, true);
    }

    private static boolean route(View picture, Host host, float x, float y, boolean includeMenu) {
        if (picture == null || host == null || !picture.isShown()
                || !picture.hasWindowFocus() || blocked(picture)
                || !host.quickpicTapReady()) return false;
        final int mode;
        try {
            mode = TapPagePreference.mode(picture.getContext());
        } catch (RuntimeException unavailable) {
            return false;
        }
        if (mode < 1 || mode >= TapPageLayout.COUNT) return false;
        int width = picture.getWidth(), height = picture.getHeight();
        if (width <= 0 || height <= 0 || Float.isNaN(x) || Float.isNaN(y)
                || Float.isInfinite(x) || Float.isInfinite(y)
                || x < 0 || y < 0 || x >= width || y >= height) return false;
        // Popup windows lose focus; native top/bottom controls use their original hit test.
        // The view's ordinary event dispatch remains responsible for clicking menu items.
        if (host.quickpicTapControlsHit(x, y)) return false;
        int action = TapPageLayout.action(mode, TapPagePreference.reversed(picture.getContext()), x, y, width, height);
        if (action == -1 || action == 1) {
            host.quickpicTapTurn(action);
            return true;
        }
        // Enabled layouts always reserve the remaining content for the controls.
        // Disabled mode above keeps the original single/double-tap zoom preferences.
        return includeMenu && host.quickpicTapToggleControls();
    }
}
