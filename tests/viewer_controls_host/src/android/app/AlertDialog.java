package android.app;
import android.content.*;
import android.view.View;
import java.util.*;
public class AlertDialog implements DialogInterface {
 public static AlertDialog last;
 public static final List<AlertDialog> shown=new ArrayList<AlertDialog>();
 public DialogInterface.OnClickListener choice,negative;
 public DialogInterface.OnShowListener onShow;
 public CharSequence[] labels;
 public int selected=-1;
 public boolean dismissed;
 public CharSequence title,positiveLabel,negativeLabel;
 public View view;
 public final View positiveButton=new View();
 public void dismiss(){dismissed=true;}
 public void setOnShowListener(DialogInterface.OnShowListener listener){onShow=listener;}
 public View getButton(int button){return positiveButton;}
 public void show(){last=this;shown.add(this);if(onShow!=null)onShow.onShow(this);}
 public void cancel(){if(negative!=null)negative.onClick(this,BUTTON_NEGATIVE);dismiss();}
 public void select(int index){selected=index;choice.onClick(this,index);}
 public static class Builder {
  final AlertDialog dialog=new AlertDialog();final Context context;
  public Builder(Context value){context=value;}
  public Builder setTitle(int title){return setTitle(context.getString(title));}
  public Builder setTitle(CharSequence title){dialog.title=title;return this;}
  public Builder setSingleChoiceItems(CharSequence[] labels,int selected,DialogInterface.OnClickListener listener){dialog.labels=labels;dialog.selected=selected;dialog.choice=listener;return this;}
  public Builder setNegativeButton(int label,DialogInterface.OnClickListener listener){return setNegativeButton(context.getString(label),listener);}
  public Builder setNegativeButton(CharSequence label,DialogInterface.OnClickListener listener){dialog.negativeLabel=label;dialog.negative=listener;return this;}
  public Builder setPositiveButton(int label,DialogInterface.OnClickListener listener){return setPositiveButton(context.getString(label),listener);}
  public Builder setPositiveButton(CharSequence label,DialogInterface.OnClickListener listener){dialog.positiveLabel=label;return this;}
  public Builder setView(View view){dialog.view=view;return this;}
  public AlertDialog create(){return dialog;}
  public AlertDialog show(){dialog.show();return dialog;}
 }
}
