#!/usr/bin/env python3
"""Validate patch seams on caller-supplied smali, without distributing legacy code."""
from pathlib import Path
import argparse,hashlib,json,shutil,sys,tempfile
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'scripts'));import patch_search_delete as patch
p=argparse.ArgumentParser();p.add_argument('--decoded',type=Path,required=True);p.add_argument('--work-dir',type=Path);p.add_argument('--output-json',type=Path);a=p.parse_args()
names=['smali/com/alensw/ui/c/'+n+'.smali' for n in ['ae','an','dc','z']]+['smali/com/alensw/bean/LocalFolder.smali']
original={n:(a.decoded/n).read_text() for n in names}
with tempfile.TemporaryDirectory(prefix='search-patch-',dir=a.work_dir) as t:
 r=Path(t)
 for n,v in original.items():f=r/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(v)
 patch.patch(r);first={n:(r/n).read_text() for n in names};assert not patch.patch(r)['changed'];assert first=={n:(r/n).read_text() for n in names}
 assert 'invoke-virtual {p1}, Lcom/alensw/a/e;->getPath()Ljava/lang/String;' in first[names[2]]
 for n,v in original.items():(r/n).write_text(v)
 f=r/names[0];s=f.read_text();m=patch.method(s,'.method public a(IIZ)V');v=patch.once(m.group(),'    if-eq v1, v2, :cond_0','    if-eq v1, v2, :different');f.write_text(s[:m.start()]+v+s[m.end():]);bad={n:(r/n).read_text() for n in names}
 try:patch.patch(r);raise AssertionError('accepted changed seam')
 except ValueError:pass
 assert bad=={n:(r/n).read_text() for n in names}
assert original=={n:(a.decoded/n).read_text() for n in names}
result={'passed':True,'idempotent':True,'changed_seam_rejected_without_writes':True,'baseline_unchanged':True,'delete_path_from_directory_model_thumbnail_unchanged':True,'input_sha256':{k:hashlib.sha256(v.encode()).hexdigest() for k,v in original.items()}}
if a.output_json:a.output_json.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
