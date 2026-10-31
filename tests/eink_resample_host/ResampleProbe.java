import com.alensw.jni.EinkResample;import java.util.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import java.io.*;
public class ResampleProbe{
 static int checks=0;static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static class Image implements EinkResample.Rows{int w,h;int[] p;Image(int w,int h){this.w=w;this.h=h;p=new int[w*h];}public int width(){return w;}public int height(){return h;}public void read(int y,int[] a){System.arraycopy(p,y*w,a,0,w);}public void write(int y,int[] a){System.arraycopy(a,0,p,y*w,w);}void png(File f)throws Exception{BufferedImage b=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);b.setRGB(0,0,w,h,p,0,w);ImageIO.write(b,"png",f);}}
 static Image scale(Image a,int w,int h,boolean l){Image b=new Image(w,h);EinkResample.scale(a,b,l);return b;}
 static Image bilinear(Image a,int w,int h){Image b=new Image(w,h);for(int y=0;y<h;y++)for(int x=0;x<w;x++){double u=(x+.5)*a.w/w-.5,v=(y+.5)*a.h/h-.5;int x0=(int)Math.floor(u),y0=(int)Math.floor(v);double f=u-x0,g=v-y0;double z=0;for(int j=0;j<2;j++)for(int i=0;i<2;i++)z+=(a.p[Math.max(0,Math.min(a.h-1,y0+j))*a.w+Math.max(0,Math.min(a.w-1,x0+i))]&255)*(i==0?1-f:f)*(j==0?1-g:g);int c=(int)Math.round(z);b.p[y*w+x]=0xff000000|c*0x010101;}return b;}
 // For the synthetic carrier all energy is above the destination Nyquist limit.
 // Interior RMS from its known mean is residual alias energy; exclude edge support.
 static double residual(Image a){double s=0;int n=0;for(int y=8;y<a.h-8;y++)for(int x=8;x<a.w-8;x++){double v=(a.p[y*a.w+x]&255)-127.5;s+=v*v;n++;}return Math.sqrt(s/n);}
 public static void main(String[] args)throws Exception{
  File out=new File(args[0]);out.mkdirs();double totalB=0,totalA=0,totalL=0;int cases=0;
  for(double theta:new double[]{0,.17,.49})for(double ratio:new double[]{.25,.37,.52,.61})for(double phase:new double[]{0,.37,.81}){
   Image a=new Image(384,384);for(int y=0;y<a.h;y++)for(int x=0;x<a.w;x++){int c=(int)Math.round(127.5+120*Math.cos(2*Math.PI*(.47*(x*Math.cos(theta)+y*Math.sin(theta))+phase)));a.p[y*a.w+x]=0xff000000|c*0x010101;}
   int dim=(int)Math.round(384*ratio);Image b=bilinear(a,dim,dim),area=scale(a,dim,dim,false),l=scale(a,dim,dim,true);
   totalB+=residual(b);totalA+=residual(area);totalL+=residual(l);cases++;
   if(theta==0&&ratio==.52&&phase==0){a.png(new File(out,"carrier-source.png"));b.png(new File(out,"bilinear.png"));area.png(new File(out,"area.png"));l.png(new File(out,"lanczos3.png"));}
  }
  check(totalA<totalB*.45,"area should reduce synthetic alias RMS substantially");check(totalL<totalB*.15,"expanded Lanczos3 should suppress out-of-band carrier");
  for(boolean l:new boolean[]{false,true}){
   Image flat=new Image(37,29);Arrays.fill(flat.p,0x806f3b20);Image z=scale(flat,11,7,l);for(int c:z.p)check(Math.abs(((c>>>16)&255)-111)<=1&&Math.abs(((c>>>8)&255)-59)<=1&&(c>>>24)==128,"constant color/alpha");
   Image a=new Image(128,128);Arrays.fill(a.p,0xffffffff);for(int y=0;y<128;y++)for(int x=48;x<64;x++)a.p[y*128+x]=0xff000000;Image b=scale(a,64,64,l);for(int y=6;y<58;y++){check((b.p[y*64+27]&255)<=1,"dark stroke interior preserved");check((b.p[y*64+15]&255)==255,"paper stays white");}
   Image identity=scale(a,128,128,l);check(Arrays.equals(a.p,identity.p),"identity keeps pixels exactly");
   Image alpha=new Image(30,15);for(int y=0;y<15;y++)for(int x=0;x<30;x++)alpha.p[y*30+x]=x<15?0x000000ff:0xffff0000;Image trans=scale(alpha,13,7,l);for(int c:trans.p)if((c>>>24)>0)check((c&255)==0&&((c>>>8)&255)==0,"no hidden-color transparent fringe");
  }
  for(boolean l:new boolean[]{false,true})for(int x:new int[]{3,47,48,61,123}){
   Image a=new Image(128,32);Arrays.fill(a.p,0xffffffff);for(int y=0;y<32;y++)a.p[y*128+x]=0xff000000;Image b=scale(a,64,16,l);
   double ink=0;for(int i=0;i<64;i++)ink+=(255-(b.p[8*64+i]&255))/255.0;
   if(!l)check(Math.abs(ink-.5)<.005,"single-pixel stroke area contribution survives phase");
  }
  boolean refused=false;try{Image a=new Image(65537,1);scale(a,1,1,false);}catch(IllegalArgumentException expected){refused=true;}check(refused,"resource axis guard rejects extreme dimensions");
  Image one=new Image(1,1);one.p[0]=0xff81632a;check(scale(one,1,1,true).p[0]==one.p[0],"single pixel");
  System.out.printf(java.util.Locale.ROOT,"{\"checks\":%d,\"carrier_cases\":%d,\"bilinear_rms_mean\":%.6f,\"area_rms_mean\":%.6f,\"lanczos3_rms_mean\":%.6f,\"status\":\"PASS\"}%n",checks,cases,totalB/cases,totalA/cases,totalL/cases);
 }
}
