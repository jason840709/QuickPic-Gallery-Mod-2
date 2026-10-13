package com.alensw.jni;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Java replacement for qpicjni156. Handles are positive registry IDs, never pointers.
 * Android API 21 is the minimum. Storage access still follows Android permissions.
 * GIF pixels/disposal are decoded by Android Movie; no native library is loaded.
 */
public class JniUtils {
    private static final Map<Integer, Scanner> SCANNERS = new HashMap<Integer, Scanner>();
    private static final Map<Integer, Gif> GIFS = new HashMap<Integer, Gif>();
    private static final Map<Integer, GifBuffer> BUFFERS = new HashMap<Integer, GifBuffer>();
    private static int nextId = 1;
    private static final int MAX_GIF_BYTES = 64 * 1024 * 1024;
    private static final int MAX_GIF_PIXELS = 16 * 1024 * 1024;
    private static final int MAX_GIF_FRAMES = 10000;

    private static synchronized int token() {
        // Never reuse a stale token after wrapping the signed integer range.
        return nextId > 0 ? nextId++ : 0;
    }

    public static void exifClose(int handle) { QuickPicExif.exifClose(handle); }
    public static double[] exifGet3RealValue(int handle, int tag, boolean converted) {
        return QuickPicExif.exifGet3RealValue(handle, tag, converted);
    }
    public static int exifGetInfo(int handle, int info) { return QuickPicExif.exifGetInfo(handle, info); }
    public static byte[] exifGetThumbnail(int handle) { return QuickPicExif.exifGetThumbnail(handle); }
    public static Object exifGetValue(int handle, int tag, boolean converted) {
        return QuickPicExif.exifGetValue(handle, tag, converted);
    }
    public static int exifOpenFD(int fd, boolean writable) { return QuickPicExif.exifOpenFD(fd, writable); }
    public static boolean exifSaveTo(int handle, int fd) { return QuickPicExif.exifSaveTo(handle, fd); }
    public static boolean exifSetDegrees(int handle, int degrees) {
        return QuickPicExif.exifSetDegrees(handle, degrees);
    }

    private static final class Scanner {
        final Method callback;
        volatile boolean canceled;
        volatile Map<String, Character> extensions = new HashMap<String, Character>();
        Scanner(Method callback) { this.callback = callback; }
    }

    public static int fsCreateScanner(Method method) {
        if (method == null || Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != Void.TYPE) return 0;
        Class<?>[] types = method.getParameterTypes();
        if (types.length != 4 || types[0] != Character.TYPE || types[1] != Integer.TYPE
                || types[2] != Long.TYPE || types[3] != String.class) return 0;
        try { method.setAccessible(true); } catch (RuntimeException e) { return 0; }
        int id = token();
        if (id != 0) synchronized (SCANNERS) { SCANNERS.put(id, new Scanner(method)); }
        return id;
    }

    private static Scanner scanner(int handle) {
        synchronized (SCANNERS) { return SCANNERS.get(handle); }
    }
    public static void fsDestroyScanner(int handle) {
        Scanner scanner;
        synchronized (SCANNERS) { scanner = SCANNERS.remove(handle); }
        if (scanner != null) scanner.canceled = true;
    }
    public static void fsCancelScan(int handle, boolean canceled) {
        Scanner scanner = scanner(handle);
        if (scanner != null) scanner.canceled = canceled;
    }
    public static void fsInitExtensions(int handle, String text) {
        Scanner scanner = scanner(handle);
        if (scanner == null) return;
        Map<String, Character> extensions = new HashMap<String, Character>();
        if (text != null) for (String part : text.split("/")) {
            if (part.length() < 2) continue;
            char kind = part.charAt(0);
            if (kind != 'I' && kind != 'V') continue;
            String extension = part.substring(1).toLowerCase(Locale.ROOT);
            if (extension.indexOf('.') < 0 && extension.indexOf('\\') < 0)
                extensions.put(extension, Character.valueOf(kind));
        }
        scanner.extensions = extensions;
    }
    public static int fsScanFolders(int handle, Object folder, int flags) {
        return scan(handle, folder, flags, true);
    }
    public static int fsScanPictures(int handle, Object folder, int flags) {
        return scan(handle, folder, flags, false);
    }

