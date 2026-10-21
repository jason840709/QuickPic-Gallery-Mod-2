package android.widget;
import android.content.Context;
import android.view.View;
public class RadioGroup extends LinearLayout {
 private int checkedId=View.NO_ID;
 private boolean protect;
 public RadioGroup(Context context){super(context);setOrientation(VERTICAL);}
 @Override public void addView(View child){
  // Android captures a prechecked child's ID before its hierarchy listener
  // generates an ID for an anonymous RadioButton. Preserve that ordering bug.
  if(child instanceof RadioButton && ((RadioButton)child).isChecked()){
   protect=true;if(checkedId!=View.NO_ID)setChildChecked(checkedId,false);protect=false;
   checkedId=child.getId();
  }
  super.addView(child);
  if(child instanceof RadioButton){
   if(child.getId()==View.NO_ID)child.setId(View.generateViewId());
   ((RadioButton)child).group=this;
  }
 }
 public int getCheckedRadioButtonId(){return checkedId;}
 void childCheckedChanged(RadioButton child){
  if(protect)return;protect=true;
  if(checkedId!=View.NO_ID)setChildChecked(checkedId,false);
  protect=false;checkedId=child.getId();
 }
 private void setChildChecked(int id,boolean value){
  for(int i=0;i<getChildCount();i++)if(getChildAt(i).getId()==id && getChildAt(i) instanceof RadioButton){
   ((RadioButton)getChildAt(i)).setChecked(value);return;
  }
 }
 public static class LayoutParams extends LinearLayout.LayoutParams {
  public LayoutParams(int width,int height){super(width,height);}
 }
}
