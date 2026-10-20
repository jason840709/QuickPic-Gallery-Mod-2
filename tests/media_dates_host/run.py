"""Exercise the real private date journal with Android storage API doubles."""
import pathlib, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
STUBS={
"android/content/Context.java": "package android.content; import java.io.File; public class Context { private File dir; public Context(File f){dir=f;} public File getFilesDir(){return dir;} }",
"android/os/Build.java": "package android.os; public class Build { public static class VERSION { public static int SDK_INT=21; } }",
"android/util/AtomicFile.java": "package android.util; import java.io.*; public class AtomicFile { final File f,b; public AtomicFile(File v){f=v;b=new File(v.toString()+\".bak\");} public FileInputStream openRead() throws IOException {if(b.exists()){f.delete();b.renameTo(f);}return new FileInputStream(f);} public FileOutputStream startWrite() throws IOException {if(f.exists())f.renameTo(b);return new FileOutputStream(f);} public void finishWrite(FileOutputStream o) throws IOException {o.getFD().sync();o.close();b.delete();}public void failWrite(FileOutputStream o){try{o.close();}catch(IOException e){}f.delete();b.renameTo(f);} }"
}
with tempfile.TemporaryDirectory(prefix="quickpic-date-test-") as d:
 p=pathlib.Path(d)
 files=[]
 for name,text in STUBS.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text);files.append(f)
 files += [ROOT/"src/com/alensw/jni/MediaDates.java",pathlib.Path(__file__).with_name("MediaDatesTest.java")]
 subprocess.run(["javac","--release","8","-d",str(p/"classes"),*map(str,files)],check=True)
 subprocess.run(["java","-cp",str(p/"classes"),"MediaDatesTest",str(p/"storage")],check=True)
