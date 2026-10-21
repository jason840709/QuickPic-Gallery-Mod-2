package android.view;
public class View {
 public OnClickListener click;
 public boolean focused;
 public int focusRequests;
 public interface OnClickListener {void onClick(View view);}
 public void setOnClickListener(OnClickListener listener){click=listener;}
 public boolean performClick(){if(click!=null){click.onClick(this);return true;}return false;}
 public boolean requestFocus(){focused=true;focusRequests++;return true;}
 public void setPadding(int left,int top,int right,int bottom){}
}
