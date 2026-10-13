#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Check the defaults patch against an external decode and its exact baseline.

Only affected files are copied into the private work directory. This proves
idempotence and unchanged surrounding control flow, including animation-image
promotion, random transition selection, other preferences, and password failure.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts'))
import patch_defaults


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--decoded', type=Path, required=True)
    ap.add_argument('--work-dir', type=Path, required=True)
    ap.add_argument('--output-json', type=Path)
    args = ap.parse_args()
    files = [*args.decoded.glob('smali/**/QuickApp.smali'),
             args.decoded / 'smali/com/alensw/ui/a/ac.smali',
             args.decoded / 'smali/com/alensw/ui/c/as.smali',
             args.decoded / 'smali/com/alensw/ui/c/dp.smali',
             *args.decoded.glob('res/xml*/main_pref.xml')]
    original = {}
    for path in files:
        relative = path.relative_to(args.decoded)
        target = args.work_dir / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        original[str(relative)] = target.read_text()
    report = patch_defaults.patch(args.work_dir)
    first = {relative:(args.work_dir / relative).read_text() for relative in original}
    patch_defaults.patch(args.work_dir)
    checks = []
    def check(name, good):
        checks.append({'name':name,'passed':bool(good)})
    check('second patch is byte-identical', first == {relative:(args.work_dir / relative).read_text() for relative in original})
    app_relative = next(relative for relative in original if relative.endswith('/QuickApp.smali'))
    descriptor = re.search(r'^\.class .* (L[^;]+;)$', original[app_relative], re.M).group(1)
    call = '    invoke-static {}, ' + descriptor + '->applyHiddenDefault()V\n'
    # Strip our new method and two narrowly positioned calls; all remaining
    # historical startup, cache, and cleanup instructions must be byte-identical.
    app = first[app_relative].replace(patch_defaults.hidden_method(descriptor), '')
    app = app.replace('\n' + call, '')
    check('all existing QuickApp code preserved around hooks', app == original[app_relative])
    relative = 'smali/com/alensw/ui/a/ac.smali'
    check('password comparison, failure and protected callback unchanged', first[relative].replace('\n' + call, '') == original[relative])
    for controller,field,reg in [('as','P','v4'),('dp','Q','v3')]:
        relative = 'smali/com/alensw/ui/c/' + controller + '.smali'
        after = first[relative]
        pattern = (r'const-string v0, "slideshow_animation".*?iput v0, p0, Lcom/alensw/ui/c/' + controller + ';->' + field + ':I')
        before_block = re.search(pattern, original[relative], re.S).group(0)
        after = re.sub(pattern, lambda m:before_block, after, count=1, flags=re.S)
        if controller == 'as':
            after = re.sub(r'\n    const/4 v2, 0x6\n    if-ne v0, v2, :defaults_transition.*?    :defaults_transition\n', '', after, count=1, flags=re.S)
        check(controller + ' existing choices/random selection, image loaders and other settings unchanged', after == original[relative])
    for relative in original:
        if relative.endswith('main_pref.xml'):
            expected = re.sub(r'(<[^>]*android:key="slideshow_animation"[^>]*android:defaultValue=")[^\"]*(")', r'\g<1>6\2', original[relative])
            check(relative + ' changes transition default only', first[relative] == expected)
    out = {'passed':all(c['passed'] for c in checks),'checks':checks,'patch':report,
           'scope':'external actual decode, idempotence and surrounding historical instructions',
           'input_sha256':{key:hashlib.sha256(value.encode()).hexdigest() for key,value in original.items()},
           'output_sha256':{key:hashlib.sha256(value.encode()).hexdigest() for key,value in first.items()}}
    if args.output_json:
        args.output_json.parent.mkdir(parents=True,exist_ok=True)
        args.output_json.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'passed':out['passed'],'checks':len(checks),'failures':[c['name'] for c in checks if not c['passed']]},ensure_ascii=False))
    raise SystemExit(0 if out['passed'] else 1)

if __name__ == '__main__':
    main()
