// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/** Exact-row metadata cleanup only after this path is proven physically absent. */
public final class MediaCleanup {
    private static final Pattern PATH_SELECTION = Pattern.compile("\\s*_data\\s*=\\s*\\?\\s*");
    private static final Pattern SHORT_VOLUME = Pattern.compile("[0-9a-fA-F]{4}-[0-9a-fA-F]{4}");
    private static final Pattern UUID_VOLUME = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final String[] TYPED_PROJECTION = {"_id", "_data"};
    private static final String[] FILES_PROJECTION = {"_id", "_data", "media_type"};
    private static final String EXACT_PATH = "_data = ? COLLATE BINARY";

    private MediaCleanup() { }

    /** All actual calls retain ContentResolver.delete return and exception semantics. */
    public static int delete(ContentResolver resolver, Uri uri, String where, String[] args) {
        int kind = collectionKind(uri);
        if (Build.VERSION.SDK_INT < 21 || kind < 0 || where == null
                || !PATH_SELECTION.matcher(where).matches() || args == null || args.length != 1
                || args[0] == null || args[0].indexOf('\0') >= 0 || !new File(args[0]).isAbsolute()) {
            DeleteTiming.cleanupOutcome(0);
            return DeleteTiming.providerDelete(resolver, uri, where, args);
        }

        final String path = args[0];
        if (!isAbsent(path)) {
            DeleteTiming.cleanupOutcome(1);
            return DeleteTiming.providerDelete(resolver, uri, where, args);
        }

        long id = -1;
        int mediaType = kind;
        int exactRows = 0;
        boolean lookupFailed = false;
        DeleteTiming.beginCleanupLookup();
        try {
            Cursor cursor = resolver.query(uri, kind == 0 ? FILES_PROJECTION : TYPED_PROJECTION,
                    "_data = ?", new String[] {path}, null);
            if (cursor == null) throw new IllegalStateException();
            try {
                while (cursor.moveToNext()) {
                    // A NOCASE index can return case-only neighbours. Count exact Java paths only.
                    if (!path.equals(cursor.getString(1))) continue;
                    exactRows++;
                    id = cursor.getLong(0);
                    if (kind == 0) mediaType = cursor.getInt(2);
                }
            } finally {
                cursor.close();
            }
        } catch (Exception ignored) {
            lookupFailed = true;
            DeleteTiming.cleanupOutcome(2);
        } finally {
            DeleteTiming.endCleanupLookup();
        }
        if (!lookupFailed && exactRows == 0) {
            // A complete query found no exact row in the original visible collection.
            // Fast deletion may already have removed it; do not issue another collection delete.
            DeleteTiming.cleanupOutcome(3);
            DeleteTiming.cleanupOutcome(9);
            return 0;
        }
        if (lookupFailed || exactRows != 1 || id <= 0) {
            if (!lookupFailed) DeleteTiming.cleanupOutcome(4);
            return exactFallback(resolver, uri, path);
        }
        // Lookup can block while another actor recreates the same path.
        if (!isAbsent(path)) {
            DeleteTiming.cleanupOutcome(1);
            return 0;
        }
        DeleteTiming.cleanupOutcome(5);

        // Item URIs include pending/trashed rows by default on Q; preserve collection visibility.
        String selection = "_id = ? AND " + EXACT_PATH + " AND media_type = ?";
        if (Build.VERSION.SDK_INT >= 29) selection += " AND is_pending = 0 AND is_trashed = 0";
        String[] selected = {Long.toString(id), path, Integer.toString(mediaType)};
        DeleteTiming.beginCleanupDelete();
        try {
            int rows = DeleteTiming.providerDelete(resolver, ContentUris.withAppendedId(uri, id),
                    selection, selected);
            DeleteTiming.cleanupOutcome(6);
            // A zero result can mean replacement by a new ID. Never broaden a completed call.
            return rows;
        } catch (Exception ignored) {
            DeleteTiming.cleanupOutcome(7);
        } finally {
            DeleteTiming.endCleanupDelete();
        }
        // Providers may throw after partial work. The original collection remains the fallback.
        return exactFallback(resolver, uri, path);
    }

    private static int exactFallback(ContentResolver resolver, Uri uri, String path) {
        // An earlier call may have partially completed, then a new file/row may have appeared.
        if (!isAbsent(path)) {
            DeleteTiming.cleanupOutcome(1);
            return 0;
        }
        DeleteTiming.cleanupOutcome(8);
        return DeleteTiming.providerDelete(resolver, uri, EXACT_PATH, new String[] {path});
    }

    private static boolean isAbsent(String path) {
        DeleteTiming.beginCleanupVerify();
        try {
            File file = new File(path);
            // Recheck aliases as well as lstat before each new cleanup mutation.
            if (!path.equals(file.getAbsolutePath())
                    || !file.getCanonicalFile().equals(file.getAbsoluteFile())) return false;
            try {
                Os.lstat(path);
            } catch (ErrnoException result) {
                return result.errno == OsConstants.ENOENT;
            }
        } catch (Exception ignored) {
            // Permission failures, ENOTDIR and every other uncertainty fail the proof.
        } finally {
            DeleteTiming.endCleanupVerify();
        }
        return false;
    }

    /** 0 Files, 1 Images, 3 Video; every unrecognized route is delegated without a lookup. */
    private static int collectionKind(Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme()) || !"media".equals(uri.getAuthority())
                || uri.getQuery() != null || uri.getFragment() != null) return -1;
        List<String> segments = uri.getPathSegments();
        if (segments.size() < 2 || segments.size() > 3) return -1;
        String volume = segments.get(0);
        if (!"external".equals(volume) && !"external_primary".equals(volume)
                && !SHORT_VOLUME.matcher(volume).matches() && !UUID_VOLUME.matcher(volume).matches()) return -1;
        int kind;
        String route;
        if (segments.size() == 2 && "file".equals(segments.get(1))) {
            kind = 0; route = "file";
        } else if (segments.size() == 3 && "media".equals(segments.get(2))
                && "images".equals(segments.get(1))) {
            kind = 1; route = "images/media";
        } else if (segments.size() == 3 && "media".equals(segments.get(2))
                && "video".equals(segments.get(1))) {
            kind = 3; route = "video/media";
        } else return -1;
        return uri.toString().equals("content://media/" + volume + "/" + route) ? kind : -1;
    }
}
