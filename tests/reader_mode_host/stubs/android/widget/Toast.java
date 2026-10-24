// SPDX-License-Identifier: MIT
package android.widget;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
public final class Toast {
    public static final int LENGTH_SHORT=0;
    public static final List<String> shown=new ArrayList<String>();
    private final String text;
    private Toast(String text) { this.text=text; }
    public static Toast makeText(Context context, CharSequence text, int length) { return new Toast(text.toString()); }
    public void show() { shown.add(text); }
}
