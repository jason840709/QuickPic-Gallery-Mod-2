#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Check decoded defaults and execute actual extracted transition/auth methods.

Supply an APK's external JADX sources and apktool decode. The harness is saved
only in --work-dir; no historical App source is bundled in this repository.
Android preferences, lifecycle collaborators and animation objects are doubles.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from check_local_links import check_hidden_default
A = '{http://schemas.android.com/apk/res/android}'


def extract(text, marker):
    start = text.index(marker)
    brace = text.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


def write(root, relative, text):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', type=Path, required=True)
    parser.add_argument('--decoded', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--output-json', type=Path)
    args = parser.parse_args()
    args.work_dir.mkdir(parents=True, exist_ok=True)
    failures = []
    checks = []
    def check(name, good):
        checks.append({'name': name, 'passed': bool(good)})
        if not good:
            failures.append(name)
    app_files = list(args.sources.glob('**/QuickApp.java'))
    if len(app_files) != 1:
        raise SystemExit('Expected one external QuickApp source')
    app_source = app_files[0].read_text()
    try:
        linkage = check_hidden_default(args.decoded)
        check('exact DEX app member descriptors resolve', True)
    except (ValueError, OSError) as error:
        linkage = {'error': str(error)}
        check('exact DEX app member descriptors resolve', False)
    app_pkg = re.search(r'package ([^;]+);', app_source).group(1)
    app_descriptor = 'L' + app_pkg.replace('.', '/') + '/QuickApp;'
    app_smali = (args.decoded / ('smali/' + app_pkg.replace('.', '/') + '/QuickApp.smali')).read_text()
    init = extract(app_source, 'private boolean g()')
    foreground = extract(app_source, 'protected void b()')
    check('cold-start initialization hook', 'applyHiddenDefault();' in init)
    check('foreground lifecycle hook', 'applyHiddenDefault();' in foreground)
    lifecycle = (args.sources / (app_pkg.replace('.', '/') + '/ba.java')).read_text()
    check('first foreground Activity triggers QuickApp.b()', re.search(r'onActivityStarted.*?incrementAndGet\(\) == 1.*?\.b\(\)', lifecycle, re.S) is not None)
    cleanup = extract(app_source, 'protected void d()')
    check('existing exit cleanup still hides items', re.search(r'q\.f\d+b = false;', cleanup) is not None)
    guard = (args.sources / 'com/alensw/ui/a/x.java').read_text()
    check('existing password dialog kept', 'string.equals(QuickApp.y)' in guard and 'runnable.run();' in guard and ').show();' in guard)
    arrays = ET.parse(args.decoded / 'res/values/arrays.xml').getroot()
    entries = arrays.find("string-array[@name='slideshow_animations']")
    check('index six is None', entries is not None and entries[6].text == '@string/animation_none')
    xmls = sorted((args.decoded / 'res').glob('xml*/main_pref.xml'))
    check('all four platform preference variants present', len(xmls) == 4)
    for path in xmls:
        pref = next(node for node in ET.parse(path).getroot().iter() if node.get(A + 'key') == 'slideshow_animation')
        check('None default in ' + str(path.relative_to(args.decoded)), pref.get(A + 'defaultValue') == '6')
    for ctl, reg, field in [('as', 'v4', 'P'), ('dp', 'v3', 'Q')]:
        text = (args.decoded / ('smali/com/alensw/ui/c/' + ctl + '.smali')).read_text()
        pattern = (r'const-string v0, "slideshow_animation"\s+const-string ' + reg + r', "6"\s+'
                   r'invoke-interface \{[^}]+\}, Landroid/content/SharedPreferences;->getString[^\n]+\s+'
                   r'move-result-object v0\s+const/4 ' + reg + r', 0x6\s+'
                   r'invoke-static \{v0, ' + reg + r'\}, Lcom/alensw/b/l/b;->a\(Ljava/lang/String;I\)I\s+'
                   r'move-result v0\s+iput v0, p0, Lcom/alensw/ui/c/' + ctl + ';->' + field + ':I')
        check('absent preference defaults to six; stored selection is read: ' + ctl, re.search(pattern, text) is not None)
    helper = ('public static void applyHiddenDefault() {}' if 'public static void applyHiddenDefault()' not in app_source
              else extract(app_source, 'public static void applyHiddenDefault()'))
    # JADX may hold QuickApp.q in a typed local; normalize presentation
    # aliases only, preserving every condition and side effect.
    helper = helper.replace('com.alensw.a.s ', 'Folder ')
    helper = re.sub(r'\.f\d+b', '.visible', helper)
    foreground = re.sub(r'q\.f\d+b', 'q.visible', foreground)
    callback = (args.sources / 'com/alensw/ui/a/ac.java').read_text()
    success = extract(callback, 'public boolean a(PasswordView passwordView, String str)')
    success = re.sub(r'this\.f\d+a', 'this.secret', success)
    success = re.sub(r'this\.f\d+b', 'this.isPassword', success)
    check('authenticated callback enables default after original password success', 'QuickApp.applyHiddenDefault();' in success)
    as_method = extract((args.sources / 'com/alensw/ui/c/as.java').read_text(), 'private void w()')
    dp_method = extract((args.sources / 'com/alensw/ui/c/dp.java').read_text(), 'private void z()')
    harness = r'''import java.util.ArrayList;
import java.util.Collections;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import com.alensw.ui.view.PasswordView;
public class DefaultsProbe {
    static int checks;
    static void eq(Object want,Object got,String title){checks++;if(!want.equals(got))throw new AssertionError(title+": expected "+want+" got "+got);}
    static final class Folder { boolean visible; int starts; void a(){starts++;} }
    static final class Cache {int clears;boolean a(boolean ignored){clears++;return false;} }
    static final class Services {void c(){} }
    static final class QuickApp {
        static Object B=new Object();static Folder q=new Folder();static Cache o=new Cache();static Services r=new Services();static String y="";
        HIDDEN
        FOREGROUND
    }
    static final class x {static int invalid;static void a(PasswordView ignored){invalid++;} }
    static final class PasswordCallback {
        String secret;boolean isPassword=true;Runnable c,d;
        CALLBACK
    }
    static final class Listener {int completions;Object last;void onAnimationEnd(Object a){completions++;last=a;} }
    static final class Animation {void setAnimationListener(Listener ignored){} }
    static final class View {int started;void startAnimation(Animation a){started++;} }
    static final class As {
        int P,T;ArrayList Z;Listener ab=new Listener();View f=new View(),g=new View();ArrayList<Integer> requested=new ArrayList<>();
        Animation b(int type,boolean incoming){requested.add(type);return new Animation();}
        AS_METHOD
        void run(int choice){P=choice;w();}
    }
    static final class Dp {
        int Q,U;ArrayList aa;Listener ac=new Listener();View f=new View(),g=new View();ArrayList<Integer> requested=new ArrayList<>();
        Animation b(int type,boolean incoming){requested.add(type);return new Animation();}
        DP_METHOD
        void run(int choice){Q=choice;z();}
    }
    public static void main(String[]args){
        if(args.length>0){As as=new As();as.run(6);eq(0,as.f.started+as.g.started,"as None immediate completion");eq(1,as.ab.completions,"as None callback once");System.out.println("None dispatch checks="+checks);return;}
        for(boolean protection:new boolean[]{false,true})for(String secret:new String[]{"","1234"})for(String authenticated:new String[]{"","wrong","1234"})for(boolean previous:new boolean[]{false,true}){
            PreferenceManager.Prefs prefs=PreferenceManager.prefs;prefs.protectedHidden=protection;prefs.password=secret;
            QuickApp.y=authenticated;QuickApp.q.visible=previous;QuickApp.o.clears=0;
            boolean wanted=!protection||secret.isEmpty()||secret.equals(authenticated);
            QuickApp.applyHiddenDefault();eq(wanted,QuickApp.q.visible,"password policy");eq(wanted==previous?0:1,QuickApp.o.clears,"cache changes once");
            QuickApp.applyHiddenDefault();eq(wanted==previous?0:1,QuickApp.o.clears,"same default no extra invalidation");
        }
        PreferenceManager.prefs.protectedHidden=true;PreferenceManager.prefs.password="";QuickApp.q.visible=false;
        QuickApp app=new QuickApp();app.b();eq(true,QuickApp.q.visible,"warm start restores show hidden");
        QuickApp.q.visible=false;app.b();eq(true,QuickApp.q.visible,"next opening restores user-hidden session");
        PreferenceManager.prefs.password="1234";QuickApp.y="";QuickApp.q.visible=true;app.b();eq(false,QuickApp.q.visible,"background return relocks protected hidden");
        PasswordCallback cb=new PasswordCallback();cb.secret="1234";final int[] ran={0};cb.c=()->ran[0]++;
        cb.a(new PasswordView(),"wrong");eq(false,QuickApp.q.visible,"wrong password stays hidden");eq(0,ran[0],"wrong password cannot run protected action");
        cb.a(new PasswordView(),"1234");eq(true,QuickApp.q.visible,"correct original authentication enables hidden");eq(1,ran[0],"existing protected action still runs");
        for(int choice=0;choice<=6;choice++){
            As as=new As();Dp dp=new Dp();as.run(choice);dp.run(choice);
            if(choice==6){eq(0,as.f.started+as.g.started,"as None has no animation");eq(1,as.ab.completions,"as completes once");eq(0,dp.f.started+dp.g.started,"dp None has no animation");eq(1,dp.ac.completions,"dp completes once");}
            else{eq(2,as.f.started+as.g.started,"as stored animation retained");eq(2,dp.f.started+dp.g.started,"dp stored animation retained");eq(0,as.ab.completions,"as no early completion");eq(0,dp.ac.completions,"dp no early completion");
                for(Integer type:as.requested)eq(true,choice==0?type>=1&&type<=5:type==choice,"as chosen transition");for(Integer type:dp.requested)eq(true,choice==0?type>=1&&type<=5:type==choice,"dp chosen transition");}
        }
        System.out.println("defaults behavioral checks="+checks);
    }
}
'''
    for marker, source in [('HIDDEN', helper), ('FOREGROUND', foreground), ('CALLBACK', success), ('AS_METHOD', as_method), ('DP_METHOD', dp_method)]:
        harness = harness.replace(marker, source)
    write(args.work_dir, 'DefaultsProbe.java', harness)
    write(args.work_dir, 'android/content/SharedPreferences.java', 'package android.content; public interface SharedPreferences {boolean getBoolean(String k,boolean d);String getString(String k,String d);}')
    write(args.work_dir, 'android/preference/PreferenceManager.java', '''package android.preference;
import android.content.SharedPreferences;
public class PreferenceManager {public static Prefs prefs=new Prefs();public static SharedPreferences getDefaultSharedPreferences(Object ignored){return prefs;}
public static class Prefs implements SharedPreferences{public boolean protectedHidden=true;public String password="";public boolean getBoolean(String k,boolean d){return k.equals("protect_hidden")?protectedHidden:d;}public String getString(String k,String d){return k.equals("password")?password:d;}}}''')
    write(args.work_dir, 'com/alensw/ui/view/PasswordView.java', 'package com.alensw.ui.view; public class PasswordView {}')
    compiled = subprocess.run(['javac', '-d', str(args.work_dir / 'classes'), *map(str, args.work_dir.rglob('*.java'))], capture_output=True, text=True)
    check('actual extracted methods compile', compiled.returncode == 0)
    run = None
    none_run = None
    if compiled.returncode == 0:
        run = subprocess.run(['java', '-cp', str(args.work_dir / 'classes'), 'DefaultsProbe'], capture_output=True, text=True)
        check('actual extracted startup/password/transition behavior', run.returncode == 0)
        none_run = subprocess.run(['java', '-cp', str(args.work_dir / 'classes'), 'DefaultsProbe', 'none'], capture_output=True, text=True)
        check('actual modern None dispatcher starts zero animations', none_run.returncode == 0)
    report = {'linkage': linkage, 'checks': checks, 'failures': failures, 'passed': not failures,
              'host_output': run.stdout if run else '', 'host_error': run.stderr if run else compiled.stderr,
              'none_output': none_run.stdout if none_run else '', 'none_error': none_run.stderr if none_run else '',
              'scope': 'Decoded preference XML/control-flow plus actual JADX methods on host doubles; not Android UI playback',
              'methods_sha256': {key:hashlib.sha256(value.encode()).hexdigest() for key,value in {'helper':helper,'foreground':foreground,'callback':success,'as_transition':as_method,'dp_transition':dp_method}.items()}}
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'passed':report['passed'], 'checks':len(checks), 'failures':failures,'host_output':report['host_output'], 'host_error':report['host_error']},ensure_ascii=False))
    raise SystemExit(0 if not failures else 1)

if __name__ == '__main__':
    main()
