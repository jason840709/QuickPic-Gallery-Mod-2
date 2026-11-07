package local.quickpic.page28;
import android.app.*;import android.content.*;import android.content.pm.ActivityInfo;import android.content.res.*;import android.graphics.*;import android.net.Uri;import android.os.*;import android.preference.*;import android.view.*;import android.view.accessibility.AccessibilityNodeInfo;import android.widget.*;
import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;
/** Synthetic fixtures and the installed App only; no copied App implementation. */
public final class Probe extends Instrumentation {
 Context target;SharedPreferences prefs;Activity activity;View picture;TextView counter;Object controller;Class<?> helper;Bundle arguments;int passed;JSONArray checks=new JSONArray();File folder;boolean tablet,folderMode;
 interface Work { void run() throws Exception; }
 void ui(Work work){final Throwable[] failure={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new RuntimeException(failure[0]);}
 void check(boolean value,String name){if(!value)throw new AssertionError(name);passed++;checks.put(name);}
 void idle(){waitForIdleSync();SystemClock.sleep(120);}
 Object call(Object owner,String name,Class<?>[] types,Object... args)throws Exception{return owner.getClass().getMethod(name,types).invoke(owner,args);}
 void hidden(Object owner,String name,Class<?>[] types,Object... args)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,args);}
 int index()throws Exception{return (Integer)call(controller,"readerIndex",new Class<?>[]{});}
 int count()throws Exception{return (Integer)call(controller,"readerCount",new Class<?>[]{});}
 Uri uri(int i)throws Exception{return (Uri)call(controller,"readerUri",new Class<?>[]{int.class},i);}
 String expected()throws Exception{return (String)helper.getMethod("format",int.class,int.class).invoke(null,index(),count());}
 View find(View root,String className){if(root.getClass().getName().equals(className)&&root.isShown())return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),className);if(v!=null)return v;}return null;}
 void bind()throws Exception{picture=find(activity.getWindow().getDecorView(),"com.alensw.ui.view.PictureView");if(picture==null)throw new Exception("No PictureView");controller=activity.getClass().getMethod("c").invoke(activity);int id=target.getResources().getIdentifier("quickpic_page_number","id",target.getPackageName());counter=null;for(ViewParent p=picture.getParent();p instanceof View;p=p.getParent()){View found=((View)p).findViewById(id);if(found instanceof TextView){counter=(TextView)found;break;}}if(counter==null)throw new Exception("No page TextView");}
 void waitLabel()throws Exception{for(int i=0;i<100;i++){final boolean[] ok={false};ui(()->ok[0]=counter.isShown()&&counter.getText().toString().equals(expected()));if(ok[0])return;idle();}throw new Exception("Page not synchronized: label="+counter.getText()+" visibility="+counter.getVisibility()+" index="+index()+" count="+count());}
 void fixtures()throws Exception{folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"QuickPicPage28");folder.mkdirs();for(int i=1;i<=50;i++){File file=new File(folder,String.format(Locale.ROOT,"page-%02d.png",i));if(file.exists())continue;Bitmap b=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(215+i%4*6,220+i%3*5,230));Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(Color.DKGRAY);p.setTextSize(130);c.drawText(""+i,170,340,p);try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}}
 void open(Uri selected)throws Exception{StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());Intent intent=new Intent(Intent.ACTION_VIEW).setClassName(target,target.getPackageName()+".GalleryActivity").setDataAndType(selected,"image/png").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);activity=startActivitySync(intent);idle();
