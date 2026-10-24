// SPDX-License-Identifier: MIT
package android.widget;
import android.content.Context;public class Toast {public static final int LENGTH_SHORT=0;public static String last;public static int made,shown;public static Toast makeText(Context c,String t,int duration){made++;last=t;return new Toast();}public void show(){shown++;}}
