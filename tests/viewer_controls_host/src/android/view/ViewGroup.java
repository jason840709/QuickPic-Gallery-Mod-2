package android.view;
import android.content.Context;
import android.graphics.Rect;
import java.util.*;
public class ViewGroup extends View {
 public static final int FOCUS_BEFORE_DESCENDANTS=0x20000,FOCUS_AFTER_DESCENDANTS=0x40000,FOCUS_BLOCK_DESCENDANTS=0x60000;
 public final List<View> children=new ArrayList<View>();
 private int descendantFocusability=FOCUS_BEFORE_DESCENDANTS;
 public ViewGroup(Context context){}
 public void setDescendantFocusability(int value){descendantFocusability=value;}
 public int getDescendantFocusability(){return descendantFocusability;}
 @Override public boolean requestFocus(int direction,Rect prior){
  if(descendantFocusability==FOCUS_BLOCK_DESCENDANTS)return super.requestFocus(direction,prior);
  if(descendantFocusability==FOCUS_BEFORE_DESCENDANTS && super.requestFocus(direction,prior))return true;
  for(View child:children)if(child.requestFocus(direction,prior))return true;
  return descendantFocusability==FOCUS_AFTER_DESCENDANTS && super.requestFocus(direction,prior);
 }
 public int getChildCount(){return children.size();}
 public View getChildAt(int index){return children.get(index);}
 public void addView(View view){if(view.parent!=null)throw new IllegalStateException("View already has parent");children.add(view);view.parent=this;}
 public void addView(View view,LayoutParams params){view.setLayoutParams(params);addView(view);}
 public static class LayoutParams {
  public static final int MATCH_PARENT=-1,WRAP_CONTENT=-2;
  public int width,height;
  public LayoutParams(int width,int height){this.width=width;this.height=height;}
 }
 public static class MarginLayoutParams extends LayoutParams {
  public int leftMargin,topMargin,rightMargin,bottomMargin;
  public MarginLayoutParams(int width,int height){super(width,height);}
 }
}
