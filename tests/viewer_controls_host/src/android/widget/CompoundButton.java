package android.widget;
import android.content.Context;
public class CompoundButton extends TextView {
 private boolean checked;
 public int idAtFirstSetChecked=Integer.MIN_VALUE;
 public CompoundButton(Context context){super(context);}
 public void setChecked(boolean value){if(idAtFirstSetChecked==Integer.MIN_VALUE)idAtFirstSetChecked=getId();checked=value;}
 public boolean isChecked(){return checked;}
 public void toggle(){setChecked(!isChecked());}
 @Override public boolean performClick(){toggle();return super.performClick();}
}
