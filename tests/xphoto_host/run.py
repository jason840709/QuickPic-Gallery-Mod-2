#!/usr/bin/env python3
"""Host route regression: compile production helper against fake Android APIs.

This checks routing and Intent contents, not Android/OEM on-device behavior.
Run: python3 tests/xphoto_host/run.py [--android-jar PATH]
"""
import argparse
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / "src/com/alensw/jni/ScreensaverOpener.java"

STUBS = {
    "android/app/Activity.java": """package android.app;
public class Activity extends android.content.Context {}""",
    "android/content/ComponentName.java": """package android.content;
public class ComponentName {
    public String pkg, name;
    public ComponentName(String p, String n) { pkg=p; name=n; }
    public String getPackageName() { return pkg; }
}""",
    "android/content/ClipData.java": """package android.content;
public class ClipData {
    public android.net.Uri uri;
    public static ClipData newRawUri(String label, android.net.Uri uri) {
        ClipData c=new ClipData(); c.uri=uri; return c;
    }
}""",
    "android/content/Context.java": """package android.content;
public class Context {
    public android.content.pm.PackageManager pm=new android.content.pm.PackageManager();
    public java.util.List<Intent> attempts=new java.util.ArrayList<Intent>();
    public boolean failOriginal, failPackage, failChooser;
    public android.content.pm.PackageManager getPackageManager() { return pm; }
    public String getPackageName() { return "test.fake"; }
    public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
    public Context getApplicationContext() { return this; }
    public void startActivity(Intent i) {
        attempts.add(i);
        if (i.component!=null && failOriginal || i.pkg!=null && failPackage
                || "chooser".equals(i.action) && failChooser)
            throw new SecurityException("launch rejected");
    }
}""",
    "android/content/Intent.java": """package android.content;
public class Intent {
    public static final String ACTION_VIEW="view";
    public static final int FLAG_ACTIVITY_CLEAR_TOP=0x4000000,
        FLAG_ACTIVITY_NEW_TASK=0x10000000, FLAG_GRANT_READ_URI_PERMISSION=1;
    public String action, pkg, type;
    public ComponentName component;
    public android.net.Uri data;
    public ClipData clip;
    public int flags;
    public java.util.Map<String,Object> extras=new java.util.HashMap<String,Object>();
    public Intent(String a) { action=a; }
    public Intent(Intent i) {
        action=i.action; pkg=i.pkg; type=i.type; component=i.component;
        data=i.data; clip=i.clip; flags=i.flags; extras.putAll(i.extras);
    }
    public Intent setComponent(ComponentName c) { component=c; return this; }
    public ComponentName getComponent() { return component; }
    public Intent putExtra(String k, java.io.Serializable v) { extras.put(k,v); return this; }
    public Intent putExtra(String k, int v) { extras.put(k,v); return this; }
    public Intent addFlags(int f) { flags|=f; return this; }
    public Intent setDataAndType(android.net.Uri d, String t) { data=d; type=t; return this; }
    public void setClipData(ClipData c) { clip=c; }
    public ClipData getClipData() { return clip; }
    public Intent setPackage(String p) { pkg=p; return this; }
    public static Intent createChooser(Intent view, String title) {
        Intent i=new Intent("chooser"); i.extras.put("payload",view); return i;
    }
}""",
    "android/content/pm/ApplicationInfo.java": """package android.content.pm;
public class ApplicationInfo { public boolean enabled=true; }""",
    "android/content/pm/ActivityInfo.java": """package android.content.pm;
public class ActivityInfo {
    public String packageName="com.ebook.imagemanager", name="com.xrz.photo.ImageFolderSubAt";
    public boolean enabled=true, exported=true;
    public ApplicationInfo applicationInfo=new ApplicationInfo();
}""",
    "android/content/pm/ResolveInfo.java": """package android.content.pm;
public class ResolveInfo { public ActivityInfo activityInfo=new ActivityInfo(); }""",
    "android/content/pm/PackageManager.java": """package android.content.pm;
public class PackageManager {
    public static final int MATCH_DISABLED_COMPONENTS=512, MATCH_DEFAULT_ONLY=65536,
        COMPONENT_ENABLED_STATE_ENABLED=1, COMPONENT_ENABLED_STATE_DEFAULT=0;
    public ActivityInfo original=new ActivityInfo();
    public ResolveInfo samePackage;
    public java.util.List<ResolveInfo> viewers=new java.util.ArrayList<ResolveInfo>();
    public int appState, componentState, queryCalls;
    public static class NameNotFoundException extends Exception {}
    public ActivityInfo getActivityInfo(android.content.ComponentName c,int f)
            throws NameNotFoundException {
        if(original==null) throw new NameNotFoundException(); return original;
    }
    public int getApplicationEnabledSetting(String p) { return appState; }
    public int getComponentEnabledSetting(android.content.ComponentName c) { return componentState; }
    public ResolveInfo resolveActivity(android.content.Intent i,int f) { return samePackage; }
    public java.util.List<ResolveInfo> queryIntentActivities(android.content.Intent i,int f) {
        queryCalls++; return viewers;
    }
}""",
    "android/net/Uri.java": """package android.net;
public class Uri {
    public String value;
    public Uri(String v) { value=v; }
    public String getScheme() { return value.substring(0,value.indexOf(':')); }
    public String toString() { return value; }
}""",
    "android/os/Looper.java": """package android.os;
public class Looper {
    static Looper main=new Looper();
    public static Looper myLooper() { return main; }
    public static Looper getMainLooper() { return main; }
}""",
    "android/os/Handler.java": """package android.os;
public class Handler {
    public Handler(Looper l) {}
    public boolean post(Runnable r) { r.run(); return true; }
}""",
    "android/util/Log.java": """package android.util;
public class Log {
    public static int i(String t,String m) { return 0; }
    public static int w(String t,String m,Throwable e) { return 0; }
    public static int e(String t,String m,Throwable e) { return 0; }
}""",
    "android/widget/Toast.java": """package android.widget;
public class Toast {
    public static final int LENGTH_LONG=1;
    public static String last;
    public static Toast makeText(android.content.Context c,CharSequence s,int l) {
        last=s.toString(); return new Toast();
    }
    public void show() {}
}""",
    "test/fake/FileProvider.java": """package test.fake;
public class FileProvider {
    public static boolean fail;
    public static android.net.Uri a(String p) {
        if(fail) throw new IllegalStateException("provider failed");
        return new android.net.Uri("content://test.fake.FileProvider"+p);
    }
}""",
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command):
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode:
        print(result.stdout)
        print(result.stderr)
        raise subprocess.CalledProcessError(result.returncode, command)
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-jar", type=Path)
    args = parser.parse_args()
    source_hash = sha(SOURCE)
    with tempfile.TemporaryDirectory(prefix="xphoto-final-host-") as temp:
        temp = Path(temp)
        for name, source in STUBS.items():
            path = temp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
        fake_compile = ["javac", "--release", "8", "-Xlint:-options", "-d", str(temp / "classes")]
        fake_compile += [str(temp / name) for name in STUBS]
        fake_compile += [str(SOURCE), str(HERE / "TestOpener.java")]
        run(fake_compile)
        png = temp / "saved.png"
        png.write_bytes(b"PNG!")
        output = run(["java", "-cp", str(temp / "classes"), "TestOpener", str(png)])
        assert png.read_bytes() == b"PNG!", "Saved file was changed"
        if args.android_jar:
            api_compile = ["javac", "--release", "8", "-Xlint:-options", "-classpath",
                           str(args.android_jar), "-d", str(temp / "android-api"), str(SOURCE)]
            run(api_compile)
    assert sha(SOURCE) == source_hash, "Production source changed during tests"
    scenarios = []
    for line in output.splitlines():
        if line.startswith("PASS|"):
            _, name, count = line.split("|")
            scenarios.append({"name": name, "assertions": int(count), "passed": True})
    assert len(scenarios) == 15, output
    result = {
        "passed": True,
        "source": str(SOURCE),
        "source_sha256": source_hash,
        "harness": str(HERE / "run.py"),
        "harness_sha256": sha(HERE / "run.py"),
        "test_source_sha256": sha(HERE / "TestOpener.java"),
        "rerun_command": "python3 tests/xphoto_host/run.py",
        "android_api_compile_passed": bool(args.android_jar),
        "scenario_count": len(scenarios),
        "assertion_count": sum(case["assertions"] for case in scenarios),
        "scenarios": scenarios,
        "saved_file_unchanged": True,
        "production_source_unchanged": True,
        "limitations": ["Host fake Android APIs verify routing and Intent construction only; Android/OEM chooser execution requires device validation."],
    }
    print(output, end="")
    print(json.dumps({"passed": True, "scenarios": len(scenarios),
                      "assertions": result["assertion_count"],
                      "android_api_compile_passed": bool(args.android_jar)}, indent=2))


if __name__ == "__main__":
    main()
