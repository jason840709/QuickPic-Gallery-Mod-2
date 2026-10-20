package com.alensw.jni;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Private, per-directory date journal. Never changes media files or their timestamps.
 * Real creation time is used when distinguishable from the platform's mtime fallback.
 * Existing files without it use mtime on first indexing; subsequent new children use
 * first observation, so preserved archive timestamps do not hide a new download.
 */
public final class MediaDates {
    private static final int MAGIC = 0x51445032;
    private static final int MAX_ENTRIES = 1000000;
    private static volatile File store;
    private static long lastArrival;
    private static final Map<String, Long> created = new ConcurrentHashMap<String, Long>();
    private static final Map<String, Long> modified = new ConcurrentHashMap<String, Long>();
    private static final Map<String, Map<String, Long>> directories = new HashMap<String, Map<String, Long>>();
    private static final Set<String> baseline = new HashSet<String>();
    private static final Set<String> newRoots = new HashSet<String>();
    private MediaDates() {}

    public static void configure(Context context) {
        if (context == null) return;
        try {
            File files = context.getFilesDir();
            if (files == null) return;
            File location = new File(files, "quickpic-date-index-v1");
            if (location.isDirectory() || location.mkdirs()) store = location;
        } catch (RuntimeException e) { /* optional index must not break startup */ }
    }

    public static long modified(File file) {
        if (file == null) return 0;
        Long value = modified.get(file.getAbsolutePath());
        return value != null ? value.longValue() : safeModified(file);
    }

    public static long created(File file) {
        if (file == null) return 0;
        String path = file.getAbsolutePath();
        Long value = created.get(path);
        if (value != null) return value.longValue();
        File parent = file.getParentFile();
        if (parent != null) synchronized (MediaDates.class) { load(parent); }
        value = created.get(path);
        if (value != null) return value.longValue();
        long result = initial(file);
        created.put(path, Long.valueOf(result));
        return result;
    }

    public static synchronized boolean isNew(File file) {
        if (file == null) return false;
        for (File parent = file; parent != null; parent = parent.getParentFile())
            if (newRoots.contains(parent.getAbsolutePath())) return true;
        return false;
    }

    /** Called on the scanner worker after listFiles and before invoking original callbacks. */
    public static synchronized void observeDirectory(File directory, File[] entries) {
        if (directory == null || entries == null || entries.length > MAX_ENTRIES) return;
        String path = directory.getAbsolutePath();
        Map<String, Long> previous = load(directory);
        boolean hasBaseline = baseline.contains(path);
        boolean inheritedNew = isNew(directory);
        long now = System.currentTimeMillis();
        boolean arrivalAssigned = false;
        Map<String, Long> next = new HashMap<String, Long>();
        for (File entry : entries) {
            String name = entry.getName(), entryPath = entry.getAbsolutePath();
            Long stamp = previous.get(name);
            if (stamp == null) {
                boolean added = hasBaseline || inheritedNew;
                // Newly observed children have an explicit local arrival time. It
                // takes precedence over historical timestamps preserved by extraction.
                if (added && !arrivalAssigned) {
                    now = Math.max(now, lastArrival + 1);
                    lastArrival = now;
                    arrivalAssigned = true;
                }
                stamp = Long.valueOf(added ? now : initial(entry));
                if (added && entry.isDirectory()) newRoots.add(entryPath);
            }
            next.put(name, stamp);
            created.put(entryPath, stamp);
            modified.put(entryPath, Long.valueOf(safeModified(entry)));
        }
        Set<String> removed = new HashSet<String>();
        for (String name : previous.keySet()) if (!next.containsKey(name))
            removed.add(new File(directory, name).getAbsolutePath());
        if (!removed.isEmpty()) invalidate(removed);
        directories.put(path, next);
        // A directory is a baseline only after a complete list was obtained. A
        // failed/inaccessible list must not mark every old file as newly added.
        baseline.add(path);
        if (!hasBaseline || !next.equals(previous)) save(directory, next);
    }

