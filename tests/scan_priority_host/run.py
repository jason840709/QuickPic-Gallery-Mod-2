#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Test priority helper and control flow in an externally decoded APK.

No historical app source is bundled. Java uses controlled date inputs; the
comparison counts folder tasks in an algorithm fixture, not device seconds.
Supply --baseline-decoded to compare original filters/cancel/publication methods.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def method(text, signature):
    start = text.index(signature)
    end = text.index('.end method', start) + len('.end method')
    return text[start:end]


def cfg(part):
    """Sufficient CFG for these methods: no switch or exception edge is needed."""
    labels, instructions = {}, []
    for line in part.splitlines():
        line = line.strip()
        if not line or line.startswith(('.', '#')):
            continue
        if line.startswith(':'):
            labels[line] = len(instructions)
        else:
            instructions.append(line)
    edges = {}
    for i, line in enumerate(instructions):
        target = re.search(r'(:[\w_]+)$', line)
        if line.startswith('goto'):
            nexts = [labels[target.group(1)]]
        elif line.startswith('if-'):
            nexts = [i + 1, labels[target.group(1)]]
        elif line.startswith(('return', 'throw')):
            nexts = []
        else:
            nexts = [i + 1] if i + 1 < len(instructions) else []
        edges[i] = nexts
    return instructions, labels, edges


def reachable(edges, start, target, blocked=()):
    todo, seen, blocked = [start], set(), set(blocked)
    while todo:
        item = todo.pop()
        if item in blocked or item in seen:
            continue
        if item == target:
            return True
        seen.add(item)
        todo.extend(edges.get(item, ()))
    return False


