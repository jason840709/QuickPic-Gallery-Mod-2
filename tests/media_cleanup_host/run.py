#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Absent-file metadata cleanup safety, SQLite counterexamples, SDK36 and D8."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/alensw/jni/MediaCleanup.java'
TESTS = Path(__file__).resolve().parent


def command(parts):
    result = subprocess.run(list(map(str, parts)), text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f'{parts[0]} failed ({result.returncode})\n{result.stdout}{result.stderr}')
    return result.stdout.strip()


def sql_checks():
    db = sqlite3.connect(':memory:')
    db.execute('CREATE TABLE files (_id INTEGER, _data TEXT COLLATE NOCASE, media_type INTEGER, is_pending INTEGER, is_trashed INTEGER, volume_name TEXT)')
    selected = "/storage/card/Book/a%_quote'[].jpg"
    paths = [selected, selected.replace('/Book/', '/book/'), selected.replace('/Book/', '/BOOK/'), selected + '.keep', selected.replace('/Book/', '/Book2/')]
    rows = [(i + 1, p, 1, 0, 0, 'card') for i, p in enumerate(paths)]
    rows += [(6, selected, 3, 0, 0, 'card'), (7, selected, 1, 1, 0, 'card'), (8, selected, 1, 0, 1, 'card'), (9, selected, 1, 0, 0, 'other')]
    db.executemany('INSERT INTO files VALUES (?,?,?,?,?,?)', rows)
    original_scope = "media_type = 1 AND is_pending = 0 AND is_trashed = 0 AND volume_name = 'card'"
    found = db.execute('SELECT _id,_data FROM files WHERE ' + original_scope + ' AND _data = ?', [selected]).fetchall()
    assert len(found) == 3 and [r for r in found if r[1] == selected] == [(1, selected)]
    # Establish the real NOCASE fallback counterexample, then restore every row.
    db.execute('SAVEPOINT before_broad_delete')
    assert db.execute('DELETE FROM files WHERE ' + original_scope + ' AND _data = ?', [selected]).rowcount == 3
    db.execute('ROLLBACK TO before_broad_delete')
    assert db.execute('DELETE FROM files WHERE ' + original_scope + ' AND _data = ? COLLATE BINARY', [selected]).rowcount == 1
    assert [r[0] for r in db.execute('SELECT _id FROM files ORDER BY _id')] == list(range(2, 10))
    # An item route supplies volume/type but defaults to including pending and trashed.
    # The explicit ID, BINARY path, original type and flags prevent all changed-row races.
    id_predicate = '_id = ? AND _data = ? COLLATE BINARY AND media_type = ? AND is_pending = 0 AND is_trashed = 0'
    for row_id, media_type in [(2, 1), (6, 1), (7, 1), (8, 1)]:
        assert db.execute('DELETE FROM files WHERE ' + id_predicate, [row_id, selected, media_type]).rowcount == 0
    db.execute('INSERT INTO files VALUES (10,?,0,0,0,?)', [selected, 'card'])
    assert db.execute('DELETE FROM files WHERE volume_name = ? AND ' + id_predicate, ['card', 10, selected, 0]).rowcount == 1
    assert db.execute('SELECT count(*) FROM files WHERE _id = 9').fetchone()[0] == 1
    # Multiple IDs for the identical path remain a collection fallback, constrained to BINARY.
    db.executemany('INSERT INTO files VALUES (?,?,1,0,0,?)', [(11, selected, 'card'), (12, selected, 'card')])
    assert db.execute('DELETE FROM files WHERE ' + original_scope + ' AND _data = ? COLLATE BINARY', [selected]).rowcount == 2
    assert [r[0] for r in db.execute('SELECT _id FROM files ORDER BY _id')] == list(range(2, 10))
    db.close()
    return {'nocase_broad_fallback_counterexample': True, 'binary_path_case_prefix_special_controls': True,
            'id_path_type_pending_trash_current_row_controls': True, 'files_type_zero_metadata_only': True,
            'duplicate_exact_fallback_and_original_volume': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--android-jar', type=Path, default=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home() / 'Library/Android/sdk')) / 'platforms/android-36/android.jar')
    parser.add_argument('--d8-jar', type=Path, default=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home() / 'Library/Android/sdk')) / 'build-tools/36.0.0/lib/d8.jar')
    parser.add_argument('--output-json', type=Path)
    parser.add_argument('--skip-api', action='store_true', help='Host cycle only; do not claim API/D8 completion.')
    args = parser.parse_args()
    work = args.work_dir.resolve()
    if work.exists() and any(work.iterdir()):
        raise SystemExit('Use a new empty work directory')
    work.mkdir(parents=True, exist_ok=True)
    classes = work / 'host-classes'
    classes.mkdir()
    command(['javac', '--release', '8', '-encoding', 'UTF-8', '-d', classes,
             *sorted((TESTS / 'fixtures').rglob('*.java')), TESTS / 'MediaCleanupProbe.java', SOURCE])
    result = json.loads(command(['java', '-cp', classes, 'com.alensw.jni.MediaCleanupProbe', work / 'synthetic-files']))
    sql = sql_checks()
    checks = {'host_provider_filesystem_safety': result['passed'],
              'lookup_recreated_physical_file_preserved': result['lookup_recreated_physical_file_preserved'],
              'partial_throw_recreated_file_and_new_id_preserved': result['partial_throw_recreated_file_and_new_id_preserved'], **sql}
    api_sources = {}
    if not args.skip_api:
        api = work / 'api-classes'
        api.mkdir()
        helpers = [ROOT / ('src/com/alensw/jni/' + name + '.java') for name in ['MediaCleanup', 'DeleteTiming', 'FastMediaDelete', 'FolderDelete']]
        api_sources = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in helpers}
        command(['javac', '--release', '8', '-encoding', 'UTF-8', '-cp', args.android_jar, '-d', api, *helpers])
        dex = work / 'helper-dex'
        dex.mkdir()
        command(['java', '-cp', args.d8_jar, 'com.android.tools.r8.D8', '--min-api', '21', '--lib', args.android_jar, '--output', dex, *sorted(api.rglob('*.class'))])
        signatures = command(['javap', '-classpath', api, '-s', 'com.alensw.jni.MediaCleanup'])
        checks.update(real_android_api36_javac=True, d8_min_api21=(dex / 'classes.dex').is_file(),
                      public_descriptor='(Landroid/content/ContentResolver;Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I' in signatures)
    output = {'passed': all(checks.values()), 'probe': result, 'checks': checks,
              'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
              'test_sources_sha256': {str(p.relative_to(TESTS)): hashlib.sha256(p.read_bytes()).hexdigest()
                                      for p in sorted(TESTS.rglob('*')) if p.is_file()},
              'api_compile_sources_sha256': api_sources,
              'scope': 'Host temporary filesystem, controlled Android/provider doubles and SQLite. SDK36 javac and D8 when not skipped. No emulator, Android runtime, physical-card timing or speed claim.'}
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(output, ensure_ascii=False, indent=2))
    if not output['passed']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
