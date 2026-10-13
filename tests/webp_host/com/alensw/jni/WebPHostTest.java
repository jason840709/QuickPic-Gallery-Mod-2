package com.alensw.jni;
import java.io.*; import java.nio.file.*;import android.os.ParcelFileDescriptor;import android.graphics.Bitmap;import android.graphics.BitmapFactory;
public final class WebPHostTest {
 static int checks; static File folder;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static int open(String name)throws Exception{ParcelFileDescriptor.register(19,new File(folder,name));ParcelFileDescriptor.position(19,7);int handle=JniUtils.gifOpenFD(19,true);check(ParcelFileDescriptor.position(19)==7,"caller fd position restored");check(ParcelFileDescriptor.DUPLICATES==0,"duplicate fd closed");ParcelFileDescriptor.unregister(19);return handle;}
 static void metadata()throws Exception{int h=open("animation.webp");check(h>0,"animated WebP opens through existing JNI seam");try{check(JniUtils.gifGetImageWidth(h)==4,"canvas width");check(JniUtils.gifGetImageHeight(h)==2,"canvas height");check(JniUtils.gifGetFrameCount(h)==5,"ANMF frame count");check(JniUtils.gifGetDuration(h)==267,"11,0,10,23,33ms durations normalize to 267ms");}finally{JniUtils.gifClose(h);}}
 static void loops() throws Exception {
  int h=open("animation.webp");try{
   check(JniUtils.gifIsAnimatedWebP(h),"WebP handle identified for worker scheduling");
   check(JniUtils.gifGetLoopCount(h)==2,"ANIM loop count is total iterations");
  }finally{JniUtils.gifClose(h);}
  check(!JniUtils.gifIsAnimatedWebP(h),"closed handle no longer WebP");
  check(JniUtils.gifGetLoopCount(h)==0,"closed handle loop count zero");
  WebPAnimation p=WebPAnimation.parse(Files.readAllBytes(new File(folder,"animation.webp").toPath()));
  check(p.background==0x80402010,"ANIM little-endian BGRA maps to ARGB");
  check(java.util.Arrays.equals(p.starts,new int[]{0,11,111,211,234}),"normalized cumulative frame starts");
  p.close();
 }
 static void wrappers() throws Exception {
  WebPAnimation p=WebPAnimation.parse(Files.readAllBytes(new File(folder,"animation.webp").toPath()));
  try {for(int i=0;i<5;i++){
   byte[] wrapped=p.frameData(i);check(wrapped.length>=12,"standalone frame has container");
   check(wrapped[0]=='R' && wrapped[8]=='W',"RIFF WEBP signatures");
   if(i==1)check(wrapped[12]=='V' && wrapped[15]=='X' && wrapped[20]==0x10,"ALPH wrapper has VP8X alpha without animation");
   Files.write(new File(folder,"wrapped-"+i+".webp").toPath(),wrapped);
  }}finally{p.close();}
  p=WebPAnimation.parse(Files.readAllBytes(new File(folder,"profile.webp").toPath()));
  try{for(int i=0;i<2;i++){byte[] wrapped=p.frameData(i);check((wrapped[20]&32)!=0 && (wrapped[20]&2)==0,"static ICC wrapper flags");Files.write(new File(folder,"wrapped-profile-"+i+".webp").toPath(),wrapped);}}finally{p.close();}
 }
 static void pixels(Bitmap b,int left,int right,String label){for(int y=0;y<2;y++)for(int x=0;x<4;x++)check(b.pixels[y*4+x]==(x<2?left:right),label+" at "+x+","+y+" actual="+Integer.toHexString(b.pixels[y*4+x]));}
 static void composition()throws Exception{
  int live=Bitmap.LIVE,h=open("animation.webp"),a=JniUtils.gifAllocBuffer(8),b=JniUtils.gifAllocBuffer(8);
  Bitmap destination=Bitmap.createBitmap(4,2,Bitmap.Config.ARGB_8888);
  int[] delays={11,100,100,23,33};int[] left={0xffff0000,0xffff0000,0x80ffff00,0x80ffff00,0x80402010};int[] right={0xffff0000,0xff7f0080,0x80402010,0x80402010,0};
  try{
   for(int i=0;i<5;i++){int buffer=i%2==0?a:b;check(JniUtils.gifDecodeFrame(h,i,buffer)==delays[i],"WebP frame delay "+i);check(JniUtils.gifDrawFrame(h,i,buffer,destination),"draw composite "+i);pixels(destination,left[i],right[i],"frame "+i);}
   check(JniUtils.gifDecodeFrame(h,4,b)==33,"repeat frame into second buffer");check(JniUtils.gifDrawFrame(h,4,b,destination),"draw repeated frame");pixels(destination,0x80402010,0,"same index does not dispose or blend twice");
   check(JniUtils.gifDecodeFrame(h,0,a)==11,"loop resets canvas");
   check(JniUtils.gifDecodeFrame(h,3,b)==23,"forward seek composites all dependency frames");check(JniUtils.gifDrawFrame(h,3,b,destination),"draw seeked frame");pixels(destination,0x80ffff00,0x80402010,"seek frame 3");
   check(JniUtils.gifDecodeFrame(h,2,a)==100,"backward seek replays from background");check(JniUtils.gifDrawFrame(h,2,a,destination),"draw backward seek");pixels(destination,0x80ffff00,0x80402010,"backward seek");
   check(JniUtils.gifDecodeFrame(h,2,b)==100,"repeat alpha frame");check(JniUtils.gifDrawFrame(h,2,b,destination),"draw repeated alpha");pixels(destination,0x80ffff00,0x80402010,"alpha stays authored opacity");
   JniUtils.gifSetBkColor(h,0xff000000);check(JniUtils.gifDecodeFrame(h,4,a)==33,"UI background does not change ANIM disposal");check(JniUtils.gifDrawFrame(h,4,a,destination),"draw over UI background");pixels(destination,0xff201008,0xff000000,"ANIM alpha background over UI black");
   int[] sample=new int[2];check(JniUtils.gifDrawFrame2(h,4,a,sample,2),"sampled output uses existing JNI seam");check(sample[0]==0xff201008 && sample[1]==0xff000000,"sampled pixels");
  }finally{JniUtils.gifClose(h);JniUtils.gifFreeBuffer(a);JniUtils.gifFreeBuffer(b);destination.recycle();}
  check(Bitmap.LIVE==live,"composition and snapshot resources released");
 }
 static void decodeFailure()throws Exception{
  int live=Bitmap.LIVE,h=open("animation.webp"),a=JniUtils.gifAllocBuffer(8),b=JniUtils.gifAllocBuffer(8);
  try{
   check(JniUtils.gifDecodeFrame(h,0,a)==11,"first snapshot before failure");
   check(JniUtils.gifDecodeFrame(h,1,b)==100,"second snapshot before failure");
   BitmapFactory.FAIL_AT=BitmapFactory.DECODES;
   check(JniUtils.gifDecodeFrame(h,2,a)==0,"decode failure returns JNI zero");
   check(Bitmap.LIVE==live,"decode failure releases dependency canvas and both snapshots");
   check(!JniUtils.gifIsAnimatedWebP(h),"failed handle removed or closed");
   check(JniUtils.gifDecodeFrame(h,0,b)==0,"failed handle cannot replay");
  }finally{BitmapFactory.FAIL_AT=-1;JniUtils.gifClose(h);JniUtils.gifFreeBuffer(a);JniUtils.gifFreeBuffer(b);}
  check(Bitmap.LIVE==live,"no decode error resource leak");
 }
 static int le32(byte[] d,int p){return (d[p]&255)|((d[p+1]&255)<<8)|((d[p+2]&255)<<16)|(d[p+3]<<24);}
 static void put32(byte[] d,int p,int n){for(int i=0;i<4;i++)d[p+i]=(byte)(n>>>(8*i));}
 static void put24(byte[] d,int p,int n){for(int i=0;i<3;i++)d[p+i]=(byte)(n>>>(8*i));}
 static int find(byte[] d,String tag,int start,int end){for(int p=start;p+8<=end;){if(new String(d,p,4,java.nio.charset.StandardCharsets.US_ASCII).equals(tag))return p;int size=le32(d,p+4);p+=8+size+(size&1);}throw new AssertionError("fixture chunk not found: "+tag);}
 static byte[] append(byte[] d,byte[] tail){byte[] out=java.util.Arrays.copyOf(d,d.length+tail.length);System.arraycopy(tail,0,out,d.length,tail.length);put32(out,4,out.length-8);return out;}
 static void reject(byte[] d,String reason)throws Exception{boolean rejected=false;try{WebPAnimation p=WebPAnimation.parse(d);p.close();}catch(IOException e){rejected=true;}check(rejected,reason);}
 static byte[] repeat(byte[] d,int count,int delay)throws Exception{int f=find(d,"ANMF",12,d.length),size=8+le32(d,f+4),length=f+count*size;byte[] out=new byte[length];System.arraycopy(d,0,out,0,f);byte[] frame=java.util.Arrays.copyOfRange(d,f,f+size);put24(frame,20,delay);for(int i=0;i<count;i++)System.arraycopy(frame,0,out,f+i*size,size);put32(out,4,length-8);return out;}
 static void structure()throws Exception{
  int created=Bitmap.CREATED,decodes=BitmapFactory.DECODES;
  byte[] d=Files.readAllBytes(new File(folder,"animation.webp").toPath()),m;
  reject(null,"null input rejected");reject(new byte[12],"not RIFF rejected");
  m=d.clone();put32(m,4,m.length-9);reject(m,"RIFF length smaller than file rejected");
  m=d.clone();put32(m,4,m.length-7);reject(m,"truncated RIFF length rejected");
  m=d.clone();put32(m,16,-1);reject(m,"unsigned oversized chunk rejected");
  m=d.clone();m[20]&=~2;reject(m,"VP8X animation flag required");
  m=d.clone();put32(m,16,9);reject(m,"VP8X payload length required");
  m=d.clone();put24(m,24,0xffffff);put24(m,27,0xffffff);reject(m,"canvas pixel cap enforced before decoding");
  m=d.clone();m[30]='J';reject(m,"ANIM required");
  int f=find(d,"ANMF",12,d.length),vp8l=find(d,"VP8L",f+24,f+8+le32(d,f+4));
  m=d.clone();put24(m,f+8,1);reject(m,"frame rectangle cannot extend outside canvas");
  m=d.clone();put24(m,f+14,1);reject(m,"bitstream dimensions must match ANMF dimensions");
  m=d.clone();m[vp8l]='N';reject(m,"frame requires image bitstream");
  m=d.clone();m[vp8l+8]=0;reject(m,"VP8L signature required");
  m=d.clone();m[vp8l+12]|=(byte)0x80;reject(m,"VP8L future version rejected");
  reject(append(d,java.util.Arrays.copyOfRange(d,12,30)),"duplicate VP8X rejected");
  reject(append(d,java.util.Arrays.copyOfRange(d,30,44)),"duplicate ANIM rejected");
  reject(append(d,new byte[]{'J','U','N','K'}),"partial trailing chunk header rejected");
  byte[] odd=new byte[]{'J','U','N','K',1,0,0,0,7,0};m=append(d,odd);WebPAnimation p=WebPAnimation.parse(m);p.close();check(true,"padded unknown chunk accepted");
  m=append(d,odd);m[m.length-1]=1;reject(m,"nonzero odd padding rejected");
  m=java.util.Arrays.copyOf(append(d,odd),d.length+9);put32(m,4,m.length-8);reject(m,"missing odd padding rejected");
  m=d.clone();m[20]|=32;reject(m,"advertised ICC profile required");
  reject(repeat(d,10001,11),"frame count cap enforced");reject(repeat(d,129,0xffffff),"duration int overflow rejected");
  p=WebPAnimation.parse(repeat(d,128,0xffffff));check(p.duration==2147483520,"largest tested cumulative duration stays signed positive");p.close();
  p=WebPAnimation.parse(repeat(d,10000,11));check(p.frames.length==10000,"frame count limit accepted");p.close();
  m=d.clone();put24(m,24,4095);put24(m,27,4095);p=WebPAnimation.parse(m);p.close();check(true,"16M pixel canvas metadata accepted without pixel allocation");
  m=d.clone();put24(m,24,4095);put24(m,27,4096);reject(m,"canvas exceeding 16M pixels rejected");
  m=d.clone();m[21]=(byte)0xff;m[f+23]|=(byte)0xfc;p=WebPAnimation.parse(m);check(p.frames[0].noBlend && !p.frames[0].dispose,"reserved fields ignored without changing defined bits");p.close();
  reject(new byte[WebPAnimation.MAX_BYTES+1],"input bytes cap enforced");
  check(Bitmap.CREATED==created && BitmapFactory.DECODES==decodes,"metadata parser never allocates or decodes pixels");
  check(open("single.webp")==0,"single-frame WebP does not register animation handle");check(open("still.webp")==0,"static WebP keeps original decoder fallback");
 }
 static void partialLoop()throws Exception{
  int live=Bitmap.LIVE,h=open("partial.webp"),buffer=JniUtils.gifAllocBuffer(8);Bitmap dest=Bitmap.createBitmap(4,2,Bitmap.Config.ARGB_8888);
  try{check(JniUtils.gifGetLoopCount(h)==0,"ANIM loop zero is infinite");check(JniUtils.gifDecodeFrame(h,0,buffer)==20,"first partial frame");check(JniUtils.gifDrawFrame(h,0,buffer,dest),"partial first draw");pixels(dest,0xffff0000,0x80402010,"first partial canvas begins with ANIM background");check(JniUtils.gifDecodeFrame(h,1,buffer)==20,"second partial frame");check(JniUtils.gifDecodeFrame(h,0,buffer)==20,"new loop resets all unused pixels");check(JniUtils.gifDrawFrame(h,0,buffer,dest),"partial new loop draw");pixels(dest,0xffff0000,0x80402010,"loop must remove previous right-hand frame");}finally{JniUtils.gifClose(h);JniUtils.gifFreeBuffer(buffer);dest.recycle();}check(Bitmap.LIVE==live,"partial loop resources released");
 }
 static void closeRace()throws Exception{
  final int live=Bitmap.LIVE,h=open("animation.webp"),b=JniUtils.gifAllocBuffer(8);final java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  BitmapFactory.ENTER=new java.util.concurrent.CountDownLatch(1);BitmapFactory.CONTINUE=new java.util.concurrent.CountDownLatch(1);
  final java.util.concurrent.CountDownLatch closing=new java.util.concurrent.CountDownLatch(1);
  Thread decode=new Thread(new Runnable(){public void run(){try{JniUtils.gifDecodeFrame(h,0,b);}catch(Throwable t){error.set(t);}}});
  Thread close=new Thread(new Runnable(){public void run(){try{closing.countDown();JniUtils.gifClose(h);}catch(Throwable t){error.set(t);}}});
  try{decode.start();check(BitmapFactory.ENTER.await(5,java.util.concurrent.TimeUnit.SECONDS),"decoder reaches owned canvas");close.start();check(closing.await(5,java.util.concurrent.TimeUnit.SECONDS),"close starts during decode");BitmapFactory.CONTINUE.countDown();decode.join(5000);close.join(5000);check(!decode.isAlive() && !close.isAlive(),"decode/close race terminates without deadlock");check(error.get()==null,"decode/close race has no exception");check(!JniUtils.gifIsAnimatedWebP(h) && JniUtils.gifDecodeFrame(h,0,b)==0,"closed handle rejects stale decode");check(Bitmap.LIVE==live,"close race releases composition and snapshot");}
  finally{BitmapFactory.CONTINUE.countDown();BitmapFactory.ENTER=null;BitmapFactory.CONTINUE=null;JniUtils.gifClose(h);JniUtils.gifFreeBuffer(b);}
 }
 static void dimensionFailure()throws Exception{
  int live=Bitmap.LIVE,h=open("animation.webp"),b=JniUtils.gifAllocBuffer(8);BitmapFactory.WRONG_SIZE=true;
  try{check(JniUtils.gifDecodeFrame(h,0,b)==0,"decoder dimensions mismatch returns zero");check(Bitmap.LIVE==live,"wrong-sized decoded bitmap and composition released");}finally{BitmapFactory.WRONG_SIZE=false;JniUtils.gifClose(h);JniUtils.gifFreeBuffer(b);}
 }
 static void bounds()throws Exception{int live=Bitmap.LIVE,h=open("animation.webp"),b=JniUtils.gifAllocBuffer(7);try{check(JniUtils.gifDecodeFrame(h,0,b)==0,"undersized output buffer rejected");check(JniUtils.gifDecodeFrame(h,-1,b)==0,"negative frame rejected");check(JniUtils.gifDecodeFrame(h,5,b)==0,"frame past end rejected");check(JniUtils.gifAllocBuffer(0)==0 && JniUtils.gifAllocBuffer(16*1024*1024+1)==0,"buffer allocation bounds");}finally{JniUtils.gifClose(h);JniUtils.gifFreeBuffer(b);}check(Bitmap.LIVE==live,"invalid requests allocate no pixels");}
 static void staticRead()throws Exception{long before=ParcelFileDescriptor.BYTES_READ;check(open("still-large.webp")==0,"large static WebP falls back");check(ParcelFileDescriptor.BYTES_READ-before<=16*1024,"static WebP rejected by header without full file read");}
 public static void main(String[] args)throws Exception{folder=new File(args[0]);metadata();if(args[1].equals("static-read")||args[1].equals("all"))staticRead();if(args[1].equals("metadata") && args.length>2){WebPAnimation local=WebPAnimation.parse(Files.readAllBytes(Paths.get(args[2])));System.out.println("Local metadata: "+local.width+"x"+local.height+", "+local.frames.length+" frames, "+local.duration+"ms, loops="+local.loopCount);local.close();}
  if(args[1].equals("structure")||args[1].equals("all"))structure();if(args[1].equals("compose")||args[1].equals("all"))partialLoop();if(args[1].equals("lifecycle")||args[1].equals("all")){closeRace();dimensionFailure();bounds();}if(args[1].equals("lifecycle")||args[1].equals("all"))decodeFailure();if(args[1].equals("compose")||args[1].equals("all"))composition();if(args[1].equals("loops")||args[1].equals("all"))loops();if(args[1].equals("wrapper")||args[1].equals("all"))wrappers();System.out.println("WebP host "+args[1]+": "+checks+" checks passed");}
}
