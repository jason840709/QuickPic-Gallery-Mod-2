package android.util;
public class Log {
 public static int errors,warnings;
 public static int w(String tag,String message,Throwable error){warnings++;return 0;}
 public static int e(String tag,String message,Throwable error){errors++;return 0;}
}
