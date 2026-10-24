#!/usr/bin/env python3
"""Renderer host checks using Java2D bitmap fixtures; Android decoder behavior is separate."""
import argparse
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SOURCE = ROOT / "src/com/alensw/jni/ScreensaverImage.java"
STUBS = {
"android/graphics/Rect.java": """package android.graphics;
public class Rect { public int left,top,right,bottom;
 public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public int width(){return right-left;} public int height(){return bottom-top;}
}""",
"android/graphics/Matrix.java": """package android.graphics;
public class Matrix { public float[] values;
 public void setValues(float[] v){values=v.clone();}
}""",
"android/graphics/Paint.java": """package android.graphics;
public class Paint {public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2,DITHER_FLAG=4;
 public Paint(int flags){} }""",
"android/graphics/Bitmap.java": """package android.graphics;
import java.awt.image.BufferedImage; import java.io.*; import javax.imageio.ImageIO;
public class Bitmap {
 public enum Config {ARGB_8888} public enum CompressFormat {PNG}
 public static volatile String compression="ok";
 public static final java.util.concurrent.atomic.AtomicInteger live=new java.util.concurrent.atomic.AtomicInteger();
 public BufferedImage image; private boolean recycled;
 public Bitmap(BufferedImage i){image=i;live.incrementAndGet();}
 public static Bitmap createBitmap(int w,int h,Config c){
  return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB));}
 public int getWidth(){return image.getWidth();} public int getHeight(){return image.getHeight();}
 public void setHasAlpha(boolean v){}
 public void recycle(){if(!recycled){recycled=true;live.decrementAndGet();image.flush();image=null;}}
 public boolean compress(CompressFormat f,int q,OutputStream stream){
  if("oom".equals(compression)) throw new OutOfMemoryError("injected");
  if("throw".equals(compression)) throw new IllegalStateException("injected");
  try {
   if("false".equals(compression)){stream.write(new byte[]{1,2,3});return false;}
   if("short".equals(compression)){stream.write(new byte[]{1,2,3});return true;}
   if("dimensions".equals(compression)) return ImageIO.write(new BufferedImage(1,1,2),"png",stream);
   if("crc".equals(compression)){
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
    byte[] data=bytes.toByteArray();data[29]^=1;stream.write(data);return true;
   }
   return ImageIO.write(image,"png",stream);
  }catch(IOException e){return false;}
 }
}""",
"android/graphics/Canvas.java": """package android.graphics;
import java.awt.*;import java.awt.geom.AffineTransform;
public class Canvas {
 private Bitmap bitmap; public Canvas(Bitmap b){bitmap=b;}
 public void drawColor(int color){Graphics2D g=bitmap.image.createGraphics();
  g.setColor(new Color(color,true));g.fillRect(0,0,bitmap.getWidth(),bitmap.getHeight());g.dispose();}
 public void drawBitmap(Bitmap image,Matrix matrix,Paint paint){
  Graphics2D g=bitmap.image.createGraphics();
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
  float[] v=matrix.values;
  g.drawImage(image.image,new AffineTransform(v[0],v[3],v[1],v[4],v[2],v[5]),null);g.dispose();
 }
}""",
"android/graphics/FakeImages.java": """package android.graphics;
import java.awt.image.BufferedImage;import java.io.*;import javax.imageio.ImageIO;
public class FakeImages {
 public static final java.util.Map<String,int[]> virtual=new java.util.concurrent.ConcurrentHashMap<String,int[]>();
 public static volatile boolean regionUnsupported,regionNull,fullNull,changedBounds;
 public static volatile Rect lastRegion; public static volatile int lastSample,fullDecodes;
 public static volatile int decoderRecycles;
 public static int[] dimensions(String path) throws IOException {
  int[] v=virtual.get(path);if(v!=null)return v;
  BufferedImage image=ImageIO.read(new File(path));
  if(image==null)throw new IOException("bad image");return new int[]{image.getWidth(),image.getHeight()};
 }
 public static Bitmap decode(String path,Rect r,int sample) throws IOException {
  int[] dimensions=dimensions(path);
  BufferedImage original=virtual.containsKey(path)?null:ImageIO.read(new File(path));
  int w=(r.width()+sample-1)/sample,h=(r.height()+sample-1)/sample;
  BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int rx=Math.min(r.right-1,r.left+x*sample),ry=Math.min(r.bottom-1,r.top+y*sample);
   int color;
   if(original==null){
    int band=(int)((long)rx*8/dimensions[0]);
    int[] colors={0xffff0000,0xff00ff00,0xff00ff00,0xff0000ff,0xff0000ff,0xffffff00,0xffffff00,0xffff00ff};
    color=colors[Math.min(7,band)];
   }else color=original.getRGB(rx,ry);
   result.setRGB(x,y,color);
  }
  return new Bitmap(result);
 }
 public static void reset(){regionUnsupported=false;regionNull=false;fullNull=false;changedBounds=false;
  lastRegion=null;lastSample=0;fullDecodes=0;decoderRecycles=0;Bitmap.compression="ok";}
}""",
"android/graphics/BitmapFactory.java": """package android.graphics;
public class BitmapFactory {
 public static class Options {public boolean inJustDecodeBounds,inScaled;
  public int outWidth=-1,outHeight=-1,inSampleSize=1;public Bitmap.Config inPreferredConfig;}
 public static Bitmap decodeFile(String p,Options o){
  try{int[] d=FakeImages.dimensions(p);o.outWidth=d[0];o.outHeight=d[1];
   if(o.inJustDecodeBounds)return null;FakeImages.fullDecodes++;
   if(FakeImages.fullNull)return null;
   return FakeImages.decode(p,new Rect(0,0,d[0],d[1]),o.inSampleSize);
  }catch(java.io.IOException e){return null;}
 }
}""",
"android/graphics/BitmapRegionDecoder.java": """package android.graphics;
public class BitmapRegionDecoder {
 private String path;private int[] dimensions;
 public static BitmapRegionDecoder newInstance(String path,boolean ignored)throws java.io.IOException{
  if(FakeImages.regionUnsupported)throw new java.io.IOException("region unsupported");
  BitmapRegionDecoder d=new BitmapRegionDecoder();d.path=path;d.dimensions=FakeImages.dimensions(path);return d;}
 public int getWidth(){return dimensions[0]+(FakeImages.changedBounds?1:0);}
 public int getHeight(){return dimensions[1];}
 public Bitmap decodeRegion(Rect r,BitmapFactory.Options o){
  FakeImages.lastRegion=r;FakeImages.lastSample=o.inSampleSize;
  if(FakeImages.regionNull)return null;
  try{return FakeImages.decode(path,r,o.inSampleSize);}catch(java.io.IOException e){return null;}
 }
 public void recycle(){FakeImages.decoderRecycles++;}
}""",
"android/media/ExifInterface.java": """package android.media;
public class ExifInterface {
 public static final String TAG_ORIENTATION="Orientation";public static final int ORIENTATION_NORMAL=1;
 public static final java.util.Map<String,Integer> tags=new java.util.concurrent.ConcurrentHashMap<String,Integer>();
 private String path;public ExifInterface(String p)throws java.io.IOException{path=p;}
 public int getAttributeInt(String tag,int fallback){Integer n=tags.get(path);return n==null?fallback:n;}
}""",
}

