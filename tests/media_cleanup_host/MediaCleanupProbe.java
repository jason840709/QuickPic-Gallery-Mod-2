// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real temporary paths; provider and lstat failures are controlled host doubles. */
public final class MediaCleanupProbe {
    private static int checks, serial;
    private static File work;
    private static final List<String> cases = new ArrayList<String>();
    private static final String IMAGE = "content://media/external/images/media";
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static ContentResolver resolver() {
        Build.VERSION.SDK_INT = 29;
        DeleteTiming.reset();
        return new ContentResolver();
    }
    private static String path(String name) throws Exception {
        File parent = new File(work, "case-" + (++serial));
        Files.createDirectories(new File(parent, "Book").toPath());
        return new File(parent, "Book/" + name).getAbsolutePath();
    }
    private static ContentResolver.Row row(ContentResolver r, long id, String p, int type) {
        ContentResolver.Row value = new ContentResolver.Row(id, p, type, "ABCD-1234");
        r.rows.add(value); return value;
    }
    private static int call(ContentResolver r, String uri, String p) {
        return MediaCleanup.delete(r, Uri.parse(uri), "_data = ?", new String[] {p});
    }
    private static boolean item(ContentResolver.Call call) {
        String uri = call.uri.toString();
        return uri.matches(".*/[0-9]+$");
    }
    private static void actualCalls(ContentResolver r, int expected) {
        check(r.calls.size() == expected && DeleteTiming.providerCalls == expected,
                "every actual call uses providerDelete");
    }
    private static void sequence(Integer... expected) {
        check(DeleteTiming.events.equals(Arrays.asList(expected)), "balanced anonymous timing outcomes");
    }
    private static void delegated(Uri uri, String where, String[] args, String name) {
        ContentResolver r = resolver(); r.mode = "delegate-only";
        check(MediaCleanup.delete(r, uri, where, args) == 17, name + ": delegate return");
        actualCalls(r, 1);
        ContentResolver.Call c = r.calls.get(0);
        check(r.queries == 0 && Os.calls == 0, name + ": no lookup or lstat");
        check(c.uri == uri && c.selection == where && c.args == args, name + ": untouched original arguments");
        sequence(0);
    }
    private static void originalFallback(String p, String name, int expectedStats) {
        ContentResolver r = new ContentResolver(); r.mode = "delegate-only";
        Uri uri = Uri.parse(IMAGE); String where = "_data = ?"; String[] args = {p};
        check(MediaCleanup.delete(r, uri, where, args) == 17, name + ": original fallback return");
        actualCalls(r, 1);
        ContentResolver.Call c = r.calls.get(0);
        check(r.queries == 0 && Os.calls == expectedStats && c.uri == uri && c.selection == where && c.args == args,
                name + ": absence uncertainty preserves original call without query");
        sequence(10, 11, 1);
    }
    public static void main(String[] arguments) throws Exception {
        work = new File(arguments[0]).getCanonicalFile(); Files.createDirectories(work.toPath());
        {
            ContentResolver r = resolver(); String p = path("a%_quote'[]*.jpg");
            String casePath = p.replace("/Book/", "/book/");
            String prefixPath = p + ".keep";
            // Host storage may be case-insensitive; case-only controls are provider rows.
            File physicalControl = new File(new File(p).getParentFile(), "unselected-control.jpg");
            Files.write(physicalControl.toPath(), new byte[] {1});
            Files.write(new File(prefixPath).toPath(), new byte[] {2});
            row(r, 71, p, 1); row(r, 72, casePath, 1); row(r, 73, prefixPath, 1); row(r, 74, p, 3);
            ContentResolver.Row pending = row(r, 75, p, 1); pending.pending = 1;
            ContentResolver.Row otherVolume = row(r, 76, p, 1); otherVolume.volume = "FFFF-FFFF";
            // external is a union; this makes a duplicate visible. Use an explicit volume for the test.
            int result = call(r, "content://media/ABCD-1234/images/media", p);
            check(result == 1 && r.rows.size() == 5, "only exact image row in original volume removed result=" + result + " rows=" + r.rows.size() + " events=" + DeleteTiming.events);
            actualCalls(r, 1);
            check(r.queryUri.toString().equals("content://media/ABCD-1234/images/media")
                    && Arrays.equals(r.queryProjection, new String[] {"_id", "_data"})
                    && r.querySelection.equals("_data = ?") && Arrays.equals(r.queryArgs, new String[] {p}),
                    "lookup uses original typed collection and indexed parameter");
            check(r.calls.get(0).uri.toString().equals("content://media/ABCD-1234/images/media/71")
                    && Arrays.equals(r.calls.get(0).args, new String[] {"71", p, "1"}),
                    "delete keeps original typed URI, row ID, exact path and media type");
            check(r.cursor.closed && Files.exists(physicalControl.toPath()) && Files.exists(new File(prefixPath).toPath())
                    && Files.isDirectory(new File(p).getParentFile().toPath()), "unselected files and parent remain");
            sequence(10, 11, 12, 13, 10, 11, 5, 14, 6, 15);
            cases.add("typed volume metadata cleanup; case prefix wildcard quote physical controls");
        }
        {
            String[] uris = {"content://media/external/file", "content://media/external_primary/images/media",
                    "content://media/ABCD-1234/video/media", "content://media/01234567-89ab-cdef-0123-456789abcdef/file"};
            int[] types = {0, 1, 3, 2}; int[] apis = {21, 28, 29, 36};
            for (int i = 0; i < uris.length; i++) {
                ContentResolver r = resolver(); Build.VERSION.SDK_INT = apis[i]; String p = path("type-" + types[i]);
                ContentResolver.Row target = row(r, 71, p, types[i]); target.volume = Uri.parse(uris[i]).getPathSegments().get(0);
                check(call(r, uris[i], p) == 1, "supported collection and API metadata cleanup");
                check(r.calls.get(0).selection.contains("is_pending") == (apis[i] >= 29), "pending/trash columns gated at Q");
                check(r.queryProjection.length == (types[i] == 0 || types[i] == 2 ? 3 : 2), "typed projection excludes media_type");
                actualCalls(r, 1);
            }
            cases.add("API21 API28 API29 API36; primary FAT UUID; Files type0 and non-image metadata");
        }
        {
            ContentResolver r = resolver(); String p = path("whitespace.jpg"); row(r, 71, p, 1);
            check(MediaCleanup.delete(r, Uri.parse(IMAGE), " \t_data  = ? \n", new String[] {p}) == 1 && r.queries == 1,
                    "strict parameter selection permits only whitespace variation");
            cases.add("whitespace-only SQL variation");
        }
        {
            resolver(); String p = path("existing.jpg"); Files.write(new File(p).toPath(), new byte[] {1});
            originalFallback(p, "existing physical file", 1); check(Files.exists(new File(p).toPath()), "helper never raw deletes existing file");
            resolver(); String dir = path("directory"); Files.createDirectories(new File(dir).toPath()); originalFallback(dir, "existing directory", 1);
            resolver(); String aliasBase = path("alias.jpg"); File parent = new File(aliasBase).getParentFile();
            File link = new File(parent.getParentFile(), "alias"); Files.createSymbolicLink(link.toPath(), parent.toPath());
            originalFallback(new File(link, "missing.jpg").getAbsolutePath(), "symlinked parent", 0);
            resolver(); String broken = path("dangling-link"); Files.createSymbolicLink(new File(broken).toPath(), new File(work, "missing-destination").toPath());
            originalFallback(broken, "dangling symlink is still an object", 1);
            resolver(); String dot = new File(parent, "../Book/missing.jpg").getAbsolutePath(); originalFallback(dot, "dot-dot alias", 0);
            resolver(); originalFallback(aliasBase.replace("/Book/", "/Book//"), "duplicate slash spelling", 0);
            cases.add("existing file directory symlink dotdot and normalized aliases keep original call");
        }
        for (int errno : new int[] {OsConstants.EACCES, OsConstants.ENOTDIR}) {
            resolver(); Os.error = errno; originalFallback(path("stat-uncertain"), "errno " + errno, 1);
        }
        resolver(); Os.runtime = true; originalFallback(path("stat-runtime"), "stat permission exception", 1);
        cases.add("lstat EACCES ENOTDIR runtime never mean absent");
        {
            String p = path("input.jpg");
            for (String uri : new String[] {"file:///external/images/media", "content://other/external/images/media", "content://media/internal/images/media",
                    "content://media/unknown/images/media", "content://media/external/images/media/71", "content://media/external/audio/media",
                    "content://media/external/images/thumbnails", "content://media/external/images/media?", "content://media/external/images/media?includePending=1",
                    "content://media/external/images/media#", "content://media/external/images/media/", "content://media//external/images/media",
                    "content://media/external%5Fprimary/images/media", "content://media/external/downloads", "content://media/external/file/71"})
                delegated(Uri.parse(uri), "_data = ?", new String[] {p}, "unsupported URI");
            for (String sql : new String[] {"_data LIKE ?", "(_data = ?)", "_data = ? OR 1=1", "_data = ? COLLATE NOCASE", "_DATA = ?", "_data=?--comment", "_id = ?", ""})
                delegated(Uri.parse(IMAGE), sql, new String[] {p}, "unsupported SQL");
            delegated(Uri.parse(IMAGE), null, new String[] {p}, "null SQL");
            for (String[] args : new String[][] {null, {}, {p, p}, {null}, {"relative.jpg"}, {p + '\0'}})
                delegated(Uri.parse(IMAGE), "_data = ?", args, "unsupported arguments");
            delegated(null, "_data = ?", new String[] {p}, "null URI");
            ContentResolver r = resolver(); Build.VERSION.SDK_INT = 20; r.mode = "delegate-only";
            check(call(r, IMAGE, p) == 17 && r.queries == 0 && Os.calls == 0, "pre21 delegates original");
            cases.add("unknown authority volume item query fragment route SQL argument and pre21 delegate");
        }
        {
            ContentResolver r = resolver(); String p = path("only-case.jpg"); String neighbour = p.replace("/Book/", "/book/"); row(r, 72, neighbour, 1);
            check(call(r, IMAGE, p) == 0 && r.rows.size() == 1, "NOCASE-only lookup leaves neighbour without redundant delete");
            check(r.cursor.closed, "complete no-exact lookup closes cursor before no-op");
            sequence(10, 11, 12, 13, 3, 9); actualCalls(r, 0);
            r = resolver(); r.deleteAllowed = false; p = path("none.jpg");
            check(call(r, IMAGE, p) == 0 && r.queries == 1 && r.cursor.closed, "successful empty lookup skips even unavailable delete API");
            sequence(10, 11, 12, 13, 3, 9); actualCalls(r, 0);
            r = resolver(); p = path("duplicate.jpg"); row(r, 71, p, 1); row(r, 72, p, 1); row(r, 73, p.replace("/Book/", "/book/"), 1);
            check(call(r, IMAGE, p) == 2 && r.rows.size() == 1 && !item(r.calls.get(0)), "duplicate exact rows use original collection binary fallback");
            sequence(10, 11, 12, 13, 4, 10, 11, 8);
            for (long id : new long[] {0, -1}) {
                r = resolver(); p = path("bad-id.jpg"); row(r, id, p, 1);
                check(call(r, IMAGE, p) == 1 && !item(r.calls.get(0)), "nonpositive ID falls back exactly");
            }
            cases.add("complete empty or case-only lookup has no delete; duplicates and bad IDs retain exact fallback");
        }
        for (String mode : new String[] {"query-error", "null-cursor", "move-error", "read-error", "close-error"}) {
            ContentResolver r = resolver(); r.mode = mode; String p = path("query-failure.jpg"); row(r, 71, p, 1); row(r, 72, p.replace("/Book/", "/book/"), 1);
            check(call(r, IMAGE, p) == 1 && r.rows.size() == 1 && !item(r.calls.get(0)), mode + ": exact fallback");
            check(r.cursor == null || r.cursor.closed, mode + ": closes cursor on failure");
            sequence(10, 11, 12, 2, 13, 10, 11, 8); actualCalls(r, 1);
        }
        cases.add("query null move read close failures use exact fallback and close cursors");
        {
            for (String mode : new String[] {"query-error", "null-cursor", "move-error", "close-error"}) {
                ContentResolver r = resolver(); r.mode = mode; String p = path("uncertain-empty.jpg");
                check(call(r, IMAGE, p) == 0 && r.queries == 1, mode + ": empty but incomplete lookup is not proof");
                actualCalls(r, 1); sequence(10, 11, 12, 2, 13, 10, 11, 8);
            }
            cases.add("zero rows with failed query null cursor move or close retains provider fallback");
        }
        {
            ContentResolver r = resolver(); r.mode = "recreate-in-query";
            String p = path("empty-recreated.jpg"); row(r, 72, p.replace("/Book/", "/book/"), 1);
            check(call(r, IMAGE, p) == 0 && r.rows.size() == 1, "no exact snapshot remains no-op if same path is recreated");
            actualCalls(r, 0); sequence(10, 11, 12, 13, 3, 9);
            check(Arrays.equals(Files.readAllBytes(new File(p).toPath()), new byte[] {9, 8, 7}),
                    "no-op preserves newly recreated physical file and case-only row");
            cases.add("empty exact snapshot with physical recreation performs no mutation");
        }

        {
            for (String typeUri : new String[] {IMAGE, "content://media/external/video/media", "content://media/external/file"}) {
                for (boolean pending : new boolean[] {true, false}) {
                    ContentResolver r = resolver(); String p = path("invisible.jpg"); int type = typeUri.contains("video") ? 3 : 1;
                    ContentResolver.Row hidden = row(r, 71, p, type); if (pending) hidden.pending = 1; else hidden.trashed = 1;
                    check(call(r, typeUri, p) == 0 && r.rows.size() == 1 && r.cursor.closed, "original collection pending/trash exclusion retained");
                    actualCalls(r, 0); sequence(10, 11, 12, 13, 3, 9);
                }
            }
            cases.add("Q plain Files Images Video pending and trash rows remain excluded");
        }
        for (String mode : new String[] {"replace-id", "changed-path", "changed-type", "changed-pending", "changed-trashed", "id-zero", "id-negative"}) {
            ContentResolver r = resolver(); r.mode = mode; String p = path("race.jpg"); row(r, 71, p, 1);
            check(call(r, IMAGE, p) == (mode.equals("id-negative") ? -1 : 0) && r.rows.size() == 1,
                    mode + ": current row constraints prevent unintended deletion");
            actualCalls(r, 1); check(item(r.calls.get(0)), "completed item call never broadens to collection");
            sequence(10, 11, 12, 13, 10, 11, 5, 14, 6, 15);
        }
        cases.add("changed ID path type pending trash and normal zero negative do not broaden");
        for (String mode : new String[] {"recreate-in-query", "recreate-and-query-error", "stat-denied-in-query"}) {
            ContentResolver r = resolver(); r.mode = mode; String p = path("lookup-recreated.jpg"); row(r, 71, p, 1);
            check(call(r, IMAGE, p) == 0 && r.rows.size() == 1, mode + ": no mutation after absence proof becomes invalid");
            actualCalls(r, 0);
            check(r.cursor == null || r.cursor.closed, mode + ": cursor closed before recheck");
            check(Os.calls == 2 && DeleteTiming.events.get(DeleteTiming.events.size() - 1) == 1,
                    mode + ": recheck reports absence proof unavailable");
            if (!mode.equals("stat-denied-in-query"))
                check(Arrays.equals(Files.readAllBytes(new File(p).toPath()), new byte[] {9, 8, 7}),
                        mode + ": real replacement file remains intact");
        }
        cases.add("lookup replacement or later permission uncertainty prevents ID and fallback mutations");
        {
            ContentResolver r = resolver(); r.mode = "partial-recreate"; String p = path("partial-recreated.jpg"); row(r, 71, p, 1);
            check(call(r, IMAGE, p) == 0 && r.rows.size() == 1 && r.rows.get(0).id == 72,
                    "partial provider failure retains replacement row with new ID");
            actualCalls(r, 1);
            check(item(r.calls.get(0)) && Os.calls == 3,
                    "replacement after ID exception is rechecked and never sent to collection fallback");
            check(Arrays.equals(Files.readAllBytes(new File(p).toPath()), new byte[] {9, 8, 7}),
                    "real replacement physical file survives partial provider failure");
            sequence(10, 11, 12, 13, 10, 11, 5, 14, 7, 15, 10, 11, 1);
            cases.add("ID partial throw with real physical replacement and new row ID");
        }
        for (String mode : new String[] {"id-error", "partial-error"}) {
            ContentResolver r = resolver(); r.mode = mode; String p = path("partial.jpg"); row(r, 71, p, 1); row(r, 72, p.replace("/Book/", "/book/"), 1);
            check(call(r, IMAGE, p) == (mode.equals("partial-error") ? 0 : 1) && r.rows.size() == 1,
                    mode + ": fallback returns actual result after partial work");
            actualCalls(r, 2); check(item(r.calls.get(0)) && !item(r.calls.get(1)) && r.calls.get(1).uri.toString().equals(IMAGE), "exception fallback preserves original typed collection");
            sequence(10, 11, 12, 13, 10, 11, 5, 14, 7, 15, 10, 11, 8);
        }
        cases.add("item exception before and after partial mutation uses exact fallback real rows");
        {
            ContentResolver r = resolver(); r.mode = "both-errors"; String p = path("both-errors.jpg"); row(r, 71, p, 1);
            boolean thrown = false; try { call(r, IMAGE, p); } catch (SecurityException e) { thrown = e.getMessage().equals("fallback failure"); }
            check(thrown && r.rows.size() == 1, "fallback exception propagates without synthetic success"); actualCalls(r, 2);
            r = resolver(); r.deleteAllowed = false; p = path("delete-denied.jpg"); row(r, 71, p, 1);
            thrown = false; try { call(r, IMAGE, p); } catch (SecurityException e) { thrown = true; }
            check(thrown && r.rows.size() == 1 && r.queries == 1, "provider delete permission remains enforced"); actualCalls(r, 2);
            r = resolver(); r.queryAllowed = false; p = path("query-denied.jpg"); row(r, 71, p, 1);
            check(call(r, IMAGE, p) == 1 && r.queries == 1 && !item(r.calls.get(0)), "query denial cannot produce item deletion; original provider decides fallback permission");
            DeleteTiming.reset(); thrown = false;
            try { MediaCleanup.delete(null, null, null, null); } catch (NullPointerException e) { thrown = true; }
            check(thrown && DeleteTiming.providerCalls == 1, "unsupported original exception propagates");
            cases.add("query and delete permission enforcement; fallback and unsupported exceptions propagate");
        }
        System.out.println("{\"checks\":" + checks + ",\"cases\":" + cases.size()
                + ",\"lookup_recreated_physical_file_preserved\":true,\"partial_throw_recreated_file_and_new_id_preserved\":true,\"passed\":true}");
    }
}
