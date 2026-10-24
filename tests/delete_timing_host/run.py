#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Production delete scope without diagnostic output: host semantics, API36 and D8."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/alensw/jni/DeleteTiming.java'
TESTS = Path(__file__).resolve().parent


def run(command):
    result = subprocess.run([str(x) for x in command], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(str(command[0]) + ' failed:\n' + result.stdout + result.stderr)
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-jar', type=Path, required=True)
    parser.add_argument('--d8-jar', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path)
    parser.add_argument('--output-json', type=Path)
    parser.add_argument('--fast-regression-json', type=Path)
    args = parser.parse_args()
    # Explicit work directories are retained as evidence; automatic directories are temporary.
    temporary = tempfile.TemporaryDirectory(prefix='delete-timing-host-') if args.work_dir is None else None
    work = Path(temporary.name) if temporary else args.work_dir
    if not temporary and work.exists() and any(work.iterdir()):
        raise SystemExit('Use a new empty work directory')
    work.mkdir(parents=True, exist_ok=True)
    host = work / 'host-classes'; host.mkdir()
    host_sources = [*sorted((TESTS / 'fixtures').rglob('*.java')), TESTS / 'DeleteTimingProbe.java', SOURCE]
    run(['javac', '--release', '8', '-encoding', 'UTF-8', '-d', host, *host_sources])
    probe = run(['java', '-cp', host, 'com.alensw.jni.DeleteTimingProbe', work / 'synthetic-files'])
    api = work / 'api-classes'; api.mkdir()
    real_helpers = [SOURCE, *[ROOT / 'src/com/alensw/jni' / name for name in
                               ['FolderDelete.java', 'FastMediaDelete.java', 'MediaCleanup.java']]]
    run(['javac', '--release', '8', '-encoding', 'UTF-8', '-cp', args.android_jar, '-d', api, *real_helpers])
    dex = work / 'helper-dex'; dex.mkdir()
    run(['java', '-cp', args.d8_jar, 'com.android.tools.r8.D8', '--min-api', '21',
         '--lib', args.android_jar, '--output', dex, *sorted(api.rglob('*.class'))])
    signatures = run(['javap', '-classpath', api, '-s', 'com.alensw.jni.DeleteTiming'])
    expected = {
        'begin': '(Landroid/app/Dialog;I)V', 'beginFile': '()V', 'endFile': '(Z)V',
        'rawDelete': '(Ljava/io/File;)Z', 'exists': '(Ljava/io/File;)Z',
        'isDirectory': '(Ljava/io/File;)Z',
        'deleteDocument': '(Landroid/content/ContentResolver;Landroid/net/Uri;)Z',
        'mediaDelete': '(Landroid/content/ContentResolver;Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I',
        'providerDelete': '(Landroid/content/ContentResolver;Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I',
        'beginCleanupVerify': '()V', 'endCleanupVerify': '()V',
        'beginCleanupLookup': '()V', 'endCleanupLookup': '()V',
        'beginCleanupDelete': '()V', 'endCleanupDelete': '()V', 'cleanupOutcome': '(I)V',
        'classifyStorage': '(Ljava/io/File;)I', 'fastGuard': '(I)V',
        'beginUri': '()V', 'endUri': '()V', 'beginMetadata': '()V', 'endMetadata': '()V',
        'beginCache': '()V', 'endCache': '()V', 'beginProgress': '()V', 'endProgress': '()V',
        'beginFastLookup': '()V', 'endFastLookup': '()V',
        'beginFastDelete': '()V', 'endFastDelete': '()V',
        'beginFastVerify': '()V', 'endFastVerify': '()V', 'fastOutcome': '(I)V',
        'complete': '()Lcom/alensw/jni/DeleteTiming$Report;',
        'finish': '(Landroid/app/Dialog;Lcom/alensw/jni/DeleteTiming$Report;)V',
    }
    lines = signatures.splitlines()
    descriptors = {}
    current = None
    for line in lines:
        if 'public static ' in line and '(' in line:
            current = line.split('(')[0].split()[-1]
        elif 'descriptor:' in line and current:
            descriptors[current] = line.split('descriptor:', 1)[1].strip()
            current = None
    production = json.loads((work / 'synthetic-files/production-results.json').read_text())
    bytecode = run(['javap', '-classpath', api, '-p', '-c', 'com.alensw.jni.DeleteTiming'])
    (work / 'helper-bytecode.txt').write_text(bytecode + '\n')

    def method_body(signature):
        return bytecode[bytecode.index(signature):].split('\n\n', 1)[0]

    complete = method_body('public static com.alensw.jni.DeleteTiming$Report complete();')
    finish = method_body('public static void finish(android.app.Dialog, com.alensw.jni.DeleteTiming$Report);')
    save = method_body('private static void save(android.content.Context, com.alensw.jni.DeleteTiming$Report);')
    checks = {
        'host_probe_passed': probe == 'checks=' + str(production['checks']) and production['passed'] is True,
        'real_android_api_javac': True,
        'd8_passed': (dex / 'classes.dex').is_file(),
        'exact_public_descriptors': descriptors == expected,
        'saf_checked_exception_retained': 'throws java.io.FileNotFoundException' in signatures,
        'complete_returns_null_and_clears_scope': production['complete_returns_null_and_clears_scope'],
        'synthetic_report_finish_has_no_ui_clipboard_toast': production['synthetic_report_finish_silent'],
        'guarded_save_no_open_file_output': production['guarded_save_no_open_file_output'],
        'historical_private_json_content_and_mtime_unchanged': production['historical_json_unchanged'],
        'active_and_file_open_cleanup_dispatch_preserved': production['active_file_dispatch_preserved'],
        'io_results_identity_checked_saf_exception_preserved': production['io_results_and_exceptions_preserved'],
        'normal_cancel_throw_rebegin_and_thread_isolation': production['lifecycle_and_thread_isolation'],
        'compiled_complete_removes_thread_local': 'java/lang/ThreadLocal.remove:()V' in complete,
        'compiled_complete_returns_null_without_report_or_save': 'aconst_null' in complete
            and 'areturn' in complete and 'save:' not in complete and 'new ' not in complete,
        'compiled_finish_is_immediate_return': bool(re.search(r'\b0:\s+return\b', finish))
            and 'invoke' not in finish,
        'compiled_save_is_immediate_return': bool(re.search(r'\b0:\s+return\b', save))
            and 'invoke' not in save,
    }
    fast_regression = None
    if args.fast_regression_json:
        fast_regression = json.loads(args.fast_regression_json.read_text())
        checks['original_fast_43_cases_and_guard_observability'] = (
            fast_regression['passed'] is True and fast_regression['probe']['cases'] == 43)
    output = {
        'passed': all(checks.values()), 'host': probe, 'checks': checks,
        'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        'production': production,
        'input_sha256': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                         for path in [Path(__file__).resolve(), *host_sources]},
        'public_descriptor_count': len(descriptors),
        'host_cleanup_scope': 'Dispatch-only MediaCleanup stub; does not test cleanup eligibility, lookup or physical proof.',
        'api_sources': [path.name for path in real_helpers],
        'api_source_sha256': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in real_helpers},
        'fast_regression': fast_regression,
        'scope': 'Production-mode synthetic host files and Android/provider/UI doubles; real Android API36 javac and D8 min21. '
                 'No Android execution, real-card timing or performance improvement claim.',
    }
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(output, ensure_ascii=False, indent=2))
    if temporary:
        temporary.cleanup()
    if not output['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
