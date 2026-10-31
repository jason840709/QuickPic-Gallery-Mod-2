"""Convert the provided clone into one independently named, maintained app."""
from pathlib import Path
import hashlib,json,re,shutil,xml.etree.ElementTree as ET
NS='http://schemas.android.com/apk/res/android';A='{'+NS+'}'
REPO='https://github.com/jason840709/QuickPic-Gallery-Mod-2'

PROGRESS_PATH='smali/com/alensw/ui/c/ScreenSaverHelper$DialogClickListener.smali'
OLD_PROGRESS='正在產生 1860×2480 PNG，完成後將開啟系統 XPhoto…'
NEW_PROGRESS='正在產生屏保 PNG，完成後將開啟圖片檢視器…'

def normalize_progress(root):
 # Normalize original and maintained-base messages to the size-independent status.
 path=Path(root)/PROGRESS_PATH;before=path.read_bytes()
 variants=(OLD_PROGRESS, '正在產生 1860×2480 PNG，完成後將開啟圖片檢視器…', NEW_PROGRESS)
 pairs=[]
 for value in variants:
  pairs.extend([(value.encode('utf-8'),NEW_PROGRESS.encode('utf-8')),
    (json.dumps(value,ensure_ascii=True)[1:-1].encode('ascii'),
     json.dumps(NEW_PROGRESS,ensure_ascii=True)[1:-1].encode('ascii'))])
 count=sum(before.count(old) for old,new in pairs)
 if count!=1:raise ValueError('Expected exactly one screensaver progress string')
 after=before
 for old,new in pairs:after=after.replace(old,new)
 if after!=before:path.write_bytes(after)
 return {'path':PROGRESS_PATH,'changed':after!=before}

def patch(decoded_root,package='io.github.jason840709.quickpic',version_name='11.0.22',version_code=11022001,app_name='快圖'):
 root=Path(decoded_root);manifest=root/'AndroidManifest.xml';old=ET.parse(manifest).getroot().get('package')
 if not old or not re.fullmatch(r'[a-zA-Z]\w*(?:\.[a-zA-Z]\w*)+',package):raise ValueError('Invalid package')
 replacements=[(old.replace('.','/'),package.replace('.','/')),(old,package),
               ('QuickPic885-Screensaver-Bridge-1','QuickPic-Screensaver'),
               ('b1051c-screensaver-1860x2480-','screensaver-1860x2480-')]
 counts={k:0 for k,_ in replacements};changed=[]
 for p in sorted(root.rglob('*')):
  if not p.is_file() or p.suffix not in ['.smali','.xml','.yml','.yaml','.json','.txt','.properties']:continue
  if p.relative_to(root).parts[0]=='original':continue
  text=p.read_text();new=text
  for a,b in replacements:
   if a!=b:counts[a]+=new.count(a);new=new.replace(a,b)
  if new!=text:p.write_text(new);changed.append(str(p.relative_to(root)))
 moved=[]
 if old!=package:
  for smali in root.glob('smali*'):
   if not smali.is_dir():continue
   src=smali/Path(*old.split('.'));dst=smali/Path(*package.split('.'))
   if src.exists():
    if dst.exists():raise ValueError('Refusing to overwrite existing package classes')
    dst.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(src),str(dst));moved.append(str(dst.relative_to(root)))
 for p in (root/'res').glob('values*/strings.xml'):
  text=p.read_text()
  for name,value in [('app_name',app_name),('version_number',version_name),('app_name_full','QuickPic Gallery Mod II'),('update_desc','GitHub Releases')]:
   text=re.sub(r'(<string\b[^>]*name="'+name+r'"[^>]*>).*?(</string>)',lambda m:m.group(1)+value+m.group(2),text,flags=re.S)
  p.write_text(text)
 yml=root/'apktool.yml';text=yml.read_text()
 for name,value in [('versionCode',str(version_code)),('versionName',version_name),('targetSdkVersion','24')]:
  text,n=re.subn(r'(?m)^(\s*'+name+r':\s*).+$',lambda m:m.group(1)+value,text,count=1)
  if n!=1:raise ValueError('Missing apktool '+name)
 yml.write_text(text)
 ET.register_namespace('android',NS);tree=ET.parse(manifest);m=tree.getroot();m.set('package',package)
 m.set(A+'versionName',version_name);m.set(A+'versionCode',str(version_code))
 app=m.find('application');app.set(A+'label','@string/app_name')
 for key in ['largeHeap','supportsRtl']:
  if key in app.attrib:app.set(A+key,app.attrib.pop(key))
 tree.write(manifest,encoding='utf-8',xml_declaration=True)
 # Replace the inherited updater entry with the maintained repository releases.
 for p in (root/'res').glob('xml*/main_pref.xml'):
  text=p.read_text();t=ET.fromstring(text)
  for pref in t:
   if pref.get(A+'title')=='@string/update':
    for child in list(pref):
     if child.tag=='intent':pref.remove(child)
    ET.SubElement(pref,'intent',{A+'action':'android.intent.action.VIEW',A+'data':REPO+'/releases'})
  ET.indent(t,space='    ');p.write_text(ET.tostring(t,encoding='unicode'))
 progress=normalize_progress(root)
 # No bundled native code remains: Java compatibility code serves all ABIs.
 if (root/'lib').exists():shutil.rmtree(root/'lib')
 stale=[]
 for p in root.rglob('*'):
  if p.is_file() and p.suffix in ['.smali','.xml','.yml']:
   if p.relative_to(root).parts[0]=='original':continue
   text=p.read_text()
   if old!=package and (old in text or old.replace('.','/') in text):stale.append(str(p.relative_to(root)))
 if stale:raise ValueError('Stale clone package references: '+repr(stale[:5]))
 return {'old_package':old,'package':package,'versionName':version_name,'versionCode':version_code,'launcher_name':app_name,
         'output_directory':'QuickPic-Screensaver','output_prefix':'screensaver-1860x2480-',
         'progress_toast':progress,'replacement_counts':counts,'moved_smali_roots':moved,'native_libraries':[],'stale_package_references':stale}

if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser();p.add_argument('decoded_root');a=p.parse_args();print(json.dumps(patch(a.decoded_root),ensure_ascii=False,indent=2))
