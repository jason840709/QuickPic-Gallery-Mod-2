// SPDX-License-Identifier: MIT
package com.alensw.jni;
import java.io.*;
import java.util.UUID;
/** OEM deletes its input: pass a fully validated, disposable byte-identical copy. */
public final class ScreensaverImportCopy {
    private ScreensaverImportCopy() {}
    public static File copy(File saved,File directory,int width,int height)throws IOException {
        ScreensaverPng.verify(saved,width,height,24L*1024*1024);
        if(!directory.isDirectory()&&!directory.mkdirs()&&!directory.isDirectory())throw new IOException("無法建立匯入資料夾");
        File temporary=File.createTempFile(".import-",".tmp",directory);
        File result=new File(directory,"import-"+UUID.randomUUID()+".png");
        try {
            try(FileInputStream in=new FileInputStream(saved);FileOutputStream out=new FileOutputStream(temporary)) {
                byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                out.flush();out.getFD().sync();
            }
            ScreensaverPng.verify(temporary,width,height,24L*1024*1024);
            if(result.exists()||!temporary.renameTo(result))throw new IOException("無法保存匯入副本");
            return result;
        }finally { temporary.delete(); }
    }
}
