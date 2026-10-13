#!/usr/bin/env python3
"""Run production EXIF Java tests with narrow, fake host APIs; no Android SDK needed."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="quickpic-exif-") as temporary:
    sources = sorted((ROOT / "tests/exif_host").rglob("*.java"))
    sources += [ROOT / "src/com/alensw/jni/QuickPicExif.java", ROOT / "tests/QuickPicExifHostTest.java"]
    subprocess.run(["javac", "--release", "8", "-Xlint:-options", "-d", temporary, *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", temporary, "com.alensw.jni.QuickPicExifHostTest"], check=True)
