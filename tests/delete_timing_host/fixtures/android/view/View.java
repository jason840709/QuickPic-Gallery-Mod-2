// SPDX-License-Identifier: MIT
package android.view;
import android.content.Context;public class View {public interface OnClickListener {void onClick(View v);}public final Context context;public OnClickListener listener;public View(Context c){context=c;}public void setOnClickListener(OnClickListener l){listener=l;}public void click(){listener.onClick(this);}}
