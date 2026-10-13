package com.alensw.jni;

/** Candidate routing only. The decoder checks actual container contents. */
public final class AnimationFormats {
    private AnimationFormats() { }
    public static boolean isCandidate(String mime) {
        return "image/gif".equals(mime) || "image/webp".equals(mime);
    }
    /** Low-detail and cached full stills both need an animation-capable load.
     * Type 4 is already animated; types 0/-1 have no usable preview yet.
     * Other formats retain each viewer's original delay-load predicate.
     */
    public static boolean needsAsUpgrade(boolean delayed, int type, String mime) {
        if ("image/webp".equals(mime)) return type == 1 || type == 2;
        return (!delayed && type == 1) || "image/gif".equals(mime);
    }
    public static boolean needsDpUpgrade(boolean delayed, int type, String mime) {
        if ("image/webp".equals(mime)) return type == 1 || type == 2;
        return type == 1 && (!delayed || "image/gif".equals(mime));
    }
    public static String mimeForHandle(int handle) {
        return JniUtils.gifIsAnimatedWebP(handle) ? "image/webp" : "image/gif";
    }
}
