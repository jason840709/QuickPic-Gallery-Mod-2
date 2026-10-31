# SPDX-License-Identifier: MIT
"""Group the existing screensaver preferences without changing their saved keys."""
from pathlib import Path
import xml.etree.ElementTree as ET
from copy import deepcopy
A='{http://schemas.android.com/apk/res/android}'
def patch(root):
 root=Path(root);ET.register_namespace('android','http://schemas.android.com/apk/res/android')
 for path in sorted((root/'res').glob('xml*/main_pref.xml')):
  tree=ET.parse(path);top=tree.getroot();parents={child:parent for parent in top.iter()for child in parent}
  nodes=[n for n in top.iter()if n.get(A+'key')in ('screensaver_size','screensaver_options')]
  if len(nodes)!=2:raise ValueError('Expected two screensaver preferences')
  existing=[n for n in top.iter('PreferenceCategory')if n.get(A+'key')=='screensaver_category']
  if existing:
   if len(existing)!=1 or any(parents[n]is not existing[0]for n in nodes):raise ValueError('Invalid screensaver category')
   continue
  group=parents[nodes[0]]
  if any(parents[n]is not group for n in nodes):raise ValueError('Screensaver settings have different parents')
  category=ET.Element('PreferenceCategory',{A+'key':'screensaver_category',A+'title':'@string/quickpic_screensaver_category'})
  if group.get(A+'layout'):category.set(A+'layout',group.get(A+'layout'))
  for node in nodes:group.remove(node);category.append(node)
  screen=parents[group];index=list(screen).index(group)
  if index<1:raise ValueError('Missing original section divider')
  template=list(screen)[index-1]
  if template.tag!='PreferenceCategory' or len(template) or template.get(A+'title') or not template.get(A+'layout'):
   raise ValueError('Expected original empty divider before Image category')
  separator=deepcopy(template);separator.set(A+'key','screensaver_separator')
  screen.insert(index+1,separator);screen.insert(index+2,category)
  ET.indent(tree,space='    ');tree.write(path,encoding='utf-8',xml_declaration=True)
 for path in (root/'res').glob('values*/strings.xml'):
  tree=ET.parse(path);res=tree.getroot();key='quickpic_screensaver_category'
  if any(n.get('name')==key for n in res):continue
  qualifier=path.parent.name
  label='Screensaver settings'
  if 'zh' in qualifier:label='屏保設定' if any(x in qualifier for x in ('TW','HK','MO','Hant'))else'屏保设置'
  ET.SubElement(res,'string',{'name':key}).text=label
  tree.write(path,encoding='utf-8',xml_declaration=True)
