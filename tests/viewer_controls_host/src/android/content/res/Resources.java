package android.content.res;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.util.DisplayMetrics;
import java.util.*;
public class Resources {
 public final Configuration config=new Configuration();
 public final DisplayMetrics metrics=new DisplayMetrics();
 public int radioTextStartPx=36;
 public DisplayMetrics getDisplayMetrics(){return metrics;}
 public final Map<Integer,Integer> colors=new HashMap<Integer,Integer>();
 public final List<Drawable> drawables=new ArrayList<Drawable>();
 public Configuration getConfiguration(){return config;}
 public int getColor(int id){if(!colors.containsKey(id))throw new AssertionError("Unknown color resource "+id);return colors.get(id);}
 public Drawable getDrawable(int id){Drawable d=new Drawable(id);drawables.add(d);return d;}
 public String getString(int id){
  if(id==android.R.string.cancel)return "Cancel";
  if(id==android.R.string.ok)return "OK";
  if(id==0x7f0a0029)return "zh".equals(config.locale.getLanguage())?"自訂":"Custom";
  return "resource:"+Integer.toHexString(id);
 }
 public static class Theme {
  public final Map<Integer,int[]> values=new HashMap<Integer,int[]>();
  public final List<Integer> resolved=new ArrayList<Integer>();
  public boolean resolveAttribute(int id,TypedValue out,boolean resolve){
   resolved.add(id);int[] v=values.get(id);if(v==null)return false;out.resourceId=v[0];out.data=v[1];return true;
  }
 }
}
