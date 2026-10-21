package android.widget;
import android.content.Context;
public class RadioButton extends CompoundButton {
 private final Context context;
 RadioGroup group;
 public RadioButton(Context context){super(context);this.context=context;}
 @Override public void setChecked(boolean value){boolean before=isChecked();super.setChecked(value);if(before!=value && group!=null)group.childCheckedChanged(this);}
 @Override public int getCompoundPaddingStart(){return getPaddingStart()+context.res.radioTextStartPx;}
 @Override public void toggle(){if(!isChecked())super.toggle();}
}
