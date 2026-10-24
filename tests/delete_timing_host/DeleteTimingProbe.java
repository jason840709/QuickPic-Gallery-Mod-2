// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Production-mode behavior with synthetic IO/UI doubles, never a device benchmark. */
public final class DeleteTimingProbe {
    static int checks;
    static final String SECRET = "synthetic-private-input-never-export";
    static final Uri URI = Uri.parse("content://synthetic/" + SECRET);
    interface Operation { void run() throws Exception; }
    static void check(boolean ok, String message) {
        checks++; if (!ok) throw new AssertionError(message);
    }
    static void sameFailure(Throwable expected, Operation operation) throws Exception {
        try { operation.run(); throw new AssertionError("operation returned instead of throwing"); }
        catch (Throwable found) { check(found == expected, "original exception identity and type"); }
    }
    static Object field(Object value, String name) throws Exception {
        Field f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }
    static Object active() throws Exception {
        Field f = DeleteTiming.class.getDeclaredField("ACTIVE"); f.setAccessible(true);
        return ((ThreadLocal<?>) f.get(null)).get();
    }
    static void cleared() throws Exception {
        check(active() == null, "worker ThreadLocal removed");
        check(DeleteTiming.complete() == null, "repeated completion returns no report");
    }
    static class TestFile extends File {
        int deleteCalls, existsCalls, directoryCalls;
        boolean value; RuntimeException failure; Error fatal;
        TestFile() { super(SECRET); }
        void fail() { if (failure != null) throw failure; if (fatal != null) throw fatal; }
        @Override public boolean delete() { deleteCalls++; fail(); return value; }
        @Override public boolean exists() { existsCalls++; fail(); return value; }
        @Override public boolean isDirectory() { directoryCalls++; fail(); return value; }
        @Override public String getAbsolutePath() { return "/storage/emulated/0/" + SECRET; }
        @Override public String getPath() { throw new AssertionError("helper read private path"); }
        @Override public String toString() { throw new AssertionError("helper rendered private path"); }
    }
    // mode 0: outside worker; 1: worker with no open file; 2: worker with an open file.
    static void wrappers(int mode) throws Exception {
        final TestFile file = new TestFile();
        final ContentResolver resolver = new ContentResolver();
        int routed = MediaCleanup.calls;
        if (mode > 0) DeleteTiming.begin(null, 2);
        if (mode == 2) DeleteTiming.beginFile();
        file.value = true;
        check(DeleteTiming.rawDelete(file), "raw true returned unchanged");
        check(DeleteTiming.exists(file), "exists true returned unchanged");
        check(DeleteTiming.isDirectory(file), "directory true returned unchanged");
        file.value = false;
        check(!DeleteTiming.rawDelete(file), "raw false returned unchanged");
        check(!DeleteTiming.exists(file), "exists false returned unchanged");
        check(!DeleteTiming.isDirectory(file), "directory false returned unchanged");
        final SecurityException error = new SecurityException(SECRET);
        file.failure = error;
        sameFailure(error, () -> DeleteTiming.rawDelete(file));
        sameFailure(error, () -> DeleteTiming.exists(file));
        sameFailure(error, () -> DeleteTiming.isDirectory(file));
        file.failure = null;
        final AssertionError fatal = new AssertionError(SECRET);
        file.fatal = fatal;
        sameFailure(fatal, () -> DeleteTiming.rawDelete(file));
        sameFailure(fatal, () -> DeleteTiming.exists(file));
        sameFailure(fatal, () -> DeleteTiming.isDirectory(file));
        check(file.deleteCalls == 4 && file.existsCalls == 4 && file.directoryCalls == 4,
                "each File wrapper delegates exactly once");
        check(DeleteTiming.deleteDocument(resolver, URI), "SAF true exact result");
        resolver.documentResult = false;
        check(!DeleteTiming.deleteDocument(resolver, URI), "SAF false exact result");
        resolver.missing = new FileNotFoundException(SECRET);
        sameFailure(resolver.missing, () -> DeleteTiming.deleteDocument(resolver, URI));
        resolver.missing = null; resolver.failure = error;
        sameFailure(error, () -> DeleteTiming.deleteDocument(resolver, URI));
        resolver.failure = null; resolver.fatal = fatal;
        sameFailure(fatal, () -> DeleteTiming.deleteDocument(resolver, URI));
        resolver.fatal = null;
        check(resolver.documentCalls == 5 && resolver.lastUri == URI, "SAF preserves Uri and delegates once");
        final String[] arguments = { SECRET };
        final String where = "_data=? /* " + SECRET + " */";
        resolver.rows = 0;
        check(DeleteTiming.mediaDelete(resolver, URI, "_id=?", arguments) == 0, "zero rows exact result");
        resolver.rows = 7;
        check(DeleteTiming.mediaDelete(resolver, URI, where, arguments) == 7, "positive rows exact result");
        check(resolver.lastUri == URI && resolver.lastWhere == where && resolver.lastArgs == arguments,
                "provider receives identical Uri, selection and argument references");
        resolver.rows = -1;
        check(DeleteTiming.mediaDelete(resolver, URI, null, null) == -1, "negative result unchanged");
        resolver.failure = error;
        sameFailure(error, () -> DeleteTiming.mediaDelete(resolver, URI, "_id=?", arguments));
        resolver.failure = null; resolver.fatal = fatal;
        sameFailure(fatal, () -> DeleteTiming.mediaDelete(resolver, URI, "_data=?", arguments));
        resolver.fatal = null;
        check(resolver.mediaCalls == 5, "one provider call per media wrapper invocation");
        check(MediaCleanup.calls - routed == (mode == 2 ? 5 : 0),
                "only ACTIVE plus fileOpen dispatches cleanup stub, including exceptions");
        routed = MediaCleanup.calls;
        check(DeleteTiming.providerDelete(resolver, URI, where, arguments) == -1,
                "direct provider wrapper preserves result");
        check(MediaCleanup.calls == routed && resolver.mediaCalls == 6,
                "providerDelete never redispatches cleanup");
        check(DeleteTiming.complete() == null, "production complete never returns Report");
        cleared();
    }
    static void dispatchLifecycle() throws Exception {
        ContentResolver resolver = new ContentResolver(); resolver.rows = 4;
        int routed = MediaCleanup.calls;
        DeleteTiming.begin(null, 3);
        check(active() != null, "begin retains worker scope with diagnostics disabled");
        DeleteTiming.mediaDelete(resolver, URI, "_data=?", new String[]{SECRET});
        check(MediaCleanup.calls == routed, "closed-file worker delegates original provider");
        DeleteTiming.beginFile();
        check(DeleteTiming.classifyStorage(new TestFile()) == 1, "primary shared path still classified");
        check(DeleteTiming.mediaDelete(resolver, URI, "_data=?", new String[]{SECRET}) == 4
                && MediaCleanup.calls == ++routed, "primary fileOpen still reaches cleanup dispatch stub");
        DeleteTiming.endFile(true);
        DeleteTiming.mediaDelete(resolver, URI, null, null);
        check(MediaCleanup.calls == routed, "endFile disables cleanup dispatch");
        DeleteTiming.beginFile();
        DeleteTiming.mediaDelete(resolver, URI, null, null);
        check(MediaCleanup.calls == ++routed, "next file independently enables cleanup dispatch");
        check(DeleteTiming.complete() == null, "completion with open file returns null");
        cleared();
        DeleteTiming.beginFile(); DeleteTiming.endFile(true);
        DeleteTiming.mediaDelete(resolver, URI, null, null);
        check(MediaCleanup.calls == routed, "post-complete hooks cannot reactivate cleanup");
        cleared();
    }
    static void hooks() {
        DeleteTiming.beginUri(); DeleteTiming.beginUri(); DeleteTiming.endUri(); DeleteTiming.endUri();
        DeleteTiming.beginMetadata(); DeleteTiming.endMetadata();
        DeleteTiming.beginCache(); DeleteTiming.endCache();
        DeleteTiming.beginProgress(); DeleteTiming.endProgress();
        DeleteTiming.beginFastLookup(); DeleteTiming.endFastLookup();
        DeleteTiming.beginFastDelete(); DeleteTiming.endFastDelete();
        DeleteTiming.beginFastVerify(); DeleteTiming.endFastVerify();
        DeleteTiming.beginCleanupVerify(); DeleteTiming.endCleanupVerify();
        DeleteTiming.beginCleanupLookup(); DeleteTiming.endCleanupLookup();
        DeleteTiming.beginCleanupDelete(); DeleteTiming.endCleanupDelete();
        for (int code = -1; code <= 10; code++) {
            DeleteTiming.fastOutcome(code); DeleteTiming.cleanupOutcome(code); DeleteTiming.fastGuard(code);
        }
    }
    static void lifecycle(File work) throws Exception {
        hooks(); cleared();
        DeleteTiming.begin(null, 205);
        int progress = 0;
        for (int i = 0; i < 205; i++) {
            DeleteTiming.beginFile(); hooks(); progress++; DeleteTiming.endFile(i % 2 == 0);
        }
        check(progress == 205, "caller still reports every file without helper batching");
        check((Integer) field(active(), "attempted") == 205, "all file scopes retained before completion");
        check(DeleteTiming.complete() == null, "normal multi-file lifecycle produces no report"); cleared();
        DeleteTiming.begin(null, 2); // Cancellation before the first file.
        check(DeleteTiming.complete() == null, "empty canceled lifecycle returns null"); cleared();
        DeleteTiming.begin(null, 2); DeleteTiming.beginFile(); // Cancellation within an open file.
        check(DeleteTiming.complete() == null, "unfinished canceled lifecycle returns null"); cleared();
        final ContentResolver resolver = new ContentResolver();
        final SecurityException error = new SecurityException(SECRET); resolver.failure = error;
        DeleteTiming.begin(null, 2); DeleteTiming.beginFile();
        try { sameFailure(error, () -> DeleteTiming.mediaDelete(resolver, URI, null, null)); }
        finally { check(DeleteTiming.complete() == null, "throw path finally completes without a report"); }
        cleared();
        DeleteTiming.begin(null, 9); DeleteTiming.beginFile(); hooks();
        Object previous = active();
        DeleteTiming.begin(null, -1);
        Object fresh = active();
        check(fresh != previous && (Integer) field(fresh, "requested") == 0,
                "rebegin replaces old session and clamps requested count");
        check(!(Boolean) field(fresh, "fileOpen") && (Integer) field(fresh, "attempted") == 0,
                "rebegin cannot inherit unfinished file state");
        for (String name : new String[]{"fastOutcomes", "cleanupOutcomes", "fastGuards", "storageCounts"}) {
            for (int count : (int[]) field(fresh, name)) check(count == 0, "rebegin resets internal counters");
        }
        int routed = MediaCleanup.calls; resolver.failure = null;
        DeleteTiming.mediaDelete(resolver, URI, null, null);
        check(MediaCleanup.calls == routed, "rebegin requires a new beginFile before cleanup");
        check(DeleteTiming.complete() == null, "rebegin lifecycle completes silently"); cleared();
        File file = new File(work, "actual-synthetic-file");
        Files.write(file.toPath(), new byte[]{1});
        DeleteTiming.begin(null, 2); DeleteTiming.beginFile();
        check(DeleteTiming.exists(file) && !DeleteTiming.isDirectory(file), "real host file checks preserved");
        check(DeleteTiming.rawDelete(file) && !file.exists(), "real host file is deleted exactly as requested");
        DeleteTiming.endFile(true); DeleteTiming.beginFile();
        check(!DeleteTiming.rawDelete(file), "absent-file raw false result preserved");
        DeleteTiming.endFile(false); check(DeleteTiming.complete() == null, "real-file lifecycle silent"); cleared();
    }
    static DeleteTiming.Report syntheticReport() throws Exception {
        Object session = active();
        Constructor<DeleteTiming.Report> c = DeleteTiming.Report.class.getDeclaredConstructor(
                session.getClass(), long.class);
        c.setAccessible(true);
        return c.newInstance(session, System.nanoTime());
    }
    static void silentOutputs(File work) throws Exception {
        File directory = new File(work, "private"); directory.mkdirs();
        File old = new File(directory, "delete-timing-latest.json");
        byte[] original = "{\"historical_diagnostic_fixture\":true}\n".getBytes(StandardCharsets.UTF_8);
        Files.write(old.toPath(), original);
        Files.setLastModifiedTime(old.toPath(), FileTime.fromMillis(1111111111000L));
        long originalTime = old.lastModified();
        Context app = new Context(directory);
        Context ui = new Context(new File(work, "ui-must-not-write")); ui.application = app;
        Dialog owner = new Dialog(ui);
        Method save = DeleteTiming.class.getDeclaredMethod("save", Context.class, DeleteTiming.Report.class);
        save.setAccessible(true);
        for (int requested : new int[]{0, 1, 2, 205}) {
            DeleteTiming.begin(owner, requested);
            if (requested > 0) { DeleteTiming.beginFile(); DeleteTiming.endFile(true); }
            // Production complete cannot create one. Construct a test-only report to challenge finish/save guards.
            DeleteTiming.Report report = syntheticReport();
            check(DeleteTiming.complete() == null, "UI-owned completion produces no report"); cleared();
            int contextReads = owner.contextReads;
            DeleteTiming.finish(owner, report); DeleteTiming.finish(owner, report);
            DeleteTiming.finish(null, report); DeleteTiming.finish(owner, null);
            check(owner.contextReads == contextReads, "finish returns before reading UI context");
            check(report.uiCompletionDelayNanos() == -1L, "finish does not update synthetic report");
            save.invoke(null, app, report); save.invoke(null, app, null); save.invoke(null, null, report);
            check(app.writes == 0 && ui.writes == 0, "guarded save makes no openFileOutput calls");
        }
        check(AlertDialog.shown == 0 && AlertDialog.last == null, "no diagnostic dialog displayed");
        check(Toast.made == 0 && Toast.shown == 0 && Toast.last == null, "no diagnostic Toast constructed or displayed");
        check(app.clipboard.writes == 0 && ui.clipboard.writes == 0 && app.clipboard.clip == null,
                "no clipboard writes");
        check(app.serviceReads == 0 && ui.serviceReads == 0, "no clipboard service lookup");
        check(java.util.Arrays.equals(original, Files.readAllBytes(old.toPath()))
                && old.lastModified() == originalTime, "historical private JSON content and timestamp unchanged");
        check(!ui.directory.exists() && directory.list().length == 1,
                "no new private diagnostic files; historical file left intact");
        check(Uri.reads == 0, "wrappers never inspect URI text");
    }
    static void isolated() throws Exception {
        DeleteTiming.begin(null, 3); DeleteTiming.beginFile();
        final Object parent = active();
        CountDownLatch ready = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                check(active() == null, "child cannot inherit parent worker scope");
                DeleteTiming.begin(null, 7); DeleteTiming.beginFile();
                check(active() != parent && (Integer) field(active(), "requested") == 7,
                        "child owns independent worker scope");
                ready.countDown(); finish.await();
                check(DeleteTiming.complete() == null, "child completes silently"); cleared();
            } catch (Throwable t) { failure.set(t); ready.countDown(); }
        });
        worker.start(); ready.await();
        check(active() == parent && (Boolean) field(parent, "fileOpen"), "parent open-file scope unaffected by child");
        DeleteTiming.endFile(true); check(DeleteTiming.complete() == null, "parent completes silently"); cleared();
        finish.countDown(); worker.join();
        if (failure.get() != null) throw new AssertionError("child probe failure", failure.get());
        cleared();
    }
    public static void main(String[] args) throws Exception {
        File work = new File(args[0]); work.mkdirs();
        Field enabled = DeleteTiming.class.getDeclaredField("DIAGNOSTICS_ENABLED"); enabled.setAccessible(true);
        check(!enabled.getBoolean(null), "production diagnostics switch is false");
        wrappers(0); wrappers(1); wrappers(2); dispatchLifecycle(); lifecycle(work); isolated(); silentOutputs(work);
        String result = "{\"passed\":true,\"checks\":" + checks
                + ",\"complete_returns_null_and_clears_scope\":true,\"synthetic_report_finish_silent\":true"
                + ",\"guarded_save_no_open_file_output\":true,\"historical_json_unchanged\":true"
                + ",\"active_file_dispatch_preserved\":true,\"io_results_and_exceptions_preserved\":true"
                + ",\"lifecycle_and_thread_isolation\":true}\n";
        Files.write(new File(work, "production-results.json").toPath(), result.getBytes(StandardCharsets.UTF_8));
        System.out.println("checks=" + checks);
    }
}
