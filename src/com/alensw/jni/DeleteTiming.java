// SPDX-License-Identifier: MIT
package com.alensw.jni;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/** Selected-image worker scope and IO wrappers; diagnostic output is disabled.
 * No wrapper changes the underlying operation's return value or exception.
 * The caller still owns cancellation, retries, model changes and every progress update.
 */
public final class DeleteTiming {
    private static final boolean DIAGNOSTICS_ENABLED = false;
    private static final ThreadLocal<Session> ACTIVE = new ThreadLocal<Session>();
    private static final int LIMIT = 200;
    private static final int RAW = 0, EXISTS = 1, DIRECTORY = 2, SAF = 3, URI = 4,
            METADATA = 5, MEDIA = 6, CACHE = 7, PROGRESS = 8,
            FAST_LOOKUP = 9, FAST_DELETE = 10, FAST_VERIFY = 11,
            CLEANUP_VERIFY = 12, CLEANUP_LOOKUP = 13, CLEANUP_DELETE = 14;
    private static final String[] KEYS = {"raw_delete", "exists", "is_directory",
            "saf_delete_document", "uri", "metadata_total", "media_delete", "cache", "progress",
            "fast_lookup", "fast_media_delete", "fast_verify",
            "cleanup_verify", "cleanup_lookup", "cleanup_id_delete"};
    private static final String[] LABELS = {"原始檔案刪除", "檔案存在檢查", "資料夾檢查",
            "SAF 刪除", "URI 建立", "媒體資料清理總計", "MediaStore delete", "快取排程／請求", "進度回報",
            "快速分支查詢", "快速分支 MediaStore 刪除", "快速分支實檔確認",
            "清理前實檔確認", "清理索引查詢", "清理單筆 ID 刪除"};
    private static final String[] FAST_OUTCOME_KEYS = {"guard_skip", "query_error", "no_exact_row",
            "ambiguous_rows", "non_media_row", "eligible_row", "delete_error", "physical_gone",
            "still_present_or_unconfirmed"};
    private static final String[] FAST_OUTCOME_LABELS = {"條件未符而跳過", "查詢例外", "沒有精確對應列",
            "對應列不唯一", "不是可嘗試的媒體列", "符合可嘗試條件的列", "刪除例外", "已確認實檔消失",
            "實檔仍存在或未能確認"};
    private static final String[] CLEANUP_OUTCOME_KEYS = {"unsupported_input_delegate", "absent_proof_unavailable",
            "lookup_error", "no_exact_row", "ambiguous_or_badid", "eligible_id", "id_call_completed",
            "id_call_error", "exact_path_fallback", "no_exact_row_noop"};
    private static final String[] CLEANUP_OUTCOME_LABELS = {"輸入不支援而委派原呼叫", "未能證明實檔已消失",
            "清理查詢例外", "沒有精確對應列", "對應列不唯一或 ID 無效", "符合單筆 ID 清理條件",
            "單筆 ID 呼叫已返回", "單筆 ID 呼叫例外", "回到精確路徑清理", "查無對應紀錄，略過重複清理"};
    private static final String[] FAST_GUARD_KEYS = {"null_or_old_api", "primary_shared_storage", "unrecognized_storage",
            "directory", "alias_or_canonical_error", "missing_write_tree", "setup_error"};
    private static final String[] FAST_GUARD_LABELS = {"空輸入或舊 API", "主要共用儲存空間", "無法辨識儲存空間",
            "資料夾", "別名或正規路徑例外", "沒有對應寫入樹狀授權", "準備階段例外"};
    private static final String[] STORAGE_KEYS = {"unknown", "primary_shared", "secondary"};
    private static final String[] STORAGE_LABELS = {"無法辨識", "主要共用儲存空間", "次要儲存空間"};
    private DeleteTiming() { }

    /** Called on the selected-image worker, never on a general IO thread entry point. */
    public static void begin(Dialog dialog, int requested) {
        ACTIVE.remove();
        Context application = null;
        try {
            if (dialog != null) application = dialog.getContext().getApplicationContext();
        } catch (RuntimeException ignored) { /* Measurement must not prevent deletion. */ }
        ACTIVE.set(new Session(application, Math.max(0, requested)));
    }

