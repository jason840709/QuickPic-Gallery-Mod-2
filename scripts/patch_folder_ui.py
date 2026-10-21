# SPDX-License-Identifier: MIT
"""Repair include/exclude folder editor presentation in a supplied decode.

Only the dialog title and pencil icon bind are changed. Compile FolderPathUi
through the existing Java/D8 pipeline; storage, validation and delete callbacks
are deliberately left in the original App classes.
"""
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

HELPER = 'Lcom/alensw/jni/FolderPathUi;'
TITLE_SIGNATURE = '.method public onClick(Landroid/view/View;)V'
ROW_SIGNATURE = '.method public getView(ILandroid/view/View;Landroid/view/ViewGroup;)Landroid/view/View;'


def method(text, signature):
    if text.count(signature) != 1:
        raise ValueError('Expected one method: ' + signature)
    start = text.index(signature)
    end = text.index('.end method', start) + len('.end method')
    return start, end, text[start:end]


def title_block(resource_id):
    return (f'    const v3, {resource_id}\n\n'
            '    invoke-virtual {v2, v3}, Landroid/app/AlertDialog$Builder;->setTitle(I)Landroid/app/AlertDialog$Builder;')


TITLE_HOOK = '''    iget-object v3, p0, Lcom/alensw/ui/activity/bm;->a:Lcom/alensw/ui/activity/PathListActivity;

    invoke-static {v3}, Lcom/alensw/jni/FolderPathUi;->editTitle(Landroid/content/Context;)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v2, v3}, Landroid/app/AlertDialog$Builder;->setTitle(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;'''

ICON_HOOK = '    invoke-static {v1}, Lcom/alensw/jni/FolderPathUi;->tintEditIcon(Landroid/widget/ImageView;)V\n\n'
ICON_SEAM = ('    check-cast v1, Landroid/widget/ImageView;\n\n'
             '    invoke-virtual {v1, v0}, Landroid/widget/ImageView;->setTag(Ljava/lang/Object;)V')


def patch(root):
    root = Path(root)
    public = ET.parse(root / 'res/values/public.xml').getroot()
    ids = {node.get('name'): node.get('id') for node in public if node.get('type') == 'string'}
    resource_id = ids.get('rename_format')
    if not resource_id:
        raise ValueError('Missing original rename_format resource')
    edit_id = next((node.get('id') for node in public
                    if node.get('type') == 'id' and node.get('name') == 'edit_icon'), None)
    if edit_id != '0x7f09007a':
        raise ValueError('Unexpected edit_icon resource id')

    title_path = root / 'smali/com/alensw/ui/activity/bm.smali'
    title_text = title_path.read_text()
    start, end, body = method(title_text, TITLE_SIGNATURE)
    if HELPER + '->editTitle' not in body:
        before = title_block(resource_id)
        if body.count(before) != 1:
            raise ValueError('Unexpected folder editor title structure')
        body = body.replace(before, TITLE_HOOK, 1)
    elif body.count(TITLE_HOOK) != 1:
        raise ValueError('Unexpected maintained folder title hook')
    title_text = title_text[:start] + body + title_text[end:]

    row_path = root / 'smali/com/alensw/ui/activity/bg.smali'
    row_text = row_path.read_text()
    start, end, body = method(row_text, ROW_SIGNATURE)
    if HELPER + '->tintEditIcon' not in body:
        if body.count(ICON_SEAM) != 1 or '    const v1, ' + edit_id not in body:
            raise ValueError('Unexpected folder editor row structure')
        body = body.replace(ICON_SEAM, ICON_SEAM.replace(
            '    invoke-virtual {v1, v0}', ICON_HOOK + '    invoke-virtual {v1, v0}'), 1)
    elif body.count(ICON_HOOK) != 1:
        raise ValueError('Unexpected maintained folder icon hook')
    row_text = row_text[:start] + body + row_text[end:]
    # Validate both seams before writing either file.
    title_path.write_text(title_text)
    row_path.write_text(row_text)
    return {'paths': [str(title_path.relative_to(root)), str(row_path.relative_to(root))],
            'title': 'localized Edit folder path',
            'pencil': 'selected App theme textColorPrimary; SRC_IN on mutated drawable',
            'path_callbacks': 'unchanged'}


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: patch_folder_ui.py DECODED_ROOT')
    print(json.dumps(patch(sys.argv[1]), ensure_ascii=False))
