# SPDX-License-Identifier: MIT
"""Add a pixel-size preference and replace the fixed-size export entry point."""
from pathlib import Path
import re

SIGNATURE = '.method public static prepareAndOpen(Landroid/content/Context;Ljava/lang/String;)Z'

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
    return {'preference_variants': len(paths), 'entry': SIGNATURE, 'helper': 'ScreensaverExport', 'default': [1860,2480]}
