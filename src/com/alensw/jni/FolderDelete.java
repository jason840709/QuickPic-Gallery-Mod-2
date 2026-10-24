// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.Dialog;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

/** Deletes an explicitly selected folder and all its contents, on the existing worker. */
public final class FolderDelete {
    private static final String AUTHORITY = "com.android.externalstorage.documents";
    private FolderDelete() {}

    public static final class Result {
        private final int total;
        private final Set<String> deleted = new HashSet<String>();
        private final Set<String> attempted = new HashSet<String>();
        private final Set<String> failed = new HashSet<String>();
        private int metadataWarnings;
        private boolean cancelled;
        private boolean permissionOffered;
        public Result(int count) { total = count; }
        public boolean wasDeleted(File file) {
            String path = file.getAbsolutePath();
            for (String parent : deleted) if (within(path, parent)) return true;
            return false;
        }
        public boolean wasAttempted(File file) { return attempted.contains(file.getAbsolutePath()); }
        @SuppressWarnings("rawtypes")
        public void settleSelected(ArrayList selected) {
            for (Object value : selected) if (value instanceof File && wasDeleted((File) value)) {
                String path = ((File) value).getAbsolutePath();
                deleted.add(path);
                failed.remove(path);
            }
        }
        public int successes() { return deleted.size(); }
        public int failures() { return failed.size(); }
        public int metadataWarnings() { return metadataWarnings; }
        public int unfinished() { return Math.max(0, total - deleted.size() - failed.size()); }
        public boolean shouldRequestPermission(File file) {
            if (permissionOffered || !failed.contains(file.getAbsolutePath()) || !isSecondaryPath(file)) return false;
            permissionOffered = true;
            return true;
        }
    }

    private static final class Location {
        final String volume, relative;
        Location(String value, String path) { volume = value; relative = path; }
    }

    private static String clean(String value) {
        StringBuilder result = new StringBuilder();
        for (String part : value.split("/")) {
            if (part.length() == 0) continue;
            if (".".equals(part) || "..".equals(part) || part.indexOf('\0') >= 0) return null;
            if (result.length() > 0) result.append('/');
            result.append(part);
        }
        return result.toString();
    }

    private static boolean within(String path, String parent) {
        return path.equals(parent) || path.startsWith(parent + "/");
    }

    private static Location locate(File file) {
        if (file == null) return null;
        String path = file.getAbsolutePath();
        String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
        while (primary.endsWith("/") && primary.length() > 1) primary = primary.substring(0, primary.length() - 1);
        if (within(path, primary)) {
            String relative = clean(path.substring(primary.length()));
            return relative == null ? null : new Location("primary", relative);
        }
        String[] roots = {"/storage/", "/mnt/media_rw/", "/mnt/"};
        for (String root : roots) {
            if (!path.startsWith(root)) continue;
            String rest = path.substring(root.length());
            int slash = rest.indexOf('/');
            String volume = slash < 0 ? rest : rest.substring(0, slash);
            if (!volume.matches("[A-Za-z0-9][A-Za-z0-9_-]*") || "emulated".equals(volume) || "self".equals(volume)) return null;
            String relative = clean(slash < 0 ? "" : rest.substring(slash + 1));
            return relative == null ? null : new Location(volume, relative);
        }
        return null;
    }

