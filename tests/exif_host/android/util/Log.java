package android.util;
public final class Log { public static int e(String tag, String message, Throwable failure) { System.err.println(tag+": "+message); return 0; } }