    private static Map<String, Long> load(File directory) {
        String path = directory.getAbsolutePath();
        Map<String, Long> cached = directories.get(path);
        if (cached != null) return cached;
        Map<String, Long> result = new HashMap<String, Long>();
        File target = target(directory);
        if (target != null) {
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(new AtomicFile(target).openRead()))) {
                if (input.readInt() != MAGIC || !path.equals(input.readUTF())) throw new IOException("Unrecognized date index");
                long generation = input.readLong();
                File parent = directory.getParentFile();
                if (parent != null) {
                    // Validate the parent first. A removed/recreated album can
                    // have the same page names and historical mtimes, but its
                    // parent's arrival generation changes across process restarts.
                    Long current = load(parent).get(directory.getName());
                    if (current != null && current.longValue() != generation) {
                        newRoots.add(path);
                        throw new IOException("Recreated directory");
                    }
                }
                int count = input.readInt();
                if (count < 0 || count > MAX_ENTRIES) throw new IOException("Invalid entry count");
                for (int i = 0; i < count; i++) {
                    String name = input.readUTF(); long time = input.readLong();
                    if (name.length() == 0 || name.indexOf('/') >= 0 || name.equals("..") || time < 0)
                        throw new IOException("Invalid date entry");
                    result.put(name, Long.valueOf(time));
                }
                baseline.add(path);
                for (Map.Entry<String, Long> value : result.entrySet())
                    created.put(new File(directory, value.getKey()).getAbsolutePath(), value.getValue());
            } catch (IOException e) { result.clear(); }
            catch (RuntimeException e) { result.clear(); }
        }
        directories.put(path, result);
        return result;
    }

    private static boolean under(String path, Set<String> roots) {
        for (File parent = new File(path); parent != null; parent = parent.getParentFile())
            if (roots.contains(parent.getAbsolutePath())) return true;
        return false;
    }
    private static void invalidate(Set<String> removed) {
        for (String path : new ArrayList<String>(directories.keySet())) if (under(path, removed)) {
            directories.remove(path); baseline.remove(path);
        }
        for (String path : new ArrayList<String>(created.keySet())) if (under(path, removed)) created.remove(path);
        for (String path : new ArrayList<String>(modified.keySet())) if (under(path, removed)) modified.remove(path);
        for (String path : new ArrayList<String>(newRoots)) if (under(path, removed)) newRoots.remove(path);
        // Old disk journals are rejected by the parent generation check. Avoid
        // scanning/deleting the entire index directory when one album is removed.
    }

    private static void save(File directory, Map<String, Long> values) {
        File target = target(directory);
        if (target == null) return;
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            DataOutputStream data = new DataOutputStream(new BufferedOutputStream(output));
            data.writeInt(MAGIC); data.writeUTF(directory.getAbsolutePath());
            Long generation = created.get(directory.getAbsolutePath());
            data.writeLong(generation == null ? initial(directory) : generation.longValue());
            data.writeInt(values.size());
            // Stable serialization makes inspection and repeated writes reproducible.
            for (String name : new TreeSet<String>(values.keySet())) { data.writeUTF(name); data.writeLong(values.get(name)); }
            data.flush(); atomic.finishWrite(output);
        } catch (IOException e) { if (output != null) atomic.failWrite(output); }
        catch (RuntimeException e) { if (output != null) atomic.failWrite(output); }
    }

    private static File target(File directory) {
        File root = store;
        if (root == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(directory.getAbsolutePath().getBytes("UTF-8"));
            char[] hex = "0123456789abcdef".toCharArray(), name = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                name[i * 2] = hex[(digest[i] & 255) >>> 4];
                name[i * 2 + 1] = hex[digest[i] & 15];
            }
            return new File(root, new String(name) + ".idx");
        } catch (Exception e) { return null; }
    }

    private static long safeModified(File file) {
        try { return Math.max(0, file.lastModified()); } catch (SecurityException e) { return 0; }
    }

    private static long initial(File file) {
        long fallback = safeModified(file);
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                long birth = Api26.creation(file);
                // Android's provider can substitute mtime when birth time is absent.
                if (birth > 0 && birth != fallback && birth <= System.currentTimeMillis() + 60000) return birth;
            } catch (IOException e) { /* inaccessible or unsupported */ }
            catch (RuntimeException e) { /* unsupported provider */ }
            catch (LinkageError e) { /* older or incomplete device runtime */ }
        }
        return fallback;
    }
    private static final class Api26 {
        static long creation(File file) throws IOException {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class).creationTime().toMillis();
        }
    }
}
