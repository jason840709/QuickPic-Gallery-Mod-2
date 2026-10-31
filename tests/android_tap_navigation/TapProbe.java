package local.quickpic.tapprobe;
import android.app.*;import android.content.*;import android.os.*;import android.graphics.*;import android.net.Uri;import android.preference.PreferenceManager;import android.view.*;import java.io.*;import java.lang.reflect.*;import org.json.*;
public final class TapProbe extends Instrumentation {
 Bundle args;Context target;Activity activity;View picture;Object host;ClassLoader loader;JSONArray checks=new JSONArray();int failures;int beforeMenuIndex;
 @Override public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 Object invoke(Object o,String name,Class<?>[] types,Object... values)throws Exception {return o.getClass().getMethod(name,types).invoke(o,values);}
 Object call(Object o,String name)throws Exception{return invoke(o,name,new Class<?>[0]);}
 @Override public void onStart(){Bundle result=new Bundle();try{
  target=getTargetContext();loader=target.getClassLoader();String mode=args.getString("mode","inspect");
  if(mode.equals("state")||mode.equals("raw-state")){
   JSONObject j=new JSONObject();j.put("version",target.getPackageManager().getPackageInfo(target.getPackageName(),0).versionName);j.put("hasKey",PreferenceManager.getDefaultSharedPreferences(target).contains("tap_page_layout"));
   if(mode.equals("state"))j.put("mode",loader.loadClass("com.alensw.jni.TapPagePreference").getMethod("mode",Context.class).invoke(null,target));result.putString("stream",j.toString()+"\n");finish(0,result);return;
  }
  prepareFixtures();
  StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
  Intent start=new Intent(Intent.ACTION_VIEW);start.setClassName(target,target.getPackageName()+".GalleryActivity");start.setDataAndType(Uri.fromFile(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"QuickPicTapTest/page-03.png")),"image/png");start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  activity=startActivitySync(start);waitForIdleSync();SystemClock.sleep(1400);dismissTutorial();
  refresh();JSONObject info=state();info.put("phase","initial");checks.put(info);
  if(mode.equals("inspect")){result.putString("stream",checks.toString()+"\n");finish(0,result);return;}
  int layout=Integer.parseInt(args.getString("layout","1"));if(!mode.equals("baseline"))setMode(layout);settle();
  if(mode.equals("diagnostic")){
   hideBars();checks.put(state().put("phase","before confirmed call"));int before=index();final boolean[] used={false};runOnMainSync(()->{try{used[0]=(Boolean)loader.loadClass("com.alensw.jni.TapPageNavigation").getMethod("onConfirmedTap",View.class,loader.loadClass("com.alensw.jni.TapPageNavigation$Host"),float.class,float.class).invoke(null,picture,picture,picture.getWidth()*.88f,picture.getHeight()*.82f);}catch(Exception e){throw new RuntimeException(e);}});settle();check(index()==(before+1)%count(),"direct actual helper turns page; consumed="+used[0]);
   hideBars();before=index();tap(.88f,.82f);settle();check(index()==(before+1)%count(),"real confirmed tap after exact owner hide");
  }else if(mode.equals("baseline")){
   for(int i=0;i<4;i++){int before=index();swipe(.83f,.82f,.17f,.82f,320);settle();JSONObject j=state();j.put("before",before);j.put("gesture","left swipe ending lower-left");checks.put(j);}
  }else if(mode.equals("gestures")){
   // Baseline-free behavioral checks on a loaded real picture and its real controller.
   setMode(0);int before=index();tap(.12f,.80f);settle();check(index()==before,"disabled lower-left tap does not change page");
   before=index();swipe(.83f,.82f,.17f,.82f,320);settle();int forward=index();check(forward==(before+1)%count(),"disabled left swipe next exactly once");
   before=index();swipe(.17f,.82f,.83f,.82f,320);settle();check(index()==(before+count()-1)%count(),"disabled right swipe previous exactly once");
   setMode(1);hideBars();before=index();tap(.88f,.82f);settle();check(index()==(before+1)%count(),"bottom-right confirmed tap next exactly once");
   hideBars();before=index();tap(.12f,.82f);settle();check(index()==(before+count()-1)%count(),"bottom-left confirmed tap previous exactly once");
   hideBars();before=index();tap(.5f,.3f);settle();check(index()==before,"center tap keeps page");
   hideBars();before=index();swipe(.83f,.82f,.17f,.82f,320);settle();check(index()==(before+1)%count(),"enabled left swipe next exactly once, no release-zone reversal");
   hideBars();before=index();tap(.88f,.82f);SystemClock.sleep(80);tap(.88f,.82f);settle();check(index()==before,"double-tap does not turn page");
   hideBars();before=index();tinyMultiTouch(.88f,.82f);settle();check(index()==before,"close two-pointer gesture does not turn page");
   hideBars();before=index();cancelTouch(.88f,.82f);settle();check(index()==before,"cancelled gesture does not turn page");
   hideBars();before=index();tap(.88f,.82f);settle();check(index()==(before+1)%count(),"ordinary tap resumes after cancel/multi gesture");
   // Opened chrome must receive these taps through Activity dispatch, not a test-only direct view call.
   checks.put(state().put("phase","after center menu"));
  }else if(mode.equals("menus")){
   setMode(3);showBars();beforeMenuIndex=index();View more=findDesc(activity.getWindow().getDecorView(),"More options");if(more==null)throw new Exception("Missing overflow button");tapView(more);settle();check(index()==beforeMenuIndex,"overflow button in top hot zone opens without turning page");
   android.view.accessibility.AccessibilityNodeInfo menu=getUiAutomation().getRootInActiveWindow();java.util.List<android.view.accessibility.AccessibilityNodeInfo> entries=menu.findAccessibilityNodeInfosByText("Set as");if(entries.isEmpty())throw new Exception("Overflow did not show Set as");android.graphics.Rect b=new android.graphics.Rect();entries.get(0).getBoundsInScreen(b);tapScreen(b.exactCenterX(),b.exactCenterY());settle();check(index()==beforeMenuIndex,"overflow menu item click opens Set as without turning page");
   android.view.accessibility.AccessibilityNodeInfo dialog=getUiAutomation().getRootInActiveWindow();check(dialog!=null&&!dialog.findAccessibilityNodeInfosByText("Set as").isEmpty(),"native Set as dialog visible with tap navigation enabled");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();check(index()==beforeMenuIndex,"closing dialog keeps the page");
  }else if(mode.equals("hold")){
   setMode(layout);hideBars();if(!"true".equals(args.getString("noTap")))tap(.5f,.3f);settle();checks.put(state().put("phase","menu-open"));SystemClock.sleep(40000);checks.put(state().put("phase","after menu interaction"));
  }
  JSONObject summary=new JSONObject();summary.put("checks",checks);summary.put("failures",failures);result.putString("stream",summary.toString()+"\n");result.putString("status",failures==0?"PASS":"FAIL");finish(failures==0?0:1,result);
 }catch(Throwable error){StringWriter w=new StringWriter();error.printStackTrace(new PrintWriter(w));result.putString("stream",w.toString());result.putString("status","FAIL");finish(1,result);}}
 void dismissTutorial(){for(int i=0;i<12;i++){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null){for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("Got it")){n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);SystemClock.sleep(200);return;}}SystemClock.sleep(100);}}
 void prepareFixtures()throws Exception{
  File d=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"QuickPicTapTest");d.mkdirs();
  for(int i=1;i<=5;i++){File f=new File(d,"page-0"+i+".png");if(f.exists())continue;Bitmap b=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);b.eraseColor(new int[]{0,0xffcc4444,0xff44aa44,0xff4477cc,0xffbbbb44,0xff994499}[i]);Canvas c=new Canvas(b);Paint p=new Paint();p.setColor(0xff000000);p.setTextSize(150);c.drawText(""+i,110,360,p);try(FileOutputStream o=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,o);}b.recycle();}
 }
 View find(View v){if(v.getClass().getName().equals("com.alensw.ui.view.PictureView"))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=find(g.getChildAt(i));if(f!=null)return f;}}return null;}
 void refresh()throws Exception{final Throwable[] e={null};runOnMainSync(()->{try{host=call(activity,"c");picture=find(activity.getWindow().getDecorView());}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new Exception(e[0]);if(picture==null)throw new Exception("No PictureView; controller="+(host==null?"null":host.getClass().getName()));}
 int index()throws Exception{refresh();return ((Integer)call(host,"readerIndex")).intValue();}int count()throws Exception{return((Integer)call(host,"readerCount")).intValue();}
 JSONObject state()throws Exception{refresh();JSONObject j=new JSONObject();j.put("controller",host.getClass().getName());j.put("index",call(host,"readerIndex"));j.put("count",call(host,"readerCount"));j.put("uri",String.valueOf(invoke(host,"readerUri",new Class<?>[]{int.class},-1)));j.put("viewWidth",picture.getWidth());j.put("viewHeight",picture.getHeight());j.put("hasFocus",picture.hasWindowFocus());j.put("shown",picture.isShown());j.put("context",picture.getContext().getClass().getName());int[] loc=new int[2];picture.getLocationInWindow(loc);j.put("windowX",loc[0]);j.put("windowY",loc[1]);try{j.put("modeValue",loader.loadClass("com.alensw.jni.TapPagePreference").getMethod("mode",Context.class).invoke(null,picture.getContext()));j.put("ready",call(picture,"quickpicTapReady"));j.put("controlsHit",invoke(picture,"quickpicTapControlsHit",new Class<?>[]{float.class,float.class},picture.getWidth()*.88f,picture.getHeight()*.82f));}catch(Exception ignored){}return j;}
 void check(boolean okay,String label)throws Exception{JSONObject j=state();j.put("check",label);j.put("pass",okay);checks.put(j);if(!okay)failures++;android.util.Log.i("TapProbe",label+"="+okay+" index="+j.get("index"));}
 void setMode(int value){PreferenceManager.getDefaultSharedPreferences(target).edit().putInt("tap_page_layout",value).putBoolean("single_tap_zoom",false).commit();}
 void settle(){waitForIdleSync();SystemClock.sleep(850);}
 void showBars()throws Exception{runOnMainSync(()->{try{loader.loadClass("com.alensw.ui.activity.ao").getDeclaredMethod("a",boolean.class).invoke(activity,true);}catch(Exception e){throw new RuntimeException(e);}});settle();}
 View findDesc(View v,String d){if(d.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View n=findDesc(g.getChildAt(i),d);if(n!=null)return n;}}return null;}
 void tapView(View v){int[]p=new int[2];v.getLocationOnScreen(p);tapScreen(p[0]+v.getWidth()/2f,p[1]+v.getHeight()/2f);}
 void tapScreen(float x,float y){long d=SystemClock.uptimeMillis();send(d,d,0,x,y);SystemClock.sleep(60);send(d,SystemClock.uptimeMillis(),1,x,y);}
 void hideBars()throws Exception{runOnMainSync(()->{try{Method method=loader.loadClass("com.alensw.ui.activity.ao").getDeclaredMethod("a",boolean.class);method.invoke(activity,false);}catch(Exception ignored){}});settle();}
 float[] point(float x,float y){int[] a=new int[2];picture.getLocationOnScreen(a);return new float[]{a[0]+x*picture.getWidth(),a[1]+y*picture.getHeight()};}
 void send(long down,long when,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,when,action,x,y,0);try{sendPointerSync(e);}finally{e.recycle();}}
 void tap(float x,float y){float[]p=point(x,y);long d=SystemClock.uptimeMillis();send(d,d,0,p[0],p[1]);SystemClock.sleep(60);send(d,SystemClock.uptimeMillis(),1,p[0],p[1]);}
 void cancelTouch(float x,float y){float[]p=point(x,y);long d=SystemClock.uptimeMillis();send(d,d,0,p[0],p[1]);SystemClock.sleep(60);send(d,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,p[0],p[1]);}
 void tinyMultiTouch(float x,float y){float[]p=point(x,y);long d=SystemClock.uptimeMillis();send(d,d,0,p[0],p[1]);SystemClock.sleep(40);multi(d,MotionEvent.ACTION_POINTER_DOWN|(1<<8),p[0],p[1]);SystemClock.sleep(70);multi(d,MotionEvent.ACTION_POINTER_UP|(1<<8),p[0],p[1]);send(d,SystemClock.uptimeMillis(),1,p[0],p[1]);}
 void multi(long d,int action,float x,float y){MotionEvent.PointerProperties[] props={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};for(int i=0;i<2;i++){props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i].x=x+i*2;coords[i].y=y+i*2;coords[i].pressure=1;coords[i].size=.1f;}MotionEvent e=MotionEvent.obtain(d,SystemClock.uptimeMillis(),action,2,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);try{sendPointerSync(e);}finally{e.recycle();}}
 void swipe(float x1,float y1,float x2,float y2,int ms){float[]a=point(x1,y1),b=point(x2,y2);long d=SystemClock.uptimeMillis();send(d,d,0,a[0],a[1]);for(int i=1;i<=12;i++){SystemClock.sleep(ms/12);float f=i/12f;send(d,SystemClock.uptimeMillis(),2,a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f);}send(d,SystemClock.uptimeMillis(),1,b[0],b[1]);}
}
