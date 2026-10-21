package android.widget;
import android.content.Context;
import android.view.View;
public class TextView extends View {
 public String text="",hint;
 public boolean singleLine;
 private int minHeight;
 public TextView(Context context){}
 public void setText(CharSequence value){text=value.toString();}
 public CharSequence getText(){return text;}
 public void setHint(CharSequence value){hint=value.toString();}
 public void setSingleLine(boolean value){singleLine=value;}
 public void setMinHeight(int value){minHeight=value;}
 public int getMinHeight(){return minHeight;}
 public int getCompoundPaddingStart(){return getPaddingStart();}
}