if(folderMode&&!selected.getPath().contains("page28-single")){
 ui(()->{ClassLoader l=target.getClassLoader();Class<?> common=l.loadClass("com.alensw.bean.CommonFolder"),local=l.loadClass("com.alensw.bean.LocalFolder"),as=l.loadClass("com.alensw.ui.c.as");Object book=local.getConstructor(String.class,String.class,int.class,long.class,long.class).newInstance(folder.getAbsolutePath(),"Synthetic book",50,0L,0L);
 for(int n=1;n<=50;n++)local.getMethod("a",char.class,int.class,String.class).invoke(book,'I',0,String.format(Locale.ROOT,"page-%02d.png",n));common.getField("k").setInt(book,6);
 Intent in=(Intent)as.getMethod("a",common,boolean.class).invoke(null,book,false);activity.getClass().getMethod("a",Class.class,Intent.class,Bundle.class).invoke(activity,as,in,null);});idle();
}
for(int i=0;i<60;i++){try{bind();if(picture.getWidth()>0&&(selected.getPath().contains("page28-single")?count()<=1:count()==50))break;}catch(Exception e){}idle();}bind();dismissTips();idle();}
 void dismissTips(){for(int n=0;n<3;n++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return;List<AccessibilityNodeInfo> tips=root.findAccessibilityNodeInfosByText("Got it");for(AccessibilityNodeInfo tip:tips)tip.performAction(AccessibilityNodeInfo.ACTION_CLICK);if(tips.isEmpty())return;idle();}}
 void controls(boolean show)throws Exception{ui(()->{Class<?> type=target.getClassLoader().loadClass("com.alensw.ui.activity.ao");Method m=type.getDeclaredMethod("a",boolean.class);m.setAccessible(true);m.invoke(activity,show);});idle();}
 void turn(int direction)throws Exception{int before=index();ui(()->call(picture,"quickpicTapTurn",new Class<?>[]{int.class},direction));for(int i=0;i<60&&index()==before;i++)idle();waitLabel();}
 void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b!=null){File dir=new File(target.getExternalCacheDir(),"page28-results");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}}
 void pointer(int action,float x,float y,long down){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{sendPointerSync(e);}finally{e.recycle();}}
 void tap(float x,float y){long t=SystemClock.uptimeMillis();pointer(0,x,y,t);SystemClock.sleep(70);pointer(1,x,y,t);idle();}
 void settings()throws Exception{
  Activity a=startActivitySync(new Intent().setClassName(target.getPackageName(),"com.alensw.ui.activity.MainPreference").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();
  PreferenceActivity pa=(PreferenceActivity)a;
  PreferenceScreen screen=pa.getPreferenceScreen();
  int title=target.getResources().getIdentifier("view","string",target.getPackageName());String viewTitle=target.getString(title);
  ListView menu=pa.getListView();int viewRow=-1;
  for(int i=0;i<menu.getAdapter().getCount();i++){Object item=menu.getAdapter().getItem(i);if(item instanceof Preference&&viewTitle.contentEquals(((Preference)item).getTitle()))viewRow=i;}
  check(viewRow>=0,"View preference screen exists");final int selectedRow=viewRow;
  ui(()->menu.setSelection(selectedRow));idle();
  ui(()->menu.performItemClick(menu.getChildAt(selectedRow-menu.getFirstVisiblePosition()),selectedRow,menu.getAdapter().getItemId(selectedRow)));idle();
  PreferenceScreen view=pa.getPreferenceScreen();
  SwitchPreference option=(SwitchPreference)view.findPreference("show_page_number");check(option!=null&&!option.isChecked(),"real preference defaults off");
  int behavior=target.getResources().getIdentifier("behavior_category","string",target.getPackageName());boolean inBehavior=false;
  for(int i=0;i<view.getPreferenceCount();i++){Preference p=view.getPreference(i);if(p instanceof PreferenceCategory&&p.getTitleRes()==behavior)inBehavior=((PreferenceGroup)p).findPreference("show_page_number")==option;}
  check(inBehavior,"page switch is inside View/Behavior");
  ListView list=pa.getListView();int row=-1;for(int i=0;i<list.getAdapter().getCount();i++){Object item=list.getAdapter().getItem(i);if(item instanceof Preference&&"show_page_number".equals(((Preference)item).getKey()))row=i;}
  check(row>=0,"page switch is a selectable visible-settings row");final int position=row;
  ui(()->list.setSelection(position));idle();
  ui(()->list.performItemClick(list.getChildAt(position-list.getFirstVisiblePosition()),position,list.getAdapter().getItemId(position)));idle();
  check(prefs.getBoolean("show_page_number",false),"actual settings row enables saved flag");shot("settings");
  ui(()->a.finish());idle();bind();waitLabel();check(counter.isShown(),"returning from settings refreshes existing viewer");
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);arguments=b;start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  target=getTargetContext();prefs=PreferenceManager.getDefaultSharedPreferences(target);String mode=arguments==null?"":arguments.getString("stage","");
  if("seed".equals(mode)){prefs.edit().remove("show_page_number").putString("slideshow_interval","7").putInt("tap_page_layout",2).putBoolean("reader_mode",false).putInt("eink_render_mode",0).commit();out.putString("seed_version",target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName);finish(Activity.RESULT_OK,out);return;}
  tablet="tablet".equals(mode);folderMode="folder".equals(mode);helper=target.getClassLoader().loadClass("com.alensw.jni.PageIndicator");
  check(target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName.equals("11.0.28"),"installed exact version28");
  if(!tablet&&!folderMode)check("7".equals(prefs.getString("slideshow_interval",""))&&prefs.getInt("tap_page_layout",0)==2,"27 preferences preserved after update");
  ui(()->prefs.edit().remove("show_page_number").putBoolean("reader_mode",false).putInt("eink_render_mode",0).putInt("tap_page_layout",2).putBoolean("tap_page_reverse",false).putBoolean("single_tap_zoom",false).putString("slideshow_animation","6").putString("screen_orientation",tablet&&"true".equals(arguments.getString("landscape","false"))?"0":"1").commit());
  String[] formats={"07/50","01/01","007/120","120/120","","","2147483647/2147483647"};int[][] values={{6,50},{0,1},{6,120},{119,120},{-1,50},{50,50},{2147483646,2147483647}};
  for(int i=0;i<values.length;i++)check(formats[i].equals(helper.getMethod("format",int.class,int.class).invoke(null,values[i][0],values[i][1])),"format boundary "+i);
  fixtures();open(Uri.fromFile(new File(folder,"page-07.png")));if(folderMode)check(controller.getClass().getName().equals("com.alensw.ui.c.as"),"actual album controller as");String initialController=controller.getClass().getName();check(count()==50,"existing list has50 images without new page scan");
  check(counter.getVisibility()==View.GONE,"default leaves image without counter");
  final Uri[] selected={null};ui(()->selected[0]=uri(6));open(selected[0]);check(index()==6&&count()==50,"current list position7of50");
  if(tablet||folderMode){ui(()->prefs.edit().putBoolean("show_page_number",true).commit());waitLabel();}
  else settings();
  controls(false);waitLabel();check("07/50".contentEquals(counter.getText()),"visible page label07/50");
  check(Math.abs(counter.getTextSize()-target.getResources().getDimension(target.getResources().getIdentifier("detail_text_size","dimen",target.getPackageName())))<=.5,"same small font as image details: actual="+counter.getTextSize()+" expected="+target.getResources().getDimension(target.getResources().getIdentifier("detail_text_size","dimen",target.getPackageName())));
  ui(()->{int[] a=new int[2],b=new int[2];picture.getLocationOnScreen(a);counter.getLocationOnScreen(b);check(Math.abs(b[0]+counter.getWidth()/2f-a[0]-picture.getWidth()/2f)<2,"page centered in content");check(!counter.isClickable()&&!counter.isFocusable(),"page text does not consume gestures");check(b[1]+counter.getHeight()<=a[1]+picture.getHeight(),"page remains within viewer bottom");});shot(tablet?("true".equals(arguments.getString("landscape","false"))?"tablet-wide":"tablet"):folderMode?"folder":"phone");
  if(tablet){boolean wide="true".equals(arguments.getString("landscape","false"));for(int t=0;t<40;t++){bind();if(wide?picture.getWidth()>picture.getHeight():picture.getHeight()>picture.getWidth())break;idle();}waitLabel();dismissTips();shot(wide?"tablet-landscape":"tablet-portrait");check(wide?picture.getWidth()>picture.getHeight():picture.getHeight()>picture.getWidth(),"tablet configured orientation keeps page label w="+picture.getWidth()+" h="+picture.getHeight()+" requested="+activity.getRequestedOrientation()+" config="+activity.getResources().getConfiguration().orientation);}
  else {
   controls(true);waitLabel();View bottom=((View)counter.getParent()).findViewById(target.getResources().getIdentifier("bottom_bar","id",target.getPackageName()));
   ui(()->{int[] a=new int[2],b=new int[2];counter.getLocationOnScreen(a);bottom.getLocationOnScreen(b);check(bottom.isShown()&&a[1]+counter.getHeight()<=b[1],"page above its own visible toolbar");});shot("phone-controls");
   controls(false);int before=index();int[] loc=new int[2];picture.getLocationOnScreen(loc);tap(loc[0]+picture.getWidth()*.9f,loc[1]+picture.getHeight()*.5f);waitLabel();check(index()==(before+1)%50,"actual page-zone tap still works");
   ui(()->prefs.edit().putBoolean("reader_mode",true).commit());final String[] pending={null};final int base=index();ui(()->{String old=counter.getText().toString();call(picture,"quickpicTapTurn",new Class<?>[]{int.class},1);pending[0]=counter.getText().toString();check(index()==base&&old.equals(pending[0]),"Reader pending retains previous image page number");});for(int i=0;i<60&&index()==base;i++)idle();waitLabel();check(index()==(base+1)%50,"Reader commit updates page number");
   ui(()->prefs.edit().putBoolean("reader_mode",false).commit());
   ui(()->hidden(controller,"d",new Class<?>[]{boolean.class},true));idle();check(counter.getVisibility()==View.GONE,"snapshot transition hides old/new mixed number");
   ui(()->hidden(controller,"d",new Class<?>[]{boolean.class},false));idle();waitLabel();check(counter.isShown(),"clear snapshot restores page without polling");
   ui(()->{Class<?> controls=target.getClassLoader().loadClass("com.alensw.jni.ViewerControls");Method apply=controls.getDeclaredMethod("apply",Context.class,Object.class,int.class);apply.setAccessible(true);apply.invoke(null,target,controller,1);});
   before=index();final String toggle=controller.getClass().getName().endsWith(".as")?"I":"L";ui(()->hidden(controller,toggle,new Class<?>[]{}));
   for(int i=0;i<75&&index()==before;i++)idle();waitLabel();check(index()!=before,"real slideshow advances and page label follows");ui(()->hidden(controller,toggle,new Class<?>[]{}));idle();waitLabel();
   ui(()->prefs.edit().putBoolean("show_page_number",false).commit());idle();check(counter.getVisibility()==View.GONE,"switch off immediately hides page");
   ui(()->prefs.edit().putBoolean("show_page_number",true).commit());idle();waitLabel();check(counter.isShown(),"switch on restores current page");
   if(folderMode){
    int old=index();int[] pos=new int[2];picture.getLocationOnScreen(pos);float y=pos[1]+picture.getHeight()*.5f;long down=SystemClock.uptimeMillis();
    pointer(0,pos[0]+picture.getWidth()*.85f,y,down);for(int n=1;n<=8;n++){SystemClock.sleep(24);pointer(2,pos[0]+picture.getWidth()*(.85f-.7f*n/8),y,down);}pointer(1,pos[0]+picture.getWidth()*.15f,y,down);idle();for(int n=0;n<60&&index()==old;n++)idle();waitLabel();check(index()==(old+1)%50,"actual swipe updates album page");
    final Uri removed=uri(-1);check("file".equals(removed.getScheme())&&removed.getPath().startsWith(folder.getAbsolutePath()+"/"),"delete restricted to generated fixture");int total=count();
    ui(()->{MenuItem item=(MenuItem)Proxy.newProxyInstance(MenuItem.class.getClassLoader(),new Class<?>[]{MenuItem.class},(obj,m,args)->{if(m.getName().equals("getItemId"))return target.getResources().getIdentifier("delete","id",target.getPackageName());if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;return null;});call(controller,"a",new Class<?>[]{MenuItem.class},item);});idle();
    AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();List<AccessibilityNodeInfo> buttons=root.findAccessibilityNodeInfosByText("Delete");boolean confirm=false;for(AccessibilityNodeInfo node:buttons){AccessibilityNodeInfo action=node;for(int parents=0;action!=null&&parents<4;parents++,action=action.getParent()){if(action.isClickable()){confirm=action.performAction(AccessibilityNodeInfo.ACTION_CLICK);break;}}if(confirm)break;}check(confirm,"native delete confirmation accepted");idle();
    for(int n=0;n<70&&(count()==total||new File(removed.getPath()).exists());n++)idle();waitLabel();check(!new File(removed.getPath()).exists()&&count()==total-1,"physical deletion updates album total");check(expected().contentEquals(counter.getText()),"post-delete page matches live49-image list");shot("folder-deleted");
   }
   File single=new File(target.getCacheDir(),"page28-single/only.png");single.getParentFile().mkdirs();try(FileInputStream in=new FileInputStream(new File(folder,"page-01.png"));FileOutputStream dest=new FileOutputStream(single)){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)dest.write(bytes,0,n);}
   open(Uri.fromFile(single));waitLabel();check("01/01".contentEquals(counter.getText()),"single external image reports01/01");shot("single");
  }
  JSONObject result=new JSONObject().put("passed",passed).put("checks",checks).put("controller",initialController).put("profile",tablet?("true".equals(arguments.getString("landscape","false"))?"tablet-wide":"tablet"):folderMode?"folder":"phone").put("scope","Installed Android App, synthetic images, no physical e-ink or SD performance claim");
  File dir=new File(target.getExternalCacheDir(),"page28-results");dir.mkdirs();try(FileOutputStream stream=new FileOutputStream(new File(dir,"result-"+(tablet?("true".equals(arguments.getString("landscape","false"))?"tablet-wide":"tablet"):folderMode?"folder":"phone")+".json"))){stream.write(result.toString(2).getBytes("UTF-8"));}
  out.putString("result",result.toString());finish(Activity.RESULT_OK,out);
 }catch(Throwable error){try{shot("failure");}catch(Exception ignored){}out.putString("failure",android.util.Log.getStackTraceString(error));out.putInt("passed",passed);finish(Activity.RESULT_CANCELED,out);}}
}
