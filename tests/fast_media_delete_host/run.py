#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Synthetic physical/MediaStore safety cases plus real SDK36 compilation and D8."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/alensw/jni/FastMediaDelete.java'

EXTRA = {
'android/content/ContentUris.java': '''package android.content; import android.net.Uri; public final class ContentUris {public static Uri withAppendedId(Uri uri,long id){return Uri.parse(uri.toString()+"/"+id);}}''',
'android/database/Cursor.java': '''package android.database; public interface Cursor {boolean moveToNext();long getLong(int column);String getString(int column);int getInt(int column);void close();}''',
'android/system/OsConstants.java': '''package android.system;public final class OsConstants {public static final int ENOENT=2,EACCES=13,ENOTDIR=20;}''',
'android/system/ErrnoException.java': '''package android.system;public class ErrnoException extends Exception {private static final long serialVersionUID=1L;public final int errno;public ErrnoException(String f,int e){errno=e;}}''',
'android/system/StructStat.java': '''package android.system;public class StructStat {}''',
'android/system/Os.java': '''package android.system;
import java.io.File;import java.util.*;import java.nio.file.*;
public final class Os {
 private static final Map<String,File> files=new HashMap<String,File>(); public static int calls,error;public static boolean runtime;
 public static void bind(String path,File file){files.put(path,file);}public static File backing(String path){return files.get(path);}
 public static boolean present(String path){return Files.exists(backing(path).toPath(),LinkOption.NOFOLLOW_LINKS);}
 public static void unlink(String path){try{Files.delete(backing(path).toPath());}catch(java.io.IOException e){throw new RuntimeException(e);}}
 public static StructStat lstat(String path)throws ErrnoException {calls++;if(runtime)throw new SecurityException();if(error!=0)throw new ErrnoException("lstat",error);if(!present(path))throw new ErrnoException("lstat",OsConstants.ENOENT);return new StructStat();}
 public static void reset(){calls=0;error=0;runtime=false;}
}''',
'com/alensw/jni/DeleteTiming.java': '''package com.alensw.jni;import java.util.*;public final class DeleteTiming {
 public static final List<Integer> events=new ArrayList<Integer>();
 public static final List<Integer> guards=new ArrayList<Integer>();public static int classifications;
 public static int classifyStorage(java.io.File file){classifications++;if(file==null)return 0;try{String p=file.getAbsolutePath(),base=android.os.Environment.getExternalStorageDirectory().getAbsolutePath();if(p.equals(base)||p.startsWith(base+"/"))return 1;return FolderDelete.isSecondaryPath(file)?2:0;}catch(Exception e){return 0;}}
 public static void fastGuard(int code){if(code<0||code>6)throw new AssertionError("guard range");guards.add(code);}
 public static void reset(){events.clear();guards.clear();classifications=0;android.system.Os.reset();}
 public static void beginFastLookup(){events.add(10);}public static void endFastLookup(){events.add(11);}
 public static void beginFastDelete(){events.add(12);}public static void endFastDelete(){events.add(13);}
 public static void beginFastVerify(){events.add(14);}public static void endFastVerify(){events.add(15);}
 public static void fastOutcome(int code){if(code<0||code>8)throw new AssertionError("nonnumeric contract");events.add(code);}
}''',
'android/content/ContentResolver.java': '''package android.content;
import java.util.*;import android.database.Cursor;import android.net.Uri;import android.system.Os;import android.system.OsConstants;
public class ContentResolver {
 public final List<UriPermission> grants=new ArrayList<UriPermission>();public final List<Row> rows=new ArrayList<Row>();
 public int queries,deletes;public String mode="";public ProbeCursor cursor;public Uri lastQueryUri,lastDeleteUri;public String[] lastProjection,lastQueryArgs,lastDeleteArgs;public String lastQuerySelection,lastDeleteSelection;
 public static final class Row {public long id;public String path;public int type;public Row(long i,String p,int t){id=i;path=p;type=t;}}
 public List<UriPermission> getPersistedUriPermissions(){return grants;}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
  queries++;lastQueryUri=uri;lastProjection=projection;lastQuerySelection=selection;lastQueryArgs=args;
  if(mode.equals("query-error"))throw new SecurityException();if(mode.equals("null-cursor"))return null;
  if(!uri.toString().equals("content://media/external/file")||!Arrays.equals(projection,new String[]{"_id","_data","media_type"})||!selection.equals("_data = ?")||args.length!=1||order!=null)throw new AssertionError("unexpected lookup contract");
  List<Row> matches=new ArrayList<Row>();for(Row row:rows)if(row.path.equalsIgnoreCase(args[0]))matches.add(row);cursor=new ProbeCursor(matches,mode);return cursor;
 }
 public int delete(Uri uri,String selection,String[] args) {
  deletes++;lastDeleteUri=uri;lastDeleteSelection=selection;lastDeleteArgs=args;
  if(cursor==null||!cursor.closed)throw new AssertionError("query must close before mutation");
  if(!selection.equals("_data = ? COLLATE BINARY AND media_type IN (1,3)")||args.length!=1)throw new AssertionError("unsafe delete predicate");
  if(mode.equals("delete-error"))throw new SecurityException();
  long id=Long.parseLong(uri.toString().substring(uri.toString().lastIndexOf('/')+1));
  if(mode.equals("changed-path"))rows.get(0).path=args[0]+".moved";
  if(mode.equals("changed-type"))rows.get(0).type=0;
  if(mode.equals("stat-denied"))Os.error=OsConstants.EACCES;
  if(mode.equals("stat-notdir"))Os.error=OsConstants.ENOTDIR;
  if(mode.equals("stat-runtime"))Os.runtime=true;
  if(mode.equals("false")||mode.equals("unlink-failure")||mode.startsWith("stat-"))return 0;
  if(mode.equals("unlink-before-row-error")){Os.unlink(args[0]);throw new SecurityException();}
  Iterator<Row> iterator=rows.iterator();while(iterator.hasNext()) {Row row=iterator.next();if(row.id==id&&row.path.equals(args[0])&&(row.type==1||row.type==3)) {iterator.remove();if(!mode.equals("row-only"))Os.unlink(args[0]);if(mode.equals("error-after-unlink"))throw new SecurityException();return mode.equals("zero-after-unlink")?0:1;}}
  return 0;
 }
 public boolean remove(Uri uri){throw new AssertionError("Fast helper never invokes SAF");}
 public static final class ProbeCursor implements Cursor {final List<Row> rows;final String mode;int index=-1;public boolean closed;ProbeCursor(List<Row> r,String m){rows=r;mode=m;}public boolean moveToNext(){if(mode.equals("move-error"))throw new SecurityException();return ++index<rows.size();}public long getLong(int c){if(mode.equals("read-error"))throw new SecurityException();return rows.get(index).id;}public String getString(int c){return rows.get(index).path;}public int getInt(int c){return rows.get(index).type;}public void close(){closed=true;if(mode.equals("close-error"))throw new SecurityException();}}
}''',
}


