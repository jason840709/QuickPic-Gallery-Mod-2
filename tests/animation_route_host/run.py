#!/usr/bin/env python3
"""Execute externally supplied decompiled loader methods with Android side-effect doubles.
The historical App methods are never stored in this repository.
"""
from pathlib import Path
import argparse, subprocess, tempfile, json, re
ROOT=Path(__file__).resolve().parents[2]
def method(source,name):
    marker='private com.alensw.b.h.j '+name+'(com.alensw.b.c.f fVar, Uri uri)'
    start=source.index(marker);brace=source.index('{',start);depth=1;pos=brace+1
    while depth:
        depth += (source[pos]=='{')-(source[pos]=='}');pos+=1
    return re.sub(r'\.f[0-9]+b(?=\.d\b)', '.meta', source[start:pos])

def write(root,path,text):
    p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader-java',type=Path,required=True)
    parser.add_argument('--work-dir',type=Path)
    args=parser.parse_args();src=args.loader_java.read_text()
    # Actual loader b/c methods, unmodified. Doubles supply only unrelated API
    # effects and a decoder outcome; production branch order remains intact.
    harness='''import android.graphics.Bitmap;import android.net.Uri;import com.alensw.jni.AnimationFormats;
public class LoaderProbe {
 private com.alensw.b.h.a i=new com.alensw.b.h.a();private boolean g=false;
 private android.os.Handler d=new android.os.Handler();private Owner c=new Owner();
 private void a(com.alensw.b.h.j value){}\nMETHODS
 static class Owner{int f=1,f498b=1,g=1;void f(){}void g(){}Bitmap a(Bitmap b,int x,int y){return b;}}
 private static int checks;
 void check(String mime,boolean animated,boolean cancel,int mode,String expected){
   i.outMimeType=mime;i.mCancel=cancel;com.alensw.b.c.f file=new com.alensw.b.c.f(animated);
   com.alensw.b.h.j out=mode==2?b(file,Uri.EMPTY):c(file,Uri.EMPTY);
   String actual=out==null?"cancelled":out.kind;
   if(!expected.equals(actual))throw new AssertionError("mode="+mode+" mime="+mime+" expected="+expected+" actual="+actual);
   checks++;
 }
 public static void main(String[] a){LoaderProbe t=new LoaderProbe();for(int mode:new int[]{2,3}){
   t.check("image/gif",true,false,mode,"animation");
   t.check("image/jpeg",false,false,mode,"static");
   t.check("image/webp",true,false,mode,"animation");
   t.check("image/webp",false,false,mode,"static");
   t.check("image/webp",true,true,mode,"cancelled");
   t.check("image/png",false,false,mode,"static");
 }System.out.println("PASS loader route checks="+checks);}
}'''.replace('METHODS',method(src,'b')+'\n'+method(src,'c'))
    with tempfile.TemporaryDirectory(prefix='animation-route-',dir=args.work_dir) as td:
        w=Path(td)
        write(w,'LoaderProbe.java',harness)
        fixtures={
        'com/alensw/jni/JniUtils.java':'package com.alensw.jni;public class JniUtils{public static boolean gifIsAnimatedWebP(int handle){return false;}}',
        'android/graphics/Bitmap.java':'package android.graphics;public class Bitmap{public void recycle(){}}',
        'android/net/Uri.java':'package android.net;public class Uri{public static final Uri EMPTY=new Uri();}',
        'android/os/Handler.java':'package android.os;public class Handler{}',
        'com/alensw/b/c/f.java':'package com.alensw.b.c;public class f{public boolean animated;public f(boolean x){animated=x;}}',
        'com/alensw/b/h/a.java':'package com.alensw.b.h;public class a{public String outMimeType;public boolean mCancel;public int c;public boolean d(){return false;}public void a(boolean b){}}',
        'com/alensw/b/h/j.java':'package com.alensw.b.h;import android.graphics.Bitmap;import android.net.Uri;public class j{public static class Meta{public int d;}public Meta meta=new Meta();public String kind="static";public j(){}public j(Bitmap b,int n,Uri u,a opts){}}',
        'com/alensw/b/h/d.java':'package com.alensw.b.h;import com.alensw.b.c.f;import android.net.Uri;import android.os.Handler;public class d extends j{public d(){kind="animation";}public static d a(f file,Uri u,Handler h){return file.animated?new d():null;}}',
        'com/alensw/b/h/n.java':'package com.alensw.b.h;import com.alensw.b.c.f;import android.net.Uri;import android.os.Handler;public class n extends j{public static n a(f file,Uri u,String m,Handler h){return null;}}',
        'com/alensw/b/h/b.java':'package com.alensw.b.h;import android.graphics.Bitmap;import com.alensw.b.c.f;public class b{public static Bitmap a(f file,boolean ignored,int limit,a opts){return new Bitmap();}public static Bitmap a(f file,int limit,a opts){return new Bitmap();}}'}
        for path,body in fixtures.items():write(w,path,body)
        formats=ROOT/'src/com/alensw/jni/AnimationFormats.java'
        if formats.exists():write(w,'com/alensw/jni/AnimationFormats.java',formats.read_text())
        else:write(w,'com/alensw/jni/AnimationFormats.java','package com.alensw.jni;public final class AnimationFormats{}')
        cp=subprocess.run(['javac','--release','8','-d',str(w/'classes'),*map(str,w.rglob('*.java'))],text=True,capture_output=True)
        if cp.returncode:raise SystemExit('Compilation error, not a behavior failure:\n'+cp.stderr)
        run=subprocess.run(['java','-cp',str(w/'classes'),'LoaderProbe'],text=True,capture_output=True)
        print(run.stdout+run.stderr,end='');raise SystemExit(run.returncode)
if __name__=='__main__':main()
