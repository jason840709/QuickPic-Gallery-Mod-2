package android.content;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
public class Context {
 public final Resources res=new Resources();
 public final Resources.Theme theme=new Resources.Theme();
 public Context(){theme.values.put(0x7f010001,new int[]{0,0xff123456});}
 public Resources getResources(){return res;}
 public Resources.Theme getTheme(){return theme;}
 public String getString(int id){return res.getString(id);}
 public Drawable getDrawable(int id){return res.getDrawable(id);}
}
