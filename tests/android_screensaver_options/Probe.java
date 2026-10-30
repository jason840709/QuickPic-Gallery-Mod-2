package local.quickpic.optionsprobe;
import android.app.*;import android.content.*;import android.os.*;import android.preference.PreferenceManager;import android.graphics.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;
public final class Probe extends Instrumentation {
 Bundle args;
 public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();ClassLoader l=c.getClassLoader();String mode=args.getString("mode","state");
  SharedPreferences p=PreferenceManager.getDefaultSharedPreferences(c);
  JSONObject j=new JSONObject();j.put("mode",mode);j.put("version",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
  if(mode.equals("off"))p.edit().putBoolean("screensaver_option_generic",false).putBoolean("screensaver_option_bigme",false).putBoolean("screensaver_option_boox",false).putBoolean("screensaver_options_configured",true).commit();
  if(mode.equals("generic"))p.edit().putBoolean("screensaver_option_generic",true).putBoolean("screensaver_option_bigme",false).putBoolean("screensaver_option_boox",false).putBoolean("screensaver_options_configured",true).commit();
  if(mode.equals("state")||mode.equals("off")||mode.equals("generic")){
   int[] a=(int[])l.loadClass("com.alensw.jni.ScreensaverOptions").getMethod("visibleActions",Context.class).invoke(null,c);
   j.put("actions",new JSONArray(a));for(Map.Entry<String,?>e:p.getAll().entrySet())if(e.getKey().startsWith("screensaver_"))j.put(e.getKey(),e.getValue());
  }
  if(mode.equals("open")||mode.equals("menu")){
   File source=new File(c.getCacheDir(),"synthetic-source.png");Bitmap b=Bitmap.createBitmap(90,120,Bitmap.Config.ARGB_8888);b.eraseColor(0xffc04a21);try(FileOutputStream o=new FileOutputStream(source)){b.compress(Bitmap.CompressFormat.PNG,100,o);}b.recycle();
   File png=(File)l.loadClass("com.alensw.jni.ScreensaverImage").getMethod("prepare",String.class,int.class,int.class,File.class,boolean.class).invoke(null,source.getAbsolutePath(),90,120,Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),false);
   j.put("png",png.getAbsolutePath());
   Object u=l.loadClass("android.support.v4.content.FileProvider").getMethod("getUriForFile",Context.class,String.class,File.class).invoke(null,c,c.getPackageName()+".provider",png);j.put("uri",u.toString());
   if(mode.equals("open"))runOnMainSync(()->{try{l.loadClass("com.alensw.jni.ScreensaverOpener").getMethod("open",Context.class,File.class).invoke(null,c,png);}catch(Exception e){throw new RuntimeException(e);}});
   else {
    Intent start=new Intent();start.setClassName(c,c.getPackageName()+".GalleryActivity");start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity a=startActivitySync(start);
    runOnMainSync(()->{try{Class<?> h=l.loadClass("com.alensw.ui.c.ScreenSaverHelper");Class<?> callback=l.loadClass("com.alensw.ui.c.ScreenSaverHelper$Callback");h.getMethod("showScreenSaverDialog",Context.class,String.class,callback).invoke(null,a,png.getAbsolutePath(),null);}catch(Exception e){throw new RuntimeException(e);}});
   }
  }
  result.putString("stream",j.toString()+"\n");result.putString("status","PASS");finish(0,result);
 }catch(Throwable e){StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));result.putString("stream",s.toString());result.putString("status","FAIL");finish(1,result);}}
}
