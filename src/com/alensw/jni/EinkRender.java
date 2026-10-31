// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.MotionEvent;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Optional pre-scaling resampling. No OEM APIs and no source bitmap mutation. */
public final class EinkRender {
    public static final String KEY="eink_render_mode";
    private static volatile SharedPreferences preferences;
    private static volatile int selected;
    private static Context application;
    private static final ThreadLocal<Integer> SCOPE=new ThreadLocal<Integer>();
    private static ArrayList<Entry> cache;
    private static long cacheBytes,processed,hits,fallbacks;
    private static boolean touching;
    private static WeakReference<View> currentView;
    private static final Paint FILTER_PAINT=new Paint(Paint.FILTER_BITMAP_FLAG);
    private EinkRender() { }
    public static int mode(Context context){init(context);return selected;}
    public static int selectedMode(){return selected;}
    public static Context applicationContext(){return application;}
    public static synchronized void init(Context context){
        if(preferences!=null||context==null)return;
        Context app=context.getApplicationContext();if(app==null)app=context;
        application=app;preferences=PreferenceManager.getDefaultSharedPreferences(app);selected=readMode(preferences);
        preferences.registerOnSharedPreferenceChangeListener(LISTENER);
    }
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER=new SharedPreferences.OnSharedPreferenceChangeListener(){
        @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){if(KEY.equals(key)){selected=readMode(p);clear();}}
    };
    private static int readMode(SharedPreferences p){try{int m=p.getInt(KEY,0);return m>=0&&m<=2?m:0;}catch(RuntimeException e){return 0;}}
    public static boolean useTiles(boolean original){return selected==0&&original;}
    /** Existing decoder uses this cap only in optional complete-image modes. */
    public static int decodeLimit(){return selected==0?0:(int)Math.min(6L*1024*1024,Math.max(1024L*1024,Runtime.getRuntime().maxMemory()/32));}
    public static int enter(View view){init(view.getContext());if(selected==0)return -1;if(currentView==null||currentView.get()!=view)currentView=new WeakReference<View>(view);Integer old=SCOPE.get();SCOPE.set(selected);return old==null?0:old;}
    public static int suspend(){if(selected==0)return -1;Integer old=SCOPE.get();SCOPE.set(0);return old==null?0:old;}
    public static void leave(int previous){if(previous<0)return;if(previous==0)SCOPE.remove();else SCOPE.set(previous);}
    public static void touchResult(View view,MotionEvent event,boolean handled){
        if(selected==0||event==null)return;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&!handled){touching=false;return;}
        touch(view,event);
    }
    public static void touch(View view,MotionEvent event){
        if(selected==0||event==null)return;int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_POINTER_DOWN)touching=true;
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;view.postInvalidateDelayed(140);}
    }
    private static int active(){if(selected==0)return 0;Integer m=SCOPE.get();return m==null?0:m;}
    public static void draw(Canvas canvas,Bitmap source,Matrix matrix,Paint paint){
        int mode=active();if(mode==0||source==null||source.isRecycled()){canvas.drawBitmap(source,matrix,paint);return;}
        // Use final screen footprint, including the viewer's canvas transform.
        Matrix total=new Matrix();canvas.getMatrix(total);if(matrix!=null)total.preConcat(matrix);
        float[] v=new float[9];total.getValues(v);
        if(Math.abs(v[6])+Math.abs(v[7])>1e-6||Math.abs(v[8]-1)>1e-6){canvas.drawBitmap(source,matrix,paint);return;}
        double sx=Math.hypot(v[0],v[3]),sy=Math.hypot(v[1],v[4]);
        if(Double.isNaN(sx)||Double.isInfinite(sx)||Double.isNaN(sy)||Double.isInfinite(sy)||sx<=0||sy<=0){canvas.drawBitmap(source,matrix,paint);return;}
        double tolerance=1e-4*(sx+sy);
        boolean aligned=Math.abs(v[1])+Math.abs(v[3])<tolerance||Math.abs(v[0])+Math.abs(v[4])<tolerance;
        if(!aligned){canvas.drawBitmap(source,matrix,paint);return;}
        int width=source.getWidth(),height=source.getHeight();
        int dw=Math.max(1,(int)Math.min(width,Math.ceil(width*sx))),dh=Math.max(1,(int)Math.min(height,Math.ceil(height*sy)));
        // Do not soften full-resolution or magnified images.
        if(dw==width&&dh==height){canvas.drawBitmap(source,matrix,paint);return;}
        Bitmap output=sample(source,dw,dh,mode);
        if(output==null){canvas.drawBitmap(source,matrix,paint);return;}
        Matrix replacement=matrix==null?new Matrix():new Matrix(matrix);
        replacement.preScale((float)width/output.getWidth(),(float)height/output.getHeight());
        Paint effective=paint==null?FILTER_PAINT:paint;boolean old=effective.isFilterBitmap();
        try{effective.setFilterBitmap(true);canvas.drawBitmap(output,replacement,effective);}finally{effective.setFilterBitmap(old);}
    }
    private static synchronized Bitmap sample(final Bitmap source,int width,int height,int mode){
        int generation=source.getGenerationId();Entry recent=null;if(cache==null)cache=new ArrayList<Entry>();
        for(int i=cache.size()-1;i>=0;i--){Entry e=cache.get(i);Bitmap key=e.source.get();
            if(key==null){cacheBytes-=e.bytes;cache.remove(i);continue;}
            if(key==source&&e.generation==generation&&e.mode==mode&&e.output!=null&&recent==null)recent=e;
            if(key==source&&e.generation==generation&&e.width==width&&e.height==height&&e.mode==mode){
                cache.remove(i);cacheBytes-=e.bytes;
                if(e.output!=null||SystemClock.uptimeMillis()-e.created<3000){cache.add(e);cacheBytes+=e.bytes;hits++;return e.output;}
            }
        }
        if(recent!=null){
            long now=SystemClock.uptimeMillis();
            if(recent.requestWidth!=width||recent.requestHeight!=height){recent.requestWidth=width;recent.requestHeight=height;recent.changed=now;}
            if(touching)return recent.output;
            if(now-recent.changed<120){View view=currentView==null?null:currentView.get();if(view!=null)view.postInvalidateDelayed(140);return recent.output;}
        }else if(touching)return null;
        long budget=Math.min(32L*1024*1024,Runtime.getRuntime().maxMemory()/6),bytes=(long)width*height*4;
        Bitmap result=null;
        try{
            if(bytes<=budget){
                result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);result.setDensity(source.getDensity());
                final Bitmap destination=result;
                EinkResample.scale(rows(source),rows(destination),mode==2);
                if(source.getGenerationId()!=generation){result=null;fallbacks++;}else processed++;
            }else fallbacks++;
        }catch(RuntimeException e){result=null;fallbacks++;}catch(OutOfMemoryError e){result=null;cache.clear();cacheBytes=0;fallbacks++;}
        long retained=result==null?0:result.getAllocationByteCount();
        while(!cache.isEmpty()&&(cache.size()>=12||cacheBytes+retained>budget)){cacheBytes-=cache.remove(0).bytes;}
        cache.add(new Entry(source,generation,width,height,mode,result,retained));cacheBytes+=retained;
        return result;
    }
    private static EinkResample.Rows rows(final Bitmap bitmap){return new EinkResample.Rows(){
        public int width(){return bitmap.getWidth();}public int height(){return bitmap.getHeight();}
        public void read(int y,int[] pixels){bitmap.getPixels(pixels,0,bitmap.getWidth(),0,y,bitmap.getWidth(),1);}
        public void write(int y,int[] pixels){bitmap.setPixels(pixels,0,bitmap.getWidth(),0,y,bitmap.getWidth(),1);}
    };}
    private static final class Entry{
        final WeakReference<Bitmap> source;final int generation,width,height,mode;final Bitmap output;final long bytes,created=SystemClock.uptimeMillis();int requestWidth,requestHeight;long changed;
        Entry(Bitmap s,int g,int w,int h,int m,Bitmap o,long b){source=new WeakReference<Bitmap>(s);generation=g;width=w;height=h;mode=m;output=o;bytes=b;requestWidth=w;requestHeight=h;}
    }
    /** Outputs may still be referenced by RenderThread; drop references, never recycle. */
    public static synchronized void clear(){if(cache!=null)cache.clear();cacheBytes=0;touching=false;}
    public static synchronized long[] statistics(){return new long[]{processed,hits,fallbacks,cacheBytes};}
}
