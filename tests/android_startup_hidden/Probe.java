package local.quickpic.hidden29;
import android.app.*;import android.content.*;import android.os.*;import android.preference.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;
public final class Probe extends Instrumentation {
 Context target;SharedPreferences prefs;Class<?> app;Bundle args;Activity activity;int passed;JSONArray checks=new JSONArray();
 interface Work{void run()throws Exception;}
 void ui(Work work){Throwable[] err={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){err[0]=e;}});if(err[0]!=null)throw new RuntimeException(err[0]);}
 void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;checks.put(name);}
 void idle(){waitForIdleSync();SystemClock.sleep(160);}
 boolean visible()throws Exception{Object state=app.getField("q").get(null);return state.getClass().getField("b").getBoolean(state);}
 void apply()throws Exception{ui(()->app.getMethod("applyHiddenDefault").invoke(null));}
 void open()throws Exception{activity=startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName(target,target.getPackageName()+".GalleryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();}
 void manual()throws Exception{ui(()->{Object controller=activity.getClass().getMethod("c").invoke(activity);Class<?> owner=controller.getClass();Method toggle=owner.getDeclaredMethod("o");toggle.setAccessible(true);toggle.invoke(controller);});idle();}
 void shot(String name)throws Exception{android.graphics.Bitmap image=getUiAutomation().takeScreenshot();if(image==null)return;File dir=new File(target.getExternalCacheDir(),"hidden29-results");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
 void setting()throws Exception{
  Activity a=startActivitySync(new Intent().setClassName(target.getPackageName(),"com.alensw.ui.activity.MainPreference").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();PreferenceActivity pa=(PreferenceActivity)a;ListView list=pa.getListView();String title=target.getString(target.getResources().getIdentifier("security","string",target.getPackageName()));int index=-1;
  for(int i=0;i<list.getAdapter().getCount();i++){Object p=list.getAdapter().getItem(i);if(p instanceof Preference&&title.contentEquals(((Preference)p).getTitle()))index=i;}check(index>=0,"native Security screen exists");final int row=index;
  ui(()->list.setSelection(row));idle();ui(()->list.performItemClick(list.getChildAt(row-list.getFirstVisiblePosition()),row,list.getAdapter().getItemId(row)));idle();
  Preference pref=pa.getPreferenceScreen().findPreference("startup_show_hidden");check(pref instanceof SwitchPreference&&((SwitchPreference)pref).isChecked(),"startup switch under Security defaults on");
  ListView child=pa.getListView();int at=-1;for(int i=0;i<child.getAdapter().getCount();i++)if(child.getAdapter().getItem(i)==pref)at=i;check(at>=0,"startup switch is a real native row");final int position=at;
  ui(()->child.setSelection(position));idle();ui(()->child.performItemClick(child.getChildAt(position-child.getFirstVisiblePosition()),position,child.getAdapter().getItemId(position)));idle();check(!prefs.getBoolean("startup_show_hidden",true),"native row saves off");shot("security-off");ui(()->a.finish());idle();
 }
 void callbacks()throws Exception{
  ui(()->prefs.edit().putBoolean("startup_show_hidden",false).putBoolean("protect_hidden",false).putBoolean("protect_delete",true).putString("password","synthetic29").commit());apply();manual();check(visible(),"off allows manual show before protected operation");
  final int[] success={0},fail={0};ui(()->{Class<?> callback=target.getClassLoader().loadClass("com.alensw.ui.a.ac");Constructor<?> ctor=callback.getDeclaredConstructor(String.class,boolean.class,Runnable.class,Runnable.class);ctor.setAccessible(true);Object handler=ctor.newInstance("synthetic29",true,(Runnable)()->success[0]++,(Runnable)()->fail[0]++);
   Class<?> password=target.getClassLoader().loadClass("com.alensw.ui.view.PasswordView");Object view=password.getConstructor(Context.class).newInstance(activity);Method invoke=callback.getMethod("a",password,String.class);invoke.setAccessible(true);invoke.invoke(handler,view,"wrong");check(success[0]==0&&fail[0]==1&&visible(),"wrong password does not authenticate or change manually shown state");invoke.invoke(handler,view,"synthetic29");check(success[0]==1&&fail[0]==1&&visible(),"off shared unlock preserves manual visibility for protected delete");
  });
  ui(()->prefs.edit().putBoolean("startup_show_hidden",true).putBoolean("protect_hidden",true).commit());ui(()->app.getField("y").set(null,""));apply();check(!visible(),"enabled startup cannot bypass locked hidden password");
 }
 @Override public void onCreate(Bundle values){super.onCreate(values);args=values;start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  target=getTargetContext();prefs=PreferenceManager.getDefaultSharedPreferences(target);app=target.getClassLoader().loadClass(target.getPackageName()+".QuickApp");String stage=args.getString("stage","");
  if(stage.equals("seed")){prefs.edit().remove("startup_show_hidden").putString("slideshow_interval","7").putBoolean("show_page_number",true).putString("probe29_marker","keep me").commit();out.putString("seed_version",target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName);finish(Activity.RESULT_OK,out);return;}
  check(target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName.equals("11.0.29"),"exact installed29");open();for(int n=0;n<60&&app.getField("q").get(null)==null;n++)idle();
  if(stage.equals("default")){check(!prefs.contains("startup_show_hidden")&&visible(),"upgrade without key keeps28 startup showing hidden");check(prefs.getBoolean("show_page_number",false)&&prefs.getString("slideshow_interval","").equals("7")&&prefs.getString("probe29_marker","").equals("keep me"),"28 preferences survive29 update");setting();}
  else if(stage.equals("off")){
   check(!prefs.getBoolean("startup_show_hidden",true)&&!visible(),"off survives process death and cold startup conceals hidden");manual();check(visible(),"original overflow show action still works when startup off");manual();check(!visible(),"original overflow hide action still works");callbacks();
   for(boolean enabled:new boolean[]{false,true})for(boolean protect:new boolean[]{false,true})for(String password:new String[]{"","synthetic29"})for(String known:new String[]{"","synthetic29"}){
    ui(()->prefs.edit().putBoolean("startup_show_hidden",enabled).putBoolean("protect_hidden",protect).putString("password",password).commit());ui(()->app.getField("y").set(null,known));apply();boolean wanted=enabled&&(!protect||password.isEmpty()||password.equals(known));check(visible()==wanted,"actual policy "+enabled+"/"+protect+"/"+password.isEmpty()+"/"+known.isEmpty());
   }
   ui(()->prefs.edit().putBoolean("startup_show_hidden",true).putBoolean("protect_hidden",false).putBoolean("protect_delete",false).putString("password","").commit());
  }else if(stage.equals("on")){check(prefs.getBoolean("startup_show_hidden",false)&&visible(),"saved on survives process death and cold startup shows hidden");manual();check(!visible(),"enabled startup still permits manual concealment in same foreground");}
  JSONObject result=new JSONObject().put("stage",stage).put("passed",passed).put("checks",checks).put("scope","Exact installed App API29 emulator; synthetic preferences and no real photos");File dir=new File(target.getExternalCacheDir(),"hidden29-results");dir.mkdirs();try(FileOutputStream stream=new FileOutputStream(new File(dir,stage+".json"))){stream.write(result.toString(2).getBytes("UTF-8"));}out.putString("result",result.toString());finish(Activity.RESULT_OK,out);
 }catch(Throwable error){try{shot("failure");}catch(Exception ignored){}out.putString("failure",android.util.Log.getStackTraceString(error));out.putInt("passed",passed);finish(Activity.RESULT_CANCELED,out);}}
}
