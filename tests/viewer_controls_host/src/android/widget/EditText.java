package android.widget;
import android.content.Context;
public class EditText extends TextView {
 public static EditText last;
 public String error;
 public int inputType;
 public boolean allSelected;
 public EditText(Context context){super(context);last=this;}
 public void setInputType(int value){inputType=value;}
 public void selectAll(){allSelected=true;}
 public void setSelection(int position){}
 public void setError(CharSequence value){error=value==null?null:value.toString();}
}
