// SPDX-License-Identifier: MIT
package local.quickpic.screensaverprobe;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.nio.file.Files;

/** Uses the installed app's real DEX and Android's real decoder, never the OEM API. */
public final class Probe extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            File root = new File(target.getCacheDir(), "screensaver-probe");
            if (!root.mkdirs() && !root.isDirectory()) throw new Exception("probe directory");
            ClassLoader loader = target.getClassLoader();
            Method prepare = Class.forName("com.alensw.jni.ScreensaverImage", true, loader)
                    .getMethod("prepare", String.class, int.class, int.class, File.class, boolean.class);
            Method verify = Class.forName("com.alensw.jni.ScreensaverPng", true, loader)
                    .getMethod("verify", File.class, int.class, int.class, long.class);
            Bitmap source = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888);
            for (int y=0;y<48;y++) for (int x=0;x<64;x++) source.setPixel(x,y,
                    ((x<16?96:255)<<24) | (x*4)<<16 | (y*5)<<8 | 0x40);
            StringBuilder stream = new StringBuilder();
            Bitmap.CompressFormat[] formats = {Bitmap.CompressFormat.PNG,
                    Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.WEBP};
            String[] names = {"png","jpg","webp"};
            for (int i=0;i<formats.length;i++) {
                File input = new File(root, "source."+names[i]);
                try(FileOutputStream out=new FileOutputStream(input)) {
                    if(!source.compress(formats[i],95,out)) throw new Exception("fixture encode");
                    out.getFD().sync();
                }
                check(prepare,verify,root,input,stream);
            }
            source.recycle();
            File animation = new File(root,"animation.webp");
            try(java.io.InputStream in=getContext().getAssets().open("animation.webp");
                    FileOutputStream out=new FileOutputStream(animation)) {
                byte[] buffer=new byte[16384];int n;
                while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                out.getFD().sync();
            }
            check(prepare,verify,root,animation,stream);
            File invalid=new File(root,"broken.webp");
            try(FileOutputStream out=new FileOutputStream(invalid)){out.write(new byte[]{1,2,3,4});}
            boolean rejected=false;
            try {prepare.invoke(null,invalid.getAbsolutePath(),1860,2480,new File(root,"bad"),false);}
            catch(java.lang.reflect.InvocationTargetException expected){rejected=true;}
            if(!rejected) throw new Exception("corrupt input not rejected");
            stream.append("PASS corrupt WebP rejected; no OEM call attempted\n");
            result.putString("stream",stream.toString());result.putString("status","PASS");
            finish(0,result);
        } catch(Throwable error) {
            java.io.StringWriter out=new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(out));
            result.putString("stream",out.toString());result.putString("status","FAIL");
            finish(1,result);
        }
    }
    private static void check(Method prepare,Method verify,File root,File input,
            StringBuilder stream)throws Exception {
        byte[] before=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input.toPath()));
        File dir=new File(root,"out-"+input.getName());
        File output=(File)prepare.invoke(null,input.getAbsolutePath(),1860,2480,dir,false);
        verify.invoke(null,output,1860,2480,24L*1024*1024);
        Bitmap bitmap=BitmapFactory.decodeFile(output.getAbsolutePath());
        if(bitmap==null || bitmap.getWidth()!=1860 || bitmap.getHeight()!=2480 || bitmap.hasAlpha())
            throw new Exception("actual decoder format/size/alpha");
        bitmap.recycle();
        if(new File(dir,".nomedia").exists())throw new Exception("public export hidden");
        if(!java.util.Arrays.equals(before,MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(input.toPath()))))throw new Exception("source changed");
        stream.append("PASS ").append(input.getName()).append(" -> real RGB PNG 1860x2480, source unchanged\n");
    }
}
