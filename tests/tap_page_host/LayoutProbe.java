import com.alensw.jni.TapPageLayout;
public final class LayoutProbe {
 private static int checks;
 private static void equal(int actual,int expected,String message){checks++;if(actual!=expected)throw new AssertionError(message+": "+actual+" != "+expected);}
 private static void at(int mode,float x,float y,int expected,String text){equal(TapPageLayout.action(mode,x,y,1000,1000),expected,text);}
 public static void main(String[]args){
  for(int m=0;m<7;m++)equal(TapPageLayout.action(m,500,500,1000,1000),0,"center stays available "+m);
  at(0,10,900,0,"disabled previous area");at(0,990,900,0,"disabled next area");
  at(1,100,499.9f,0,"bottom begins at half");at(1,100,500,-1,"bottom upper edge included");at(1,333,800,0,"left integer seam is menu");at(1,332.9f,800,-1,"left integer interior");at(1,666,800,0,"right integer seam is menu");at(1,666.1f,800,1,"right integer interior");
  equal(TapPageLayout.action(1,332.9f,800,1001,1601),-1,"odd width left");equal(TapPageLayout.action(1,333,800,1001,1601),0,"odd width seam");equal(TapPageLayout.action(1,667,800,1001,1601),0,"odd width right seam");equal(TapPageLayout.action(1,667.1f,800,1001,1601),1,"odd width right interior");equal(TapPageLayout.action(1,10,799.9f,1001,1601),0,"odd height seam");
  at(2,299.9f,50,-1,"side left");at(2,300,50,0,"side left seam");at(2,700,900,0,"side right seam");at(2,700.1f,900,1,"side right");
  at(3,900,299.9f,-1,"top");at(3,100,300,0,"top seam");at(3,100,700,0,"bottom seam");at(3,100,700.1f,1,"bottom");
  at(4,100,499.9f,-1,"left upper");at(4,100,500,1,"left lower");at(4,300,50,0,"left width seam");at(4,900,800,0,"opposite side menu");
  at(5,900,499.9f,-1,"right upper");at(5,900,500,1,"right lower");at(5,700,50,0,"right width seam");at(5,100,800,0,"opposite left menu");
  at(6,100,349.9f,0,"middle above");at(6,100,350,-1,"middle upper included");at(6,900,650,1,"middle lower included");at(6,900,650.1f,0,"middle below");at(6,300,500,0,"middle left seam");at(6,700,500,0,"middle right seam");
  float[] invalid={Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1,1000};
  for(int m=0;m<7;m++)for(float v:invalid){at(m,v,900,0,"invalid x");at(m,100,v,0,"invalid y");}
  for(int m=-2;m<10;m++){equal(TapPageLayout.action(m,1,1,0,10),0,"no width");equal(TapPageLayout.action(m,1,1,10,0),0,"no height");equal(TapPageLayout.action(m,0,0,2,10),0,"tiny width");equal(TapPageLayout.action(m,0,0,10,1),0,"tiny height");}
  for(int m:new int[]{-2,-1,7,8,Integer.MAX_VALUE})at(m,100,900,0,"invalid mode");
  for(int[]size:new int[][]{{1080,1920},{1920,1080},{1860,2480},{2480,1860}}){
   equal(TapPageLayout.action(1,size[0]*.1f,size[1]*.8f,size[0],size[1]),-1,"legacy orientation");equal(TapPageLayout.action(1,size[0]*.9f,size[1]*.8f,size[0],size[1]),1,"legacy next orientation");equal(TapPageLayout.action(2,size[0]*.1f,size[1]*.1f,size[0],size[1]),-1,"side orientation");equal(TapPageLayout.action(3,size[0]*.9f,size[1]*.9f,size[0],size[1]),1,"top bottom orientation");
   for(int m=1;m<7;m++){boolean prev=false,menu=false,next=false;for(int y=1;y<20;y++)for(int x=1;x<20;x++){int a=TapPageLayout.action(m,size[0]*x/20f,size[1]*y/20f,size[0],size[1]);equal(a>=-1&&a<=1?0:1,0,"valid action range");prev|=a<0;menu|=a==0;next|=a>0;}equal(prev&&menu&&next?0:1,0,"all three areas reachable mode"+m);}
  }
  System.out.println("PASS "+checks+" production layout assertions");
 }
}
