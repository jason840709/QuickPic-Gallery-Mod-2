#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Single-file SAF result, exact original smali hooks, API and assembled DEX checks."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import patch_file_delete

PROBE = r"""package com.alensw.jni;
import java.io.*;import java.nio.file.*;import android.content.*;import android.os.Build;
public class FileDeleteProbe {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static class Target extends File {
  int attempts;boolean directory,rawSuccess,alias,denyRaw,exists=true;
  Target(String p){super(p);}public boolean isDirectory(){return directory;}
  public boolean delete(){attempts++;if(denyRaw)throw new SecurityException("raw denied");return rawSuccess;}
  public File getCanonicalFile()throws IOException{return alias?new File("/storage/ABCD-1234/elsewhere/keep.jpg"):super.getCanonicalFile();}
  public boolean exists(){return exists;}
 }
 static Context grant(String uri,boolean write){Context c=new Context();c.resolver.grants.add(new UriPermission(uri,write));return c;}
 static String tree="content://com.android.externalstorage.documents/tree/ABCD-1234%3Aalbum";
 static Target target(){return new Target("/storage/ABCD-1234/album/a.jpg");}
 public static void main(String[] args)throws Exception {
  Context c=grant(tree,true);Target f=target();
  check(FileDelete.tryDelete(c,f)==1,"authorized failed raw uses SAF flag one");
  check(f.attempts==1&&c.resolver.deletes==1&&c.resolver.batches==0,"raw once, document once, no MediaStore");
  check(c.resolver.last.toString().contains("ABCD-1234%3Aalbum%2Fa.jpg"),"exact selected document");
  c=grant(tree,true);f=target();f.rawSuccess=true;
  check(FileDelete.tryDelete(c,f)==0&&f.attempts==1&&c.resolver.deletes==0,"raw success retains metadata cleanup");
  f=target();f.exists=false;
  check(FileDelete.tryDelete(c,f)==0&&c.resolver.deletes==0,"already absent original semantics");
  c=grant(tree,true);c.resolver.result=false;f=target();
  check(FileDelete.tryDelete(c,f)==-1&&c.resolver.deletes==1,"provider false not success");
  c.resolver.deny=true;f=target();check(FileDelete.tryDelete(c,f)==-1,"provider exception falls back");
  c=grant(tree,true);f=target();FastMediaDelete.result=0;int fastCalls=FastMediaDelete.calls;check(FileDelete.tryDelete(c,f)==0&&c.resolver.deletes==0&&FastMediaDelete.calls==fastCalls+1,"fast confirmed result avoids SAF and keeps metadata after failed raw");FastMediaDelete.result=-1;
  c=grant(tree,true);f=target();f.rawSuccess=true;fastCalls=FastMediaDelete.calls;check(FileDelete.tryDelete(c,f)==0&&FastMediaDelete.calls==fastCalls,"new Android raw success still precedes fast lookup");
  c=grant(tree,true);f=target();f.alias=true;check(FileDelete.tryDelete(c,f)==-1&&c.resolver.deletes==0,"failed unlink never deletes alias target through SAF");
  f=target();f.denyRaw=true;check(FileDelete.tryDelete(c,f)==-1&&c.resolver.deletes==0,"raw security exception uses original fallback");
  c=new Context();f=target();check(FileDelete.tryDelete(c,f)==-1&&c.resolver.deletes==0,"no grant original fallback");
  c=grant(tree,false);check(FileDelete.tryDelete(c,target())==-1&&c.resolver.deletes==0,"read-only grant rejected");
  c=grant("content://evil.documents/tree/ABCD-1234%3Aalbum",true);check(FileDelete.tryDelete(c,target())==-1&&c.resolver.deletes==0,"other provider rejected");
  c=grant("content://com.android.externalstorage.documents/tree/FFFF-FFFF%3A",true);check(FileDelete.tryDelete(c,target())==-1&&c.resolver.deletes==0,"other volume rejected");
  c=grant(tree,true);check(FileDelete.tryDelete(c,new Target("/storage/ABCD-1234/album2/a.jpg"))==-1&&c.resolver.deletes==0,"sibling grant boundary");
  f=new Target("/storage/ABCD-1234/album/../secret.jpg");check(FileDelete.tryDelete(c,f)==-1&&c.resolver.deletes==0,"traversal rejected");
  f=target();f.directory=true;check(FileDelete.tryDelete(c,f)==-1&&f.attempts==0&&c.resolver.deletes==0,"directory never deleted by helper");
  f=new Target("/storage/emulated/0/Pictures/a.jpg");check(FileDelete.tryDelete(c,f)==-1&&f.attempts==0,"primary uses unchanged original helper");
  Build.VERSION.SDK_INT=29;f=target();check(FileDelete.tryDelete(c,f)==-1&&f.attempts==0,"old Android uses unchanged helper");Build.VERSION.SDK_INT=30;
  check(FileDelete.tryDelete(c,null)==-1&&FileDelete.tryDelete(null,target())==-1,"null rejects");
  File work=new File(args[0]);work.mkdirs();File keep=new File(work,"keep.jpg");Files.write(keep.toPath(),new byte[]{1});File link=new File(work,"link.jpg");Files.createSymbolicLink(link.toPath(),keep.toPath());
  // A real selected link can only be unlinked raw; this helper does not follow it through SAF.
  check(FileDelete.tryDelete(c,link)==-1&&keep.exists()&&link.exists(),"nonsecondary link remains for original path");
  check(c.resolver.batches==0,"helper never invokes MediaStore cleanup");
  System.out.println("checks="+checks);
 }
}
"""


