package android.graphics.drawable;
import android.graphics.PorterDuff;
public class Drawable {
 public final int resourceId;
 public boolean mutated;
 public int color,filters;
 public PorterDuff.Mode mode;
 public Drawable(){this(0);}
 public Drawable(int id){resourceId=id;}
 public Drawable mutate(){Drawable independent=new Drawable(resourceId);independent.mutated=true;return independent;}
 public void setColorFilter(int value,PorterDuff.Mode valueMode){color=value;mode=valueMode;filters++;}
}
