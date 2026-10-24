// SPDX-License-Identifier: MIT
package android.preference;
import android.content.Context;
public final class PreferenceManager {
    public static Preferences getDefaultSharedPreferences(Context context) { return new Preferences(context); }
    public static final class Preferences {
        private final Context context;
        Preferences(Context context) { this.context=context; }
        public boolean getBoolean(String key, boolean fallback) { return "reader_mode".equals(key)?context.readerEnabled:fallback; }
    }
}