def run(command):
    result = subprocess.run(command, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-jar", type=Path)
    args = parser.parse_args()
    before = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory(prefix="screensaver-renderer-host-") as temporary:
        temporary = Path(temporary)
        for name, source in STUBS.items():
            path = temporary / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
        run(["javac", "--release", "8", "-d", str(temporary / "classes")]
            + [str(temporary / name) for name in STUBS]
            + [str(SOURCE), str(HERE / "RendererProbe.java")])
        output = run(["java", "-Djava.awt.headless=true", "-Xmx512m", "-cp",
                      str(temporary / "classes"), "RendererProbe", str(temporary / "fixtures")])
        output += run(["java", "-Djava.awt.headless=true", "-Xmx32m", "-cp",
                       str(temporary / "classes"), "RendererProbe",
                       str(temporary / "low-heap-fixtures"), "low-memory"])
        if args.android_jar:
            run(["javac", "--release", "8", "-Xlint:-options", "-classpath",
                 str(args.android_jar), "-d", str(temporary / "api"), str(SOURCE)])
    assert before == hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    print(output, end="")
    print(json.dumps({"passed": True, "source_sha256": before,
                      "android_api_compile_passed": bool(args.android_jar),
                      "scope": "Java2D-backed host renderer; actual Android decoder/EXIF remains separate"}))

if __name__ == "__main__":
    main()
