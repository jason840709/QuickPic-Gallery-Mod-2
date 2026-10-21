package android.widget;
import android.content.Context;
import android.view.View;
import java.util.*;
public class LinearLayout extends View {
 public static final int VERTICAL=1;
 public final List<View> children=new ArrayList<View>();
 public LinearLayout(Context context){}
 public void setOrientation(int orientation){}
 public void addView(View view){children.add(view);}
 public void addView(View view,LayoutParams params){children.add(view);}
 public static class LayoutParams {
  public static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;
  public LayoutParams(int width,int height){}
 }
}
