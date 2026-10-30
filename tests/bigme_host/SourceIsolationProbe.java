// SPDX-License-Identifier: MIT
import android.graphics.Bitmap;
import android.graphics.FakeImages;
import com.alensw.jni.ScreensaverImage;
import com.alensw.jni.ScreensaverPng;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Real renderer/overload logic, Java2D bitmap codec double, no Android/OEM calls. */
public final class SourceIsolationProbe {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args)throws Exception{
        File root=new File(args[0]);root.mkdirs();
        File source=new File(root,"original.png");
        BufferedImage input=new BufferedImage(40,40,BufferedImage.TYPE_INT_ARGB);
        input.setRGB(20,20,0xffcc9900);ImageIO.write(input,"png",source);
        byte[] original=Files.readAllBytes(source.toPath());
        File legacy=new File(root,"legacy"),oem=new File(root,"oem-import");
        FakeImages.reset();
        File old=ScreensaverImage.prepare(source.getAbsolutePath(),30,40,legacy);
        byte[] oldBytes=Files.readAllBytes(old.toPath());
        check(new File(legacy,".nomedia").isFile(),"Legacy default must retain .nomedia");
        File first=ScreensaverImage.prepare(source.getAbsolutePath(),30,40,oem,false);
        ScreensaverPng.verify(first,30,40,1024*1024);
        byte[] firstBytes=Files.readAllBytes(first.toPath());
        check(!new File(oem,".nomedia").exists(),"OEM import must remain media visible");
        File second=ScreensaverImage.prepare(source.getAbsolutePath(),30,40,oem,false);
        ScreensaverPng.verify(second,30,40,1024*1024);
        check(!first.equals(second),"Repeated exports must have unique paths");
        check(!source.getCanonicalFile().equals(first.getCanonicalFile()),"Source must not be returned");
        check(Arrays.equals(original,Files.readAllBytes(source.toPath())),"Original source changed");
        check(Arrays.equals(oldBytes,Files.readAllBytes(old.toPath())),"Legacy export changed");
        check(Arrays.equals(firstBytes,Files.readAllBytes(first.toPath())),"Previous OEM export changed");
        Bitmap.compression="short";
        try{ScreensaverImage.prepare(source.getAbsolutePath(),30,40,oem,false);throw new AssertionError("Expected bad compression failure");}catch(IOException expected){}
        check(Arrays.equals(original,Files.readAllBytes(source.toPath())),"Failure changed original");
        check(Arrays.equals(firstBytes,Files.readAllBytes(first.toPath())),"Failure changed prior output");
        for(File f:oem.listFiles())check(!f.getName().endsWith(".tmp"),"Failure left partial temp");
        check(Bitmap.live.get()==0,"Bitmap leak");
        System.out.println("PASS actual renderer overload source/output isolation; Java2D-backed Android doubles only");
    }
}
