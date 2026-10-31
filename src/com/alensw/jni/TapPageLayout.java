// SPDX-License-Identifier: MIT
package com.alensw.jni;

/** Geometry only. Zero leaves the image viewer's normal tap action unchanged. */
public final class TapPageLayout {
    public static final int OFF = 0;
    public static final int BOTTOM_CORNERS = 1;
    public static final int SIDES = 2;
    public static final int TOP_BOTTOM = 3;
    public static final int LEFT_SIDE = 4;
    public static final int RIGHT_SIDE = 5;
    public static final int MIDDLE_SIDES = 6;
    public static final int COUNT = 7;

    private TapPageLayout() {}

    public static int action(int mode, float x, float y, int width, int height) {
        if (mode <= OFF || mode >= COUNT || width < 3 || height < 2
                || Float.isNaN(x) || Float.isInfinite(x)
                || Float.isNaN(y) || Float.isInfinite(y)
                || x < 0 || y < 0 || x >= width || y >= height) return 0;
        if (mode == BOTTOM_CORNERS) {
            // Preserve the original integer thresholds, including the middle seams.
            if (y < height / 2) return 0;
            if (x < width / 3) return -1;
            if (x > (width * 2L) / 3L) return 1;
            return 0;
        }
        double horizontal = (double)x / width;
        double vertical = (double)y / height;
        switch (mode) {
            case SIDES:
                return horizontal < .30 ? -1 : horizontal > .70 ? 1 : 0;
            case TOP_BOTTOM:
                return vertical < .30 ? -1 : vertical > .70 ? 1 : 0;
            case LEFT_SIDE:
                return horizontal < .30 ? (vertical < .50 ? -1 : 1) : 0;
            case RIGHT_SIDE:
                return horizontal > .70 ? (vertical < .50 ? -1 : 1) : 0;
            case MIDDLE_SIDES:
                if (vertical < .35 || vertical > .65) return 0;
                return horizontal < .30 ? -1 : horizontal > .70 ? 1 : 0;
            default:
                return 0;
        }
    }
}
