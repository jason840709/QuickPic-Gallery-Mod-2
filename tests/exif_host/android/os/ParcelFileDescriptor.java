package android.os;
import java.io.*;
import java.util.*;
/** JVM test double only; not packaged in Android app. */
public final class ParcelFileDescriptor implements Closeable {
    private static final Map<Integer, File> files = new HashMap<Integer, File>();
    public static final Map<FileDescriptor, RandomAccessFile> OPEN = new IdentityHashMap<FileDescriptor, RandomAccessFile>();
    private final RandomAccessFile raf;
    public static void register(int raw, File file) { files.put(raw, file); }
    public static void unregister(int raw) { files.remove(raw); }
    private ParcelFileDescriptor(File file) throws IOException { raf = new RandomAccessFile(file, "rw"); OPEN.put(raf.getFD(), raf); }
    public static ParcelFileDescriptor fromFd(int raw) throws IOException {
        File file = files.get(raw); if (file == null) throw new IOException("bad fd"); return new ParcelFileDescriptor(file);
    }
    public FileDescriptor getFileDescriptor() { try { return raf.getFD(); } catch (IOException e) { throw new RuntimeException(e); } }
    public void close() throws IOException { OPEN.remove(raf.getFD()); raf.close(); }
}
