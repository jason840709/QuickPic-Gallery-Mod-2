"""Execute production geometry; preference UI is tested on Android."""
from pathlib import Path
import os,subprocess,tempfile
r=Path(__file__).resolve().parents[2];j=Path(os.environ['JAVA_HOME'])/'bin'
with tempfile.TemporaryDirectory()as d:
 subprocess.run([str(j/'javac'),'--release','8','-d',d,str(r/'src/com/alensw/jni/TapPageLayout.java'),str(Path(__file__).with_name('LayoutProbe.java'))],check=True)
 subprocess.run([str(j/'java'),'-cp',d,'LayoutProbe'],check=True)
