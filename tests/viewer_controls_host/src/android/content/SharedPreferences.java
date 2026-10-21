package android.content;
import java.util.*;
public class SharedPreferences {
 public final Map<String,String> map=new HashMap<String,String>();
 public int commits,applies;
 public String getString(String key,String fallback){return map.containsKey(key)?map.get(key):fallback;}
 public Editor edit(){return new Editor();}
 public class Editor {
  final Map<String,String> pending=new HashMap<String,String>();
  public Editor putString(String key,String value){pending.put(key,value);return this;}
  public boolean commit(){map.putAll(pending);commits++;return true;}
  public void apply(){map.putAll(pending);applies++;}
 }
}
