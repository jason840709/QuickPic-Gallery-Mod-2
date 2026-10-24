#!/usr/bin/env python3
"""Build a locally supplied, supported base APK with the public maintenance layer."""
import argparse
import hashlib
import importlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE_SHA256 = "8a707e4285e22e668d50305864d05eb968ea584a56ec2d89e1ce50336fd5c605"
RELEASE_BASE_SHA256 = "1eb348eab476fda8f4be42b64c438767d60a65daa61aebc9b6c818c0bdf10405"
MAINTAINED_BASE_SHA256 = (RELEASE_BASE_SHA256, "62cd0745a358ff11c8f098f2cbe4d5cd7ecabfe0102a490a66b32d68cc613947", "f16bebaeb1e71a62668e7971a2c104f1760cf4ac42142a3f06cf6765950f44bf", "3f9e83b3f6e263a2e4e62dead7e1c50bf2b625681850aa206a1301fa369acab8")
PACKAGE = "io.github.jason840709.quickpic"
VERSION_NAME = "11.0.15"
VERSION_CODE = 11015001


def sha(path):
    with Path(path).open("rb") as stream:
        result = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def validate_base(path):
    actual = sha(path)
    if actual not in (BASE_SHA256, *MAINTAINED_BASE_SHA256):
        raise ValueError("Unsupported base APK SHA-256: " + actual)
    return actual


def run(command):
    # Never log command arguments: signing password-file/keystore paths are private.
    process = subprocess.run([str(x) for x in command], text=True, capture_output=True)
    if process.returncode:
        raise RuntimeError(Path(str(command[0])).name + " failed:\n" + process.stdout + process.stderr)


def apktool_command(tool):
    return ["java", "-jar", tool] if str(tool).lower().endswith(".jar") else [tool]


def compile_helpers(args, work):
    classes, dex = work / "classes", work / "dex"
    classes.mkdir(); dex.mkdir()
    sources = sorted((ROOT / "src").rglob("*.java"))
    if not sources:
        raise ValueError("No maintenance Java sources")
    run(["javac", "--release", "8", "-cp", args.android_jar, "-d", classes, *sources])
    run(["java", "-cp", args.d8_jar, "com.android.tools.r8.D8", "--min-api", "21",
         "--lib", args.android_jar, "--output", dex, *sorted(classes.rglob("*.class"))])
    if sorted(p.name for p in dex.glob("*.dex")) != ["classes.dex"]:
        raise ValueError("Expected one helper DEX")
    helper = work / "helper.apk"
    with zipfile.ZipFile(args.base_apk) as source, zipfile.ZipFile(helper, "w") as output:
        for name in ("AndroidManifest.xml", "resources.arsc"):
            output.writestr(name, source.read(name))
        output.writestr("classes.dex", (dex / "classes.dex").read_bytes())
    decoded = work / "helper"
    run([*apktool_command(args.apktool), "d", "-r", "-o", decoded, helper])
    return decoded / "smali"


def merge_helpers(decoded, helpers, maintained=False):
    source = decoded / "smali/com/alensw/jni"
    replacement = source / "JniUtils.smali"
    if not replacement.is_file():
        raise ValueError("Base has no expected JniUtils class")
    if maintained:
        # The pinned release contains only this repository's classes in jni/.
        # Replace the entire layer, including obsolete generated inner classes.
        shutil.rmtree(source)
    else:
        replacement.unlink()
    for path in sorted(helpers.rglob("*.smali")):
        destination = decoded / "smali" / path.relative_to(helpers)
        if destination.exists():
            raise ValueError("Unexpected helper class collision: " + str(path.relative_to(helpers)))
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, destination)


def verify_output(path):
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        if any(name.startswith("lib/") for name in names):
            raise ValueError("Output still contains legacy native libraries")
        if "classes.dex" not in names or "resources.arsc" not in names:
            raise ValueError("Incomplete APK")


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ("base-apk", "android-jar", "d8-jar", "apksigner-jar", "zipalign",
                 "apktool", "keystore", "password-file", "out"):
        p.add_argument("--" + name, required=True, type=Path)
    p.add_argument("--package", default=PACKAGE,
                   help="Application ID; use a different ID for locally signed test builds")
    return p