    public static void beginFile() {
        Session s = ACTIVE.get();
        if (s == null) return;
        long now = System.nanoTime();
        if (s.fileOpen) s.closeFile(false, false, now);
        s.attempted++;
        s.fileStart = now;
        s.fileOpen = true;
        s.storageRecorded = false;
    }

    public static void endFile(boolean returnedSuccess) {
        Session s = ACTIVE.get();
        if (s != null && s.fileOpen) s.closeFile(returnedSuccess, true, System.nanoTime());
    }

    public static boolean rawDelete(File file) {
        Session s = ACTIVE.get();
        if (s == null) return file.delete();
        classifyStorage(file);
        s.stages[RAW].begin();
        boolean returned = false, result = false;
        try {
            result = file.delete();
            returned = true;
            return result;
        } finally {
            s.stages[RAW].end();
            s.booleanResult(RAW, returned, result);
        }
    }

    public static boolean exists(File file) {
        Session s = ACTIVE.get();
        if (s == null) return file.exists();
        s.stages[EXISTS].begin();
        boolean returned = false, result = false;
        try {
            result = file.exists();
            returned = true;
            return result;
        } finally {
            s.stages[EXISTS].end();
            s.booleanResult(EXISTS, returned, result);
        }
    }

    public static boolean isDirectory(File file) {
        Session s = ACTIVE.get();
        if (s == null) return file.isDirectory();
        s.stages[DIRECTORY].begin();
        boolean returned = false, result = false;
        try {
            result = file.isDirectory();
            returned = true;
            return result;
        } finally {
            s.stages[DIRECTORY].end();
            s.booleanResult(DIRECTORY, returned, result);
        }
    }

    public static boolean deleteDocument(ContentResolver resolver, Uri uri)
            throws FileNotFoundException {
        Session s = ACTIVE.get();
        if (s == null) return DocumentsContract.deleteDocument(resolver, uri);
        s.stages[SAF].begin();
        boolean returned = false, result = false;
        try {
            result = DocumentsContract.deleteDocument(resolver, uri);
            returned = true;
            return result;
        } finally {
            s.stages[SAF].end();
            s.booleanResult(SAF, returned, result);
        }
    }

    public static int mediaDelete(ContentResolver resolver, Uri uri, String where, String[] args) {
        Session s = ACTIVE.get();
        if (s == null) return resolver.delete(uri, where, args);
        s.stages[METADATA].begin();
        try {
            return s.fileOpen ? MediaCleanup.delete(resolver, uri, where, args)
                    : providerDelete(resolver, uri, where, args);
        } finally {
            s.stages[METADATA].end();
        }
    }

    /** Actual provider request only; preparation belongs to the outer metadata span. */
    public static int providerDelete(ContentResolver resolver, Uri uri, String where, String[] args) {
        Session s = ACTIVE.get();
        if (s == null) return resolver.delete(uri, where, args);
        // Only classify fixed column tokens. Never retain SQL, URI, arguments or errors.
        boolean id = token(where, "_id"), path = token(where, "_data");
        if (id && path) s.sqlMixed++;
        else if (id) s.sqlById++;
        else if (path) s.sqlByPath++;
        else s.sqlOther++;
        s.stages[MEDIA].begin();
        boolean returned = false;
        int rows = 0;
        try {
            rows = resolver.delete(uri, where, args);
            returned = true;
            return rows;
        } finally {
            s.stages[MEDIA].end();
            if (!returned) s.mediaErrors++;
            else if (rows == 0) s.mediaZero++;
            else if (rows > 0) { s.mediaPositive++; s.mediaRows += rows; }
            else s.mediaNegative++;
        }
    }

