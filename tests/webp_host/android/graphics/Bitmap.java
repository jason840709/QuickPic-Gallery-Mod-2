package android.graphics;
/** Narrow host pixel double; never packaged. No native/Android codec coverage. */
public final class Bitmap {
 public enum Config { ARGB_8888 }
 public static int LIVE=0, CREATED=0, RECYCLED=0;
 private final int width,height; public final int[] pixels; private boolean recycled;
 private Bitmap(int w,int h){if(w<=0||h<=0)throw new IllegalArgumentException();width=w;height=h;pixels=new int[w*h];LIVE++;CREATED++;}
 public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}
 public int getWidth(){return width;} public int getHeight(){return height;}
 public boolean isRecycled(){return recycled;} public boolean isMutable(){return !recycled;}
 public void recycle(){if(!recycled){recycled=true;LIVE--;RECYCLED++;}}
 public void getPixels(int[] target,int offset,int stride,int x,int y,int w,int h){for(int row=0;row<h;row++)System.arraycopy(pixels,(row+y)*width+x,target,offset+row*stride,w);}
}
