// SPDX-License-Identifier: MIT
package android.widget;
import android.view.View;import android.content.Context;public class ScrollView extends View {public View child;public ScrollView(Context c){super(c);}public void addView(View v){child=v;}}
