# SPDX-License-Identifier: MIT
"""Wire PNG exports and filter the set-as menu through a fixed visibility snapshot."""
from pathlib import Path
import re

SIGNATURE = '.method public static prepareAndOpen(Landroid/content/Context;Ljava/lang/String;)Z'
MENU_PATH = 'smali/com/alensw/ui/c/ScreenSaverHelper.smali'
LISTENER_PATH = 'smali/com/alensw/ui/c/ScreenSaverHelper$DialogClickListener.smali'
MENU_SIGNATURE = '.method public static showScreenSaverDialog(Landroid/content/Context;Ljava/lang/String;Lcom/alensw/ui/c/ScreenSaverHelper$Callback;)V'
CLICK_SIGNATURE = '.method public onClick(Landroid/content/DialogInterface;I)V'
ARRAY_HEADER = '    const/4 v0, 0x2\n\n    new-array v0, v0, [Ljava/lang/CharSequence;'
THREE_ARRAY_HEADER = ARRAY_HEADER.replace('v0, 0x2', 'v0, 0x3')
NEW_ARRAY_HEADER = ARRAY_HEADER.replace('v0, 0x2', 'v0, 0x4')
BUILDER = '    new-instance v1, Landroid/app/AlertDialog$Builder;'
OPTION_MARKER = '# quickpic-b1051c-option'
OPTION_BLOCK = '''    # quickpic-b1051c-option
    const-string v1, "設置為 B1051C 屏保"

    const/4 v2, 0x2

    aput-object v1, v0, v2

'''
BOOX_MARKER = '# quickpic-boox-option'
BOOX_OPTION_BLOCK = '''    # quickpic-boox-option
    const-string v1, "設置為文石屏保"

    const/4 v2, 0x3

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
BOOX_DISPATCH_MARKER = '# quickpic-boox-dispatch'
BOOX_DISPATCH_BLOCK = '''
    # quickpic-boox-dispatch
    const/4 v0, 0x3

    if-ne p2, v0, :quickpic_boox_existing

    iget-object p1, p0, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;->context:Landroid/content/Context;

    iget-object p2, p0, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;->filePath:Ljava/lang/String;

    invoke-static {p1, p2}, Lcom/alensw/jni/BooxScreensaver;->confirm(Landroid/content/Context;Ljava/lang/String;)V

    return-void

    :quickpic_boox_existing
