# SPDX-License-Identifier: MIT
"""Add the opt-in tap layout preference to every View/Controls XML variant."""
from pathlib import Path
import xml.etree.ElementTree as ET
A='{http://schemas.android.com/apk/res/android}'
CLASS='com.alensw.jni.TapPagePreference'
def patch(root):
 root=Path(root);staged=[]
 for p in sorted((root/'res').glob('xml*/main_pref.xml')):
  tree=ET.parse(p);categories=[n for n in tree.getroot().iter('PreferenceCategory') if n.get(A+'title')=='@string/controls_category']
  if len(categories)!=1:raise ValueError('Expected one Controls settings category: '+str(p))
  group=categories[0];all_entries=list(tree.getroot().iter(CLASS));entries=[n for n in group if n.tag==CLASS]
  if all_entries:
   if len(all_entries)!=1 or len(entries)!=1 or entries[0].get(A+'key')!='tap_page_layout':raise ValueError('Invalid tap layout preference placement')
   continue
  anchors=[n for n in group if n.get(A+'key')=='single_tap_zoom']
  if len(anchors)!=1:raise ValueError('Expected one tap zoom preference in Controls')
  node=ET.Element(CLASS,{A+'icon':'@drawable/ic_tap_zoom',A+'key':'tap_page_layout'})
  group.insert(list(group).index(anchors[0])+1,node);staged.append((p,tree))
 if not list((root/'res').glob('xml*/main_pref.xml')):raise ValueError('Missing settings XML variants')
 ET.register_namespace('android','http://schemas.android.com/apk/res/android')
 for p,tree in staged:
  ET.indent(tree,space='    ');tree.write(p,encoding='utf-8',xml_declaration=True)
 return [str(p.relative_to(root))for p,_ in staged]
