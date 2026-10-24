// SPDX-License-Identifier: MIT
package android.app;
import android.content.Context;public class Dialog {private final Context context;public int contextReads;public Dialog(Context c){context=c;}public Context getContext(){contextReads++;return context;}}
