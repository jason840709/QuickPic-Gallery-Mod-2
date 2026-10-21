#!/usr/bin/env python3
"""Run the actual ViewerControls and MenuLayout helpers against host Android doubles.

No APK, device, historical source mutation, network, or Python package is required.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, help='Parent for temporary Java classes')
    parser.add_argument('--output-json', type=Path)
    args = parser.parse_args()
    if args.work_dir:
        args.work_dir.mkdir(parents=True, exist_ok=True)
    helpers = [ROOT / 'src/com/alensw/jni' / name for name in ['ViewerControls.java', 'MenuLayout.java']]
    with tempfile.TemporaryDirectory(prefix='viewer-controls-host-', dir=args.work_dir) as temp:
        classes = Path(temp) / 'classes'
        classes.mkdir()
        compile_result = subprocess.run(
            ['javac', '--release', '8', '-encoding', 'UTF-8', '-d', str(classes),
             *map(str, sorted((HERE / 'src').rglob('*.java'))), *map(str, helpers)],
            text=True, capture_output=True)
        if compile_result.returncode:
            raise SystemExit('Compile error, not behavior failure:\n' + compile_result.stderr)
        result = subprocess.run(['java', '-cp', str(classes), 'ViewerControlsTest'], text=True, capture_output=True)
        print(result.stdout + result.stderr, end='')
        report = {
            'passed': result.returncode == 0,
            'scope': 'Actual production helpers with host Android doubles; excludes Android rendering and device equivalence',
            'helper_sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in helpers},
            'host_output': result.stdout.strip(),
            'host_error': result.stderr.strip(),
        }
        if args.output_json:
            args.output_json.parent.mkdir(parents=True, exist_ok=True)
            args.output_json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
