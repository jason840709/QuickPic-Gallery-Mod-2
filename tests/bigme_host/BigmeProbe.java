// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemProperties;
import android.widget.Toast;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Executes production bridge/validator. Android, image rendering and OEM IPC are doubles. */
public final class BigmeProbe {
    static File apk,root,source,valid;
    static byte[] cert,sourceBytes;
    static int passed;
    static Method apply;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Context fresh() throws Exception {
        Context c=new Context();c.pm.provider.applicationInfo.sourceDir=apk.getAbsolutePath();
        c.pm.info.signatures=new Signature[]{new Signature(cert)};
        SystemProperties.model="B1051C";SystemProperties.version="B1051C_R_4.9.0";
        Environment.state=Environment.MEDIA_MOUNTED;Environment.root=new File(root,"case-"+passed+"-"+System.nanoTime());Environment.root.mkdirs();
        StatFs.available=1L<<40;Bitmap.alpha=false;BitmapFactory.fail=false;
        MediaScannerConnection.calls=0;MediaScannerConnection.result="content://media/external/images/media/1";MediaScannerConnection.wrongPath=false;
        ScreensaverImage.calls=0;ScreensaverImage.fixture=valid;ScreensaverImage.corrupt=false;ScreensaverImage.last=null;
        Toast.last=null;AlertDialog.shown=0;AlertDialog.positive=null;AlertDialog.negative=null;
        return c;
    }
    static void noCall(Context c,String name) throws Exception {
        apply.invoke(null,c,source.getAbsolutePath());
        check(c.resolver.client.calls==0,name+" unexpectedly submitted");
        check(c.resolver.acquired==0,name+" unexpectedly started provider");
        check(Arrays.equals(Files.readAllBytes(source.toPath()),sourceBytes),name+" touched source");passed++;
    }
    static byte[] chunk(String type,byte[] data)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);
        byte[] t=type.getBytes("US-ASCII");o.writeInt(data.length);o.write(t);o.write(data);
        CRC32 crc=new CRC32();crc.update(t);crc.update(data);o.writeInt((int)crc.getValue());return b.toByteArray();
    }
    static byte[] join(byte[]...blocks)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] a:blocks)b.write(a);return b.toByteArray();}
    static byte[] header(int width,int height,int color)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeInt(width);d.writeInt(height);d.write(new byte[]{8,(byte)color,0,0,0});return chunk("IHDR",b.toByteArray());}
    static final byte[] MAGIC={(byte)137,80,78,71,13,10,26,10};
    static byte[] idat(int width,int height)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(DeflaterOutputStream d=new DeflaterOutputStream(b)){byte[] row=new byte[1+width*3];for(int y=0;y<height;y++)d.write(row);}return chunk("IDAT",b.toByteArray());}
    static void rejectPng(String name,byte[] data,int width,int height,long max)throws Exception{
        File f=new File(root,"reject-"+name+".png");Files.write(f.toPath(),data);
        try{ScreensaverPng.verify(f,width,height,max);throw new AssertionError("Accepted malformed PNG: "+name);}catch(IOException expected){passed++;}
    }
    public static void main(String[] args)throws Exception{
        apk=new File(args[0]);cert=Files.readAllBytes(new File(args[1]).toPath());root=new File(args[2]);root.mkdirs();
        source=new File(root,"original-source.jpg");sourceBytes="untouched-original-source".getBytes("UTF-8");Files.write(source.toPath(),sourceBytes);
        byte[] hdr=header(1860,2480,2),data=idat(1860,2480),end=chunk("IEND",new byte[0]);
        byte[] png=join(MAGIC,hdr,data,end);valid=new File(root,"valid-rgb-fixture.png");Files.write(valid.toPath(),png);
        ScreensaverPng.verify(valid,1860,2480,24L*1024*1024);passed++;
        apply=BigmeScreensaver.class.getDeclaredMethod("apply",Context.class,String.class);apply.setAccessible(true);
        Context c=fresh();BigmeScreensaver.confirm(c,source.getAbsolutePath());
        check(AlertDialog.shown==1&&AlertDialog.positive!=null,"Confirmation absent");
        check(AlertDialog.negative==null,"Cancel must have no work callback");
        check(ScreensaverImage.calls==0&&MediaScannerConnection.calls==0&&c.resolver.acquired==0,"Confirmation must be metadata only");
        check(Arrays.equals(sourceBytes,Files.readAllBytes(source.toPath())),"Confirmation changed original");passed++;
        // Cancellation dismisses this double without invoking the positive listener.
        check(c.resolver.client.calls==0&&ScreensaverImage.calls==0,"Cancel wrote data");passed++;
        c=fresh();SystemProperties.version="B1051C_R_4.9.1";noCall(c,"unknown firmware");
        c=fresh();SystemProperties.model="other";noCall(c,"unknown model");
        c=fresh();c.pm.provider=null;noCall(c,"missing provider");
        c=fresh();c.pm.provider.packageName="other";noCall(c,"wrong package");
        c=fresh();c.pm.provider.name="other";noCall(c,"wrong provider class");
        c=fresh();c.pm.provider.authority="other";noCall(c,"wrong authority");
        c=fresh();c.pm.provider.applicationInfo.uid=2000;noCall(c,"wrong UID");
        c=fresh();c.pm.provider.applicationInfo.flags=0;noCall(c,"non-system app");
        c=fresh();c.pm.provider.exported=false;noCall(c,"unexported provider");
        c=fresh();c.pm.provider.enabled=false;noCall(c,"disabled provider");
        c=fresh();c.pm.provider.readPermission="permission";noCall(c,"read permission");
        c=fresh();c.pm.provider.writePermission="permission";noCall(c,"write permission");
        c=fresh();c.pm.info.versionCode++;noCall(c,"wrong version");
        c=fresh();c.pm.info.signatures=new Signature[]{new Signature(new byte[]{1})};noCall(c,"wrong cert");
        c=fresh();c.pm.provider.applicationInfo.sourceDir=source.getAbsolutePath();noCall(c,"wrong APK hash");
        c=fresh();Environment.state="unmounted";noCall(c,"unmounted storage");
        c=fresh();ScreensaverImage.corrupt=true;noCall(c,"bad output PNG");
        c=fresh();BitmapFactory.fail=true;noCall(c,"decode failed");
        c=fresh();Bitmap.alpha=true;noCall(c,"alpha output");
        c=fresh();MediaScannerConnection.result=null;noCall(c,"scan failed");
        c=fresh();MediaScannerConnection.wrongPath=true;noCall(c,"scan wrong path");
        c=fresh();MediaScannerConnection.result="file:///fake.png";noCall(c,"scan non-content URI");
        c=fresh();MediaScannerConnection.result="content://other/1";noCall(c,"scan wrong authority");
        c=fresh();StatFs.available=1;noCall(c,"insufficient data storage");
        c=fresh();new File(Environment.root,".nomedia").createNewFile();noCall(c,"hidden parent");
        for(boolean fail:new boolean[]{false,true}){
            c=fresh();c.resolver.client.fail=fail;c.resolver.client.deleteInput=true;apply.invoke(null,c,source.getAbsolutePath());
            check(c.resolver.acquired==1&&c.resolver.client.calls==1,"Must submit once, no retry");
            check("set_screensaver".equals(c.resolver.client.method),"Wrong import method");
            check(!source.getCanonicalPath().equals(c.resolver.client.arg),"Original source passed to OEM");
            check(c.resolver.client.arg.equals(ScreensaverImage.last.getAbsolutePath()),"Must pass only isolated rendered copy");
            check(!ScreensaverImage.last.exists(),"Double failed to simulate OEM removal");
            check(Arrays.equals(sourceBytes,Files.readAllBytes(source.toPath())),"OEM removal touched original");
            check(c.grants==1&&c.revokes==1&&c.resolver.client.releases==1,"IPC resources not released");
            check(fail ? "設定屏保時發生錯誤".equals(Toast.last) : "設定成功".equals(Toast.last),"Unexpected completion notice or missing error");passed++;
        }
        c=fresh();c.resolver.client.deleteInput=false;apply.invoke(null,c,source.getAbsolutePath());
        check(c.resolver.client.calls==1 && ScreensaverImage.last.exists(),"Silent copy failure not simulated");
        check("設定屏保時發生錯誤".equals(Toast.last),"Silent OEM failure falsely reported success");passed++;
        byte[] small=join(MAGIC,header(2,3,2),idat(2,3),end);
        byte[] badMagic=small.clone();badMagic[0]=0;rejectPng("magic",badMagic,2,3,10000);
        rejectPng("truncated",Arrays.copyOf(small,small.length-1),2,3,10000);
        rejectPng("trailing",join(small,new byte[]{0}),2,3,10000);
        rejectPng("size-limit",small,2,3,1);
        rejectPng("wrong-dimensions",small,3,2,10000);
        rejectPng("alpha-type",join(MAGIC,header(2,3,6),idat(2,3),end),2,3,10000);
        rejectPng("duplicate-header",join(MAGIC,header(2,3,2),header(2,3,2),idat(2,3),end),2,3,10000);
        rejectPng("no-idat",join(MAGIC,header(2,3,2),chunk("tEXt",new byte[32]),end),2,3,10000);
        rejectPng("empty-idat",join(MAGIC,header(2,3,2),chunk("IDAT",new byte[0]),end),2,3,10000);
        rejectPng("split-idat",join(MAGIC,header(2,3,2),idat(2,3),chunk("tEXt",new byte[0]),idat(2,3),end),2,3,10000);
        rejectPng("unknown-critical",join(MAGIC,header(2,3,2),chunk("ABCD",new byte[0]),idat(2,3),end),2,3,10000);
        byte[] crc=small.clone();crc[29]^=1;rejectPng("crc",crc,2,3,10000);
        byte[] overflow=small.clone();Arrays.fill(overflow,33,37,(byte)255);rejectPng("huge-chunk",overflow,2,3,10000);
        System.out.println("PASS "+passed+" production bridge/PNG host checks; Android/render/IPC doubles only; no device or OEM writes");
    }
}
