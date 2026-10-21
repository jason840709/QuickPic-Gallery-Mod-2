package android.widget;
import android.content.Context;
import android.view.ViewGroup;
public class LinearLayout extends ViewGroup {
 public static final int HORIZONTAL=0,VERTICAL=1;
 private int orientation=HORIZONTAL;
 public LinearLayout(Context context){super(context);}
 public void setOrientation(int value){orientation=value;}
 public int getOrientation(){return orientation;}
 public static class LayoutParams extends ViewGroup.MarginLayoutParams {
  public float weight;
  public LayoutParams(int width,int height){super(width,height);}
 }
}
