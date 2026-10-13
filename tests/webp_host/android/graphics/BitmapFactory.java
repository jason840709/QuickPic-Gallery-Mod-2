package android.graphics;
/** Decodes only the synthetic RAW subchunk. Pillow separately verifies real codec payloads. */
public final class BitmapFactory {
 public static int DECODES=0, FAIL_AT=-1; public static volatile java.util.concurrent.CountDownLatch ENTER, CONTINUE; public static boolean WRONG_SIZE=false;
 public static Bitmap decodeByteArray(byte[] b,int offset,int length){
  if(DECODES++==FAIL_AT)return null;
  if(ENTER!=null){ENTER.countDown();try{CONTINUE.await();}catch(InterruptedException e){Thread.currentThread().interrupt();return null;}}
  int end=offset+length;
  for(int p=offset+12;p+8<=end;){int size=u32(b,p+4);if(size<0||p+8L+size>end)return null;
   if(b[p]=='R'&&b[p+1]=='A'&&b[p+2]=='W'&&b[p+3]==' '){int w=(b[p+8]&255)|((b[p+9]&255)<<8),h=(b[p+10]&255)|((b[p+11]&255)<<8);Bitmap bitmap=Bitmap.createBitmap(WRONG_SIZE?w+1:w,h,Bitmap.Config.ARGB_8888);for(int i=0;i<w*h;i++)bitmap.pixels[i]=u32(b,p+12+4*i);return bitmap;}p+=8+size+(size&1);
  }return null;
 }
 private static int u32(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|(b[p+3]<<24);}
}
