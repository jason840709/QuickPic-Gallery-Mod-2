package android.widget;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
public class ScrollView extends ViewGroup {
 public ScrollView(Context context){super(context);setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);}
 @Override public void addView(View child){if(getChildCount()!=0)throw new IllegalStateException("ScrollView permits one child");super.addView(child);}
}