def run(command):
    process = subprocess.run([str(x) for x in command], capture_output=True, text=True)
    if process.returncode:
        raise RuntimeError(process.stdout + process.stderr)
    return process.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--decoded', type=Path, required=True)
    parser.add_argument('--baseline-decoded', type=Path)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--output-json', type=Path)
    args = parser.parse_args()
    args.work_dir.mkdir(parents=True, exist_ok=True)
    checks = []

    def check(name, result):
        checks.append({'name': name, 'passed': bool(result)})

    def read(root, relative):
        return (root / 'smali' / relative).read_text()

    scanner = read(args.decoded, 'com/alensw/a/v.smali')
    traversal = method(scanner, '.method private a(Lcom/alensw/a/e;)I')
    dispatch = method(scanner, '.method private a(Ljava/util/ArrayList;ZZLjava/util/Stack;)I')
    full = method(scanner, '.method private c()I')
    check('actual DEX references priority pending Stack factory',
          'Lcom/alensw/jni/ScanPriority;->pending()Ljava/util/Stack;' in traversal)
    check('eligible traversal batches capped at original two-worker count', re.search(
        r'ArrayList;->add\(Ljava/lang/Object;\)Z\s+(?::\w+\s+)?'
        r'invoke-virtual \{v3\}, Ljava/util/ArrayList;->size\(\)I\s+'
        r'move-result v0\s+const/4 v5, 0x2\s+if-ge v0, v5, :\w+', traversal) is not None)
    check('traversal heap batches avoid repeat metadata ranking', re.search(
        r'if-nez p4, :\w+\s+invoke-static \{p1\}, '
        r'Lcom/alensw/jni/ScanPriority;->order\(Ljava/util/ArrayList;\)V', dispatch) is not None)
    check('original two-worker invokeAll retained', dispatch.count('->invokeAll(') == 1)

    ins, labels, edges = cfg(full)
    cache = [i for i, line in enumerate(ins) if '->a(Ljava/util/ArrayList;ZZLjava/util/Stack;)I' in line]
    root_iterator = [i for i, line in enumerate(ins)
                     if line == 'invoke-virtual {v7}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;']
    good = len(cache) == 1 and len(root_iterator) == 1
    root_exit = None
    if good:
        for line in ins[root_iterator[0]:]:
            found = re.match(r'if-eqz v0, (:\w+)$', line)
            if found:
                root_exit = labels[found.group(1)]
                break
    good = good and root_exit is not None
    check('cached fallback cannot run before selected-root traversal begins', good and
          not reachable(edges, 0, cache[0], blocked=root_iterator))
    check('cached fallback occurs only at root traversal completion seam', good and
          not reachable(edges, 0, cache[0], blocked=[root_exit]))
    check('no former all-cache startup prescan before the root iterator', good and
          not any('->a(Ljava/util/ArrayList;ZZLjava/util/Stack;)I' in line
                  for line in ins[:root_iterator[0]]))
    cancel_gate = re.search(
        r'iget-boolean v0, p0, Lcom/alensw/a/v;->e:Z\s+'
        r'if-nez v0, (:\w+)\s+'
        r'invoke-direct \{p0, v12, v3, v3, v10\}, '
        r'Lcom/alensw/a/v;->a\(Ljava/util/ArrayList;ZZLjava/util/Stack;\)I', full)
    check('cancel true branch bypasses deferred cache dispatch', good and cancel_gate is not None
          and not reachable(edges, labels[cancel_gate.group(1)], cache[0]))
    ti, tl, te = cfg(traversal)
    cancel = next((i for i, line in enumerate(ti) if line.startswith('if-nez v0, :')), None)
    dispatches = [i for i, line in enumerate(ti) if '->a(Ljava/util/ArrayList;ZZLjava/util/Stack;)I' in line]
    check('cancel true at traversal top cannot dispatch another batch', cancel is not None
          and dispatches and not reachable(te, tl[ti[cancel].split(', ')[-1]], dispatches[0]))
    hidden = [i for i, line in enumerate(ins) if '->a(Ljava/util/ArrayList;)V' in line]
    merge_ok = len(hidden) == 1 and hidden[0] >= 2
    if merge_ok:
        h = hidden[0]
        branch = re.match(r'if-eqz v0, (:\w+)$', ins[h - 2])
        merge_ok = ('->b(Ljava/util/ArrayList;)V' in ins[h - 1] and branch is not None
                    and not reachable(edges, labels[branch.group(1)], h))
    hidden_loader = method(scanner, '.method private a(Ljava/util/ArrayList;)V')
    check('cache merge and dedup are limited to original show-hidden branch',
          merge_ok and 'Ljava/util/ArrayList;->contains(Ljava/lang/Object;)Z' in hidden_loader)
    for gate in ('->endsWith(Ljava/lang/String;)Z', 'Lcom/alensw/a/bh;->g(Ljava/lang/String;)Z',
                 'Lcom/alensw/a/v;->a(Ljava/lang/String;ZZ)Z', 'Lcom/alensw/a/v;->a(Ljava/lang/String;Z)Z'):
        check('original traversal gate retained: ' + gate, gate in traversal)

    if args.baseline_decoded:
        before = read(args.baseline_decoded, 'com/alensw/a/v.smali')
        original_traversal = method(before, '.method private a(Lcom/alensw/a/e;)I')
        for gate in ('->endsWith(Ljava/lang/String;)Z', 'Lcom/alensw/a/bh;->g(Ljava/lang/String;)Z',
                     'Lcom/alensw/a/v;->a(Ljava/lang/String;ZZ)Z', 'Lcom/alensw/a/v;->a(Ljava/lang/String;Z)Z'):
            before_lines = [x.strip() for x in original_traversal.splitlines() if gate in x]
            after_lines = [x.strip() for x in traversal.splitlines() if gate in x]
            check('filter call parameters equal baseline: ' + gate, before_lines == after_lines)
        unchanged = [
            ('com/alensw/a/u.smali', '.method protected a(Lcom/alensw/b/k/e;)V'),
            ('com/alensw/a/w.smali', '.method public a()Ljava/lang/Void;'),
            ('com/alensw/ui/c/bz.smali', '.method public handleMessage(Landroid/os/Message;)Z'),
        ]
        for relative, signature in unchanged:
            check('original cancellation/publication method byte-identical: ' + relative + ' ' + signature,
                  method(read(args.baseline_decoded, relative), signature)
                  == method(read(args.decoded, relative), signature))

    # Exercise the real public patcher on external input; no old app text is saved.
    spec = importlib.util.spec_from_file_location('patch_scan', ROOT / 'scripts/patch_scan.py')
    patcher = importlib.util.module_from_spec(spec); spec.loader.exec_module(patcher)
    patch_root = args.work_dir / 'patch-probe'
    destination = patch_root / 'smali/com/alensw/a/v.smali'
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile((args.baseline_decoded or args.decoded) / 'smali/com/alensw/a/v.smali', destination)
    patcher.patch(patch_root)
    once = destination.read_bytes()
    check('repeat patch is byte-idempotent', not patcher.patch(patch_root)['changed'] and once == destination.read_bytes())
    # Real DEX re-decodes rename authored labels, so check maintained-base
    # recognition against the actual candidate too, not only the fresh patch.
    shutil.copyfile(args.decoded / 'smali/com/alensw/a/v.smali', destination)
    before_repeat = destination.read_bytes()
    check('actual candidate DEX round-trip patch is byte-idempotent',
          not patcher.patch(patch_root)['changed'] and before_repeat == destination.read_bytes())
    destination.write_text(destination.read_text().replace('.method private c()I', '.method private missing()I'))
    try:
        patcher.patch(patch_root)
        refused = False
    except ValueError:
        refused = True
    check('patcher refuses an unsupported missing method', refused)

    classes = args.work_dir / 'classes'
    classes.mkdir(exist_ok=True)
    helper = ROOT / 'src/com/alensw/jni/ScanPriority.java'
    java = [helper, *sorted((HERE / 'com').rglob('*.java'))]
    run(['javac', '--release', '8', '-d', classes, *java])
    host = json.loads(run(['java', '-cp', classes, 'com.alensw.jni.ScanPriorityProbe']))
    check('host helper assertions pass', host['checks'] > 0)
    check('host early-publication fixture improves processed sibling count',
          host['old_bfs_siblings_before_comic'] == 100 and host['priority_siblings_before_comic'] == 1)
    result = {
        'scope': 'actual external smali control flow plus production Java helper with controlled dates; no Android execution',
        'checks': checks,
        'passed': all(check['passed'] for check in checks),
        'static_checks': len(checks),
        'host_assertions': host,
        'helper_sha256': hashlib.sha256(helper.read_bytes()).hexdigest(),
        'patcher_sha256': hashlib.sha256((ROOT/'scripts/patch_scan.py').read_bytes()).hexdigest(),
        'scanner_smali_sha256': hashlib.sha256(scanner.encode()).hexdigest(),
        'limitations': ['fixture task counts are not device timing',
                        'original independent storage roots remain sequential',
                        'legacy path cache saves only after uncanceled full scan'],
    }
    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