    /** No provider queries or file creation. Root volumes are never returned. */
    public static Uri documentFor(Context context, File file) {
        if (Build.VERSION.SDK_INT < 21 || context == null) return null;
        Location target = locate(file);
        if (target == null || target.relative.length() == 0) return null;
        Uri best = null;
        int length = -1;
        try {
            for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                Uri tree = permission.getUri();
                if (!permission.isWritePermission() || tree == null || !"content".equals(tree.getScheme()) || !AUTHORITY.equals(tree.getAuthority())) continue;
                try {
                    String id = DocumentsContract.getTreeDocumentId(tree);
                    int colon = id.indexOf(':');
                    if (colon <= 0 || !target.volume.equalsIgnoreCase(id.substring(0, colon))) continue;
                    String parent = clean(id.substring(colon + 1));
                    if (parent == null || (parent.length() > 0 && !within(target.relative, parent))) continue;
                    if (parent.length() > length) {
                        best = DocumentsContract.buildDocumentUriUsingTree(tree, id.substring(0, colon) + ":" + target.relative);
                        length = parent.length();
                    }
                } catch (RuntimeException ignored) { /* A non-tree or malformed grant cannot authorize another path. */ }
            }
        } catch (RuntimeException ignored) { return null; }
        return best;
    }

    public static boolean isSecondaryPath(File file) {
        Location location = locate(file);
        return location != null && !"primary".equalsIgnoreCase(location.volume);
    }

    public static boolean mayContinue(Dialog dialog, Result result) {
        if (dialog.isShowing()) return true;
        result.cancelled = true;
        return false;
    }

    /** Merge model snapshots by exact path, retaining selected models if they are no longer globally visible. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ArrayList snapshot(ArrayList all, ArrayList selected) {
        LinkedHashMap<String, Object> models = new LinkedHashMap<String, Object>();
        for (Object value : all) if (value instanceof File) models.put(((File) value).getAbsolutePath(), value);
        for (Object value : selected) if (value instanceof File) models.put(((File) value).getAbsolutePath(), value);
        return new ArrayList(models.values());
    }

    /**
     * ExternalStorageProvider may follow directory symlinks during recursive deletion.
     * Inspect directories before sending one request; unreadable or aliased trees use raw unlink.
     */
    static boolean safeForSaf(Dialog dialog, File folder) throws IOException {
        if (!dialog.isShowing()) throw new Cancelled();
        if (!folder.getAbsoluteFile().equals(folder.getCanonicalFile()) || !folder.isDirectory()) return false;
        File[] children = folder.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (!dialog.isShowing()) throw new Cancelled();
            // A file symlink is unlinked by the provider; directory symlinks would be entered.
            if (child.isDirectory() && !safeForSaf(dialog, child)) return false;
        }
        return true;
    }

    private static boolean root(File file) throws IOException {
        String path = file.getCanonicalPath();
        Location location = locate(file);
        return file.getParentFile() == null || "/".equals(path) || "/storage".equals(path) || "/mnt".equals(path)
                || file.getCanonicalFile().equals(Environment.getExternalStorageDirectory().getCanonicalFile())
                || (location != null && location.relative.length() == 0);
    }

    private static boolean link(File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        File normalized = parent == null ? file.getAbsoluteFile() : new File(parent.getCanonicalFile(), file.getName());
        return !normalized.getCanonicalFile().equals(normalized.getAbsoluteFile());
    }

    private static void raw(Dialog dialog, File file, String boundary) throws IOException {
        if (!dialog.isShowing()) throw new Cancelled();
        if (link(file)) {
            // Unlink the selected entry itself; never enter a symlink's target.
            if (!file.delete()) throw new IOException("Cannot remove symbolic link");
            return;
        }
        String canonical = file.getCanonicalPath();
        if (!within(canonical, boundary)) throw new IOException("Path left selected folder");
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list folder");
            for (File child : children) raw(dialog, child, boundary);
        }
        if (!file.delete()) throw new IOException("Cannot remove entry");
    }

    private static void cleanupMedia(Context context, File file, String canonical) throws IOException {
        String path = file.getAbsolutePath();
        String selection = "_data = ? COLLATE BINARY OR (_data >= ? COLLATE BINARY AND _data < ? COLLATE BINARY)";
        String[] args;
        if (canonical != null && !canonical.equals(path)) {
            selection = "(" + selection + ") OR (" + selection + ")";
            args = new String[] {path, path + "/", path + "0", canonical, canonical + "/", canonical + "0"};
        } else {
            args = new String[] {path, path + "/", path + "0"};
        }
        int removed = context.getContentResolver().delete(MediaStore.Files.getContentUri("external"), selection, args);
        if (removed < 0) throw new IOException("MediaStore cleanup failed");
    }

    private static final class Cancelled extends IOException {
        private static final long serialVersionUID = 1L;
    }

    /** Directory preflight then one SAF request for a readable unaliased tree, otherwise raw traversal. */
    public static boolean deleteOne(Dialog dialog, File file, Result result) {
        if (!mayContinue(dialog, result)) return false;
        String path = file.getAbsolutePath();
        result.attempted.add(path);
        try {
            if (root(file)) throw new IOException("Storage root is not an album folder");
            // An aliased ancestor would relocate the entire selected tree outside its visible path.
            // A leaf symlink is different: raw() only unlinks that entry itself.
            if (!file.getAbsoluteFile().equals(file.getCanonicalFile()) && !link(file))
                throw new IOException("Selected folder has an aliased ancestor");
            boolean covered = false;
            for (String deleted : result.deleted) {
                if (within(path, deleted)) { covered = true; break; }
            }
            if (covered) { result.deleted.add(path); return true; }
            Uri document = documentFor(dialog.getContext(), file);
            if (document != null && safeForSaf(dialog, file)) {
                // A provider request already in progress cannot be interrupted by the dialog.
                if (!DocumentsContract.deleteDocument(dialog.getContext().getContentResolver(), document))
                    throw new IOException("Document provider rejected deletion");
            } else {
                if (!file.exists() && !link(file)) throw new IOException("Folder is missing or unreadable");
                // A selected symlink is unlinked, so its live target must not be cleaned from MediaStore.
                String canonical = link(file) ? null : file.getCanonicalPath();
                raw(dialog, file, file.getCanonicalPath());
                try {
                    cleanupMedia(dialog.getContext(), file, canonical);
                } catch (Exception warning) {
                    result.metadataWarnings++;
                    Log.w("FolderDelete", "Folder removed; MediaStore cleanup failed", warning);
                }
            }
            result.deleted.add(path);
            return true;
        } catch (Cancelled cancelled) {
            result.cancelled = true;
            return false;
        } catch (Exception failure) {
            result.failed.add(path);
            return false;
        }
    }

    /** Called by the original worker's UI completion callback. */
    public static void finish(Dialog dialog, Result result) {
        // Keep actionable failures, but do not show completion counts or diagnostic summaries.
        if (result == null || result.failures() == 0) return;
        Toast.makeText(dialog.getContext(), "無法刪除部分資料夾，請確認存取權限。", Toast.LENGTH_LONG).show();
    }
}
