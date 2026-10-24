import android.graphics.Bitmap;
import android.graphics.FakeImages;
import android.graphics.Rect;
import android.media.ExifInterface;
import com.alensw.jni.ScreensaverImage;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public final class RendererProbe {
    private static int assertions;
    private static File root;
    private static void check(boolean value,String message) {
        assertions++;
        if(!value)throw new AssertionError(message);
    }
    private static void near(double a,double b,String message) {
        check(Math.abs(a-b)<0.0000001,message+": "+a+" vs "+b);
    }
    private static File source(String name,int width,int height) throws IOException {
        File f=new File(root,name+".png");
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int color=x<width/2 ? (y<height/2?0xffff0000:0xff0000ff)
                    : (y<height/2?0xff00ff00:0xffffff00);
            image.setRGB(x,y,color);
        }
        ImageIO.write(image,"png",f);
        return f;
    }
    private static File virtual(String name,int width,int height) throws IOException {
        File f=new File(root,name);
        Files.write(f.toPath(),new byte[]{10,20,30});
        FakeImages.virtual.put(f.getAbsolutePath(),new int[]{width,height});
        return f;
    }
    private static BufferedImage prepare(File source,int w,int h,String dir) throws IOException {
        File result=ScreensaverImage.prepare(source.getAbsolutePath(),w,h,new File(root,dir));
        check(result.getName().matches("screensaver-"+w+"x"+h+"-[0-9a-f-]+\\.png"),"filename");
        BufferedImage image=ImageIO.read(result);
        check(image.getWidth()==w&&image.getHeight()==h,"exact dimensions");
        check(new File(result.getParentFile(),".nomedia").isFile(),"nomedia retained");
        check(Bitmap.live.get()==0,"all allocated bitmaps recycled");
        return image;
    }
    private static void corners(BufferedImage image,int[] expected,String message) {
        int[] actual={image.getRGB(1,1),image.getRGB(image.getWidth()-2,1),
                image.getRGB(1,image.getHeight()-2),
                image.getRGB(image.getWidth()-2,image.getHeight()-2)};
        for(int i=0;i<4;i++)check(actual[i]==expected[i],message+" corner "+i+
                " "+Integer.toHexString(actual[i]));
    }
    private static void geometry() {
        check(ScreensaverImage.validSize(1860,2480),"legacy valid");
        check(ScreensaverImage.validSize(7680,4320)==false,"above pixel cap");
        check(ScreensaverImage.validSize(8000,4000),"32m boundary");
        check(ScreensaverImage.validSize(8192,1),"axis boundary");
        check(!ScreensaverImage.validSize(8193,1),"axis max");
        check(!ScreensaverImage.validSize(0,100),"zero");
        check(!ScreensaverImage.validSize(-1,100),"negative");
        check(!ScreensaverImage.validSize(Integer.MAX_VALUE,Integer.MAX_VALUE),"overflow");
        ScreensaverImage.Crop c=ScreensaverImage.centeredCrop(10000,10000,1860,2480);
        near(c.left,1250,"square left");near(c.top,0,"square top");
        near(c.width,7500,"square width");near(c.height,10000,"square height");
        c=ScreensaverImage.centeredCrop(12000,5000,1860,2480);
        near(c.left,4125,"wide left");near(c.width,3750,"wide width");
        c=ScreensaverImage.centeredCrop(5000,12000,2480,1860);
        near(c.top,4125,"portrait top");near(c.height,3750,"portrait height");
        c=ScreensaverImage.centeredCrop(101,89,16,9);
        near(c.width/c.height,16.0/9,"fractional aspect");
        near(c.top,(89-c.height)/2,"fractional center");
        c=ScreensaverImage.centeredCrop(Integer.MAX_VALUE,Integer.MAX_VALUE,8192,1);
        near(c.width,Integer.MAX_VALUE,"large geometry no overflow");
        check(c.height>0,"tiny fractional crop");
    }
    private static void orientation()throws Exception {
        int R=0xffff0000,G=0xff00ff00,B=0xff0000ff,Y=0xffffff00;
        int[][] expected={{R,G,B,Y},{G,R,Y,B},{Y,B,G,R},{B,Y,R,G},
                {R,B,G,Y},{B,R,Y,G},{Y,G,B,R},{G,Y,R,B}};
        File input=source("orientation",80,40);
        for(int o=1;o<=8;o++){
            FakeImages.reset();
            ExifInterface.tags.put(input.getAbsolutePath(),o);
            corners(prepare(input,o>=5?40:80,o>=5?80:40,"orientation-"+o),
                    expected[o-1],"EXIF "+o);
            check(FakeImages.fullDecodes==0,"region only");
            check(FakeImages.decoderRecycles==1,"decoder released");
            corners(prepare(input,20,20,"cropped-orientation-"+o),expected[o-1],"cropped EXIF "+o);
        }
        ExifInterface.tags.put(input.getAbsolutePath(),99);
        corners(prepare(input,80,40,"invalid-exif"),expected[0],"invalid orientation normal");
        // EXIF 6 with an upright non-square center crop: raw height is reduced, not raw width.
        ExifInterface.tags.put(input.getAbsolutePath(),6);
        prepare(input,20,20,"rotated-crop");
        Rect r=FakeImages.lastRegion;
        check(r.left==20&&r.top==0&&r.right==60&&r.bottom==40,"EXIF raw crop geometry");
    }
    private static void largeAndFallback()throws Exception {
        FakeImages.reset();
        File big=virtual("synthetic-square",10000,10000);
        byte[] input=Files.readAllBytes(big.toPath());
        BufferedImage output=prepare(big,1860,2480,"big-square");
        Rect r=FakeImages.lastRegion;
        check(r.left==1250&&r.top==0&&r.right==8750&&r.bottom==10000,"10000 center region");
        check(FakeImages.lastSample==4,"sample retains target detail");
        check(FakeImages.fullDecodes==0,"10000 not full decoded");
        check(output.getRGB(0,100)==0xff00ff00,"left retained band");
        check(output.getRGB(1859,100)==0xffffff00,"right retained band");
        check(Arrays.equals(input,Files.readAllBytes(big.toPath())),"source untouched");

        FakeImages.reset();
        prepare(virtual("synthetic-wide",1000000,1000),300,400,"very-wide");
        r=FakeImages.lastRegion;
        check(r.width()==750&&r.height()==1000,"wide only crop decoded");
        check(FakeImages.lastSample==2,"wide quality sample");

        FakeImages.reset();
        FakeImages.regionUnsupported=true;
        File small=source("fallback",80,40);
        prepare(small,30,40,"fallback");
        check(FakeImages.fullDecodes==1,"bounded fallback works");

        FakeImages.reset();
        FakeImages.regionUnsupported=true;
        File extreme=virtual("unsafe-fallback",1000000,1000);
        expectFailure(extreme,1860,2480,new File(root,"unsafe"),IOException.class);
        check(FakeImages.fullDecodes==0,"unsafe fallback rejected before decode");

        FakeImages.reset();
        FakeImages.regionNull=true;FakeImages.fullNull=true;
        expectFailure(small,30,40,new File(root,"baddecode"),IOException.class);
        check(FakeImages.decoderRecycles==1,"failed decoder recycled");

        FakeImages.reset();FakeImages.changedBounds=true;
        expectFailure(small,30,40,new File(root,"changed"),IOException.class);
        check(FakeImages.fullDecodes==0,"changed source rejected");
        FakeImages.reset();
    }
    private static void expectFailure(File source,int w,int h,File dir,Class<?> type)throws Exception {
        boolean failed=false;
        try {ScreensaverImage.prepare(source==null?null:source.getAbsolutePath(),w,h,dir);}
        catch(Throwable e){check(type.isInstance(e),"failure class "+e);failed=true;}
        check(failed,"failure required");
        check(Bitmap.live.get()==0,"failure releases bitmaps");
        File[] files=dir==null?null:dir.listFiles();
        if(files!=null)for(File f:files)check(!f.getName().endsWith(".tmp"),"no partial temp");
    }
    private static void filesAndErrors()throws Exception {
        FakeImages.reset();
        File input=source("files",40,40);
        File dir=new File(root,"persistent");dir.mkdirs();
        File previous=new File(dir,"screensaver-20x20-previous.png");
        byte[] sentinel={4,5,6};Files.write(previous.toPath(),sentinel);
        File foreign=new File(dir,"photo.png");Files.write(foreign.toPath(),sentinel);
        File nomedia=new File(dir,".nomedia");Files.write(nomedia.toPath(),sentinel);
        File one=ScreensaverImage.prepare(input.getAbsolutePath(),20,20,dir);
        File two=ScreensaverImage.prepare(input.getAbsolutePath(),20,20,dir);
        check(!one.equals(two),"unique successive exports");
        check(Arrays.equals(sentinel,Files.readAllBytes(previous.toPath())),"previous untouched");
        check(Arrays.equals(sentinel,Files.readAllBytes(foreign.toPath())),"unowned untouched");
        check(Arrays.equals(sentinel,Files.readAllBytes(nomedia.toPath())),"nomedia not truncated");
        for(String mode:new String[]{"false","short","dimensions","crc","throw","oom"}){
            Bitmap.compression=mode;
            Class<?> failure="throw".equals(mode)?IllegalStateException.class:
                    "oom".equals(mode)?OutOfMemoryError.class:IOException.class;
            File failedDir=new File(root,"error-"+mode);
            expectFailure(input,20,20,failedDir,failure);
            File[] files=failedDir.listFiles();
            if(files!=null)for(File f:files)check(f.getName().equals(".nomedia"),"no invalid final");
        }
        FakeImages.reset();
        expectFailure(null,20,20,new File(root,"null"),IOException.class);
        expectFailure(input,0,20,new File(root,"size"),IOException.class);
        expectFailure(input,8192,8192,new File(root,"size2"),IOException.class);
        File bad=new File(root,"garbage");Files.write(bad.toPath(),sentinel);
        expectFailure(bad,20,20,new File(root,"bad"),IOException.class);
        File blocked=new File(root,"directory-file");Files.write(blocked.toPath(),sentinel);
        expectFailure(input,20,20,blocked,IOException.class);
        File noMarker=new File(root,"marker-directory");new File(noMarker,".nomedia").mkdirs();
        expectFailure(input,20,20,noMarker,IOException.class);
    }
    private static void alphaAndConcurrent()throws Exception {
        FakeImages.reset();
        File input=new File(root,"transparent.png");
        BufferedImage image=new BufferedImage(40,40,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<40;y++)for(int x=20;x<40;x++)image.setRGB(x,y,0x80000000);
        ImageIO.write(image,"png",input);
        BufferedImage output=prepare(input,40,40,"alpha");
        check(output.getRGB(2,2)==0xffffffff,"transparent becomes white");
        check(output.getRGB(30,2)==0xff7f7f7f,"half alpha composite");
        for(int y=0;y<40;y++)for(int x=0;x<40;x++)
            check((output.getRGB(x,y)>>>24)==255,"opaque output");

        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Future<File>> futures=new ArrayList<Future<File>>();
            final File concurrentDir=new File(root,"concurrent");
            for(int i=0;i<4;i++)futures.add(pool.submit(new Callable<File>(){
                public File call()throws Exception{
                    return ScreensaverImage.prepare(input.getAbsolutePath(),40,40,concurrentDir);
                }
            }));
            Set<String> names=new HashSet<String>();
            for(Future<File> f:futures){File png=f.get();check(png.isFile(),"concurrent saved");
                check(ImageIO.read(png).getWidth()==40,"concurrent valid");names.add(png.getName());}
            check(names.size()==4,"concurrent unique");
            for(File f:concurrentDir.listFiles())check(!f.getName().endsWith(".tmp"),"concurrent no temp");
            check(Bitmap.live.get()==0,"concurrent releases bitmaps");
        }finally{pool.shutdownNow();}
    }
    public static void main(String[] args)throws Exception {
        root=new File(args[0]);root.mkdirs();
        if(args.length>1 && "low-memory".equals(args[1])){
            File big=virtual("low-heap-square",10000,10000);
            expectFailure(big,1860,2480,new File(root,"low-heap-output"),IOException.class);
            check(FakeImages.lastRegion==null,"memory guard before decoder allocation");
            check(FakeImages.fullDecodes==0,"memory guard before full decode");
            System.out.println("PASS|low-memory|"+assertions);return;
        }
        geometry();orientation();largeAndFallback();filesAndErrors();alphaAndConcurrent();
        System.out.println("PASS|renderer|"+assertions);
    }
}
