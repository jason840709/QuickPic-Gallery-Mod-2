# SPDX-License-Identifier: MIT
"""Add an opt-in page indicator to the existing viewer. No legacy code included."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
A = '{http://schemas.android.com/apk/res/android}'
VIEW = 'Lcom/alensw/ui/view/j;'
HELPER = 'Lcom/alensw/jni/PageIndicator;'
STATE = 'Lcom/alensw/jni/PageIndicator$State;'
HOST = 'Lcom/alensw/jni/PageIndicator$Host;'
FIELD = 'quickpicPageIndicator'
KEY = 'show_page_number'

def method(text, signature):
    matches = list(re.finditer('^' + re.escape(signature) + '$', text, re.M))
    if len(matches) != 1: raise ValueError('Expected one page indicator method: ' + signature)
    start = matches[0].start()
    end = text.index('.end method', start) + len('.end method')
    return start, end, text[start:end]

def patch_view(text):
    if HELPER in text or HOST in text or FIELD in text:
        raise ValueError('Page indicator already installed; use a fresh input')
    if '.implements Lcom/alensw/jni/ReaderMode$Frame;' in text:
        raise ValueError('Wrong page view target')
    if '.method protected onAttachedToWindow()V' in text:
        raise ValueError('Existing page view attach callback requires review')
    signature = '.method protected onDetachedFromWindow()V'
    start, end, body = method(text, signature)
    match = re.search(r'^    \.locals (\d+)\n', body, re.M)
    if match is None or int(match[1]) < 1: raise ValueError('Missing detach local')
    pos = match.end()
    body = body[:pos] + ('\n    iget-object v0, p0, ' + VIEW + '->' + FIELD + ':' + STATE + '\n'
        '    invoke-static {v0}, ' + HELPER + '->detach(' + STATE + ')V\n'
        '    const/4 v0, 0x0\n    iput-object v0, p0, ' + VIEW + '->' + FIELD + ':' + STATE + '\n') + body[pos:]
    text = text[:start] + body + text[end:]
    text = text.replace('.super Landroid/view/View;\n', '.super Landroid/view/View;\n.implements ' + HOST + '\n', 1)
    if '.implements ' + HOST not in text: raise ValueError('Unexpected image view superclass')
    pos = text.index('.method ')
    text = text[:pos] + '.field private ' + FIELD + ':' + STATE + '\n\n' + text[pos:]
    return text.rstrip() + """

.method protected onAttachedToWindow()V
    .locals 1
    invoke-super {p0}, Landroid/view/View;->onAttachedToWindow()V
    invoke-static {p0}, Lcom/alensw/jni/PageIndicator;->attach(Landroid/view/View;)Lcom/alensw/jni/PageIndicator$State;
    move-result-object v0
    iput-object v0, p0, Lcom/alensw/ui/view/j;->quickpicPageIndicator:Lcom/alensw/jni/PageIndicator$State;
    return-void
.end method

.method public pageController()Lcom/alensw/jni/ReaderMode$Host;
    .locals 2
    invoke-virtual {p0}, Lcom/alensw/ui/view/j;->getContext()Landroid/content/Context;
    move-result-object v0
    instance-of v1, v0, Lcom/alensw/ui/c/cx;
    if-eqz v1, :page_controller_none
    check-cast v0, Lcom/alensw/ui/c/cx;
    invoke-virtual {v0}, Lcom/alensw/ui/c/cx;->c()Lcom/alensw/ui/c/cu;
    move-result-object v0
    instance-of v1, v0, Lcom/alensw/jni/ReaderMode$Host;
    if-eqz v1, :page_controller_none
    check-cast v0, Lcom/alensw/jni/ReaderMode$Host;
    return-object v0
    :page_controller_none
    const/4 v0, 0x0
    return-object v0
.end method

.method public pageFrame()Lcom/alensw/jni/ReaderMode$Frame;
    .locals 1
    iget-object v0, p0, Lcom/alensw/ui/view/j;->b:Lcom/alensw/b/h/j;
    if-nez v0, :page_frame_done
    iget-object v0, p0, Lcom/alensw/ui/view/j;->a:Lcom/alensw/b/h/j;
    :page_frame_done
    return-object v0
.end method

.method public pageBackground()I
    .locals 1
    iget v0, p0, Lcom/alensw/ui/view/j;->y:I
    return v0
.end method
.method public pageState()Lcom/alensw/jni/PageIndicator$State;
    .locals 1
    iget-object v0, p0, Lcom/alensw/ui/view/j;->quickpicPageIndicator:Lcom/alensw/jni/PageIndicator$State;
    return-object v0
.end method

