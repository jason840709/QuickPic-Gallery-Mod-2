// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** One crop/export path for all three backends. Never shares the only saved copy with Bigme. */
public final class ScreensaverFlow {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static Toast progress;
    private ScreensaverFlow() {}
    public static void choose(final Context context, final String path, final int backend) {
        final int[] size=ScreensaverPreference.dimensions(context);
        new AlertDialog.Builder(context).setTitle("屏保裁切範圍")
            .setItems(new String[]{"預設範圍（置中裁切）", "自訂範圍"}, (d,which)->{
                if(which==0) save(context,path,backend,size[0],size[1],null,null);
                else {
                    Intent intent=new Intent(context,ScreensaverCropActivity.class)
                        .putExtra("source",path).putExtra("backend",backend)
                        .putExtra("width",size[0]).putExtra("height",size[1]);
                    if(!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }).setNegativeButton("取消",null).show();
    }
    public static boolean save(final Context context, final String path, final int backend,
            final int width, final int height, final ScreensaverImage.Crop crop, final Runnable done) {
        if(backend<0 || backend>2 || !ScreensaverImage.validSize(width,height)) return false;
        if(!BUSY.compareAndSet(false,true)) { toast(context,"正在處理圖片，請稍候"); return false; }
        final Context app=context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(()->{progress=Toast.makeText(app,"正在處理圖片…",Toast.LENGTH_SHORT);progress.show();});
        Thread worker = new Thread(()->{
            try {
                File directory=new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES),"QuickPic-Screensaver");
                // Old generic exports were hidden. Make this owned album visible again.
                File hidden=new File(directory,".nomedia");
                if(hidden.exists() && !hidden.delete()) throw new java.io.IOException("無法顯示屏保資料夾");
                final File png=ScreensaverImage.prepare(path,width,height,directory,false,crop);
                MediaScannerConnection.scanFile(app,new String[]{png.getAbsolutePath()},new String[]{"image/png"},null);
                new Handler(Looper.getMainLooper()).post(()->{
                    if(progress!=null){progress.cancel();progress=null;}
                    if(backend==1) BigmeScreensaver.applySaved(app,png,width,height);
                    else if(backend==2) BooxScreensaver.applySaved(app,png,width,height);
                    else ScreensaverOpener.open(context,png);
                });
            } catch(Exception error) { toast(app,"無法製作屏保圖片，請檢查圖片與儲存空間"); }
              catch(OutOfMemoryError error) { toast(app,"記憶體不足，請調小屏保圖片尺寸"); }
            finally { new Handler(Looper.getMainLooper()).post(()->{BUSY.set(false);if(progress!=null){progress.cancel();progress=null;}if(done!=null)done.run();}); }
        },"QuickPic-ScreensaverCrop");
        try { worker.start();return true; }
        catch(RuntimeException|OutOfMemoryError error) { BUSY.set(false);toast(app,"無法啟動圖片處理");return false; }
    }
    private static void toast(Context c,String text) {
        new Handler(Looper.getMainLooper()).post(()->Toast.makeText(c.getApplicationContext(),text,Toast.LENGTH_SHORT).show());
    }
}