    private static int scan(int handle, Object folder, int flags, boolean onlyFolders) {
        Scanner scanner = scanner(handle);
        if (scanner == null) return -1;
        if (scanner.canceled) return -4;
        if (!(folder instanceof File) || !scanner.callback.getDeclaringClass().isInstance(folder)) return -2;
        String path = ((File) folder).getPath();
        if (path == null || path.length() == 0 || path.indexOf('\0') >= 0) return -2;
        File directory = new File(path);
        try {
            if (!directory.isDirectory()) return -3;
            // Native scanner returns -3 when this folder is excluded by .nomedia.
            if (!onlyFolders && (flags & 8) != 0 && new File(directory, ".nomedia").exists()) return -3;
            File[] entries = directory.listFiles();
            if (entries == null) return -3;
            Map<String, Character> extensions = scanner.extensions;
            int count = 0;
            for (File entry : entries) {
                if (scanner.canceled) return -4;
                String name = entry.getName();
                if (name.startsWith(".") && (flags & 2) == 0) continue;
                char kind;
                if (entry.isDirectory()) {
                    if (!onlyFolders && (flags & 1) == 0) continue;
                    kind = 'D';
                } else {
                    if (onlyFolders || !entry.isFile()) continue;
                    int dot = name.lastIndexOf('.');
                    if (dot < 0 || dot == name.length() - 1) continue;
                    Character extension = extensions.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
                    if (extension == null) continue;
                    kind = extension.charValue();
                }
                boolean stat = (flags & 0xF0) != 0;
                int time = stat ? seconds(entry.lastModified()) : 0;
                long size = stat ? entry.length() : 0;
                try { scanner.callback.invoke(folder, Character.valueOf(kind), Integer.valueOf(time),
                        Long.valueOf(size), name); }
                catch (Exception e) { return -4; }
                count++;
            }
            return scanner.canceled ? -4 : count;
        } catch (SecurityException e) { return -3; }
    }