def run(command):
    result = subprocess.run(list(map(str, command)), capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('Command failed: ' + str(command[0]) + '\n' + result.stdout + result.stderr)
    return result.stdout.strip()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--decoded',type=Path,required=True)
    p.add_argument('--work-dir',type=Path,required=True)
    p.add_argument('--android-jar',type=Path,required=True)
    p.add_argument('--d8-jar',type=Path,required=True)
    p.add_argument('--apktool-jar',type=Path,required=True)
    p.add_argument('--output-json',type=Path)
    a=p.parse_args();a.work_dir=a.work_dir.resolve()
    if a.work_dir.exists() and any(a.work_dir.iterdir()): raise SystemExit('Use new empty work directory')
    a.work_dir.mkdir(parents=True,exist_ok=True)
    spec=importlib.util.spec_from_file_location('folder_fixtures',ROOT/'tests/folder_delete_host/run.py')
    fixture_module=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture_module)
    fixtures=dict(fixture_module.FIXTURES);fixtures['com/alensw/jni/FileDeleteProbe.java']=PROBE
    # This harness isolates the caller's fallback ordering; actual fast-helper IO has its own suite.
    fixtures['com/alensw/jni/FastMediaDelete.java']='package com.alensw.jni; import android.content.Context; import java.io.File; public class FastMediaDelete { public static int result=-1,calls; public static int tryDelete(Context c,File f){calls++;return result;} }'
    host=a.work_dir/'host'
    for name,text in fixtures.items():
        path=host/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
    helpers=[ROOT/'src/com/alensw/jni/FolderDelete.java',ROOT/'src/com/alensw/jni/FileDelete.java']
    classes=a.work_dir/'host-classes';classes.mkdir()
    run(['javac','--release','8','-encoding','UTF-8','-d',classes,*host.rglob('*.java'),*helpers])
    host_result=run(['java','-cp',classes,'com.alensw.jni.FileDeleteProbe',a.work_dir/'synthetic-files'])
    api=a.work_dir/'api-classes';api.mkdir()
    # API/D8 compilation uses all real maintenance sources, never the host fast-route stub.
    run(['javac','--release','8','-encoding','UTF-8','-cp',a.android_jar,'-d',api,*sorted((ROOT/'src').rglob('*.java'))])
    dex=a.work_dir/'helper-dex';dex.mkdir()
    run(['java','-cp',a.d8_jar,'com.android.tools.r8.D8','--min-api','21','--lib',a.android_jar,'--output',dex,*api.rglob('*.class')])
    decode=a.work_dir/'decode'
    original={}
    for rel in ['smali/com/alensw/b/l/b.smali','smali/com/alensw/a/ba.smali','smali/com/alensw/ui/c/bl.smali','smali/com/alensw/ui/c/as.smali']:
        path=decode/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((a.decoded/rel).read_bytes());original[rel]=path.read_text()
    patch_file_delete.patch(decode)
    first={rel:(decode/rel).read_text() for rel in original}
    again=patch_file_delete.patch(decode)
    checks={'host_probe_passed':True,'real_android_api_javac':True,'helper_d8_passed':True,'patch_idempotent':again['changed']==[] and first=={rel:(decode/rel).read_text() for rel in original}}
    checks['verified_source_untouched']=all((a.decoded/rel).read_text()==text for rel,text in original.items())
    for rel,sig in [('smali/com/alensw/ui/c/bl.smali','.method protected a()V'),('smali/com/alensw/ui/c/as.smali','.method protected m()V')]:
        old=patch_file_delete.method(original[rel],sig);new=patch_file_delete.method(first[rel],sig)
        checks[rel+'_outside_method_unchanged']=original[rel][:old.start()]+original[rel][old.end():]==first[rel][:new.start()]+first[rel][new.end():]
        checks[rel+'_integer_flag_and_failure_gate']='->quickpicDelete(Landroid/content/Context;Ljava/io/File;)I' in new.group() and 'if-ltz ' in new.group()
        checks[rel+'_keeps_original_metadata_cleanup']='->a(Ljava/lang/String;CZ)Z' in new.group()
    ba=first['smali/com/alensw/a/ba.smali'];forget=patch_file_delete.method(ba,'.method public quickpicForget(Ljava/lang/String;)V').group()
    checks['forget_has_only_original_two_cache_calls']=forget.count('invoke-virtual')==2 and 'Lcom/alensw/a/an;->a(Ljava/lang/String;)V' in forget and 'Lcom/alensw/a/am;->c(Ljava/lang/String;)Ljava/lang/Integer;' in forget and 'ContentResolver' not in forget
    wrapper=patch_file_delete.WRAPPER
    checks['sdk29_fast_before_legacy_boolean']=wrapper.index('FastMediaDelete;->tryDelete')<wrapper.index('b/l/b;->c(Ljava/io/File;)Z') and 'if-ge v0, v1, :quickpic_delete_original' in wrapper
    checks['original_boolean_helper_unchanged']=patch_file_delete.method(first['smali/com/alensw/b/l/b.smali'],'.method public static c(Ljava/io/File;)Z').group()==patch_file_delete.method(original['smali/com/alensw/b/l/b.smali'],'.method public static c(Ljava/io/File;)Z').group()
    oldm=patch_file_delete.method(original['smali/com/alensw/ui/c/as.smali'],'.method protected m()V').group();newm=patch_file_delete.method(first['smali/com/alensw/ui/c/as.smali'],'.method protected m()V').group()
    checks['ordinary_mediastore_branch_unchanged']=oldm[oldm.index('    :cond_2'):]==newm[newm.index('    :cond_2'):]
    # A missing hook must fail before any of the four supplied source files is written.
    bad=a.work_dir/'bad';shutil.copytree(decode,bad)
    rel='smali/com/alensw/ui/c/as.smali';(bad/rel).write_text(original[rel].replace('->i(Ljava/lang/String;)Z','->unrecognized(Ljava/lang/String;)Z'))
    before={rel:(bad/rel).read_bytes() for rel in original}
    try:patch_file_delete.patch(bad);checks['unknown_seam_fails_without_partial_writes']=False
    except ValueError:checks['unknown_seam_fails_without_partial_writes']=all((bad/rel).read_bytes()==data for rel,data in before.items())
    bridge=a.work_dir/'bridge';bridge.mkdir()
    run(['javac','--release','8','-cp',a.apktool_jar,'-d',bridge,Path(__file__).with_name('SmaliBridge.java')])
    bridge_cp=str(bridge)+':'+str(a.apktool_jar)
    assembled=a.work_dir/'hooks.dex'
    run(['java','-cp',bridge_cp,'SmaliBridge','assemble',decode/'smali',assembled])
    roundtrip=a.work_dir/'roundtrip'
    run(['java','-cp',bridge_cp,'SmaliBridge','disassemble',assembled,roundtrip])
    checks['actual_smali_assembles']=assembled.is_file()
    checks['dex_roundtrip_integer_descriptors']=all('->quickpicDelete(Landroid/content/Context;Ljava/io/File;)I' in (roundtrip/f'com/alensw/ui/c/{name}.smali').read_text() for name in ('bl','as'))
    checks['dex_roundtrip_forget_void_descriptor']='.method public quickpicForget(Ljava/lang/String;)V' in (roundtrip/'com/alensw/a/ba.smali').read_text()
    helper_roundtrip=a.work_dir/'helper-roundtrip'
    run(['java','-cp',bridge_cp,'SmaliBridge','disassemble',dex/'classes.dex',helper_roundtrip])
    checks['helper_dex_integer_descriptor']='.method public static tryDelete(Landroid/content/Context;Ljava/io/File;)I' in (helper_roundtrip/'com/alensw/jni/FileDelete.smali').read_text()
    output={'passed':all(checks.values()),'host':host_result,'checks':checks,'scope':'FileDelete caller host/provider doubles with injected FastMediaDelete return only; actual Fast IO has separate suite. Full real Android36 helpers javac/D8, actual 11.0.6 smali assembly and DEX roundtrip; no Android execution/performance benchmark','source_sha256':{str(x.relative_to(ROOT)):hashlib.sha256(x.read_bytes()).hexdigest() for x in helpers+[ROOT/'scripts/patch_file_delete.py']}}
    if a.output_json:a.output_json.parent.mkdir(parents=True,exist_ok=True);a.output_json.write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(output,ensure_ascii=False,indent=2))
    if not output['passed']:raise SystemExit(1)

if __name__=='__main__':main()