    public static void beginUri() { beginStage(URI); }
    public static void endUri() { endStage(URI); }
    public static void beginMetadata() { beginStage(METADATA); }
    public static void endMetadata() { endStage(METADATA); }
    public static void beginCache() { beginStage(CACHE); }
    public static void endCache() { endStage(CACHE); }
    public static void beginProgress() { beginStage(PROGRESS); }
    public static void endProgress() { endStage(PROGRESS); }
    public static void beginFastLookup() { beginStage(FAST_LOOKUP); }
    public static void endFastLookup() { endStage(FAST_LOOKUP); }
    public static void beginFastDelete() { beginStage(FAST_DELETE); }
    public static void endFastDelete() { endStage(FAST_DELETE); }
    public static void beginFastVerify() { beginStage(FAST_VERIFY); }
    public static void endFastVerify() { endStage(FAST_VERIFY); }
    public static void beginCleanupVerify() { beginStage(CLEANUP_VERIFY); }
    public static void endCleanupVerify() { endStage(CLEANUP_VERIFY); }
    public static void beginCleanupLookup() { beginStage(CLEANUP_LOOKUP); }
    public static void endCleanupLookup() { endStage(CLEANUP_LOOKUP); }
    public static void beginCleanupDelete() { beginStage(CLEANUP_DELETE); }
    public static void endCleanupDelete() { endStage(CLEANUP_DELETE); }

    public static void cleanupOutcome(int code) {
        Session s = ACTIVE.get();
        if (s != null && code >= 0 && code < s.cleanupOutcomes.length) s.cleanupOutcomes[code]++;
    }
    public static void fastGuard(int code) {
        Session s = ACTIVE.get();
        if (s != null && code >= 0 && code < s.fastGuards.length) s.fastGuards[code]++;
    }

    /** Lexical classification only: 0 unknown, 1 primary shared, 2 secondary. Never stat or retain a path. */
    public static int classifyStorage(File file) {
        Session s = ACTIVE.get();
        if (s != null && s.fileOpen && s.storageRecorded) return s.fileStorage;
        int kind = 0;
        try {
            if (file != null) {
                String path = file.getAbsolutePath();
                String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
                while (primary.endsWith("/") && primary.length() > 1)
                    primary = primary.substring(0, primary.length() - 1);
                if (path.equals(primary) || path.startsWith(primary + "/")) kind = 1;
                else if (FolderDelete.isSecondaryPath(file)) kind = 2;
            }
        } catch (Exception ignored) { kind = 0; }
        if (s != null && s.fileOpen) {
            s.storageCounts[kind]++;
            s.fileStorage = kind;
            s.storageRecorded = true;
        }
        return kind;
    }

    /** Fixed anonymous event codes only; several outcomes can belong to one file. */
    public static void fastOutcome(int code) {
        Session s = ACTIVE.get();
        if (s != null && code >= 0 && code < s.fastOutcomes.length) s.fastOutcomes[code]++;
    }

    /** Always remove worker state; production mode creates and saves no report. */
    public static Report complete() {
        Session s = ACTIVE.get();
        ACTIVE.remove();
        if (!DIAGNOSTICS_ENABLED || s == null) return null;
        long now = System.nanoTime();
        if (s.fileOpen) s.closeFile(false, false, now);
        for (Stage stage : s.stages) stage.close(now);
        Report report = new Report(s, now);
        save(s.application, report);
        return report;
    }

    private static void save(Context application, Report report) {
        // One fixed app-private file, overwritten per run. No external-storage permission.
        if (!DIAGNOSTICS_ENABLED || application == null) return;
        try {
            FileOutputStream output = application.openFileOutput(
                    "delete-timing-latest.json", Context.MODE_PRIVATE);
            try { output.write(report.toJson().getBytes("UTF-8")); }
            finally { output.close(); }
        } catch (IOException ignored) { /* The on-screen report remains available. */ }
        catch (RuntimeException ignored) { /* Storage failure cannot fail a deletion. */ }
    }

