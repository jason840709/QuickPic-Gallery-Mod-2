// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Internal editor: sampled preview only, original pixels decoded on export worker. */
public final class ScreensaverCropActivity extends Activity {
    private String source;
    private int backend,width,height;
    private CropView crop;
    private Bitmap preview;
    private Button confirm;
    private Bundle restored;
    private boolean saving;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);restored=state;
        source=getIntent().getStringExtra("source");backend=getIntent().getIntExtra("backend",-1);
        width=getIntent().getIntExtra("width",0);height=getIntent().getIntExtra("height",0);
        if(source==null||backend<0||backend>2||!ScreensaverImage.validSize(width,height)){finish();return;}
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setBackgroundColor(Color.WHITE);
        TextView title=new TextView(this);title.setText("自訂屏保範圍  ·  "+width+" × "+height);title.setTextColor(Color.BLACK);title.setTextSize(20);title.setPadding(20,18,20,12);body.addView(title);
        TextView help=new TextView(this);help.setText("拖曳方框移動；拖曳四角或雙指縮放");help.setTextColor(Color.DKGRAY);help.setTextSize(15);help.setPadding(20,0,20,12);body.addView(help);
        FrameLayout area=new FrameLayout(this);body.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        TextView loading=new TextView(this);loading.setText("正在載入圖片…");loading.setTextColor(Color.BLACK);loading.setGravity(Gravity.CENTER);area.addView(loading,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout buttons=new LinearLayout(this);buttons.setPadding(12,8,12,12);
        Button cancel=new Button(this);cancel.setText("取消");cancel.setOnClickListener(v->{if(!saving)finish();});buttons.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));
        confirm=new Button(this);confirm.setText("確認");confirm.setEnabled(false);buttons.addView(confirm,new LinearLayout.LayoutParams(0,-2,1));body.addView(buttons);setContentView(body);
        confirm.setOnClickListener(v->{
            if(crop==null||saving)return;saving=true;confirm.setEnabled(false);cancel.setEnabled(false);
            ScreensaverImage.Crop selection=crop.selection();
            if(!ScreensaverFlow.save(this,source,backend,width,height,selection,()->finish())) { saving=false;confirm.setEnabled(true);cancel.setEnabled(true); }
        });
        new Thread(()->{
            try {
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(source,bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new java.io.IOException();
                int orientation=ScreensaverImage.readOrientation(source),sw=bounds.outWidth,sh=bounds.outHeight;
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;options.inScaled=false;
                while(Math.max(sw,sh)/(long)options.inSampleSize>1600)options.inSampleSize*=2;
                Bitmap raw=BitmapFactory.decodeFile(source,options);if(raw==null)throw new java.io.IOException();
                Matrix m=new Matrix();
                switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setValues(new float[]{0,1,0,1,0,0,0,0,1});break;case 6:m.setRotate(90);break;case 7:m.setValues(new float[]{0,-1,0,-1,0,0,0,0,1});break;case 8:m.setRotate(-90);break;}
                final Bitmap image=Bitmap.createBitmap(raw,0,0,raw.getWidth(),raw.getHeight(),m,true);if(raw!=image)raw.recycle();
                final int uprightWidth=orientation>=5?sh:sw,uprightHeight=orientation>=5?sw:sh;
                new Handler(Looper.getMainLooper()).post(()->{
                    if(isFinishing()||isDestroyed()){image.recycle();return;}
                    preview=image;crop=new CropView(image,uprightWidth,uprightHeight);area.removeAllViews();area.addView(crop,new FrameLayout.LayoutParams(-1,-1));confirm.setEnabled(true);
                });
            }catch(Exception|OutOfMemoryError error){new Handler(Looper.getMainLooper()).post(()->{Toast.makeText(this,"無法載入圖片，請改用其他圖片",Toast.LENGTH_LONG).show();finish();});}
        },"QuickPic-CropPreview").start();
    }
    @Override public void onBackPressed(){if(!saving)super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(crop!=null)out.putFloatArray("crop",new float[]{crop.box.left,crop.box.top,crop.box.right,crop.box.bottom});}
    @Override protected void onDestroy(){super.onDestroy();if(preview!=null)preview.recycle();}
    private final class CropView extends View {
        final Bitmap image;final int sw,sh;final float ratio;final RectF box=new RectF(),display=new RectF(),drag=new RectF();
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final ScaleGestureDetector scale;
        float downX,downY;int corner=-1,mode=-1;
        CropView(Bitmap image,int sw,int sh){
            super(ScreensaverCropActivity.this);this.image=image;this.sw=sw;this.sh=sh;ratio=(float)width/height;
            ScreensaverImage.Crop c=ScreensaverImage.centeredCrop(sw,sh,width,height);box.set((float)c.left,(float)c.top,(float)(c.left+c.width),(float)(c.top+c.height));
            if(restored!=null){float[] a=restored.getFloatArray("crop");if(a!=null&&a.length==4){try{ScreensaverImage.checkedCrop(new ScreensaverImage.Crop(a[0],a[1],a[2]-a[0],a[3]-a[1]),sw,sh,width,height);box.set(a[0],a[1],a[2],a[3]);}catch(Exception ignored){}}}
            scale=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){resize(box.width()*d.getScaleFactor(),box.centerX(),box.centerY());mode=-1;return true;}
            });setContentDescription("鎖定比例的屏保裁切框");
        }
        ScreensaverImage.Crop selection(){return new ScreensaverImage.Crop(box.left,box.top,box.width(),box.height());}
        float factor(){return display.width()/sw;}
        void resize(float w,float cx,float cy){
            w=Math.max(Math.min(sw,sh*ratio)*0.03f,Math.min(w,Math.min(sw,sh*ratio)));
            float h=w/ratio;float l=Math.max(0,Math.min(sw-w,cx-w/2)),t=Math.max(0,Math.min(sh-h,cy-h/2));box.set(l,t,l+w,t+h);invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float f=Math.min((getWidth()-24f)/sw,(getHeight()-24f)/sh);if(f<=0)return;
            display.set((getWidth()-sw*f)/2,(getHeight()-sh*f)/2,(getWidth()+sw*f)/2,(getHeight()+sh*f)/2);
            canvas.drawColor(0xffdddddd);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawRect(display,paint);canvas.drawBitmap(image,null,display,paint);
            RectF b=new RectF(display.left+box.left*f,display.top+box.top*f,display.left+box.right*f,display.top+box.bottom*f);
            paint.setColor(0x88000000);canvas.drawRect(display.left,display.top,display.right,b.top,paint);canvas.drawRect(display.left,b.bottom,display.right,display.bottom,paint);canvas.drawRect(display.left,b.top,b.left,b.bottom,paint);canvas.drawRect(b.right,b.top,display.right,b.bottom,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);paint.setColor(Color.WHITE);canvas.drawRect(b,paint);paint.setStrokeWidth(3);paint.setColor(Color.BLACK);canvas.drawRect(b,paint);
            paint.setStyle(Paint.Style.FILL);float handle=7*getResources().getDisplayMetrics().density;
            for(int i=0;i<4;i++){float x=(i==0||i==3)?b.left:b.right,y=i<2?b.top:b.bottom;paint.setColor(Color.WHITE);canvas.drawRect(x-handle-2,y-handle-2,x+handle+2,y+handle+2,paint);paint.setColor(Color.BLACK);canvas.drawRect(x-handle,y-handle,x+handle,y+handle,paint);}
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(display.width()<=0||saving)return true;scale.onTouchEvent(e);
            float x=(e.getX()-display.left)/factor(),y=(e.getY()-display.top)/factor();
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                downX=x;downY=y;drag.set(box);corner=-1;float radius=28*getResources().getDisplayMetrics().density/factor();
                for(int i=0;i<4;i++){float cx=(i==0||i==3)?box.left:box.right,cy=i<2?box.top:box.bottom;if(Math.hypot(x-cx,y-cy)<radius){corner=i;break;}}
                mode=corner>=0?1:box.contains(x,y)?0:-1;return true;
            }
            if(e.getPointerCount()>1||e.getActionMasked()==MotionEvent.ACTION_POINTER_UP){mode=-1;return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                if(mode==0){float l=Math.max(0,Math.min(sw-drag.width(),drag.left+x-downX)),t=Math.max(0,Math.min(sh-drag.height(),drag.top+y-downY));box.set(l,t,l+drag.width(),t+drag.height());}
                else if(mode==1){boolean left=corner==0||corner==3,top=corner<2;float ax=left?drag.right:drag.left,ay=top?drag.bottom:drag.top;
                    float dx=drag.width()+(left?downX-x:x-downX),dy=drag.height()+(top?downY-y:y-downY);
                    float w=(dx+dy/ratio)/(1+1/(ratio*ratio)),max=Math.min(left?ax:sw-ax,(top?ay:sh-ay)*ratio);
                    w=Math.max(Math.min(max,Math.min(sw,sh*ratio)*0.03f),Math.min(w,max));float h=w/ratio;
                    box.set(left?ax-w:ax,top?ay-h:ay,left?ax:ax+w,top?ay:ay+h);
                }invalidate();return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)mode=-1;
            return true;
        }
    }
}
