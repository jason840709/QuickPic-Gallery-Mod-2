package android.view;
import android.graphics.drawable.Drawable;
public interface MenuItem {
 int SHOW_AS_ACTION_NEVER=0,SHOW_AS_ACTION_IF_ROOM=1,SHOW_AS_ACTION_ALWAYS=2;
 void setShowAsAction(int mode);
 MenuItem setIcon(int id);
 MenuItem setIcon(Drawable icon);
 Drawable getIcon();
 int getItemId();
 MenuItem setTitle(int title);
 MenuItem setTitle(CharSequence title);
 CharSequence getTitle();
}
