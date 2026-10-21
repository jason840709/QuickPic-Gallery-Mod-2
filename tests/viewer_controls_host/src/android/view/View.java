package android.view;
import android.graphics.Rect;
import java.util.concurrent.atomic.AtomicInteger;
public class View {
 public static final int NO_ID=-1,FOCUS_DOWN=130;
 private static final AtomicInteger NEXT_GENERATED_ID=new AtomicInteger(1);
 public OnClickListener click;
 public boolean focused,focusableInTouchMode;
 public int focusRequests,paddingLeft,paddingTop,paddingRight,paddingBottom,paddingStart,paddingEnd;
 ViewGroup parent;
 private int id=NO_ID;
 private ViewGroup.LayoutParams layoutParams;
 public static int generateViewId(){
  for(;;){int value=NEXT_GENERATED_ID.get(),next=value+1;if(next>0x00ffffff)next=1;
   if(NEXT_GENERATED_ID.compareAndSet(value,next))return value;}
 }
 public void setId(int value){id=value;}
 public int getId(){return id;}
 public interface OnClickListener {void onClick(View view);}
 public void setOnClickListener(OnClickListener listener){click=listener;}
 public boolean performClick(){if(click!=null){click.onClick(this);return true;}return false;}
 public final boolean requestFocus(){return requestFocus(FOCUS_DOWN,null);}
 public final boolean requestFocus(int direction){return requestFocus(direction,null);}
 // Model touch-mode focus for this dialog; nonfocusable containers reject focus.
 public boolean requestFocus(int direction,Rect prior){focusRequests++;if(!focusableInTouchMode)return false;focused=true;return true;}
 public void setFocusableInTouchMode(boolean value){focusableInTouchMode=value;}
 public void setPadding(int left,int top,int right,int bottom){paddingLeft=paddingStart=left;paddingTop=top;paddingRight=paddingEnd=right;paddingBottom=bottom;}
 public void setPaddingRelative(int start,int top,int end,int bottom){setPadding(start,top,end,bottom);}
 public int getPaddingStart(){return paddingStart;}
 public int getPaddingEnd(){return paddingEnd;}
 public int getPaddingLeft(){return paddingLeft;}
 public int getPaddingRight(){return paddingRight;}
 public int getPaddingTop(){return paddingTop;}
 public int getPaddingBottom(){return paddingBottom;}
 public ViewGroup.LayoutParams getLayoutParams(){return layoutParams;}
 public void setLayoutParams(ViewGroup.LayoutParams value){layoutParams=value;}
}