    private static int seconds(long millis) {
        long seconds = millis / 1000;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, seconds));
    }
    public static long fuGetFileSize(String path) {
        if (path == null) return 0;
        try { return new File(path).length(); } catch (SecurityException e) { return 0; }
    }
    public static int fuGetFileTime(String path) {
        if (path == null) return 0;
        try { return seconds(new File(path).lastModified()); } catch (SecurityException e) { return 0; }
    }
    public static long fuGetAvailBytes(String path) {
        if (path == null || path.length() == 0) return 0;
        try { return new StatFs(path).getAvailableBytes(); }
        catch (RuntimeException e) { return 0; }
    }
    public static boolean fuHasNoMedia(String path) {
        if (path == null || path.length() == 0) return false;
        try {
            File folder = new File(path).getAbsoluteFile();
            if (folder.isFile()) folder = folder.getParentFile();
            while (folder != null) {
                if (new File(folder, ".nomedia").exists()) return true;
                folder = folder.getParentFile();
            }
        } catch (SecurityException e) { return false; }
        return false;
    }

    /** Copies from byte zero and truncates to the actual copied length. The caller's
     * descriptors remain open; seekable descriptors' original positions are restored.
     * Nonseekable descriptors return false instead of silently doing a partial copy.
     */
    public static boolean fuCopyFD(int source, int destination) {
        if (source < 0 || destination < 0 || source == destination) return false;
        ParcelFileDescriptor input = null, output = null;
        long inputPosition = -1, outputPosition = -1;
        boolean success = false;
        try {
            input = ParcelFileDescriptor.fromFd(source);
            output = ParcelFileDescriptor.fromFd(destination);
            FileDescriptor inFd = input.getFileDescriptor(), outFd = output.getFileDescriptor();
            StructStat inStat = Os.fstat(inFd), outStat = Os.fstat(outFd);
            if (inStat.st_dev == outStat.st_dev && inStat.st_ino == outStat.st_ino) return false;
            // O_APPEND ignores lseek on writes; reject it before touching either file.
            if ((Os.fcntlInt(outFd, OsConstants.F_GETFL, 0) & OsConstants.O_APPEND) != 0) return false;
            inputPosition = Os.lseek(inFd, 0, OsConstants.SEEK_CUR);
            outputPosition = Os.lseek(outFd, 0, OsConstants.SEEK_CUR);
            Os.lseek(inFd, 0, OsConstants.SEEK_SET);
            Os.lseek(outFd, 0, OsConstants.SEEK_SET);
            byte[] bytes = new byte[64 * 1024];
            long copied = 0;
            for (;;) {
                int read;
                try { read = Os.read(inFd, bytes, 0, bytes.length); }
                catch (ErrnoException e) { if (e.errno == OsConstants.EINTR) continue; throw e; }
                if (read == 0) break;
                int written = 0;
                while (written < read) {
                    int n;
                    try { n = Os.write(outFd, bytes, written, read - written); }
                    catch (ErrnoException e) { if (e.errno == OsConstants.EINTR) continue; throw e; }
                    if (n <= 0) throw new IOException("No copy progress");
                    written += n;
                }
                copied += read;
            }
            Os.ftruncate(outFd, copied);
            Os.fsync(outFd);
            success = true;
        } catch (Exception e) { success = false; }
        finally {
            try { if (input != null && inputPosition >= 0)
                Os.lseek(input.getFileDescriptor(), inputPosition, OsConstants.SEEK_SET); }
            catch (Exception e) { success = false; }
            try { if (output != null && outputPosition >= 0)
                Os.lseek(output.getFileDescriptor(), outputPosition, OsConstants.SEEK_SET); }
            catch (Exception e) { success = false; }
            close(input); close(output);
        }
        return success;
    }
    private static void close(ParcelFileDescriptor fd) {
        if (fd != null) try { fd.close(); } catch (IOException ignored) { }
    }

    private static final class Gif {
        final Movie movie;
        final GifMetadata metadata;
        int background;
        boolean closed;
        Gif(Movie movie, GifMetadata metadata) { this.movie = movie; this.metadata = metadata; }
    }
    private static final class GifBuffer {
        final int pixels;
        Bitmap bitmap;
        int gif, frame = -1;
        boolean closed;
        GifBuffer(int pixels) { this.pixels = pixels; }
    }
    private static Gif gif(int handle) { synchronized (GIFS) { return GIFS.get(handle); } }
    private static GifBuffer buffer(int handle) { synchronized (BUFFERS) { return BUFFERS.get(handle); } }

    public static int gifOpenFD(int fd, boolean directBitmap) {
        if (fd < 0) return 0;
        ParcelFileDescriptor duplicate = null;
        InputStream input = null;
        long position = -1;
        try {
            duplicate = ParcelFileDescriptor.fromFd(fd);
            position = Os.lseek(duplicate.getFileDescriptor(), 0, OsConstants.SEEK_CUR);
            Os.lseek(duplicate.getFileDescriptor(), 0, OsConstants.SEEK_SET);
            input = new ParcelFileDescriptor.AutoCloseInputStream(duplicate);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] chunk = new byte[16 * 1024];
            int length;
            while ((length = input.read(chunk)) != -1) {
                if (output.size() > MAX_GIF_BYTES - length) return 0;
                output.write(chunk, 0, length);
            }
            byte[] data = output.toByteArray();
            GifMetadata metadata = GifMetadata.parse(data);
            data = metadata.movieData(data);
            Movie movie = Movie.decodeByteArray(data, 0, data.length);
            if (movie == null || movie.width() != metadata.width || movie.height() != metadata.height
                    || movie.duration() != metadata.duration) return 0;
            int id = token();
            if (id != 0) synchronized (GIFS) { GIFS.put(id, new Gif(movie, metadata)); }
            return id;
        } catch (Exception e) { return 0; }
        catch (OutOfMemoryError e) { return 0; }
        finally {
            try { if (duplicate != null && position >= 0)
                Os.lseek(duplicate.getFileDescriptor(), position, OsConstants.SEEK_SET); }
            catch (Exception ignored) { }
            if (input != null) try { input.close(); } catch (IOException ignored) { }
            else close(duplicate);
        }
    }
    public static void gifClose(int handle) {
        Gif gif;
        synchronized (GIFS) { gif = GIFS.remove(handle); }
        if (gif != null) synchronized (gif) { gif.closed = true; }
        // Release decoded bitmaps even if the original UI's exception path omitted freeBuffer.
        synchronized (BUFFERS) {
            for (GifBuffer buffer : BUFFERS.values()) synchronized (buffer) {
                if (buffer.gif == handle) release(buffer);
            }
        }
    }
    public static int gifAllocBuffer(int pixels) {
        if (pixels <= 0 || pixels > MAX_GIF_PIXELS) return 0;
        int id = token();
        if (id != 0) synchronized (BUFFERS) { BUFFERS.put(id, new GifBuffer(pixels)); }
        return id;
    }
    public static void gifFreeBuffer(int handle) {
        GifBuffer buffer;
        synchronized (BUFFERS) { buffer = BUFFERS.remove(handle); }
        if (buffer != null) synchronized (buffer) { buffer.closed = true; release(buffer); }
    }
    private static void release(GifBuffer buffer) {
        if (buffer.bitmap != null) buffer.bitmap.recycle();
        buffer.bitmap = null; buffer.gif = 0; buffer.frame = -1;
    }
    public static int gifGetDuration(int handle) {
        Gif gif = gif(handle); return gif == null ? 0 : gif.metadata.duration;
    }
    public static int gifGetFrameCount(int handle) {
        Gif gif = gif(handle); return gif == null ? 0 : gif.metadata.delays.length;
    }
    public static int gifGetImageHeight(int handle) {
        Gif gif = gif(handle); return gif == null ? 0 : gif.metadata.height;
    }
    public static int gifGetImageWidth(int handle) {
        Gif gif = gif(handle); return gif == null ? 0 : gif.metadata.width;
    }
    public static void gifSetBkColor(int handle, int color) {
        Gif gif = gif(handle); if (gif != null) synchronized (gif) { gif.background = color; }
    }
    public static int gifDecodeFrame(int handle, int frame, int bufferHandle) {
        Gif gif = gif(handle); GifBuffer buffer = buffer(bufferHandle);
        if (gif == null || buffer == null || frame < 0 || frame >= gif.metadata.delays.length) return 0;
        synchronized (gif) { synchronized (buffer) {
            if (gif.closed || buffer.closed || buffer.pixels < gif.metadata.width * gif.metadata.height) return 0;
            try {
                if (buffer.bitmap == null || buffer.bitmap.getWidth() != gif.metadata.width
                        || buffer.bitmap.getHeight() != gif.metadata.height) {
                    release(buffer);
                    buffer.bitmap = Bitmap.createBitmap(gif.metadata.width, gif.metadata.height, Bitmap.Config.ARGB_8888);
                }
                Canvas canvas = new Canvas(buffer.bitmap);
                canvas.drawColor(0, PorterDuff.Mode.CLEAR);
                // Movie composites frame disposal itself, including resetting at loop start.
                // AOSP Movie chooses the first cumulative end >= time; sample inside
                // the requested interval, not exactly on the preceding frame's end.
                gif.movie.setTime(gif.metadata.starts[frame] + 1);
                gif.movie.draw(canvas, 0, 0);
                buffer.gif = handle; buffer.frame = frame;
                return gif.metadata.delays[frame];
            } catch (RuntimeException e) { release(buffer); return 0; }
            catch (OutOfMemoryError e) { release(buffer); return 0; }
        } }
    }
    public static boolean gifDrawFrame(int handle, int frame, int bufferHandle, Bitmap destination) {
        Gif gif = gif(handle); GifBuffer buffer = buffer(bufferHandle);
        if (gif == null || buffer == null || destination == null) return false;
        synchronized (gif) { synchronized (buffer) {
            if (!drawable(gif, buffer, handle, frame) || destination.isRecycled() || !destination.isMutable()) return false;
            try {
                draw(gif, buffer, destination);
                return true;
            } catch (RuntimeException e) { return false; }
        } }
    }
    public static boolean gifDrawFrame2(int handle, int frame, int bufferHandle, int[] pixels, int sampleSize) {
        Gif gif = gif(handle); GifBuffer buffer = buffer(bufferHandle);
        if (gif == null || buffer == null || pixels == null || sampleSize <= 0) return false;
        synchronized (gif) { synchronized (buffer) {
            if (!drawable(gif, buffer, handle, frame)) return false;
            int width = gif.metadata.width / sampleSize, height = gif.metadata.height / sampleSize;
            if (width <= 0 || height <= 0 || (long) width * height > pixels.length) return false;
            Bitmap destination = null;
            try {
                destination = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                draw(gif, buffer, destination);
                destination.getPixels(pixels, 0, width, 0, 0, width, height);
                return true;
            } catch (RuntimeException e) { return false; }
            catch (OutOfMemoryError e) { return false; }
            finally { if (destination != null) destination.recycle(); }
        } }
    }
    private static boolean drawable(Gif gif, GifBuffer buffer, int handle, int frame) {
        return !gif.closed && !buffer.closed && buffer.bitmap != null
                && buffer.gif == handle && buffer.frame == frame;
    }
    private static void draw(Gif gif, GifBuffer buffer, Bitmap destination) {
        Canvas canvas = new Canvas(destination);
        canvas.drawColor(gif.background, PorterDuff.Mode.SRC);
        canvas.drawBitmap(buffer.bitmap, null,
            new Rect(0, 0, destination.getWidth(), destination.getHeight()), new Paint(Paint.FILTER_BITMAP_FLAG));
    }

    /** Bounded structural parser; pixel decoding and GIF disposal belong to Movie.
     * <=10 ms UI delays are normalized to 100 ms. movieData patches/injects the
     * matching GCE so zero-delay frames remain individually addressable in Movie.
     * Netscape loop count is intentionally ignored: the original UI loops all frames.
     */
    static final class GifMetadata {
        final int width, height, duration;
        final int[] delays, starts, controls;
        GifMetadata(int width, int height, ArrayList<Integer> values, ArrayList<Integer> locations) throws IOException {
            this.width = width; this.height = height;
            delays = new int[values.size()]; starts = new int[values.size()]; controls = new int[values.size()];
            long total = 0;
            for (int i = 0; i < delays.length; i++) {
                starts[i] = (int) total; delays[i] = values.get(i).intValue(); total += delays[i];
                controls[i] = locations.get(i).intValue();
                if (total > Integer.MAX_VALUE) throw new IOException("GIF duration overflow");
            }
            duration = (int) total;
        }
        byte[] movieData(byte[] original) throws IOException {
            int inserted = 0;
            for (int control : controls) if (control < 0) inserted += 8;
            if (original.length > MAX_GIF_BYTES - inserted) throw new IOException("Expanded GIF too large");
            byte[] result = new byte[original.length + inserted];
            int source = 0, target = 0;
            for (int i = 0; i < controls.length; i++) {
                int control = controls[i], delay = delays[i] / 10;
                if (control >= 0) {
                    int count = control - source;
                    System.arraycopy(original, source, result, target, count);
                    target += count; source = control;
                    result[target++] = (byte) delay; result[target++] = (byte) (delay >> 8); source += 2;
                } else {
                    int position = -control - 1, count = position - source;
                    System.arraycopy(original, source, result, target, count);
                    target += count; source = position;
                    byte[] gce = new byte[] {0x21, (byte) 0xf9, 4, 0, (byte) delay, (byte) (delay >> 8), 0, 0};
                    System.arraycopy(gce, 0, result, target, gce.length); target += gce.length;
                }
            }
            System.arraycopy(original, source, result, target, original.length - source);
            return result;
        }
        static GifMetadata parse(byte[] data) throws IOException {
            if (data == null || data.length > MAX_GIF_BYTES) throw new IOException("GIF input too large");
            Cursor cursor = new Cursor(data);
            StringBuilder signature = new StringBuilder();
            for (int i = 0; i < 6; i++) signature.append((char) cursor.read());
            if (!"GIF87a".contentEquals(signature) && !"GIF89a".contentEquals(signature))
                throw new IOException("Not a GIF");
            int width = cursor.word(), height = cursor.word();
            if (width <= 0 || height <= 0 || (long) width * height > MAX_GIF_PIXELS)
                throw new IOException("GIF dimensions unsupported");
            int packed = cursor.read(); cursor.skip(2);
            if ((packed & 128) != 0) cursor.skip(3 * (1 << ((packed & 7) + 1)));
            ArrayList<Integer> delays = new ArrayList<Integer>();
            ArrayList<Integer> controls = new ArrayList<Integer>();
            int delay = 100, pendingControl = -1;
            for (;;) {
                int block = cursor.read();
                if (block == 0x3b) break;
                if (block == 0x21) {
                    int label = cursor.read();
                    if (label == 0xf9) {
                        if (cursor.read() != 4) throw new IOException("Invalid GIF control block");
                        cursor.read(); pendingControl = cursor.position;
                        int raw = cursor.word() * 10; delay = raw <= 10 ? 100 : raw;
                        cursor.read(); if (cursor.read() != 0) throw new IOException("GIF control terminator");
                    } else { cursor.blocks(); if (label == 1) { delay = 100; pendingControl = -1; } }
                } else if (block == 0x2c) {
                    controls.add(Integer.valueOf(pendingControl < 0 ? -cursor.position : pendingControl));
                    int left = cursor.word(), top = cursor.word(), w = cursor.word(), h = cursor.word();
                    if (w <= 0 || h <= 0 || (long) left + w > width || (long) top + h > height)
                        throw new IOException("GIF frame dimensions unsupported");
                    int local = cursor.read();
                    if ((local & 128) != 0) cursor.skip(3 * (1 << ((local & 7) + 1)));
                    int codeSize = cursor.read();
                    if (codeSize < 2 || codeSize > 8) throw new IOException("Invalid GIF LZW size");
                    cursor.blocks(); delays.add(Integer.valueOf(delay)); delay = 100; pendingControl = -1;
                    if (delays.size() > MAX_GIF_FRAMES) throw new IOException("Too many GIF frames");
                } else throw new IOException("Invalid GIF block");
            }
            if (delays.isEmpty()) throw new IOException("GIF has no frames");
            return new GifMetadata(width, height, delays, controls);
        }
    }
    private static final class Cursor {
        final byte[] data; int position;
        Cursor(byte[] data) { this.data = data; }
        int read() throws IOException {
            if (position >= data.length) throw new IOException("Truncated GIF");
            return data[position++] & 255;
        }
        int word() throws IOException { return read() | (read() << 8); }
        void skip(int count) throws IOException {
            if (count < 0 || count > data.length - position) throw new IOException("Truncated GIF block");
            position += count;
        }
        void blocks() throws IOException { int size; while ((size = read()) != 0) skip(size); }
    }
}
