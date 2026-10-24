#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Exercise whole-folder deletion with synthetic files and provider doubles."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sqlite3
import base64
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import patch_delete

FIXTURES = {
'android/net/Uri.java': '''package android.net;
public final class Uri { final String value; private Uri(String s){value=s;} public static Uri parse(String s){return new Uri(s);} public String getScheme(){return java.net.URI.create(value).getScheme();} public String getAuthority(){return java.net.URI.create(value).getAuthority();} public String toString(){return value;} }''',
'android/content/UriPermission.java': '''package android.content; import android.net.Uri; public class UriPermission {private Uri uri;private boolean write;public UriPermission(String s,boolean w){uri=Uri.parse(s);write=w;}public Uri getUri(){return uri;}public boolean isWritePermission(){return write;}}''',
'android/content/ContentResolver.java': '''package android.content; import java.util.*; import android.net.Uri; public class ContentResolver {public List<UriPermission> grants=new ArrayList<UriPermission>();public int deletes,batches;public String selection;public String[] args;public boolean cleanupDeny;public boolean result=true;public boolean deny;public android.app.Dialog cancelAfter;public Uri last;public List<UriPermission> getPersistedUriPermissions(){return grants;}public int delete(Uri uri,String where,String[] values){batches++;selection=where;args=values;if(cleanupDeny)throw new SecurityException("metadata denied");return 2;}public boolean remove(Uri uri){deletes++;last=uri;if(deny)throw new SecurityException("denied");if(cancelAfter!=null)cancelAfter.showing=false;return result;}}''',
'android/content/Context.java': '''package android.content; public class Context {public final ContentResolver resolver=new ContentResolver();public ContentResolver getContentResolver(){return resolver;}}''',
'android/app/Dialog.java': '''package android.app; import android.content.Context;public class Dialog {public boolean showing=true;private Context context;public Dialog(Context c){context=c;}public boolean isShowing(){return showing;}public Context getContext(){return context;}}''',
'android/os/Build.java': '''package android.os;public class Build {public static class VERSION {public static int SDK_INT=36;}}''',
'android/os/Environment.java': '''package android.os;public class Environment {public static java.io.File getExternalStorageDirectory(){return new java.io.File("/storage/emulated/0");}}''',
'android/provider/DocumentsContract.java': '''package android.provider;import android.net.Uri;import android.content.ContentResolver;public class DocumentsContract {public static String getTreeDocumentId(Uri uri){try {String s=uri.toString();int start=s.indexOf("/tree/");if(start<0)throw new IllegalArgumentException();String id=s.substring(start+6).split("/")[0];return java.net.URLDecoder.decode(id,"UTF-8");}catch(java.io.UnsupportedEncodingException e){throw new RuntimeException(e);}}public static Uri buildDocumentUriUsingTree(Uri tree,String id){try{return Uri.parse(tree.toString()+"/document/"+java.net.URLEncoder.encode(id,"UTF-8"));}catch(Exception e){throw new RuntimeException(e);}}public static boolean deleteDocument(ContentResolver resolver,Uri uri){return resolver.remove(uri);}}''',
'android/provider/MediaStore.java': '''package android.provider;import android.net.Uri;public class MediaStore {public static class Files {public static Uri getContentUri(String volume){return Uri.parse("content://media/"+volume+"/file");}}}''',
'android/util/Log.java': '''package android.util;public class Log {public static int warnings;public static int w(String tag,String text,Throwable error){warnings++;return 0;}}''',
'android/widget/Toast.java': '''package android.widget;import android.content.Context;public class Toast {public static final int LENGTH_LONG=1;public static String last;public static Toast makeText(Context c,String text,int duration){last=text;return new Toast();}public void show(){}}''',
'com/alensw/jni/FolderDeleteProbe.java': r'''package com.alensw.jni;
import java.io.*;import java.nio.file.*;import android.app.Dialog;import android.content.*;import android.net.Uri;
public class FolderDeleteProbe {
 static int checks;
 static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
 static void put(File f)throws Exception{f.getParentFile().mkdirs();Files.write(f.toPath(),new byte[]{1,2});}
 static String grant(String relative){return "content://com.android.externalstorage.documents/tree/ABCD-1234%3A"+relative;}
 static Context context(String relative){Context c=new Context();c.resolver.grants.add(new UriPermission(grant(relative),true));return c;}
 static class Broken extends File {boolean deleted;Broken(String p){super(p);}public boolean exists(){return true;}public boolean isDirectory(){return true;}public File[] listFiles(){return null;}public boolean delete(){deleted=true;return false;}}
 static class ProviderOnly extends File {int listings;ProviderOnly(String p){super(p);}public boolean isDirectory(){return true;}public File[] listFiles(){listings++;return new File[0];}public boolean delete(){throw new AssertionError("SAF should not call raw unlink");}}
 static class CancelFile extends File {final Dialog dialog;CancelFile(File f,Dialog d){super(f.getPath());dialog=d;}public boolean delete(){boolean ok=super.delete();dialog.showing=false;return ok;}}
 static class Listed extends File {final File[] children;Listed(File f,File[] entries){super(f.getPath());children=entries;}public File[] listFiles(){return children;}}
 public static void main(String[] args)throws Exception {
  File work=new File(args[0]);work.mkdirs();
  Context c=new Context();Dialog dialog=new Dialog(c);
  File outside=new File(work,"outside");put(new File(outside,"keep.txt"));
  File album=new File(work,"album");put(new File(album,"picture.jpg"));put(new File(album,".hidden"));put(new File(album,"nested/deeper/notes.json"));put(new File(album,"nested/movie.bin"));
  Files.createSymbolicLink(new File(album,"outside-link").toPath(),outside.toPath().toAbsolutePath());
  Files.createSymbolicLink(new File(album,"dangling-link").toPath(),new File(work,"absent").toPath().toAbsolutePath());
  FolderDelete.Result result=new FolderDelete.Result(1);
  check(FolderDelete.deleteOne(dialog,album,result),"raw deletes all content");
  check(!album.exists(),"folder itself removed");check(new File(outside,"keep.txt").exists(),"symlink target retained");check(result.successes()==1&&result.failures()==0,"raw result semantics");check(c.resolver.deletes==0&&c.resolver.batches==1,"raw uses one metadata batch and no SAF call");
  android.widget.Toast.last=null;FolderDelete.finish(dialog,result);check(android.widget.Toast.last==null,"successful folder deletion has no completion report");
  Broken broken=new Broken(new File(work,"unreadable").getPath());result=new FolderDelete.Result(1);
  check(!FolderDelete.deleteOne(dialog,broken,result),"null listing fails");check(!broken.deleted,"null listing not treated as empty");check(result.failures()==1&&!result.wasDeleted(broken),"failure not success");
  Context saf=context("album");dialog=new Dialog(saf);File target=new ProviderOnly("/storage/ABCD-1234/album/child");result=new FolderDelete.Result(2);
  check(FolderDelete.deleteOne(dialog,target,result),"authorized SAF folder");check(saf.resolver.deletes==1&&saf.resolver.batches==0,"one SAF request with no metadata duplicate");check(saf.resolver.last.toString().contains("ABCD-1234%3Aalbum%2Fchild"),"exact document id");
  check(FolderDelete.documentFor(saf,new File("/storage/ABCD-1234/album"))!=null,"grant folder itself allowed");
  check(FolderDelete.documentFor(saf,new File("/storage/ABCD-1234/album2"))==null,"sibling prefix rejected");
  check(FolderDelete.documentFor(saf,new File("/storage/ABCD-1234/album/../secret"))==null,"dot traversal rejected");
  check(FolderDelete.documentFor(saf,new File("/storage/ABCD-1234"))==null,"volume root URI rejected");
  check(FolderDelete.documentFor(saf,new File("/storage/FFFF-FFFF/album"))==null,"other volume rejected");
  check(FolderDelete.isSecondaryPath(target),"secondary classification");check(!FolderDelete.isSecondaryPath(new File("/storage/emulated/0/Pictures/a")),"primary classification");
  Context wrong=new Context();wrong.resolver.grants.add(new UriPermission("content://evil.documents/tree/ABCD-1234%3A",true));
  check(FolderDelete.documentFor(wrong,target)==null,"wrong authority rejected");wrong.resolver.grants.clear();wrong.resolver.grants.add(new UriPermission(grant(""),false));check(FolderDelete.documentFor(wrong,target)==null,"read-only grant rejected");
  Context card=context("");dialog=new Dialog(card);result=new FolderDelete.Result(1);
  check(!FolderDelete.deleteOne(dialog,new File("/storage/ABCD-1234"),result)&&card.resolver.deletes==0,"volume root never deleted");
  card.resolver.result=false;result=new FolderDelete.Result(1);check(!FolderDelete.deleteOne(dialog,target,result),"provider false fails");check(result.failures()==1&&!result.wasDeleted(target),"provider false preserves UI outcome");
  card.resolver.deny=true;result=new FolderDelete.Result(1);check(!FolderDelete.deleteOne(dialog,target,result),"permission exception fails");check(result.failures()==1,"permission exception counted");
  card=context("");dialog=new Dialog(card);card.resolver.cancelAfter=dialog;result=new FolderDelete.Result(2);
  check(FolderDelete.deleteOne(dialog,target,result),"in-flight provider can finish after cancel");check(!FolderDelete.mayContinue(dialog,result),"cancel blocks next folder");
  check(!FolderDelete.deleteOne(dialog,new ProviderOnly("/storage/ABCD-1234/next"),result)&&card.resolver.deletes==1,"cancel does not launch next provider");check(result.unfinished()==1&&result.failures()==0,"cancel not failure");
  android.widget.Toast.last=null;FolderDelete.finish(dialog,result);check(android.widget.Toast.last==null,"cancelled folder deletion has no summary report");
  c=new Context();dialog=new Dialog(c);dialog.showing=false;File untouched=new File(work,"untouched");put(new File(untouched,"extra.dat"));result=new FolderDelete.Result(1);
  check(!FolderDelete.deleteOne(dialog,untouched,result)&&untouched.exists(),"cancel before first keeps folder");check(!result.wasAttempted(untouched),"unstarted distinct from failure");
  Broken secondary=new Broken("/storage/ABCD-1234/denied");dialog=new Dialog(c);result=new FolderDelete.Result(1);check(!FolderDelete.deleteOne(dialog,secondary,result),"ungranted raw failure");check(result.shouldRequestPermission(secondary)&&!result.shouldRequestPermission(secondary),"permission offer once");
  File parent=new File(work,"parent");File child=new File(parent,"child");put(new File(child,"nonimage.txt"));result=new FolderDelete.Result(2);check(FolderDelete.deleteOne(dialog,parent,result),"parent selected");check(FolderDelete.deleteOne(dialog,child,result),"nested selected already removed by parent");check(result.successes()==2,"overlapping selections completed");
  File rootLink=new File(work,"root-link");Files.createSymbolicLink(rootLink.toPath(),outside.toPath().toAbsolutePath());result=new FolderDelete.Result(1);check(FolderDelete.deleteOne(dialog,rootLink,result),"selected symlink unlinked");check(new File(outside,"keep.txt").exists(),"selected symlink never enters target");check(c.resolver.args.length==3&&c.resolver.args[0].equals(rootLink.getAbsolutePath()),"symlink metadata cleanup only uses link path");Files.write(new File(work,"link-selection.txt").toPath(),c.resolver.selection.getBytes("UTF-8"));StringBuilder linkArgs=new StringBuilder();for(String arg:c.resolver.args)linkArgs.append(java.util.Base64.getEncoder().encodeToString(arg.getBytes("UTF-8"))).append('\n');Files.write(new File(work,"link-args.txt").toPath(),linkArgs.toString().getBytes("UTF-8"));
  File partial=new File(work,"partial");File firstFile=new File(partial,"first.txt");File secondFile=new File(partial,"second.txt");put(firstFile);put(secondFile);dialog=new Dialog(c);result=new FolderDelete.Result(1);
  File wrapped=new Listed(partial,new File[]{new CancelFile(firstFile,dialog),secondFile});
  check(!FolderDelete.deleteOne(dialog,wrapped,result),"raw cancellation stops traversal");check(!firstFile.exists()&&secondFile.exists()&&partial.exists(),"raw cancellation retains remaining contents and folder");check(result.failures()==0&&result.unfinished()==1&&result.wasAttempted(wrapped),"partial cancelled folder reloadable without failure count");
  File canonicalWork=work.getCanonicalFile();File cleanTree=new File(canonicalWork,"preflight-clean");put(new File(cleanTree,"nested/photo.jpg"));dialog=new Dialog(c);
  check(FolderDelete.safeForSaf(dialog,cleanTree),"readable unaliased directory tree can use SAF");
  File other=new File(canonicalWork,"preflight-other");put(new File(other,"child/keep.bin"));
  File rootAlias=new File(canonicalWork,"preflight-root-link");Files.createSymbolicLink(rootAlias.toPath(),other.toPath());
  check(!FolderDelete.safeForSaf(dialog,rootAlias),"root directory symlink refuses SAF");
  check(!FolderDelete.safeForSaf(dialog,new File(rootAlias,"child")),"ancestor alias refuses SAF");
  Context ancestorContext=new Context();FolderDelete.Result ancestorResult=new FolderDelete.Result(1);check(!FolderDelete.deleteOne(new Dialog(ancestorContext),new File(rootAlias,"child"),ancestorResult),"ancestor alias refuses whole deletion rather than raw fallback");check(new File(other,"child/keep.bin").exists()&&other.exists(),"ancestor alias outside folder and contents retained");check(ancestorResult.successes()==0&&ancestorResult.failures()==1&&!ancestorResult.wasDeleted(new File(rootAlias,"child")),"ancestor alias failure cannot remove model");check(ancestorContext.resolver.deletes==0&&ancestorContext.resolver.batches==0,"ancestor alias does not send deletion or metadata request");
  Files.createSymbolicLink(new File(cleanTree,"nested-dir-link").toPath(),other.toPath());
  check(!FolderDelete.safeForSaf(dialog,cleanTree),"internal directory symlink refuses SAF");
  check(!FolderDelete.safeForSaf(dialog,new Broken(new File(canonicalWork,"preflight-null").getPath())),"unreadable directory never assumed safe for SAF");
  File onlyFileLink=new File(canonicalWork,"preflight-file-link");onlyFileLink.mkdirs();Files.createSymbolicLink(new File(onlyFileLink,"file-link").toPath(),new File(other,"child/keep.bin").toPath());
  check(FolderDelete.safeForSaf(dialog,onlyFileLink),"file symlink does not require following a directory");
  Context lower=new Context();lower.resolver.grants.add(new UriPermission("content://com.android.externalstorage.documents/tree/abcd-1234%3Aalbum",true));
  check(FolderDelete.documentFor(lower,new File("/storage/ABCD-1234/album/child")).toString().contains("document/abcd-1234%3Aalbum%2Fchild"),"document id preserves grant volume case");
  File modelRoot=new File(canonicalWork,"models");File modelChild=new File(modelRoot,"child");put(new File(modelChild,"extra.bin"));result=new FolderDelete.Result(2);check(FolderDelete.deleteOne(dialog,modelRoot,result),"model root removed");
  check(result.wasDeleted(modelChild)&&!result.wasDeleted(new File(modelRoot.getPath()+"2/child")),"successful root covers descendant models with separator boundary");
  java.util.ArrayList<File> all=new java.util.ArrayList<File>();all.add(modelChild);all.add(new File(canonicalWork,"unrelated"));java.util.ArrayList<File> selected=new java.util.ArrayList<File>();selected.add(modelRoot);selected.add(new File(modelChild.getPath()));
  java.util.ArrayList snapshot=FolderDelete.snapshot(all,selected);check(snapshot.size()==3&&all.size()==2&&selected.size()==2,"global model snapshot merges selected and deduplicates without mutation");
  int removedModels=0;for(Object value:snapshot)if(result.wasDeleted((File)value))removedModels++;check(removedModels==2,"unselected descendant model removed with successful root");
  FolderDelete.Result failureResult=new FolderDelete.Result(1);Broken failedModel=new Broken(new File(canonicalWork,"failed-model").getPath());check(!FolderDelete.deleteOne(dialog,failedModel,failureResult),"failed model operation");check(!failureResult.wasDeleted(new File(failedModel,"child")),"failed root never removes descendant models");
  FolderDelete.Result cancelResult=new FolderDelete.Result(1);dialog.showing=false;check(!FolderDelete.deleteOne(dialog,new File(canonicalWork,"cancel-model"),cancelResult)&&!cancelResult.wasDeleted(new File(canonicalWork,"cancel-model/child")),"cancelled root never removes descendants");dialog.showing=true;
  java.util.ArrayList<File> overlapping=new java.util.ArrayList<File>();overlapping.add(modelRoot);overlapping.add(modelChild);result.settleSelected(overlapping);check(result.successes()==2&&result.unfinished()==0,"overlapping selected children settled without another delete");
  Context metadata=new Context();metadata.resolver.cleanupDeny=true;dialog=new Dialog(metadata);File removed=new File(work,"metadata-denied");put(new File(removed,"hidden.bin"));result=new FolderDelete.Result(1);
  check(FolderDelete.deleteOne(dialog,removed,result)&&!removed.exists(),"metadata failure does not undo physical success");check(result.successes()==1&&result.failures()==0&&result.metadataWarnings()==1,"metadata warnings separate from physical failure");android.widget.Toast.last=null;FolderDelete.finish(dialog,result);check(android.widget.Toast.last==null&&android.util.Log.warnings==1,"metadata warning has no completion report; local warning retained");
  metadata=new Context();dialog=new Dialog(metadata);File escaped=new File(work,"quote'_%[]*?\\pictures");put(new File(escaped,"nested/secret.txt"));result=new FolderDelete.Result(1);check(FolderDelete.deleteOne(dialog,escaped,result),"SQL-special folder deleted");check(metadata.resolver.batches==1,"SQL-special folder single cleanup batch");check(!metadata.resolver.selection.contains("quote")&&metadata.resolver.selection.indexOf('?')>=0,"path never interpolated into selection");
  Files.write(new File(work,"selection.txt").toPath(),metadata.resolver.selection.getBytes("UTF-8"));StringBuilder encoded=new StringBuilder();for(String arg:metadata.resolver.args)encoded.append(java.util.Base64.getEncoder().encodeToString(arg.getBytes("UTF-8"))).append('\n');Files.write(new File(work,"args.txt").toPath(),encoded.toString().getBytes("UTF-8"));
  dialog=new Dialog(c);result=new FolderDelete.Result(1);check(!FolderDelete.deleteOne(dialog,new File("/storage/emulated/0"),result),"primary root rejected");
  FolderDelete.finish(dialog,result);check(android.widget.Toast.last.equals("無法刪除部分資料夾，請確認存取權限。"),"actionable error retained without diagnostic counts");
  System.out.println("checks="+checks);
 }
}
'''
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--decoded', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--output-json', type=Path)
    args = parser.parse_args()
    args.work_dir = args.work_dir.resolve()
    args.decoded = args.decoded.resolve()
    if args.work_dir.exists() and any(args.work_dir.iterdir()):
        raise SystemExit('Use a new empty work directory')
    source = args.work_dir / 'host'
    for name, text in FIXTURES.items():
        target = source / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    classes = args.work_dir / 'classes'
    classes.mkdir(parents=True)
    source_files = sorted(source.rglob('*.java')) + [ROOT / 'src/com/alensw/jni/FolderDelete.java']
    compile_result = subprocess.run(['javac', '--release', '8', '-encoding', 'UTF-8', '-d', str(classes), *map(str, source_files)], capture_output=True, text=True)
    if compile_result.returncode:
        raise SystemExit(compile_result.stderr)
    probe = subprocess.run(['java', '-cp', str(classes), 'com.alensw.jni.FolderDeleteProbe', str(args.work_dir / 'synthetic-files')], capture_output=True, text=True)
    if probe.returncode:
        raise SystemExit(probe.stderr)
    relative = Path('smali/com/alensw/ui/c/cf.smali')
    original = args.decoded / relative
    before = hashlib.sha256(original.read_bytes()).hexdigest()
    target = args.work_dir / 'decode' / relative
    target.parent.mkdir(parents=True)
    shutil.copy2(original, target)
    patch_delete.patch(args.work_dir / 'decode')
    first = target.read_text()
    patch_delete.patch(args.work_dir / 'decode')
    checks = {
        'patch_idempotent': first == target.read_text(),
        'source_decode_untouched': before == hashlib.sha256(original.read_bytes()).hexdigest(),
        'result_field_once': first.count(patch_delete.FIELD) == 1,
        'worker_keeps_void_descriptor': first.count('.method protected a()V') == 1,
        'folder_total_progress': 'invoke-virtual {p5}, Ljava/util/ArrayList;->size()I' in first,
        'no_per_image_mediastore_deletion': '->a(Ljava/lang/String;CZ)Z' not in first,
        'explicit_success_result': '->wasDeleted(Ljava/io/File;)Z' in first,
        'failure_reload_preserved': '->a(Lcom/alensw/a/e;Ljava/lang/Runnable;)V' in first,
        'permission_path_preserved': '->a(Ljava/lang/String;)Z' in first,
        'no_model_empty_as_success': '->h()Z' not in first,
        'no_worker_model_removal': '->h(I)Z' not in first,
        'before_each_folder_cancel': first.index('->mayContinue(') < first.index('->deleteOne('),
        'all_album_snapshot_before_removal': first.count('Lcom/alensw/a/x;->h()Ljava/util/ArrayList;') == 2,
        'global_selected_merge_worker_and_UI': first.count('->snapshot(Ljava/util/ArrayList;Ljava/util/ArrayList;)Ljava/util/ArrayList;') == 2,
        'cache_clear_uses_successful_descendant_models': ':folder_delete_cache_model' in first and first.index(':folder_delete_cache_model') < first.index('->a(Ljava/lang/String;)V', first.index(':folder_delete_cache_model')),
        'selected_covered_results_settled': '->settleSelected(Ljava/util/ArrayList;)V' in first,
        'boolean_delete_descriptor': '->deleteOne(Landroid/app/Dialog;Ljava/io/File;Lcom/alensw/jni/FolderDelete$Result;)Z' in first,
    }
    def sql_record(prefix):
        fixture = args.work_dir / 'synthetic-files'
        sql = (fixture / (prefix + 'selection.txt')).read_text()
        bound = [base64.b64decode(value).decode() for value in (fixture / (prefix + 'args.txt')).read_text().splitlines()]
        return sql, bound

    def scope_check(sql, bound, declaration):
        database = sqlite3.connect(':memory:')
        database.execute('CREATE TABLE files (_data ' + declaration + ')')
        expected = set()
        rows = set()
        for path in bound[::3]:
            wanted = {path, path + '/hidden.txt', path + '/nested/file.json'}
            expected.update(wanted)
            rows.update(wanted)
            rows.update({path + '-adjacent/picture.jpg', path + '2/file.bin', path + '0'})
            changed = path.replace('%', 'abc').replace('_', 'x').replace('[', 'q').replace('*', 'star').replace('?', 'ask')
            if changed != path:
                rows.add(changed + '/other.jpg')
            parent, name = path.rsplit('/', 1)
            for variant in [name.swapcase(), name.lower(), name.upper()]:
                other_case = parent + '/' + variant
                if other_case != path:
                    rows.update({other_case, other_case + '/b.jpg'})
        database.executemany('INSERT INTO files VALUES (?)', [(row,) for row in rows])
        actual = {row[0] for row in database.execute('SELECT _data FROM files WHERE ' + sql, bound)}
        database.close()
        return actual == expected

    selection, values = sql_record('')
    checks['parameterized_binary_bounds_from_helper'] = (
        'LIKE' not in selection and 'GLOB' not in selection
        and selection.count('COLLATE BINARY') == len(values)
        and all(values[n + 1] == values[n] + '/' and values[n + 2] == values[n] + '0' for n in range(0, len(values), 3)))
    for declaration, label in [('TEXT', 'TEXT'), ('TEXT COLLATE NOCASE', 'NOCASE')]:
        checks['special_names_exact_scope_' + label] = scope_check(selection, values, declaration)
        for name in ["Book", "book", "percent%_brackets[]star*question?", "quote'backslash\\"]:
            replacements = []
            for n in range(0, len(values), 3):
                root = values[n].rsplit('/', 1)[0] + '/' + name
                replacements.extend(root + original[len(values[n]):] for original in values[n:n + 3])
            checks['binary_case_and_special_scope_' + label + '_' + name] = scope_check(selection, replacements, declaration)
        link_sql, link_values = sql_record('link-')
        checks['symlink_index_scope_' + label] = scope_check(link_sql, link_values, declaration)
    output = {'passed': all(checks.values()), 'host': probe.stdout.strip(), 'patch_checks': checks, 'scope': 'host doubles and synthetic temporary files; no Android execution'}
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(output, ensure_ascii=False))
    if not output['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
