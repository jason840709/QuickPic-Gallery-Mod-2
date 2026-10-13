package android.graphics;
public class Paint { public static final int FILTER_BITMAP_FLAG=2; public int color; public PorterDuffXfermode xfermode;
 public Paint(){} public Paint(int flags){} public void setColor(int c){color=c;} public void setXfermode(PorterDuffXfermode x){xfermode=x;} }
