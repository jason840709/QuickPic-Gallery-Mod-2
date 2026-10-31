// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.view.View;
import android.view.MotionEvent;
import java.util.WeakHashMap;

/** Routes only the existing detector's confirmed single-tap callback. */
public final class TapPageNavigation {
    // Values never retain their View keys. UP intentionally leaves this bit intact
    // until the existing detector's delayed callback, or the next DOWN, consumes it.
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

    /** False leaves the original single-tap behavior completely in control. */
    public static boolean onConfirmedTap(View picture, Host host, float x, float y) {
        if (picture == null || host == null || !picture.isShown()
                || !picture.hasWindowFocus() || blocked(picture)
                || !host.quickpicTapReady()) return false;
        final int mode;
        try {
            mode = TapPagePreference.mode(picture.getContext());
        } catch (RuntimeException unavailable) {
            return false;
        }
        if (mode < 1 || mode > 6) return false;
        int width = picture.getWidth(), height = picture.getHeight();
        if (width <= 0 || height <= 0 || Float.isNaN(x) || Float.isNaN(y)
                || Float.isInfinite(x) || Float.isInfinite(y)
                || x < 0 || y < 0 || x >= width || y >= height) return false;
        // Popup windows lose focus; native top/bottom controls use their original hit test.
        // The view's ordinary event dispatch remains responsible for clicking menu items.
        if (host.quickpicTapControlsHit(x, y)) return false;
        int action = TapPageLayout.action(mode, x, y, width, height);
        if (action == -1 || action == 1) {
            host.quickpicTapTurn(action);
            return true;
        }
        // Enabled layouts always reserve the remaining content for the controls.
        // Disabled mode above keeps the original single/double-tap zoom preferences.
        return host.quickpicTapToggleControls();
    }
}
