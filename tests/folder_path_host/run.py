#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Check the folder UI patch against an external decode and run its helper.

Copies only affected classes and original path callbacks into --work-dir. The
decode stays read-only. Host Android doubles exercise title and tint behavior;
byte comparisons prove all historical save/delete/validation code is retained.
No historical App source or user folder data is included in this test package.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import patch_folder_ui as patcher


def write(root, path, text):
    target = root / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--decoded', type=Path, required=True)
    ap.add_argument('--work-dir', type=Path, required=True)
    ap.add_argument('--output-json', type=Path)
    args = ap.parse_args()
    if args.work_dir.resolve() == args.decoded.resolve() or args.decoded.resolve() in args.work_dir.resolve().parents:
        raise SystemExit('Use a private work directory outside the supplied decode')
    copied = ['res/values/public.xml', 'res/layout/text_button.xml']
    copied += ['smali/com/alensw/ui/activity/' + name + '.smali'
               for name in ['bm', 'bg', 'bm$1', 'bf', 'PathListActivity']]
    original = {}
    for relative in copied:
        source = args.decoded / relative
        original[relative] = source.read_text()
        target = args.work_dir / 'decode' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    decoded = args.work_dir / 'decode'
    patch = patcher.patch(decoded)
    first = {relative: (decoded / relative).read_text() for relative in copied}
    patcher.patch(decoded)
    checks = []

    def check(name, passed):
        checks.append({'name': name, 'passed': bool(passed)})

    check('patch is idempotent', first == {relative: (decoded / relative).read_text() for relative in copied})
    relative = 'smali/com/alensw/ui/activity/bm.smali'
    resource_id = re.search(r'<public type="string" name="rename_format" id="([^"]+)"', original['res/values/public.xml']).group(1)
    check('title changes only one original resource call',
          first[relative].replace(patcher.TITLE_HOOK, patcher.title_block(resource_id)) == original[relative])
    check('title helper has exact Context-to-String smali signature',
          first[relative].count(patcher.HELPER + '->editTitle(Landroid/content/Context;)Ljava/lang/String;') == 1)
    relative = 'smali/com/alensw/ui/activity/bg.smali'
    check('icon changes only the final pencil row bind',
          first[relative].replace(patcher.ICON_HOOK, '') == original[relative])
    check('icon helper has exact ImageView-to-void smali signature',
          first[relative].count(patcher.HELPER + '->tintEditIcon(Landroid/widget/ImageView;)V') == 1)
    row = patcher.method(first[relative], patcher.ROW_SIGNATURE)[2]
    check('tint executes for new and recycled rows before original path tag',
          row.index(':cond_0') < row.index(patcher.ICON_HOOK)
          < row.rindex('invoke-virtual {v1, v0}, Landroid/widget/ImageView;->setTag'))
    for name in ['bm$1', 'bf', 'PathListActivity']:
        relative = 'smali/com/alensw/ui/activity/' + name + '.smali'
        check(name + ' storage/delete/refresh callback is byte-identical', first[relative] == original[relative])
    check('edit XML and original drawable remain untouched',
          first['res/layout/text_button.xml'] == original['res/layout/text_button.xml'])
    callback = first['smali/com/alensw/ui/activity/bm$1.smali']
    check('trim, one trailing slash removal and empty guard retain original order',
          callback.index('->trim()') < callback.index('->endsWith(') < callback.index('->substring(') < callback.index('->isEmpty()') < callback.index('->indexOf('))
    check('both include/exclude modes still use original saved mode flag',
          '->e:Z' in callback and '->a(Ljava/util/ArrayList;Z)V' in callback
          and '"exclude_mode"' in first['smali/com/alensw/ui/activity/PathListActivity.smali'])

    source = args.work_dir / 'host'
    fixtures = {
        'android/R.java': 'package android; public final class R { public static final class attr {public static final int textColorPrimary=0x01010036;} }',
        'android/util/TypedValue.java': 'package android.util; public class TypedValue { public int resourceId,data; }',
        'android/content/res/Configuration.java': 'package android.content.res; import java.util.Locale; public class Configuration { public Locale locale=Locale.ENGLISH; public int uiMode; }',
        'android/content/res/Resources.java': '''package android.content.res;
import android.util.TypedValue; import java.util.HashMap;
public class Resources {
 public final Configuration config=new Configuration();
 public final HashMap<Integer,Integer> colors=new HashMap<Integer,Integer>();
 public Configuration getConfiguration(){return config;}
 public int getColor(int id){if(!colors.containsKey(id))throw new AssertionError("unknown color");return colors.get(id);}
 public static class Theme {
  public final HashMap<Integer,int[]> values=new HashMap<Integer,int[]>();
  public boolean resolveAttribute(int id,TypedValue out,boolean resolve){int[] v=values.get(id);if(v==null)return false;out.resourceId=v[0];out.data=v[1];return true;}
 }
}''',
        'android/content/Context.java': '''package android.content; import android.content.res.Resources;
public class Context {public final Resources resources=new Resources();public final Resources.Theme theme=new Resources.Theme();
public Resources getResources(){return resources;}public Resources.Theme getTheme(){return theme;} }''',
        'android/graphics/PorterDuff.java': 'package android.graphics; public class PorterDuff {public enum Mode {SRC_IN}}',
        'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;
import android.graphics.PorterDuff; public class Drawable {
private static class State {int color;PorterDuff.Mode mode;State(){}State(State from){color=from.color;mode=from.mode;}}
private State state=new State();public boolean mutated;
public Drawable sharedStateCopy(){Drawable copy=new Drawable();copy.state=state;return copy;}
public Drawable mutate(){state=new State(state);mutated=true;return this;}
public void setColorFilter(int value,PorterDuff.Mode valueMode){if(!mutated)throw new AssertionError("filter before mutate");state.color=value;state.mode=valueMode;}
public int color(){return state.color;}public PorterDuff.Mode mode(){return state.mode;} }''',
        'android/widget/ImageView.java': '''package android.widget;
import android.content.Context;import android.graphics.drawable.Drawable;
public class ImageView {public final Context context;public Drawable drawable;public int assignments;
public ImageView(Context value,Drawable icon){context=value;drawable=icon;}
public Context getContext(){return context;}public Drawable getDrawable(){return drawable;}
public void setImageDrawable(Drawable icon){drawable=icon;assignments++;}}''',
        'FolderPathUiProbe.java': '''import com.alensw.jni.FolderPathUi;
import android.content.Context;import android.graphics.PorterDuff;import android.graphics.drawable.Drawable;
import android.widget.ImageView;import java.util.Locale;
public class FolderPathUiProbe {
 static int checks;
 static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  Context context=new Context();
  String[][] titles={{"en","US","Edit folder path"},{"de","DE","Edit folder path"},{"zh","TW","編輯資料夾路徑"},{"zh","HK","編輯資料夾路徑"},{"zh","MO","編輯資料夾路徑"},{"zh","CN","编辑文件夹路径"},{"zh","SG","编辑文件夹路径"}};
  for(String[] t:titles){context.resources.config.locale=new Locale(t[0],t[1]);check(t[2].equals(FolderPathUi.editTitle(context)),"localized title "+t[0]+t[1]);}
  context.resources.config.locale=new Locale.Builder().setLanguage("zh").setScript("Hant").build();
  check("編輯資料夾路徑".equals(FolderPathUi.editTitle(context)),"Hant title");
  Locale previous=Locale.getDefault();try{Locale.setDefault(Locale.TAIWAN);context.resources.config.locale=null;
   check("編輯資料夾路徑".equals(FolderPathUi.editTitle(context)),"missing locale uses default");
  }finally{Locale.setDefault(previous);}
  for(int color:new int[]{0xffffffff,0xff212121})for(boolean reference:new boolean[]{false,true})for(int systemMode:new int[]{0x10,0x20}){
   context.resources.config.uiMode=systemMode;context.theme.values.clear();
   context.resources.colors.put(99,color);context.theme.values.put(android.R.attr.textColorPrimary,new int[]{reference?99:0,reference?0:color});
   // A colored toolbar can use white icons above a light list: do not use its color.
   context.theme.values.put(0x7f010001,new int[]{0,0xffffffff});
   Drawable original=new Drawable();ImageView view=new ImageView(context,original);ImageView neighbor=new ImageView(context,original.sharedStateCopy());
   FolderPathUi.tintEditIcon(view);
   check(view.drawable.color()==color,"App theme overrides system uiMode");
   check(view.drawable.mode()==PorterDuff.Mode.SRC_IN,"tint retains source alpha");
   check(view.drawable.mutated && neighbor.drawable.color()==0,"mutated tint does not leak");
   context.theme.values.put(android.R.attr.textColorPrimary,new int[]{0,0xff123456});FolderPathUi.tintEditIcon(view);
   check(view.drawable.color()==0xff123456 && view.assignments==2,"recycled row reflects theme");
  }
  context.theme.values.clear();context.theme.values.put(0x7f01001a,new int[]{0,0xffabcdef});
  ImageView fallback=new ImageView(context,new Drawable());FolderPathUi.tintEditIcon(fallback);
  check(fallback.drawable.color()==0xffabcdef,"menuIconColor fallback");
  context.theme.values.clear();ImageView unresolved=new ImageView(context,new Drawable());FolderPathUi.tintEditIcon(unresolved);
  check(unresolved.assignments==0,"unresolved theme keeps original");
  ImageView missing=new ImageView(context,null);FolderPathUi.tintEditIcon(missing);check(missing.assignments==0,"missing drawable is safe");
  System.out.println("PASS folder UI host checks="+checks);
 }
}'''
    }
    for relative, text in fixtures.items():
        write(source, relative, text)
    helper = ROOT / 'src/com/alensw/jni/FolderPathUi.java'
    classes = args.work_dir / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    compile_result = subprocess.run(['javac', '--release', '8', '-encoding', 'UTF-8', '-d', str(classes),
                                     *map(str, source.rglob('*.java')), str(helper)], capture_output=True, text=True)
    check('helper compiles with exact Android API signatures', compile_result.returncode == 0)
    if compile_result.returncode:
        print(compile_result.stderr, file=sys.stderr)
    host_result = subprocess.run(['java', '-cp', str(classes), 'FolderPathUiProbe'], capture_output=True, text=True) if compile_result.returncode == 0 else None
    check('host title and icon behavior', host_result is not None and host_result.returncode == 0)
    if host_result:
        print(host_result.stdout.strip())
        if host_result.returncode:
            print(host_result.stderr, file=sys.stderr)
    report = {'passed': all(item['passed'] for item in checks), 'checks': checks, 'patch': patch,
              'host_output': host_result.stdout.strip() if host_result else None,
              'scope': 'actual external smali patch + unchanged path callbacks; helper with host Android doubles, not device rendering',
              'input_sha256': {name: hashlib.sha256(text.encode()).hexdigest() for name, text in original.items()},
              'output_sha256': {name: hashlib.sha256(text.encode()).hexdigest() for name, text in first.items()},
              'helper_sha256': hashlib.sha256(helper.read_bytes()).hexdigest()}
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'passed': report['passed'], 'checks': len(checks),
                      'failures': [item['name'] for item in checks if not item['passed']]}, ensure_ascii=False))
    raise SystemExit(0 if report['passed'] else 1)


if __name__ == '__main__':
    main()
