// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.Dialog;
import android.content.Context;
import android.provider.MediaStore;
import android.util.Log;
import android.net.Uri;
import android.system.Os;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.widget.Toast;
import java.io.File;
import java.util.Collection;

/** Search-only bridge to the existing whole-folder deletion implementation. */
public final class SearchDelete {
    private SearchDelete() {}

    private static File local(Uri uri) {
        if (uri == null || !"file".equals(uri.getScheme()) || uri.getQuery() != null
                || uri.getFragment() != null || (uri.getAuthority() != null && !uri.getAuthority().isEmpty())) return null;
        String path = uri.getPath();
        if (path == null || !path.startsWith("/")) return null;
        File file = new File(path);
        return file.getAbsolutePath().equals(path) ? file : null;
    }

    private static boolean gone(File file) {
        try {
            if (!file.getAbsolutePath().equals(file.getCanonicalPath())) return false;
            Os.lstat(file.getAbsolutePath());
            return false;
        } catch (ErrnoException e) {
            return e.errno == OsConstants.ENOENT;
        } catch (Exception e) { return false; }
    }

    public static boolean isFile(Uri uri) {
        File file = local(uri);
        try { return file != null && file.isFile() && file.getAbsolutePath().equals(file.getCanonicalPath()); }
        catch (Exception e) { return false; }
    }

    public static boolean afterFile(Context context, Uri uri) {
        File file = local(uri);
        if (file == null || !gone(file)) return false;
        try {
            MediaCleanup.delete(context.getContentResolver(), MediaStore.Files.getContentUri("external"),
                    "_data = ?", new String[] {file.getAbsolutePath()});
        } catch (Exception e) { Log.w("SearchDelete", "File removed; media index cleanup failed", e); }
        return true;
    }

    public static boolean alreadyGone(Uri uri, FolderDelete.Result result) {
        File file = local(uri);
        return file != null && result != null && result.wasDeleted(file) && gone(file);
    }

    public static boolean deleteFolder(Dialog dialog, Uri uri, FolderDelete.Result result) {
        File file = local(uri);
        if (file == null || result == null) return false;
        // Parent + child may both be selected. Only a confirmed deleted parent covers the child.
        if (result.wasDeleted(file) && gone(file)) return true;
        if (!file.isDirectory()) return false;
        return FolderDelete.deleteOne(dialog, file, result) && gone(file);
    }

    /** Exact directory boundary, plus physical absence; never removes a newly recreated entry. */
    public static boolean covered(Uri candidate, Collection<Uri> deletedFolders) {
        if (deletedFolders == null || deletedFolders.isEmpty()) return false;
        File file = local(candidate);
        if (file == null) return false;
        String path = file.getAbsolutePath();
        for (Uri uri : deletedFolders) {
            File parent = local(uri);
            if (parent == null) continue;
            String root = parent.getAbsolutePath();
            if ((path.equals(root) || path.startsWith(root + "/")) && gone(parent) && gone(file)) return true;
        }
        return false;
    }

    public static void finish(Dialog dialog, boolean failed) {
        if (failed) Toast.makeText(dialog.getContext(), "無法刪除部分資料夾，請確認存取權限。", Toast.LENGTH_LONG).show();
    }
}
