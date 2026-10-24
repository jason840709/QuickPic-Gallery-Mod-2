// SPDX-License-Identifier: MIT
package android.system;
import java.nio.file.*;import java.nio.file.attribute.BasicFileAttributes;
public final class Os {
 public static int calls,error;public static boolean runtime;public static void reset(){calls=0;error=0;runtime=false;}
 public static StructStat lstat(String path)throws ErrnoException {
  calls++;if(runtime)throw new SecurityException("stat permission");if(error!=0)throw new ErrnoException("lstat",error);
  try{Files.readAttributes(Paths.get(path),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);return new StructStat();}
  catch(NoSuchFileException e){throw new ErrnoException("lstat",OsConstants.ENOENT);}
  catch(NotDirectoryException e){throw new ErrnoException("lstat",OsConstants.ENOTDIR);}
  catch(java.io.IOException e){throw new ErrnoException("lstat",OsConstants.EACCES);}
 }
}