'''
STARTUP_SIGNATURE = '.method private g()Z'
INITIALIZE_CALL = '    invoke-static {p0}, Lcom/alensw/jni/ScreensaverOptions;->initialize(Landroid/content/Context;)V\n'
VISIBILITY_MARKER = '# quickpic-screensaver-visible-menu'
DYNAMIC_TAIL = '''    # quickpic-screensaver-visible-menu
    new-instance v2, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;

    invoke-direct {v2, p0, p1, p2}, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;-><init>(Landroid/content/Context;Ljava/lang/String;Lcom/alensw/ui/c/ScreenSaverHelper$Callback;)V

    const v1, 0x7f0a0141

    invoke-static {p0, v1, v0, v2}, Lcom/alensw/jni/ScreensaverMenu;->show(Landroid/content/Context;I[Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)V

    return-void
.end method'''
ORIGINAL_TAIL = '''new-instance v1, Landroid/app/AlertDialog$Builder;
invoke-direct {v1, p0}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V
const v2, 0x7f0a0141
invoke-virtual {v1, v2}, Landroid/app/AlertDialog$Builder;->setTitle(I)Landroid/app/AlertDialog$Builder;
move-result-object v1
new-instance v2, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;
invoke-direct {v2, p0, p1, p2}, Lcom/alensw/ui/c/ScreenSaverHelper$DialogClickListener;-><init>(Landroid/content/Context;Ljava/lang/String;Lcom/alensw/ui/c/ScreenSaverHelper$Callback;)V
invoke-virtual {v1, v0, v2}, Landroid/app/AlertDialog$Builder;->setItems([Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;
move-result-object p0
invoke-virtual {p0}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;
return-void
.end method'''


def _method(text, signature, transform, label):
    matches = list(re.finditer(r'(?ms)^' + re.escape(signature) + r'\n.*?^\.end method', text))
    if len(matches) != 1:
        raise ValueError('Expected exactly one ' + label)
    match = matches[0]
    return text[:match.start()] + transform(match.group()) + text[match.end():]


def _menu19(body):
    if VISIBILITY_MARKER in body:
        if (body.count(VISIBILITY_MARKER) != 1 or body.count(DYNAMIC_TAIL) != 1
                or body.count(THREE_ARRAY_HEADER) != 1 or body.count(OPTION_BLOCK) != 1):
            raise ValueError('Invalid existing screensaver visibility menu patch')
        return body
    if OPTION_MARKER in body:
        if (body.count(OPTION_MARKER) != 1 or body.count(OPTION_BLOCK + BUILDER) != 1
                or body.count(THREE_ARRAY_HEADER) != 1 or ARRAY_HEADER in body):
            raise ValueError('Invalid existing B1051C screensaver menu patch')
    else:
        if body.count(ARRAY_HEADER) != 1 or body.count(BUILDER) != 1:
            raise ValueError('Expected original two-item screensaver menu and builder')
        body = body.replace(ARRAY_HEADER, THREE_ARRAY_HEADER).replace(BUILDER, OPTION_BLOCK + BUILDER)
    start = body.index(BUILDER)
    compact = '\n'.join(line.strip() for line in body[start:].splitlines()
                        if line.strip() and not line.strip().startswith('.line '))
    if compact != ORIGINAL_TAIL:
        raise ValueError('Expected original screensaver builder/listener contract')
    return body[:start] + DYNAMIC_TAIL


def _menu(body):
    if BOOX_MARKER in body:
        if (body.count(BOOX_MARKER) != 1 or body.count(BOOX_OPTION_BLOCK + DYNAMIC_TAIL) != 1
                or body.count(NEW_ARRAY_HEADER) != 1 or body.count(OPTION_BLOCK) != 1):
            raise ValueError('Invalid existing BOOX screensaver menu patch')
        return body
    body = _menu19(body)
    return body.replace(THREE_ARRAY_HEADER, NEW_ARRAY_HEADER).replace(DYNAMIC_TAIL, BOOX_OPTION_BLOCK + DYNAMIC_TAIL)


def _listener19(body):
    anchor = '    .locals 1\n'
    if DISPATCH_MARKER in body:
        if body.count(DISPATCH_MARKER) != 1 or body.count(anchor + DISPATCH_BLOCK) != 1:
            raise ValueError('Invalid existing B1051C screensaver dispatch patch')
        return body
    if body.count(anchor) != 1 or ':quickpic_b1051c_existing' in body:
        raise ValueError('Expected original screensaver click listener registers')
    return body.replace(anchor, anchor + DISPATCH_BLOCK, 1)


def _listener(body):
    anchor = '    .locals 1\n'
    if BOOX_DISPATCH_MARKER in body:
        if body.count(BOOX_DISPATCH_MARKER) != 1 or body.count(anchor + BOOX_DISPATCH_BLOCK + DISPATCH_BLOCK) != 1:
            raise ValueError('Invalid existing BOOX screensaver dispatch patch')
        return body
    body = _listener19(body)
    return body.replace(anchor, anchor + BOOX_DISPATCH_BLOCK, 1)


def _startup(body, app):
    anchor = '    invoke-static {}, ' + app + '->applyHiddenDefault()V\n'
    if body.count(anchor) != 1:
        raise ValueError('Expected one cold-start hidden-default anchor')
    if INITIALIZE_CALL in body:
        if body.count(INITIALIZE_CALL) != 1 or body.count(anchor + INITIALIZE_CALL) != 1:
            raise ValueError('Invalid existing screensaver initialization hook')
        return body
    if 'Lcom/alensw/jni/ScreensaverOptions;->initialize(' in body:
        raise ValueError('Unrecognized screensaver initialization hook')
    return body.replace(anchor, anchor + INITIALIZE_CALL, 1)

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
    apps = list(root.glob('smali*/**/QuickApp.smali'))
    if len(apps) != 1: raise ValueError('Expected one QuickApp cold-start class')
    app_text = apps[0].read_text()
    declarations = re.findall(r'(?m)^\.class[^\n]*[ \t]+(L[^;\s]+;)[ \t]*$', app_text)
    if len(declarations) != 1 or not declarations[0].endswith('/QuickApp;'):
        raise ValueError('Expected one QuickApp class descriptor')
    app = declarations[0]
    staged[apps[0]] = _method(app_text, STARTUP_SIGNATURE,
                             lambda body: _startup(body, app), 'QuickApp cold-start method')
    paths = sorted((root / 'res').glob('xml*/main_pref.xml'))
    if not paths: raise ValueError('Missing preference XML')
    for path in paths:
        text = path.read_text()
        if 'android:key="screensaver_size"' not in text:
            item = '\n            <com.alensw.jni.ScreensaverPreference android:icon="@drawable/ic_view" android:title="設定屏保圖尺寸" android:key="screensaver_size" />'
            text, count = re.subn(r'(<PreferenceCategory\b[^>]*android:title="@string/image_category"[^>]*>)', lambda m: m[1] + item, text)
            if count != 1: raise ValueError('Expected one image category')
        if 'android:key="screensaver_options"' not in text:
            item = '\n            <com.alensw.jni.ScreensaverOptions android:icon="@drawable/ic_view" android:title="顯示屏保選項" android:key="screensaver_options" />'
            text, count = re.subn(r'(<com\.alensw\.jni\.ScreensaverPreference\b[^>]*android:key="screensaver_size"[^>]*/>)', lambda m: m[1] + item, text)
            if count != 1: raise ValueError('Expected one screensaver size preference anchor')
        elif text.count('android:key="screensaver_options"') != 1 or '<com.alensw.jni.ScreensaverOptions ' not in text:
            raise ValueError('Invalid existing screensaver options preference')
        staged[path] = text
    for path, text in staged.items(): path.write_text(text)
    return {'preference_variants': len(paths), 'entry': SIGNATURE, 'helper': 'ScreensaverExport',
            'default': [1860,2480], 'set_as_items': 4,
            'b1051c_entry': 'BigmeScreensaver.confirm(Context,String)V',
            'boox_entry': 'BooxScreensaver.confirm(Context,String)V',
            'visibility': 'ScreensaverMenu fixed action snapshot; wallpaper always retained'}
