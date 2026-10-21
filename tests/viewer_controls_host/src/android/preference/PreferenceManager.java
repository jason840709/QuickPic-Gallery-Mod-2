package android.preference;
import android.content.*;
public class PreferenceManager {
 public static SharedPreferences prefs=new SharedPreferences();
 public static SharedPreferences getDefaultSharedPreferences(Context context){return prefs;}
}
