#!/usr/bin/env python3
"""Run the real Bigme bridge/PNG validator with narrow host-only Android doubles.

No APK build, device, provider or OEM data writes occur. The renderer and bitmap
decode are doubles; these checks do not establish real Skia/MediaStore behavior.
"""
from pathlib import Path
import argparse
import os
import hashlib
import runpy
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
JAVA_HOME = Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else Path(__import__('shutil').which('javac')).resolve().parents[1]
STUBS = {
'android/content/DialogInterface.java': '''package android.content; public interface DialogInterface { interface OnClickListener { void onClick(DialogInterface d,int w); } }''',
'android/app/AlertDialog.java': '''package android.app; import android.content.*; public class AlertDialog { public static DialogInterface.OnClickListener positive,negative; public static int shown; public static class Builder { public Builder(Context c){} public Builder setTitle(String s){return this;} public Builder setMessage(String s){return this;} public Builder setNegativeButton(String s,DialogInterface.OnClickListener l){negative=l;return this;} public Builder setPositiveButton(String s,DialogInterface.OnClickListener l){positive=l;return this;} public void show(){shown++;} } }''',
'android/content/Intent.java': '''package android.content; public class Intent { public static final int FLAG_GRANT_READ_URI_PERMISSION=1; }''',
'android/content/Context.java': '''package android.content; import android.content.pm.*; import android.net.Uri; public class Context { public PackageManager pm=new PackageManager(); public ContentResolver resolver=new ContentResolver(); public int grants,revokes; public String grantedPackage; public Context getApplicationContext(){return this;} public String getPackageName(){return "host.quickpic";} public PackageManager getPackageManager(){return pm;} public ContentResolver getContentResolver(){return resolver;} public void grantUriPermission(String p,Uri u,int f){grants++;grantedPackage=p;} public void revokeUriPermission(Uri u,int f){revokes++;} }''',
'android/content/ContentResolver.java': '''package android.content; import android.net.Uri; public class ContentResolver { public int acquired;public Uri acquiredUri; public ContentProviderClient client=new ContentProviderClient(); public ContentProviderClient acquireContentProviderClient(Uri u){acquired++;acquiredUri=u;return client;} }''',
'android/content/ContentProviderClient.java': '''package android.content; import java.io.*; public class ContentProviderClient { public int calls,releases; public String method,arg; public boolean fail,deleteInput;public Object result;public Runnable afterCall; public Object call(String m,String a,Object e) throws IOException {calls++;method=m;arg=a;if(deleteInput)new File(a).delete();if(fail)throw new IOException("simulated OEM error after commit boundary");if(afterCall!=null)afterCall.run();return result;} public void release(){releases++;} }''',
'android/content/pm/ApplicationInfo.java': '''package android.content.pm; public class ApplicationInfo { public static final int FLAG_SYSTEM=1; public boolean enabled=true; public int uid=1000,flags=1; public String sourceDir; }''',
'android/content/pm/ProviderInfo.java': '''package android.content.pm; public class ProviderInfo { public boolean enabled=true,exported=true; public String packageName="com.xrz.standby",name="com.xrz.settings.provider.LogoProvider",authority="com.xrz.LogoProvider",readPermission,writePermission; public ApplicationInfo applicationInfo=new ApplicationInfo(); }''',
'android/content/pm/Signature.java': '''package android.content.pm; public class Signature { final byte[] b; public Signature(byte[] b){this.b=b;} public byte[] toByteArray(){return b;} }''',
'android/content/pm/PackageInfo.java': '''package android.content.pm; public class PackageInfo { public int versionCode=11115; public Signature[] signatures; }''',
'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager { public static final int GET_SIGNATURES=64; public String resolvedAuthority,requestedPackage;public int metadataReads;public ProviderInfo provider=new ProviderInfo(); public PackageInfo info=new PackageInfo(); public ProviderInfo resolveContentProvider(String a,int f){resolvedAuthority=a;metadataReads++;return provider;} public PackageInfo getPackageInfo(String p,int f){requestedPackage=p;metadataReads++;return info;} }''',
'android/net/Uri.java': '''package android.net; public class Uri { final java.net.URI uri; private Uri(String s){uri=java.net.URI.create(s);} public static Uri parse(String s){return new Uri(s);} public String getScheme(){return uri.getScheme();} public String getAuthority(){return uri.getAuthority();} }''',
'android/graphics/Bitmap.java': '''package android.graphics; public class Bitmap { public static boolean alpha; public enum Config {ARGB_8888} public int getWidth(){return 1860;} public int getHeight(){return 2480;} public boolean hasAlpha(){return alpha;} public void recycle(){} }''',
'android/graphics/BitmapFactory.java': '''package android.graphics; public class BitmapFactory { public static boolean fail; public static class Options {public boolean inScaled;public Bitmap.Config inPreferredConfig;} public static Bitmap decodeFile(String p,Options o){return fail?null:new Bitmap();} }''',
'android/media/MediaScannerConnection.java': '''package android.media; import android.content.*; import android.net.*; public class MediaScannerConnection { public static int calls;public static String result="content://media/external/images/media/1"; public static boolean wrongPath;public interface OnScanCompletedListener{void onScanCompleted(String p,Uri u);} public static void scanFile(Context c,String[] p,String[] m,OnScanCompletedListener l){calls++;l.onScanCompleted(wrongPath?"wrong-path":p[0],result==null?null:Uri.parse(result));} }''',
'android/os/Environment.java': '''package android.os; import java.io.File; public class Environment { public static final String MEDIA_MOUNTED="mounted",DIRECTORY_PICTURES="Pictures"; public static String state=MEDIA_MOUNTED; public static File root; public static String getExternalStorageState(){return state;} public static File getExternalStoragePublicDirectory(String s){return new File(root,s);} public static File getDataDirectory(){return root;} }''',
'android/os/SystemProperties.java': '''package android.os; public class SystemProperties { public static String model="B1051C",version="B1051C_R_4.9.0"; public static String get(String k,String d){return k.equals("ro.build.versionCode")?model:version;} }''',
'android/os/StatFs.java': '''package android.os; public class StatFs {public static long available=1L<<40;public StatFs(String p){}public long getAvailableBytes(){return available;} }''',
'android/os/Looper.java': '''package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}''',
'android/os/Handler.java': '''package android.os;public class Handler{public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}''',
'android/widget/Toast.java': '''package android.widget;import android.content.*;public class Toast{public static final int LENGTH_LONG=1;public static String last;public static Toast makeText(Context c,String s,int n){last=s;return new Toast();}public void show(){}public void cancel(){}}''',
'com/alensw/jni/ScreensaverImage.java': '''package com.alensw.jni;import java.io.*;import java.nio.file.*;public class ScreensaverImage{public static File fixture,last;public static String source;public static int calls;public static boolean corrupt;public static File prepare(String s,int w,int h,File d,boolean exclude)throws IOException{calls++;source=s;if(exclude)throw new AssertionError("OEM export must stay media visible");d.mkdirs();last=new File(d,"host-disposable-"+calls+".png");Files.copy(fixture.toPath(),last.toPath());if(corrupt)try(RandomAccessFile f=new RandomAccessFile(last,"rw")){f.seek(29);f.write(42);}return last;}}''',
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--oem-apk', type=Path, required=True)
    parser.add_argument('--legacy-oem-apk', type=Path, required=True)
    parser.add_argument('--baseline-root', type=Path, default=ROOT)
    args = parser.parse_args()
    expected = 'd1cb4a72d337e55e3f523a6760d9017db11dd7ae58adb301e5f15172549ba215'
    if hashlib.sha256(args.oem_apk.read_bytes()).hexdigest() != expected:
        parser.error('OEM fixture is not the audited 4.9.0 StandbyApp.apk')
    if hashlib.sha256(args.legacy_oem_apk.read_bytes()).hexdigest() != 'd3eae14d99e86861c082c1d6e1b1d07de16207fb383176dd279b1fb721ada24d':
        parser.error('Legacy fixture is not the audited 4.8.8 XrzSettings.apk')
    baseline = args.baseline_root
    with tempfile.TemporaryDirectory(prefix='quickpic-bigme-host-') as tmp:
        temp = Path(tmp)
        for name, content in STUBS.items():
            path=temp/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
        with zipfile.ZipFile(args.oem_apk) as z:
            pkcs7=z.read('META-INF/CERT.RSA')
        pem=subprocess.run(['openssl','pkcs7','-inform','DER','-print_certs'],input=pkcs7,capture_output=True,check=True).stdout
        der=subprocess.run(['openssl','x509','-outform','DER'],input=pem,capture_output=True,check=True).stdout
        assert hashlib.sha256(der).hexdigest()=='ec4bb6c0f4c281829f7ec09a20d3350fd0d9486fa896b2aa8f27e01ebc85bdf4'
        cert=temp/'public-oem-cert.der';cert.write_bytes(der)
        sources=list((temp/'src').rglob('*.java'))+[
            ROOT/'src/com/alensw/jni/BigmeScreensaver.java',
            baseline/'src/com/alensw/jni/ScreensaverPng.java',
            Path(__file__).with_name('BigmeProbe.java'),
        ]
        subprocess.run([str(JAVA_HOME/'bin/javac'),'-encoding','UTF-8','-d',str(temp/'classes')]+[str(p) for p in sources],check=True)
        subprocess.run([str(JAVA_HOME/'bin/java'),'-cp',str(temp/'classes'),'com.alensw.jni.BigmeProbe',str(args.oem_apk),str(cert),str(temp/'data'),str(args.legacy_oem_apk)],check=True)
        # Exercise the real renderer overload separately with the existing Java2D
        # Android doubles. Model setHasAlpha(false) as opaque RGB PNG compression.
        renderer_stubs=runpy.run_path(str(baseline/'tests/screensaver_host/renderer/run.py'))['STUBS']
        renderer_stubs['android/graphics/Bitmap.java']=renderer_stubs['android/graphics/Bitmap.java'].replace(
            'public void setHasAlpha(boolean v){}',
            'public void setHasAlpha(boolean v){if(!v){BufferedImage rgb=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=rgb.createGraphics();g.drawImage(image,0,0,null);g.dispose();image=rgb;}}')
        renderer_sources=[]
        for name,content in renderer_stubs.items():
            path=temp/'renderer-src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content);renderer_sources.append(path)
        renderer_sources += [baseline/'src/com/alensw/jni/ScreensaverImage.java',baseline/'src/com/alensw/jni/ScreensaverPng.java',Path(__file__).with_name('SourceIsolationProbe.java')]
        subprocess.run([str(JAVA_HOME/'bin/javac'),'--release','8','-encoding','UTF-8','-d',str(temp/'renderer-classes')]+[str(p) for p in renderer_sources],check=True)
        subprocess.run([str(JAVA_HOME/'bin/java'),'-Djava.awt.headless=true','-cp',str(temp/'renderer-classes'),'SourceIsolationProbe',str(temp/'renderer-data')],check=True)

if __name__=='__main__':main()
