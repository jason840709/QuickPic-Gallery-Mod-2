#!/usr/bin/env python3
"""Check the OEM system identity boundary using the public Android SDK stubs."""
import argparse, os, shutil, subprocess, tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--android-jar',required=True,type=Path);args=p.parse_args()
java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac')).parent
with tempfile.TemporaryDirectory(prefix='quickpic-boox-host-') as work:
 subprocess.run([str(java/'javac'),'--release','8','-encoding','UTF-8','-cp',str(args.android_jar),'-d',work,*map(str,(ROOT/'src').rglob('*.java')),str(Path(__file__).with_name('BooxIdentityCheck.java'))],check=True)
 subprocess.run([str(java/'java'),'-cp',work+os.pathsep+str(args.android_jar),'com.alensw.jni.BooxIdentityCheck'],check=True)
