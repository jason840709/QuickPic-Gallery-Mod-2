// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.Context;
import android.content.UriPermission;
import android.content.ContentResolver;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Synthetic selected paths map to private host files; no real /storage file is used. */
public final class FastMediaDeleteProbe {
    private static int checks;
    private static final List<String> cases = new ArrayList<String>();
    private static final String ROOT = "/storage/ABCD-1234/album/";
    private static final String TREE = "content://com.android.externalstorage.documents/tree/ABCD-1234%3Aalbum";
    private static File work;
    private static int serial;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Target target(String name) throws Exception {
        Target value = new Target(ROOT + name);
        File backing = new File(work, "synthetic-" + (++serial));
        Files.write(backing.toPath(), new byte[] {1, 2, 3});
        Os.bind(value.getAbsolutePath(), backing);
        return value;
    }
    private static Context grant() {
        Context value = new Context();
        value.resolver.grants.add(new UriPermission(TREE, true));
        DeleteTiming.reset();
        return value;
    }
    private static Context indexed(Target file) {
        Context value = grant();
        value.resolver.rows.add(new ContentResolver.Row(71, file.getAbsolutePath(), 1));
        return value;
    }
    private static void unchanged(Context context, Target file, String name, int reason) {
        check(FastMediaDelete.tryDelete(context, file) == -1, name + ": fallback result");
        check(context.resolver.queries == 0 && context.resolver.deletes == 0, name + ": no query or mutation");
        check(Os.present(file.getAbsolutePath()) && file.rawCalls == 0, name + ": physical target retained");
        check(DeleteTiming.events.equals(Arrays.asList(0)), name + ": numeric guard outcome");
        check(DeleteTiming.guards.equals(Arrays.asList(reason)) && DeleteTiming.classifications == 1,
                name + ": exactly one anonymous guard reason and entry classification");
        cases.add(name);
    }
    private static void verifySequence(int... expected) {
        List<Integer> values = new ArrayList<Integer>();
        for (int value : expected) values.add(value);
        check(DeleteTiming.events.equals(values), "balanced numeric timing and outcomes " + values);
    }
    private static final class Target extends File {
        private static final long serialVersionUID = 1L;
        boolean directory, alias, canonicalDenied, directoryDenied;
        int rawCalls;
        Target(String path) { super(path); }
        public boolean isDirectory() {
            if (directoryDenied) throw new SecurityException();
            return directory;
        }
        public boolean delete() { rawCalls++; throw new AssertionError("helper must never raw unlink"); }
        public File getCanonicalFile() throws IOException {
            if (canonicalDenied) throw new IOException();
            return alias ? new File(ROOT + "outside.jpg") : super.getCanonicalFile();
        }
    }
    public static void main(String[] arguments) throws Exception {
        work = new File(arguments[0]);
        work.mkdirs();
        {
            Target unlinked = target("unlink-before-row-error.jpg");
            Context partial = indexed(unlinked);
            partial.resolver.mode = "unlink-before-row-error";
            check(FastMediaDelete.tryDelete(partial, unlinked) == 0,
                    "physical unlink before provider exception returns zero to retain original metadata cleanup");
            check(!Os.present(unlinked.getAbsolutePath()) && partial.resolver.rows.size() == 1
                    && partial.resolver.rows.get(0).path.equals(unlinked.getAbsolutePath()),
                    "counterexample actually retains stale exact row after unlink");
            check(java.util.Collections.frequency(DeleteTiming.events, 7) == 1,
                    "physical gone outcome counted once even when metadata cleanup remains");
            verifySequence(10, 11, 5, 12, 6, 13, 14, 15, 7);
            cases.add("unlink before stale row provider exception");
        }
        Target file = target("selected.jpg");
        Context context = indexed(file);
        check(FastMediaDelete.tryDelete(context, file) == 0, "physical ENOENT returns zero and retains original metadata cleanup");
        check(!Os.present(file.getAbsolutePath()) && context.resolver.deletes == 1 && file.rawCalls == 0,
                "one provider delete removes only selected physical target without raw unlink");
        check(context.resolver.lastDeleteUri.toString().equals("content://media/external/file/71"), "delete selected ID URI");
        check(context.resolver.lastDeleteSelection.equals("_data = ? COLLATE BINARY AND media_type IN (1,3)")
                && Arrays.equals(context.resolver.lastDeleteArgs, new String[] {file.getAbsolutePath()}), "binary exact path and media type guard");
        check(context.resolver.lastQueryUri.toString().equals("content://media/external/file")
                && Arrays.equals(context.resolver.lastProjection, new String[] {"_id", "_data", "media_type"})
                && context.resolver.lastQuerySelection.equals("_data = ?")
                && Arrays.equals(context.resolver.lastQueryArgs, new String[] {file.getAbsolutePath()}), "indexed parameter lookup includes type zero");
        check(context.resolver.cursor.closed, "cursor closed before delete");
        verifySequence(10, 11, 5, 12, 13, 14, 15, 7);
        cases.add("eligible physical removal");

        for (String name : new String[] {"Book/picture.jpg", "book/picture.jpg", "BOOK/picture.jpg", "Book2/picture.jpg", "percent%_quote'[]*.jpg"}) {
            file = target(name); context = indexed(file);
            Target caseControl = target("control/" + name);
            Target prefixControl = target(name + ".keep");
            context.resolver.rows.add(new ContentResolver.Row(72, caseControl.getAbsolutePath(), 1));
            context.resolver.rows.add(new ContentResolver.Row(73, prefixControl.getAbsolutePath(), 3));
            context.resolver.rows.add(new ContentResolver.Row(74, file.getAbsolutePath().toLowerCase(), 1));
            // Avoid creating a duplicate exact row for the intentionally lowercase case.
            if (file.getAbsolutePath().equals(file.getAbsolutePath().toLowerCase())) context.resolver.rows.remove(3);
            check(FastMediaDelete.tryDelete(context, file) == 0, "exact selection succeeds for case/special characters");
            check(Os.present(caseControl.getAbsolutePath()) && Os.present(prefixControl.getAbsolutePath())
                    && context.resolver.rows.size() >= 2, "unselected case and prefix controls remain");
            check(context.resolver.deletes == 1 && context.resolver.cursor.closed, "per-file request and closed lookup");
        }
        cases.add("Book book BOOK prefixes percent underscore quote controls");

        file = target("only-upper.jpg"); context = grant();
        context.resolver.rows.add(new ContentResolver.Row(71, file.getAbsolutePath().toUpperCase(), 1));
        check(FastMediaDelete.tryDelete(context, file) == -1 && context.resolver.deletes == 0
                && Os.present(file.getAbsolutePath()), "NOCASE match without Java exact path does not delete");
        verifySequence(10, 11, 2); cases.add("Java exact case filter");
        file = target("duplicate.jpg"); context = indexed(file);
        context.resolver.rows.add(new ContentResolver.Row(72, file.getAbsolutePath(), 0));
        check(FastMediaDelete.tryDelete(context, file) == -1 && context.resolver.deletes == 0
                && Os.present(file.getAbsolutePath()), "duplicate exact rows reject even when one is non-media");
        verifySequence(10, 11, 3); cases.add("ambiguous exact rows");
        for (int type : new int[] {0, 2, 4}) {
            file = target("non-media-" + type); context = indexed(file); context.resolver.rows.get(0).type = type;
            check(FastMediaDelete.tryDelete(context, file) == -1 && context.resolver.deletes == 0
                    && Os.present(file.getAbsolutePath()), "non image/video row remains for original SAF");
            verifySequence(10, 11, 4);
        }
        cases.add("hidden type zero and other non-media rows");
        for (long id : new long[] {0, -1}) {
            file = target("invalid-id" + id); context = indexed(file); context.resolver.rows.get(0).id = id;
            check(FastMediaDelete.tryDelete(context, file) == -1 && context.resolver.deletes == 0, "nonpositive row ID rejected");
        }
        cases.add("nonpositive IDs");
        file = target("no-index.jpg"); context = grant();
        check(FastMediaDelete.tryDelete(context, file) == -1 && context.resolver.deletes == 0 && Os.present(file.getAbsolutePath()), "unindexed file retained");
        verifySequence(10, 11, 2); cases.add("no index");

        for (String mode : new String[] {"row-only", "unlink-failure", "false", "delete-error", "stat-denied", "stat-notdir", "stat-runtime", "changed-path", "changed-type"}) {
            file = target("fallback-" + mode + ".jpg"); context = indexed(file); context.resolver.mode = mode;
            check(FastMediaDelete.tryDelete(context, file) == -1 && context.resolver.deletes == 1
                    && Os.present(file.getAbsolutePath()), mode + ": no false physical success");
            check(Os.calls == 1 && context.resolver.cursor.closed && file.rawCalls == 0, mode + ": one lstat and no raw unlink");
            verifySequence(mode.equals("delete-error") ? new int[] {10, 11, 5, 12, 6, 13, 14, 15, 8}
                    : new int[] {10, 11, 5, 12, 13, 14, 15, 8});
            cases.add(mode);
        }
        file = target("partial-delete.jpg"); context = indexed(file); context.resolver.mode = "error-after-unlink";
        check(FastMediaDelete.tryDelete(context, file) == 0 && !Os.present(file.getAbsolutePath()), "exception after physical removal confirms ENOENT and retains metadata cleanup");
        verifySequence(10, 11, 5, 12, 6, 13, 14, 15, 7); cases.add("partial delete exception");
        file = target("video.mp4"); context = indexed(file); context.resolver.rows.get(0).type = 3;
        check(FastMediaDelete.tryDelete(context, file) == 0 && context.resolver.deletes == 1, "video type three eligible"); cases.add("video");
        file = target("zero-return-gone.jpg"); context = indexed(file); context.resolver.mode = "zero-after-unlink";
        check(FastMediaDelete.tryDelete(context, file) == 0, "provider row count zero retains confirmed ENOENT success with metadata cleanup"); cases.add("row count independent physical success");
        for (String mode : new String[] {"query-error", "null-cursor", "move-error", "read-error", "close-error"}) {
            file = target(mode + ".jpg"); context = indexed(file); context.resolver.mode = mode;
            check(FastMediaDelete.tryDelete(context, file) == -1 && context.resolver.deletes == 0 && Os.present(file.getAbsolutePath()), mode + ": lookup error leaves physical file");
            check(context.resolver.cursor == null || context.resolver.cursor.closed, mode + ": query cursor closed");
            verifySequence(10, 1, 11); cases.add(mode);
        }

        file = target("directory"); context = indexed(file); file.directory = true; unchanged(context, file, "directory", 3);
        file = target("alias.jpg"); context = indexed(file); file.alias = true; unchanged(context, file, "alias", 4);
        file = target("canonical-denied.jpg"); context = indexed(file); file.canonicalDenied = true; unchanged(context, file, "canonical denied", 4);
        file = target("directory-denied.jpg"); context = indexed(file); file.directoryDenied = true; unchanged(context, file, "directory denied", 6);
        file = target("no-grant.jpg"); context = indexed(file); context.resolver.grants.clear(); unchanged(context, file, "no grant", 5);
        file = target("read-grant.jpg"); context = indexed(file); context.resolver.grants.clear(); context.resolver.grants.add(new UriPermission(TREE, false)); unchanged(context, file, "read-only grant", 5);
        for (String tree : new String[] {"content://evil.documents/tree/ABCD-1234%3Aalbum", "file://com.android.externalstorage.documents/tree/ABCD-1234%3Aalbum", "content://com.android.externalstorage.documents/tree/FFFF-FFFF%3Aalbum", "content://com.android.externalstorage.documents/tree/ABCD-1234%3Aalbum2", "content://com.android.externalstorage.documents/not-a-tree"}) {
            file = target("wrong-grant.jpg"); context = indexed(file); context.resolver.grants.clear(); context.resolver.grants.add(new UriPermission(tree, true)); unchanged(context, file, "invalid tree grant", 5);
        }
        for (String path : new String[] {"/storage/emulated/0/Pictures/keep.jpg", "/storage/ABCD-1234/album2/keep.jpg", "/storage/ABCD-1234/album/../keep.jpg", "/storage/ABCD-1234"}) {
            file = new Target(path); Os.bind(path, new File(work, "guard-" + (++serial))); Files.write(Os.backing(path).toPath(), new byte[] {1}); context = indexed(file); unchanged(context, file, "outside or noncanonical path", path.startsWith("/storage/emulated/0/") ? 1 : path.contains("/../") ? 2 : 5);
        }
        file = target("sdk20.jpg"); context = indexed(file); Build.VERSION.SDK_INT = 20; unchanged(context, file, "pre API21", 0); Build.VERSION.SDK_INT = 21;
        file = target("sdk21.jpg"); context = indexed(file); check(FastMediaDelete.tryDelete(context, file) == 0, "API21 supported"); Build.VERSION.SDK_INT = 29;
        file = target("sdk29.jpg"); context = indexed(file); check(FastMediaDelete.tryDelete(context, file) == 0, "Android10 API29 supported"); Build.VERSION.SDK_INT = 36; cases.add("API21 API29 support");
        context = grant(); check(FastMediaDelete.tryDelete(context, null) == -1 && context.resolver.queries == 0, "null file is guard fallback");
        check(DeleteTiming.guards.equals(Arrays.asList(0)), "null file guard reason");
        file = target("null-context.jpg"); DeleteTiming.reset(); check(FastMediaDelete.tryDelete(null, file) == -1 && Os.present(file.getAbsolutePath()), "null context is guard fallback"); cases.add("null inputs");
        check(DeleteTiming.guards.equals(Arrays.asList(0)), "null context guard reason");
        System.out.println("{\"checks\":" + checks + ",\"cases\":" + cases.size() + ",\"passed\":true}");
    }
}
