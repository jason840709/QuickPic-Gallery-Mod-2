#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Execute manual-open gates extracted from externally supplied JADX sources.

No historical App source is stored in this repository. Generated harnesses belong
in a private --work-dir. Tests execute actual preview / predicate / mode-3 loader
methods; framework and decoder outcomes are doubles. They are not device playback.
"""
from pathlib import Path
import argparse, hashlib, json, re, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[2]

def extract(text, marker):
    start = text.index(marker)
    brace = text.index('{', start)
    depth, pos = 1, brace + 1
    while depth:
        depth += (text[pos] == '{') - (text[pos] == '}')
        pos += 1
    return text[start:pos]

def normalize_loader(text):
    # JADX globally numbered field aliases are presentation names, not control flow.
    text = re.sub(r'\.f[0-9]+b(?=\.d\b)', '.meta', text)
    text = re.sub(r'this\.c\.f[0-9]+a\b', 'this.c.cacheThumbnails', text)
    text = re.sub(r'this\.c\.f[0-9]+b\b', 'this.c.targetWidth', text)
    return re.sub(r'this\.f[0-9]+a\b', 'this.sourceFile', text)

def normalize_viewer(text):
    return re.sub(r'this\.f[0-9]+b\b', 'this.handler', text)

def write(root, path, value):
    p = root / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(value)

def methods(sources):
    as_text = (sources / 'com/alensw/ui/c/as.java').read_text()
    dp_text = (sources / 'com/alensw/ui/c/dp.java').read_text()
    loader = (sources / 'com/alensw/a/at.java').read_text()
    return {
        'as_predicate': extract(as_text, 'private boolean s()'),
        'as_request': extract(as_text, 'private void t()'),
        'dp_predicate': extract(dp_text, 'private boolean v()'),
        'dp_request': extract(dp_text, 'private void w()'),
        'preview': extract(loader, 'private com.alensw.b.h.j a(com.alensw.b.c.f fVar, Uri uri)'),
        'mode3': extract(loader, 'private com.alensw.b.h.j c(com.alensw.b.c.f fVar, Uri uri)'),
    }

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--sources', type=Path, required=True, help='candidate JADX sources directory')
    ap.add_argument('--baseline-sources', type=Path, help='unfixed JADX directory for non-WebP truth-table comparison')
    ap.add_argument('--work-dir', type=Path, required=True, help='private parent for generated extracted methods')
    ap.add_argument('--output-json', type=Path)
    args = ap.parse_args()
    candidate = methods(args.sources)
    baseline = methods(args.baseline_sources or args.sources)
    hashes = {name: hashlib.sha256(body.encode()).hexdigest() for name, body in candidate.items()}
    helper = args.sources / 'com/alensw/jni/AnimationFormats.java'
    if not helper.exists():
        helper = ROOT / 'src/com/alensw/jni/AnimationFormats.java'
    if not helper.exists():
        raise SystemExit('Preflight error: AnimationFormats source unavailable; no behavior verdict.')

    harness = r'''import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import com.alensw.jni.AnimationFormats;
import com.alensw.bean.CommonFile;
import com.alensw.gallery.ui.QuickApp;
import com.alensw.gallery.ui.Task;

public class ManualOpenProbe {
    static final class PictureView {
        final int type; final String mime;
        PictureView(int type, String mime) { this.type=type; this.mime=mime; }
        int getPictureType() { return type; }
        String getMimeType() { return mime; }
    }
    static final class AsView {
        private boolean s;
        private PictureView f;
        private Task M;
        private CommonFile l=new CommonFile();
        private Handler handler=new Handler();
        AS_PREDICATE
        AS_REQUEST
        boolean eligible(boolean defer,int type,String mime) { s=defer; f=new PictureView(type,mime);return s(); }
        int request(boolean defer,int type,String mime) {
            s=defer;f=new PictureView(type,mime);QuickApp.u.reset();t();return QuickApp.u.mode;
        }
        int requestAgain() { QuickApp.u.reset();t();return QuickApp.u.mode; }
    }
    static final class DpView {
        private boolean u;
        private PictureView f;
        private Task N;
        private Uri o=Uri.EMPTY;
        private char n='I';
        private Handler handler=new Handler();
        DP_PREDICATE
        DP_REQUEST
        boolean eligible(boolean defer,int type,String mime) { u=defer;f=new PictureView(type,mime);return v(); }
        int request(boolean defer,int type,String mime) {
            u=defer;f=new PictureView(type,mime);QuickApp.u.reset();w();return QuickApp.u.mode;
        }
        int requestAgain() { QuickApp.u.reset();w();return QuickApp.u.mode; }
    }
    static final class BaselineAsView {
        private boolean s; private PictureView f;
        BASELINE_AS
        boolean eligible(boolean defer,int type,String mime) { s=defer;f=new PictureView(type,mime);return s(); }
    }
    static final class BaselineDpView {
        private boolean u; private PictureView f;
        BASELINE_DP
        boolean eligible(boolean defer,int type,String mime) { u=defer;f=new PictureView(type,mime);return v(); }
    }
    static final class Loader {
        private CommonFile sourceFile=new CommonFile();
        private com.alensw.b.h.a i=new com.alensw.b.h.a();
        private com.alensw.b.h.c j=new com.alensw.b.h.c();
        private java.util.concurrent.atomic.AtomicReference<com.alensw.b.h.j> k=new java.util.concurrent.atomic.AtomicReference<com.alensw.b.h.j>();
        private Owner c=new Owner();
        private Handler d=new Handler();
        private String f=null;
        private boolean g=false;
        private long h=0;
        private void a(com.alensw.b.h.j value) { }
        PREVIEW_METHOD
        MODE3_METHOD
        com.alensw.b.h.j preview(String mime,int sample,int memory,int cachedType) throws Throwable {
            i.outMimeType=mime;i.inSampleSize=sample;i.outWidth=4;i.outHeight=2;c.d=memory;
            c.o.cached=cachedType<0?null:new com.alensw.b.h.j(new Bitmap(),cachedType,Uri.EMPTY,i);
            return a(new com.alensw.b.c.f(false,mime),Uri.EMPTY);
        }
        com.alensw.b.h.j upgrade(String mime,boolean animated) { i.outMimeType=mime;return c(new com.alensw.b.c.f(animated,mime),Uri.EMPTY); }
        static final class Cache {com.alensw.b.h.j cached;Object b(Uri uri){return cached;}void b(int ignored){} }
        static final class Owner {
            Cache o=new Cache();int c=100,d=4096,e=100,f=100,g=100,targetWidth=100;
            boolean cacheThumbnails=false;
            void e(){}void f(){}void g(){}void a(com.alensw.b.h.j value){}void a(Uri uri,String path,long age){}
            boolean d(Uri uri){return false;}
            Bitmap a(Uri uri,String path,long age,com.alensw.b.h.a options,int max){return null;}
            Bitmap a(Bitmap bitmap,int width,int height){return bitmap;}
        }
    }
    private static int checks, failures, predicateChecks, requestChecks, previewChecks, chainChecks, unchangedChecks;
    private static void check(boolean value,String detail){checks++;if(!value){failures++;System.out.println("FAIL "+detail);}}
    private static boolean eligible(String viewer,boolean defer,int type,String mime){return viewer.equals("as")?new AsView().eligible(defer,type,mime):new DpView().eligible(defer,type,mime);}
    private static boolean baseline(String viewer,boolean defer,int type,String mime){return viewer.equals("as")?new BaselineAsView().eligible(defer,type,mime):new BaselineDpView().eligible(defer,type,mime);}
    private static int request(String viewer,boolean defer,int type,String mime){return viewer.equals("as")?new AsView().request(defer,type,mime):new DpView().request(defer,type,mime);}
    public static void main(String[] args) throws Throwable {
        for(String viewer:new String[]{"as","dp"})for(boolean defer:new boolean[]{false,true}){
            for(int type:new int[]{-1,0,1,2,3,4}){
                boolean expected=type==1 || type==2;
                boolean actual=eligible(viewer,defer,type,"image/webp");
                predicateChecks++;check(actual==expected,"WebP predicate viewer="+viewer+" defer="+defer+" type="+type+" expected="+expected+" actual="+actual);
                int mode=request(viewer,defer,type,"image/webp");
                requestChecks++;check(mode==(expected?3:0),"actual upgrade request viewer="+viewer+" defer="+defer+" type="+type+" expectedMode="+(expected?3:0)+" actualMode="+mode);
            }
            for(String mime:new String[]{"image/gif","image/jpeg","image/png","application/octet-stream",null,"","image/WebP"})for(int type:new int[]{-1,0,1,2,4}){
                boolean expected=baseline(viewer,defer,type,mime),actual=eligible(viewer,defer,type,mime);
                unchangedChecks++;check(actual==expected,"non-WebP legacy predicate viewer="+viewer+" defer="+defer+" type="+type+" mime="+mime+" baseline="+expected+" actual="+actual);
            }
        }
        for(String mime:new String[]{"image/webp","image/gif","image/jpeg","image/png","application/octet-stream",null})for(int sample:new int[]{1,2})for(int memory:new int[]{4096,1048576}){
            com.alensw.b.h.j preview=new Loader().preview(mime,sample,memory,-1);
            int expected=sample>1 || "image/gif".equals(mime) || "image/webp".equals(mime)?1:2;
            previewChecks++;check(preview.type==expected,"actual preview classifier mime="+mime+" sample="+sample+" memory="+memory+" expectedType="+expected+" actualType="+preview.type);
        }
        for(String viewer:new String[]{"as","dp"})for(boolean defer:new boolean[]{false,true})for(boolean animated:new boolean[]{false,true})for(int cachedType:new int[]{-1,2})for(int memory:new int[]{4096,1048576}){
            Loader loader=new Loader();com.alensw.b.h.j preview=loader.preview("image/webp",1,memory,cachedType);
            int mode;int again;
            if(viewer.equals("as")){AsView view=new AsView();mode=view.request(defer,preview.type,preview.mime);again=view.requestAgain();}
            else{DpView view=new DpView();mode=view.request(defer,preview.type,preview.mime);again=view.requestAgain();}
            String kind=mode==3?loader.upgrade("image/webp",animated).kind:"preview";
            String expected=animated?"animation":"static";
            chainChecks++;check(mode==3 && expected.equals(kind),"manual chain viewer="+viewer+" defer="+defer+" cachedType="+cachedType+" previewType="+preview.type+" animatedMetadata="+animated+" memory="+memory+" expected="+expected+" actual="+kind+" mode="+mode);
            chainChecks++;check(again==0,"existing upgrade task does not request mode3 twice viewer="+viewer+" cachedType="+cachedType+" firstMode="+mode+" again="+again);
        }
        System.out.println("COUNTS checks="+checks+" failures="+failures+" predicate="+predicateChecks+" request="+requestChecks+" preview="+previewChecks+" chain="+chainChecks+" legacy="+unchangedChecks);
        System.out.println("SCOPE actual extracted methods with platform/decoder doubles; not Android UI or codec playback.");
        if(failures>0)throw new AssertionError("manual-open semantics failures="+failures);
        System.out.println("PASS manual-open semantics");
    }
}'''
    for name, value in {
        'AS_PREDICATE': normalize_viewer(candidate['as_predicate']),
        'AS_REQUEST': normalize_viewer(candidate['as_request']),
        'DP_PREDICATE': normalize_viewer(candidate['dp_predicate']),
        'DP_REQUEST': normalize_viewer(candidate['dp_request']),
        'BASELINE_AS': normalize_viewer(baseline['as_predicate']),
        'BASELINE_DP': normalize_viewer(baseline['dp_predicate']),
        'PREVIEW_METHOD': normalize_loader(candidate['preview']),
        'MODE3_METHOD': normalize_loader(candidate['mode3']),
    }.items():
        harness = harness.replace(name, value)
    fixtures = {
        'android/graphics/Bitmap.java': 'package android.graphics;public final class Bitmap{public void recycle(){}}',
        'android/net/Uri.java': 'package android.net;public final class Uri{public static final Uri EMPTY=new Uri();}',
        'android/os/Handler.java': 'package android.os;public final class Handler{}',
        'com/alensw/jni/JniUtils.java': 'package com.alensw.jni;public final class JniUtils{public static boolean gifIsAnimatedWebP(int h){return false;}}',
        'com/alensw/bean/CommonFile.java': 'package com.alensw.bean;import android.net.Uri;public final class CommonFile{public char h(){return \'I\';}public Uri i(){return Uri.EMPTY;}}',
        'com/alensw/gallery/ui/Task.java': 'package com.alensw.gallery.ui;import android.net.Uri;public final class Task{public boolean b(Uri u){return true;}public void cancel(boolean ignored){}}',
        'com/alensw/gallery/ui/QuickApp.java': 'package com.alensw.gallery.ui;import android.net.Uri;import android.os.Handler;import com.alensw.bean.CommonFile;public final class QuickApp{public static final Dispatcher u=new Dispatcher();public static final class Dispatcher{public int mode;public void reset(){mode=0;}public Task a(CommonFile f,int m,Handler h){mode=m;return new Task();}public Task a(Uri u,char c,int m,Handler h){mode=m;return new Task();}}}',
        'com/alensw/b/c/f.java': 'package com.alensw.b.c;public final class f{public final boolean animated;public final String mime;public f(boolean animated,String mime){this.animated=animated;this.mime=mime;}}',
        'com/alensw/b/h/a.java': 'package com.alensw.b.h;public final class a{public String outMimeType;public boolean mCancel;public int c,inSampleSize,outWidth,outHeight;public boolean d(){return false;}public void a(boolean ignored){}}',
        'com/alensw/b/h/c.java': 'package com.alensw.b.h;public final class c{public boolean b(){return false;}}',
        'com/alensw/b/h/j.java': 'package com.alensw.b.h;import android.graphics.Bitmap;import android.net.Uri;public class j{public static final class Meta{public int d;}public final Meta meta=new Meta();public int type;public String mime,kind="static";public j(Bitmap b,int type,Uri u,a options){this.type=type;mime=options.outMimeType;}public j l(){return this;}}',
        'com/alensw/b/h/d.java': 'package com.alensw.b.h;import android.graphics.Bitmap;import android.net.Uri;import android.os.Handler;import com.alensw.b.c.f;public final class d extends j{private d(String mime){super(new Bitmap(),4,Uri.EMPTY,options(mime));kind="animation";}private static a options(String mime){a o=new a();o.outMimeType=mime;return o;}public static d a(f input,Uri uri,Handler handler){return input.animated?new d(input.mime):null;}}',
        'com/alensw/b/h/n.java': 'package com.alensw.b.h;import android.graphics.Bitmap;import android.net.Uri;import android.os.Handler;import com.alensw.b.c.f;public final class n extends j{private n(){super(new Bitmap(),4,Uri.EMPTY,new a());}public static n a(f input,Uri uri,String mime,Handler handler){return null;}}',
        'com/alensw/b/h/b.java': 'package com.alensw.b.h;import android.graphics.Bitmap;import com.alensw.b.c.f;public final class b{public static Bitmap a(c thumbnail,int limit,a options){return null;}public static Bitmap a(f input,boolean flag,int limit,a options){return new Bitmap();}public static Bitmap a(f input,int limit,a options){return new Bitmap();}public static Bitmap a(f input){return new Bitmap();}}',
    }
    args.work_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='manual-open-', dir=args.work_dir) as td:
        work = Path(td)
        write(work, 'ManualOpenProbe.java', harness)
        for path, body in fixtures.items():
            write(work, path, body)
        write(work, 'com/alensw/jni/AnimationFormats.java', helper.read_text())
        cp = subprocess.run(['javac','--release','8','-Xlint:-options','-d',str(work/'classes'),*map(str,work.rglob('*.java'))],text=True,capture_output=True)
        if cp.returncode:
            print(cp.stdout+cp.stderr,end='')
            raise SystemExit('Compilation error / preflight; no behavior RED verdict.')
        run = subprocess.run(['java','-cp',str(work/'classes'),'ManualOpenProbe'],text=True,capture_output=True)
        text = run.stdout+run.stderr
        print(text,end='')
        match = re.search(r'COUNTS checks=(\d+) failures=(\d+) predicate=(\d+) request=(\d+) preview=(\d+) chain=(\d+) legacy=(\d+)',text)
        result = {
            'compile_exit':cp.returncode,'behavior_exit':run.returncode,
            'result':'PASS' if run.returncode==0 else 'FAIL',
            'scope':'actual externally supplied preview, predicates, mode-3 request and mode-3 loader; platform/decoder doubles, not device playback',
            'source_method_sha256':hashes,
            'helper_sha256':hashlib.sha256(helper.read_bytes()).hexdigest(),
            'checks':dict(zip(['total','failed','predicate','mode3_request','preview_classifier','manual_chain','legacy_preserved'],map(int,match.groups()))) if match else None,
        }
        if args.output_json:
            args.output_json.parent.mkdir(parents=True,exist_ok=True)
            args.output_json.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
        raise SystemExit(run.returncode)

if __name__=='__main__':
    main()
