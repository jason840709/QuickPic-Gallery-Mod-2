#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Compile actual ReaderMode.java with controlled Android queue/decoder doubles.

Requires an existing Java 9+ JDK. No Android SDK, downloads, private paths, historical
App sources or third-party Python packages are required. This is transaction and
ownership testing; it does not claim Android UI, DEX bridges or codec playback.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / 'src/com/alensw/jni/ReaderMode.java')
    parser.add_argument('--work-dir', type=Path, help='parent for disposable compilation files')
    parser.add_argument('--output-json', type=Path)
    args = parser.parse_args()
    result = {'result': 'PREFLIGHT_ERROR', 'scope': 'actual ReaderMode.java; controlled Android main queue, preferences, host and frame doubles; not device playback'}
    if not args.source.is_file():
        raise SystemExit('Preflight error: ReaderMode source unavailable; no behavior verdict.')
    result['source_sha256'] = hashlib.sha256(args.source.read_bytes()).hexdigest()
    java_home = os.environ.get('JAVA_HOME')
    javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
    java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
    if not javac or not java:
        raise SystemExit('Preflight error: an existing JDK is required; no behavior verdict.')
    if args.work_dir:
        args.work_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='reader-mode-', dir=args.work_dir) as temporary:
        work = Path(temporary)
        shutil.copytree(HERE / 'stubs', work / 'src')
        shutil.copyfile(HERE / 'ReaderModeProbe.java', work / 'src/ReaderModeProbe.java')
        production = work / 'src/com/alensw/jni/ReaderMode.java'
        production.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.source, production)
        sources = sorted(map(str, (work / 'src').rglob('*.java')))
        compile_run = subprocess.run([javac, '--release', '8', '-Xlint:-options', '-d', str(work / 'classes'), *sources], text=True, capture_output=True)
        result['compile_exit'] = compile_run.returncode
        print(compile_run.stdout + compile_run.stderr, end='')
        exit_code = compile_run.returncode
        if compile_run.returncode:
            print('Compilation error / preflight; no behavior verdict.')
        else:
            behavior = subprocess.run([java, '-cp', str(work / 'classes'), 'ReaderModeProbe'], text=True, capture_output=True)
            print(behavior.stdout + behavior.stderr, end='')
            result['behavior_exit'] = behavior.returncode
            exit_code = behavior.returncode
            for line in behavior.stdout.splitlines():
                if line.startswith('RESULT_JSON '):
                    result.update(json.loads(line[len('RESULT_JSON '):]))
            if result['result'] == 'PREFLIGHT_ERROR':
                result['result'] = 'HARNESS_ERROR'
        if args.output_json:
            args.output_json.parent.mkdir(parents=True, exist_ok=True)
            args.output_json.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
        raise SystemExit(exit_code)

if __name__ == '__main__':
    main()