    /** Called on the UI completion path. Themed UI context is used only for this dialog. */
    public static void finish(Dialog owner, Report report) {
        if (!DIAGNOSTICS_ENABLED || owner == null || report == null) return;
        report.markUiCompletion(System.nanoTime());
        try {
            Context ui = owner.getContext();
            final Context application = ui.getApplicationContext();
            save(application, report);
            if (report.requested < 2 || report.attempted == 0) return;
            final String text = report.toText();
            TextView content = new TextView(ui);
            content.setText(text);
            content.setTextIsSelectable(true);
            content.setPadding(24, 16, 24, 16);
            ScrollView scroll = new ScrollView(ui);
            scroll.addView(content);
            final AlertDialog summary = new AlertDialog.Builder(ui)
                    .setTitle("刪除計時").setView(scroll)
                    .setPositiveButton("複製結果", null).setNegativeButton("關閉", null).create();
            summary.show();
            // Keep the scrollable report open after copying.
            summary.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (application == null) return;
                    try {
                        ClipboardManager clipboard = (ClipboardManager) application.getSystemService(
                                Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("刪除計時", text));
                            Toast.makeText(application, "已複製刪除計時", Toast.LENGTH_SHORT).show();
                        }
                    } catch (RuntimeException ignored) { /* Diagnostic UI is best effort. */ }
                }
            });
        } catch (RuntimeException ignored) { /* Finishing Activity must not crash completion. */ }
    }

    private static void beginStage(int stage) {
        Session s = ACTIVE.get();
        if (s != null) s.stages[stage].begin();
    }
    private static void endStage(int stage) {
        Session s = ACTIVE.get();
        if (s != null) s.stages[stage].end();
    }
    private static boolean token(String sql, String token) {
        if (sql == null) return false;
        for (int i = 0; i + token.length() <= sql.length(); i++) {
            if (!sql.regionMatches(true, i, token, 0, token.length())) continue;
            int end = i + token.length();
            if ((i == 0 || !identifier(sql.charAt(i - 1)))
                    && (end == sql.length() || !identifier(sql.charAt(end)))) return true;
        }
        return false;
    }
    private static boolean identifier(char c) {
        return c == '_' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }
    private static long elapsed(long start, long end) { return Math.max(0L, end - start); }

    private static final class Stage {
        int calls, depth;
        long start, nanos;
        void begin() { if (depth++ == 0) { calls++; start = System.nanoTime(); } }
        void end() { if (depth > 0 && --depth == 0) nanos += elapsed(start, System.nanoTime()); }
        void close(long now) { if (depth > 0) { nanos += elapsed(start, now); depth = 0; } }
    }
    private static final class Session {
        final Context application;
        final int requested, sdk = Build.VERSION.SDK_INT;
        final long start = System.nanoTime();
        final Stage[] stages = new Stage[KEYS.length];
        final int[] trueCount = new int[4], falseCount = new int[4], errors = new int[4];
        final int[] fastOutcomes = new int[FAST_OUTCOME_KEYS.length];
        final int[] cleanupOutcomes = new int[CLEANUP_OUTCOME_KEYS.length],
                fastGuards = new int[FAST_GUARD_KEYS.length], storageCounts = new int[STORAGE_KEYS.length];
        final ArrayList<FileResult> files = new ArrayList<FileResult>();
        int attempted, reportedSuccesses, unfinished, sqlById, sqlByPath, sqlMixed, sqlOther,
                mediaZero, mediaPositive, mediaNegative, mediaErrors;
        long fileStart, fileNanos, mediaRows;
        boolean fileOpen, storageRecorded;
        int fileStorage;
        Session(Context application, int requested) {
            this.application = application;
            this.requested = requested;
            for (int i = 0; i < stages.length; i++) stages[i] = new Stage();
        }
        void booleanResult(int stage, boolean returned, boolean result) {
            if (!returned) errors[stage]++;
            else if (result) trueCount[stage]++;
            else falseCount[stage]++;
        }
        void closeFile(boolean returnedSuccess, boolean completed, long now) {
            long nanos = elapsed(fileStart, now);
            fileNanos += nanos;
            if (returnedSuccess) reportedSuccesses++;
            if (!completed) unfinished++;
            if (files.size() < LIMIT) files.add(new FileResult(attempted, returnedSuccess, completed, nanos));
            fileOpen = false;
        }
    }

    public static final class FileResult {
        public final int ordinal;
        public final boolean returnedSuccess, completed;
        public final long nanos;
        FileResult(int ordinal, boolean returnedSuccess, boolean completed, long nanos) {
            this.ordinal = ordinal; this.returnedSuccess = returnedSuccess; this.completed = completed; this.nanos = nanos;
        }
    }

    /** Anonymous worker snapshot plus a numeric UI-completion latch.
     * No Dialog, Context, File, Uri, SQL or Throwable reference is retained.
     */
    public static final class Report {
        public final int sdk, requested, attempted, reportedSuccesses, unfinished;
        public final long totalNanos, fileNanos, mediaRows, workerStartNanos, workerEndNanos;
        private volatile long uiCompletionNanos = Long.MIN_VALUE;
        public final int sqlById, sqlByPath, sqlMixed, sqlOther,
                mediaZero, mediaPositive, mediaNegative, mediaErrors;
        private final int[] calls, trueCount, falseCount, errors, fastOutcomes, cleanupOutcomes, fastGuards, storageCounts;
        private final long[] nanos;
        private final FileResult[] files;
        Report(Session s, long now) {
            sdk = s.sdk; requested = s.requested; attempted = s.attempted; reportedSuccesses = s.reportedSuccesses;
            unfinished = s.unfinished; totalNanos = elapsed(s.start, now); fileNanos = s.fileNanos;
            workerStartNanos = s.start; workerEndNanos = now;
            mediaRows = s.mediaRows; sqlById = s.sqlById; sqlByPath = s.sqlByPath;
            sqlMixed = s.sqlMixed; sqlOther = s.sqlOther; mediaZero = s.mediaZero;
            mediaPositive = s.mediaPositive; mediaNegative = s.mediaNegative; mediaErrors = s.mediaErrors;
            calls = new int[s.stages.length]; nanos = new long[s.stages.length];
            for (int i = 0; i < calls.length; i++) { calls[i] = s.stages[i].calls; nanos[i] = s.stages[i].nanos; }
            trueCount = s.trueCount.clone(); falseCount = s.falseCount.clone(); errors = s.errors.clone();
            fastOutcomes = s.fastOutcomes.clone();
            cleanupOutcomes = s.cleanupOutcomes.clone(); fastGuards = s.fastGuards.clone();
            storageCounts = s.storageCounts.clone();
            files = s.files.toArray(new FileResult[s.files.size()]);
        }
        private synchronized void markUiCompletion(long now) {
            if (uiCompletionNanos == Long.MIN_VALUE) uiCompletionNanos = now;
        }
        /** -1 means finish has not been called. */
        public long uiCompletionDelayNanos() {
            long end = uiCompletionNanos;
            return end == Long.MIN_VALUE ? -1L : elapsed(workerEndNanos, end);
        }
        /** -1 means finish has not been called. */
        public long uiTotalNanos() {
            long end = uiCompletionNanos;
            return end == Long.MIN_VALUE ? -1L : elapsed(workerStartNanos, end);
        }
        public FileResult[] files() { return files.clone(); }
        public int calls(String stage) { return calls[index(stage)]; }
        public long nanos(String stage) { return nanos[index(stage)]; }
        private int index(String stage) {
            for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(stage)) return i;
            throw new IllegalArgumentException("Unknown timing stage");
        }
        public String toJson() {
            long uiEnd = uiCompletionNanos;
            StringBuilder out = new StringBuilder(8192);
            out.append("{\"schema\":1,\"diagnostic\":true,\"sdk_int\":").append(sdk)
                    .append(",\"requested\":").append(requested).append(",\"attempted\":").append(attempted)
                    .append(",\"reported_successes\":").append(reportedSuccesses).append(",\"unfinished\":").append(unfinished)
                    .append(",\"total_ns\":").append(totalNanos).append(",\"file_total_ns\":").append(fileNanos)
                    .append(",\"worker_start_ns\":").append(workerStartNanos)
                    .append(",\"worker_end_ns\":").append(workerEndNanos)
                    .append(",\"ui_completion_delay_ns\":");
            if (uiEnd == Long.MIN_VALUE) out.append("null");
            else out.append(elapsed(workerEndNanos, uiEnd));
            out.append(",\"ui_total_ns\":");
            if (uiEnd == Long.MIN_VALUE) out.append("null");
            else out.append(elapsed(workerStartNanos, uiEnd));
            out.append(",\"metadata_includes_media_delete\":true,\"stages\":{");
            for (int i = 0; i < KEYS.length; i++) {
                if (i > 0) out.append(',');
                out.append('"').append(KEYS[i]).append("\":{\"calls\":").append(calls[i])
                        .append(",\"ns\":").append(nanos[i]);
                if (i <= SAF) out.append(",\"true\":").append(trueCount[i])
                        .append(",\"false\":").append(falseCount[i]).append(",\"errors\":").append(errors[i]);
                out.append('}');
            }
            out.append("},\"media_results\":{\"rows_zero\":").append(mediaZero)
                    .append(",\"rows_positive\":").append(mediaPositive).append(",\"rows_negative\":").append(mediaNegative)
                    .append(",\"rows_total\":").append(mediaRows).append(",\"errors\":").append(mediaErrors)
                    .append(",\"sql_by_id\":").append(sqlById).append(",\"sql_by_path\":").append(sqlByPath)
                    .append(",\"sql_mixed\":").append(sqlMixed).append(",\"sql_other\":").append(sqlOther)
                    .append("},\"fast_outcomes\":{");
            for (int i = 0; i < FAST_OUTCOME_KEYS.length; i++) {
                if (i > 0) out.append(',');
                out.append('"').append(FAST_OUTCOME_KEYS[i]).append("\":").append(fastOutcomes[i]);
            }
            out.append("},\"cleanup_outcomes\":{");
            appendCounts(out, CLEANUP_OUTCOME_KEYS, cleanupOutcomes);
            out.append("},\"fast_guards\":{");
            appendCounts(out, FAST_GUARD_KEYS, fastGuards);
            out.append("},\"storage_counts\":{");
            appendCounts(out, STORAGE_KEYS, storageCounts);
            out.append("},\"file_records_limit\":200,\"file_records_omitted\":").append(attempted - files.length)
                    .append(",\"files\":[");
            for (int i = 0; i < files.length; i++) {
                FileResult file = files[i];
                if (i > 0) out.append(',');
                out.append("{\"ordinal\":").append(file.ordinal).append(",\"returned_success\":").append(file.returnedSuccess)
                        .append(",\"completed\":").append(file.completed).append(",\"ns\":").append(file.nanos).append('}');
            }
            return out.append("]}\n").toString();
        }
        private static void appendCounts(StringBuilder out, String[] keys, int[] counts) {
            for (int i = 0; i < keys.length; i++) {
                if (i > 0) out.append(',');
                out.append('"').append(keys[i]).append("\":").append(counts[i]);
            }
        }
        private static void appendLabels(StringBuilder out, String[] keys, String[] labels, int[] counts) {
            for (int i = 0; i < keys.length; i++) out.append(labels[i]).append("（").append(keys[i])
                    .append("）：").append(counts[i]).append('\n');
        }
        public String toText() {
            StringBuilder out = new StringBuilder(8192);
            out.append("快圖刪除計時（診斷版）\nAndroid API：").append(sdk)
                    .append("\n選取：").append(requested).append("；已嘗試：").append(attempted)
                    .append("；程式回報成功：").append(reportedSuccesses).append("；未回報成功：").append(attempted - reportedSuccesses)
                    .append("\n未完成回報：").append(unfinished)
                    .append("\n背景工作耗時（begin 至 complete）：").append(ms(totalNanos)).append(" ms")
                    .append("\n工作結束至統計顯示前：");
            long uiDelay = uiCompletionDelayNanos(), uiTotal = uiTotalNanos();
            if (uiDelay < 0) out.append("未量測"); else out.append(ms(uiDelay)).append(" ms");
            out.append("\n開始工作至統計顯示前合計：");
            if (uiTotal < 0) out.append("未量測"); else out.append(ms(uiTotal)).append(" ms");
            out.append("\n工作結束後區段包含報告保存、排隊與原完成回呼，不代表純 UI 渲染時間。")
                    .append("\n逐檔耗時合計：").append(ms(fileNanos)).append(" ms\n\n分段計時：\n");
            for (int i = 0; i < KEYS.length; i++) {
                out.append(LABELS[i]).append("（").append(KEYS[i]).append("）：")
                        .append(ms(nanos[i])).append(" ms／").append(calls[i]).append(" 次");
                if (i <= SAF) out.append("；true ").append(trueCount[i]).append("／false ")
                        .append(falseCount[i]).append("／例外 ").append(errors[i]);
                out.append('\n');
            }
            out.append("快取計時是排程／請求耗時，不含背景快取工作完成。\n")
                    .append("媒體資料清理總計包含 MediaStore delete；後者是子集，請勿重複相加。\n")
                    .append("cleanup_verify／lookup／id_delete 包含於媒體資料清理總計；media_delete 只計實際提供者呼叫，請勿重複加總。\n")
                    .append("分段可能包含於逐檔或其他區段，不能直接相加當作總耗時。\n")
                    .append("MediaStore 結果：0 列 ").append(mediaZero).append(" 次／正列數 ").append(mediaPositive)
                    .append(" 次／負列數 ").append(mediaNegative).append(" 次／例外 ").append(mediaErrors)
                    .append(" 次；正列數合計 ").append(mediaRows)
                    .append("\n實際 delete SQL 模式：_id ").append(sqlById).append("／_data ").append(sqlByPath)
                    .append("／兩者 ").append(sqlMixed).append("／其他 ").append(sqlOther)
                    .append("\n\n快速分支結果（事件次數；同一檔可有多項）：\n");
            for (int i = 0; i < FAST_OUTCOME_KEYS.length; i++) out.append(FAST_OUTCOME_LABELS[i])
                    .append("（").append(FAST_OUTCOME_KEYS[i]).append("）：").append(fastOutcomes[i]).append('\n');
            out.append("eligible_row 只代表可嘗試，不能據此確認實檔已刪除。\n")
                    .append("type 0／.nomedia 可能仍走原 SAF 後備；不能把全部 SAF 統計視為改善。\n")
                    .append("\nFast 跳過原因：\n");
            appendLabels(out, FAST_GUARD_KEYS, FAST_GUARD_LABELS, fastGuards);
            out.append("儲存空間匿名分類（每檔最多一次，僅路徑字串判斷）：\n");
            appendLabels(out, STORAGE_KEYS, STORAGE_LABELS, storageCounts);
            out.append("分類不代表速度；沒有媒體列也不能推論有 .nomedia。\n\n刪後媒體清理結果（事件次數）：\n");
            appendLabels(out, CLEANUP_OUTCOME_KEYS, CLEANUP_OUTCOME_LABELS, cleanupOutcomes);
            out.append("eligible_id 只代表可嘗試；id_call_completed 只代表呼叫已返回，實際列數另看 media_delete。\n")
                    .append("\n逐檔紀錄（序號／程式回報成功／耗時；最多 200 筆）：\n");
            for (FileResult file : files) out.append(file.ordinal).append("／")
                    .append(file.completed ? file.returnedSuccess ? "是" : "否" : "未完成回報")
                    .append("／").append(ms(file.nanos)).append(" ms\n");
            if (attempted > files.length) out.append("其餘 ").append(attempted - files.length)
                    .append(" 筆明細省略；總次數與總計時仍包含全部檔案。\n");
            return out.append("\n程式回報成功只記錄刪除流程回傳 true；成功總數本身不代表實檔已確認消失。快速分支的確認次數另見 physical_gone。\n僅含匿名計時與計數。請複製結果貼回。\n").toString();
        }
        private static String ms(long nanos) { return String.format(Locale.US, "%.3f", nanos / 1000000.0); }
    }
}
