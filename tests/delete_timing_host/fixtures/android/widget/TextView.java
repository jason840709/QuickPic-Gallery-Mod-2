// SPDX-License-Identifier: MIT
package android.widget;
import android.view.View;import android.content.Context;public class TextView extends View {public CharSequence text;public boolean selectable;public TextView(Context c){super(c);}public void setText(CharSequence t){text=t;}public void setTextIsSelectable(boolean b){selectable=b;}public void setPadding(int a,int b,int c,int d){}}