def build(args):
    actual_base = validate_base(args.base_apk)
    maintained = actual_base in MAINTAINED_BASE_SHA256
    for name in ("android_jar", "d8_jar", "apksigner_jar", "keystore", "password_file"):
        if not getattr(args, name).is_file():
            raise ValueError("Missing --" + name.replace("_", "-"))
    args.out = args.out.resolve()
    if args.out.exists():
        raise ValueError("Output already exists; choose a new --out path")
    if ROOT == args.out or ROOT in args.out.parents:
        raise ValueError("Keep generated APKs outside the public source tree")
    # This import must succeed before decoding or signing. The release normalizer
    # and hook module are part of the source package, not optional steps.
    core = importlib.import_module("patch_core")
    normalize = importlib.import_module("patch_normalize")
    viewer = importlib.import_module("patch_viewer")
    animation = importlib.import_module("patch_animation")
    defaults = importlib.import_module("patch_defaults")
    links = importlib.import_module("check_local_links")
    dates = importlib.import_module("patch_dates")
    scan = importlib.import_module("patch_scan")
    index = importlib.import_module("patch_index")
    folder_ui = importlib.import_module("patch_folder_ui")
    deletion = importlib.import_module("patch_delete")
    file_deletion = importlib.import_module("patch_file_delete")
    timing = importlib.import_module("patch_delete_timing")
    xphoto = importlib.import_module("patch_xphoto")
    about = importlib.import_module("patch_about")
    reader = importlib.import_module("patch_reader")
    screensaver = importlib.import_module("patch_screensaver")
    with tempfile.TemporaryDirectory(prefix="quickpic-build-") as temporary:
        work = Path(temporary)
        decoded = work / "decoded"
        run([*apktool_command(args.apktool), "d", "-o", decoded, args.base_apk])
        helpers = compile_helpers(args, work)
        merge_helpers(decoded, helpers, maintained)
        if not maintained:
            core.patch(decoded)
        viewer.patch(decoded)
        animation.patch(decoded)
        defaults.patch(decoded)
        xphoto.patch(decoded)
        about.patch(decoded)
        dates.patch(decoded)
        scan.patch(decoded)
        index.patch(decoded)
        folder_ui.patch(decoded)
        deletion.patch(decoded)
        file_deletion.patch(decoded)
        timing.patch(decoded)
        reader.patch(decoded)
        screensaver.patch(decoded)
        normalize.patch(decoded, package=args.package, version_name=VERSION_NAME,
                        version_code=VERSION_CODE, app_name="快圖")
        startup_links = links.check_hidden_default(decoded)
        resolver = links.LocalLinks(decoded)
        new_links = []
        for smali in decoded.glob("smali*/**/*.smali"):
            source = smali.read_text()
            hooked = "\n".join(line for line in source.splitlines()
                if any("Lcom/alensw/jni/" + helper + ";" in line for helper in
                       ("MediaDates", "DateSort", "ScanPriority", "ScanPriority$Pending", "FolderPathUi", "MenuLayout", "ViewerControls", "FolderDelete", "FolderDelete$Result", "FileDelete", "FastMediaDelete", "MediaCleanup", "DeleteTiming", "DeleteTiming$Report", "ReaderMode", "ReaderMode$Host", "ReaderMode$Frame", "ScreensaverExport", "ScreensaverImage", "ScreensaverPreference")) or "->quickpic" in line)
            if hooked:
                new_links.append(resolver.check(hooked))
        unsigned, aligned, signed = (work / name for name in ("unsigned.apk", "aligned.apk", "signed.apk"))
        run([*apktool_command(args.apktool), "b", "-o", unsigned, decoded])
        verify_output(unsigned)
        run([args.zipalign, "-f", "4", unsigned, aligned])
        run(["java", "-jar", args.apksigner_jar, "sign", "--ks", args.keystore,
             "--ks-pass", "file:" + str(args.password_file.resolve()),
             "--out", signed, aligned])
        run(["java", "-jar", args.apksigner_jar, "verify", "--verbose", signed])
        run([args.zipalign, "-c", "4", signed])
        verify_output(signed)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(signed, args.out)
    print(json.dumps({"status": "built; on-device verification still required",
                      "package": args.package, "versionName": VERSION_NAME,
                      "versionCode": VERSION_CODE, "base_sha256": actual_base,
                      "output_sha256": sha(args.out), "startup_links": startup_links}, indent=2))


if __name__ == "__main__":
    try:
        build(parser().parse_args())
    except (OSError, ValueError, RuntimeError, ImportError) as error:
        raise SystemExit(str(error)) from None