"""

def patch_controller(text, name):
    owner = 'Lcom/alensw/ui/c/' + name + ';'
    if HELPER in text: raise ValueError('Page controller already patched')
    start, end, body = method(text, '.method protected a(Lcom/alensw/b/h/j;Z)V')
    update = ('    iget-object v0, p0, ' + owner + '->f:Lcom/alensw/ui/view/PictureView;\n'
              '    invoke-static {v0, p0}, ' + HELPER + '->update(Landroid/view/View;Lcom/alensw/jni/ReaderMode$Host;)V\n')
    # Update same-frame totals even when ReaderMode keeps the current frame.
    match = re.search(r'^    \.locals (\d+)\n', body, re.M)
    if match is None or int(match[1]) < 1: raise ValueError('Missing controller local')
    pos = match.end()
    body = body[:pos] + '\n' + update + body[pos:]
    seam = '    invoke-virtual {v0, p1, p2}, Lcom/alensw/ui/view/PictureView;->a(Lcom/alensw/b/h/j;Z)V'
    if body.count(seam) != 1: raise ValueError('Unexpected picture commit seam')
    body = body.replace(seam, seam + '\n' + update, 1)
    text = text[:start] + body + text[end:]
    start, end, body = method(text, '.method private d(Z)V')
    if body.count('    return-void') != 1: raise ValueError('Unexpected transition completion')
    code = ('    iget-object v0, p0, ' + owner + '->f:Lcom/alensw/ui/view/PictureView;\n'
            '    invoke-static {v0, p1}, ' + HELPER + '->transition(Landroid/view/View;Z)V\n')
    body = body.replace('    return-void', code + '    return-void', 1)
    return text[:start] + body + text[end:]

def patch(root):
    root = Path(root)
    view = root / 'smali/com/alensw/ui/view/j.smali'
    transformed = patch_view(view.read_text())
    code = {view: transformed}
    for name in ('as', 'dp'):
        path = root / ('smali/com/alensw/ui/c/' + name + '.smali')
        code[path] = patch_controller(path.read_text(), name)
    staged = []
    layouts = sorted((root / 'res').glob('layout*/viewer.xml'))
    if not layouts: raise ValueError('Missing viewer layouts')
    for path in layouts:
        tree = ET.parse(path)
        node = tree.getroot()
        if node.tag != 'RelativeLayout': raise ValueError('Unexpected viewer root')
        if any(n.get(A+'id') in ('@id/quickpic_page_number', '@+id/quickpic_page_number') for n in node.iter()):
            raise ValueError('Duplicate page label')
        ET.SubElement(node, 'TextView', {A+'id':'@+id/quickpic_page_number', A+'layout_width':'wrap_content',
            A+'layout_height':'wrap_content', A+'layout_alignParentBottom':'true', A+'layout_centerHorizontal':'true',
            A+'layout_marginBottom':'8dp', A+'textSize':'@dimen/detail_text_size', A+'singleLine':'true',
            A+'includeFontPadding':'false', A+'visibility':'gone', A+'clickable':'false', A+'focusable':'false'})
        staged.append((path, tree))
    paths = sorted((root / 'res').glob('xml*/main_pref.xml'))
    if not paths: raise ValueError('Missing View settings')
    for path in paths:
        tree = ET.parse(path)
        screens = [n for n in tree.getroot().iter('PreferenceScreen') if n.get(A+'title') == '@string/view']
        groups = [n for screen in screens for n in screen if n.tag == 'PreferenceCategory'
                  and n.get(A+'title') == '@string/behavior_category']
        if len(groups) != 1: raise ValueError('Expected one View/Behavior category: ' + str(path))
        if any(n.get(A+'key') == KEY for n in tree.getroot().iter()):
            raise ValueError('Duplicate page number setting')
        ET.SubElement(groups[0], 'SwitchPreference', {
            A+'key': KEY, A+'title': '@string/quickpic_page_number_title',
            A+'summary': '@string/quickpic_page_number_summary', A+'defaultValue': 'false',
            A+'icon': '@drawable/ic_invisible', A+'switchTextOn': '@string/quickpic_switch_on',
            A+'switchTextOff': '@string/quickpic_switch_off'})
        staged.append((path, tree))
    values = {
        'values': ('Show page numbers', 'Show the current page and total at the bottom, for example 07/50.'),
        'values-zh': ('显示页码', '在底部显示当前页和总页数，例如07/50。'),
        'values-zh-rCN': ('显示页码', '在底部显示当前页和总页数，例如07/50。'),
        'values-zh-rTW': ('顯示頁碼', '在底部顯示目前頁數與總頁數，例如07/50。'),
        'values-zh-rHK': ('顯示頁碼', '在底部顯示目前頁數與總頁數，例如07/50。')}
    resources = []
    for folder, (title, summary) in values.items():
        dest = root / 'res' / folder / 'quickpic_page_number.xml'
        node = ET.Element('resources')
        for key, value in [('title', title), ('summary', summary)]:
            ET.SubElement(node, 'string', {'name': 'quickpic_page_number_' + key}).text = value
        resources.append((dest, ET.ElementTree(node)))
    ET.register_namespace('android', A[1:-1])
    for path, text in code.items(): path.write_text(text)
    for path, tree in staged + resources:
        path.parent.mkdir(parents=True, exist_ok=True)
        ET.indent(tree, space='    ')
        tree.write(path, encoding='utf-8', xml_declaration=True)
    return [str(p.relative_to(root)) for p in code] + [str(p.relative_to(root)) for p,_ in staged+resources]