def command(parts):
    result = subprocess.run(list(map(str, parts)), text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f'{parts[0]} failed ({result.returncode})\n{result.stdout}{result.stderr}')
    return result.stdout.strip()


def sql_checks():
    db = sqlite3.connect(':memory:')
    db.execute('CREATE TABLE files (_id INTEGER PRIMARY KEY, _data TEXT COLLATE NOCASE, media_type INTEGER)')
    paths = ["/storage/card/Book/a%_'.jpg", "/storage/card/book/a%_'.jpg", "/storage/card/BOOK/a%_'.jpg", "/storage/card/Book/a%_'.jpg.keep", "/storage/card/Book2/a%_'.jpg"]
    db.executemany('INSERT INTO files VALUES (?, ?, ?)', [(i + 1, p, 1) for i, p in enumerate(paths)])
    query = 'SELECT _id,_data,media_type FROM files WHERE _data = ?'
    found = db.execute(query, [paths[0]]).fetchall()
    assert len(found) == 3 and [row for row in found if row[1] == paths[0]] == [(1, paths[0], 1)]
    # Appended-ID content URI is represented by the provider's mandatory _id term.
    predicate = '_id = ? AND _data = ? COLLATE BINARY AND media_type IN (1,3)'
    assert db.execute('DELETE FROM files WHERE ' + predicate, [2, paths[0]]).rowcount == 0
    assert db.execute('DELETE FROM files WHERE ' + predicate, [1, paths[0]]).rowcount == 1
    assert [row[0] for row in db.execute('SELECT _data FROM files ORDER BY _id')] == paths[1:]
    db.execute('UPDATE files SET media_type=0 WHERE _id=2')
    assert db.execute('DELETE FROM files WHERE ' + predicate, [2, paths[1]]).rowcount == 0
    db.execute('UPDATE files SET _data=? WHERE _id=3', [paths[2] + '.moved'])
    assert db.execute('DELETE FROM files WHERE ' + predicate, [3, paths[2]]).rowcount == 0
    db.close()
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--android-jar', type=Path, default=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home() / 'Library/Android/sdk')) / 'platforms/android-36/android.jar')
    parser.add_argument('--d8-jar', type=Path, default=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home() / 'Library/Android/sdk')) / 'build-tools/36.0.0/lib/d8.jar')
    parser.add_argument('--output-json', type=Path)
    parser.add_argument('--skip-api', action='store_true', help='Host red/green cycle only; no API/D8 completion claim.')
    args = parser.parse_args()
    work = args.work_dir.resolve()
    if work.exists() and any(work.iterdir()):
        raise SystemExit('Use a new empty work directory')
    work.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location('folder_fixtures', ROOT / 'tests/folder_delete_host/run.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    fixtures = dict(module.FIXTURES)
    fixtures.pop('com/alensw/jni/FolderDeleteProbe.java')
    fixtures.update(EXTRA)
    host = work / 'host'
    for relative, content in fixtures.items():
        file = host / relative
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)
    classes = work / 'host-classes'
    classes.mkdir()
    helpers = [SOURCE, ROOT / 'src/com/alensw/jni/FolderDelete.java']
    probe = Path(__file__).with_name('FastMediaDeleteProbe.java')
    command(['javac', '--release', '8', '-encoding', 'UTF-8', '-d', classes, *sorted(host.rglob('*.java')), probe, *helpers])
    result = json.loads(command(['java', '-cp', classes, 'com.alensw.jni.FastMediaDeleteProbe', work / 'synthetic-files']))
    checks = {'synthetic_physical_provider_cases': result['passed'], 'sqlite_binary_id_type_guard_controls': sql_checks()}
    if not args.skip_api:
        api = work / 'api-classes'
        api.mkdir()
        command(['javac', '--release', '8', '-encoding', 'UTF-8', '-cp', args.android_jar, '-d', api, *helpers, ROOT / 'src/com/alensw/jni/DeleteTiming.java', ROOT / 'src/com/alensw/jni/MediaCleanup.java'])
        dex = work / 'helper-dex'
        dex.mkdir()
        command(['java', '-cp', args.d8_jar, 'com.android.tools.r8.D8', '--min-api', '21', '--lib', args.android_jar, '--output', dex, *sorted(api.rglob('*.class'))])
        signatures = command(['javap', '-classpath', api, '-s', 'com.alensw.jni.FastMediaDelete'])
        checks.update(real_android_api36_javac=True, d8_min_api21=(dex / 'classes.dex').is_file(), public_descriptor='(Landroid/content/Context;Ljava/io/File;)I' in signatures)
    output = {'passed': all(checks.values()), 'probe': result, 'checks': checks,
              'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
              'scope': 'Synthetic host filesystem and Android/provider doubles; API36 javac and D8 when not skipped. No Android execution, physical SD-card timing or speed claim.'}
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(output, ensure_ascii=False, indent=2))
    if not output['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
