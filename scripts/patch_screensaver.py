# SPDX-License-Identifier: MIT
"""Wire PNG export/preferences and append an isolated B1051C set-as action."""
from pathlib import Path
import re

SIGNATURE = '.method public static prepareAndOpen(Landroid/content/Context;Ljava/lang/String;)Z'
MENU_PATH = 'smali/com/alensw/ui/c/ScreenSaverHelper.smali'
LISTENER_PATH = 'smali/com/alensw/ui/c/ScreenSaverHelper$DialogClickListener.smali'
MENU_SIGNATURE = '.method public static showScreenSaverDialog(Landroid/content/Context;Ljava/lang/String;Lcom/alensw/ui/c/ScreenSaverHelper$Callback;)V'
CLICK_SIGNATURE = '.method public onClick(Landroid/content/DialogInterface;I)V'
ARRAY_HEADER = '    const/4 v0, 0x2\n\n    new-array v0, v0, [Ljava/lang/CharSequence;'
NEW_ARRAY_HEADER = ARRAY_HEADER.replace('v0, 0x2', 'v0, 0x3')
BUILDER = '    new-instance v1, Landroid/app/AlertDialog$Builder;'
OPTION_MARKER = '# quickpic-b1051c-option'
OPTION_BLOCK = '''    # quickpic-b1051c-option
    const-string v1, "設置為 B1051C 屏保"

    const/4 v2, 0x2

    aput-object v1, v0, v2

'''
DISPATCH_MARKER = '# quickpic-b1051c-dispatch'
DISPATCH_BLOCK = '''
    # quickpic-b1051c-dispatch
    const/4 v0, 0x2

    if-ne p2, v0, :quickpic_b1051c_existing

    iget-object p1, p0, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;->context:Landroid/content/Context;

    iget-object p2, p0, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;->filePath:Ljava/lang/String;

    invoke-static {p1, p2}, Lcom/alensw/jni/BigmeScreensaver;->confirm(Landroid/content/Context;Ljava/lang/String;)V

    return-void

    :quickpic_b1051c_existing
'''


def _method(text, signature, transform, label):
    matches = list(re.finditer(r'(?ms)^' + re.escape(signature) + r'\n.*?^\.end method', text))
    if len(matches) != 1:
        raise ValueError('Expected exactly one ' + label)
    match = matches[0]
    return text[:match.start()] + transform(match.group()) + text[match.end():]


def _menu(body):
    if OPTION_MARKER in body:
        if (body.count(OPTION_MARKER) != 1 or body.count(OPTION_BLOCK + BUILDER) != 1
                or body.count(NEW_ARRAY_HEADER) != 1 or ARRAY_HEADER in body):
            raise ValueError('Invalid existing B1051C screensaver menu patch')
        return body
    if body.count(ARRAY_HEADER) != 1 or body.count(BUILDER) != 1:
        raise ValueError('Expected original two-item screensaver menu and builder')
    return body.replace(ARRAY_HEADER, NEW_ARRAY_HEADER).replace(BUILDER, OPTION_BLOCK + BUILDER)


def _listener(body):
    anchor = '    .locals 1\n'
    if DISPATCH_MARKER in body:
        if body.count(DISPATCH_MARKER) != 1 or body.count(anchor + DISPATCH_BLOCK) != 1:
            raise ValueError('Invalid existing B1051C screensaver dispatch patch')
        return body
    if body.count(anchor) != 1 or ':quickpic_b1051c_existing' in body:
        raise ValueError('Expected original screensaver click listener registers')
    return body.replace(anchor, anchor + DISPATCH_BLOCK, 1)

def patch(decoded_root):
    root = Path(decoded_root)
    staged = {}
    path = root / 'smali/com/alensw/ui/c/XPhotoScreenSaverBridge.smali'
    text = path.read_text()
    pattern = r'(?ms)^' + re.escape(SIGNATURE) + r'\n.*?^\.end method'
    replacement = SIGNATURE + '''
    .locals 1
    invoke-static {p0, p1}, Lcom/alensw/jni/ScreensaverExport;->prepareAndOpen(Landroid/content/Context;Ljava/lang/String;)Z
    move-result v0
    return v0
.end method'''
    text, count = re.subn(pattern, lambda m: replacement, text)
    if count != 1: raise ValueError('Expected exactly one screensaver export entry')
    staged[path] = text
    # Stage every requested change before writing: a different OEM/app method shape
    # must never leave the export, menu and click handler only partially patched.
    for relative, signature, transform, label in (
        (MENU_PATH, MENU_SIGNATURE, _menu, 'screensaver menu method'),
        (LISTENER_PATH, CLICK_SIGNATURE, _listener, 'screensaver click listener'),
    ):
        path = root / relative
        if not path.is_file():
            raise ValueError('Missing ' + label)
        staged[path] = _method(path.read_text(), signature, transform, label)
    paths = sorted((root / 'res').glob('xml*/main_pref.xml'))
    if not paths: raise ValueError('Missing preference XML')
    for path in paths:
        text = path.read_text()
        if 'android:key="screensaver_size"' not in text:
            item = '\n            <com.alensw.jni.ScreensaverPreference android:icon="@drawable/ic_view" android:title="設定屏保圖尺寸" android:key="screensaver_size" />'
            text, count = re.subn(r'(<PreferenceCategory\b[^>]*android:title="@string/image_category"[^>]*>)', lambda m: m[1] + item, text)
            if count != 1: raise ValueError('Expected one image category')
        staged[path] = text
    for path, text in staged.items(): path.write_text(text)
    return {'preference_variants': len(paths), 'entry': SIGNATURE, 'helper': 'ScreensaverExport',
            'default': [1860,2480], 'set_as_items': 3,
            'b1051c_entry': 'BigmeScreensaver.confirm(Context,String)V'}
