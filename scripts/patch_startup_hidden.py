# SPDX-License-Identifier: MIT
"""Make the existing startup hidden policy opt-out; retain the password gate."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re
from patch_defaults import method
NS = "http://schemas.android.com/apk/res/android"
A = "{" + NS + "}"
KEY = "startup_show_hidden"

def patch(root):
    root = Path(root)
    apps = list(root.glob("smali*/**/QuickApp.smali"))
    if len(apps) != 1: raise ValueError("Expected one QuickApp")
    app = apps[0]
    source = app.read_text()
    start, end, part = method(source, ".method public static applyHiddenDefault()V")
    if KEY in part: raise ValueError("Startup hidden policy already installed")
    seam = '    const-string v2, "protect_hidden"\n    const/4 v3, 0x1\n'
    if part.count(seam) != 1 or '    :hidden_default_allowed' not in part:
        raise ValueError("Unexpected hidden policy; password gate must be retained")
    replacement = ('    const-string v2, "' + KEY + '"\n'
        '    const/4 v3, 0x1\n'
        '    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z\n'
        '    move-result v3\n'
        '    if-eqz v3, :hidden_default_allowed\n'
        '    const-string v2, "protect_hidden"\n')
    after = source[:start] + part.replace(seam, replacement, 1) + source[end:]
    owner = re.search(r'^\.class .* (L[^;]+;)$', source, re.M).group(1)
    after += ("\n.method public static applyHiddenAfterUnlock()V\n    .locals 3\n"
        "    sget-object v0, " + owner + "->B:" + owner + "\n"
        "    invoke-static {v0}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;\n"
        "    move-result-object v0\n    const-string v1, \"startup_show_hidden\"\n    const/4 v2, 0x1\n"
        "    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z\n"
        "    move-result v0\n    if-eqz v0, :unlock_done\n"
        "    invoke-static {}, " + owner + "->applyHiddenDefault()V\n"
        "    :unlock_done\n    return-void\n.end method\n")
    callback = root / 'smali/com/alensw/ui/a/ac.smali'
    original_callback = callback.read_text()
    call = '    invoke-static {}, ' + owner + '->applyHiddenDefault()V'
    if original_callback.count(call) != 1: raise ValueError('Missing authenticated hidden hook')
    after_callback = original_callback.replace(call, call.replace('applyHiddenDefault', 'applyHiddenAfterUnlock'), 1)

    staged = []
    xmls = sorted(root.glob("res/xml*/main_pref.xml"))
    if not xmls: raise ValueError("Missing preferences")
    for path in xmls:
        tree = ET.parse(path)
        screens = [n for n in tree.getroot().iter("PreferenceScreen") if n.get(A+"title") == "@string/security"]
        if len(screens) != 1: raise ValueError("Expected one Security screen")
        screen = screens[0]
        if any(n.get(A+"key") == KEY for n in tree.getroot().iter()):
            raise ValueError("Duplicate startup hidden setting")
        row = ET.Element("SwitchPreference", {A+"key": KEY, A+"title": "@string/quickpic_startup_hidden_title",
            A+"summary": "@string/quickpic_startup_hidden_summary", A+"icon": "@drawable/ic_pass_hidden",
            A+"defaultValue": "true", A+"switchTextOn": "@string/quickpic_switch_on", A+"switchTextOff": "@string/quickpic_switch_off"})
        screen.insert(0, row)
        screen.insert(1, ET.Element("Preference", {A+"layout": "@layout/pref_divider"}))
        staged.append((path, tree))
    labels = {
        "values": ("Show hidden items at startup", "Apply when QuickPic is opened again"),
        "values-zh": ("启动时自动显示隐藏项目", "重新打开快图时应用"),
        "values-zh-rCN": ("启动时自动显示隐藏项目", "重新打开快图时应用"),
        "values-zh-rTW": ("啟動時自動顯示隱藏項目", "重新開啟快圖時套用"),
        "values-zh-rHK": ("啟動時自動顯示隱藏項目", "重新開啟快圖時套用")}
    for folder, words in labels.items():
        node = ET.Element("resources")
        for suffix, word in zip(("title", "summary"), words):
            ET.SubElement(node, "string", {"name": "quickpic_startup_hidden_"+suffix}).text = word
        path = root/"res"/folder/"quickpic_startup_hidden.xml"
        if path.exists(): raise ValueError("Existing startup strings")
        staged.append((path, ET.ElementTree(node)))
    # Validate every expected seam before mutating the supplied decode.
    ET.register_namespace("android", NS)
    app.write_text(after)
    callback.write_text(after_callback)
    for path, tree in staged:
        path.parent.mkdir(parents=True, exist_ok=True)
        tree.write(path, encoding="utf-8", xml_declaration=True)
    return {"key": KEY, "default": True, "password_gate": "retained", "paths": [str(app.relative_to(root)), str(callback.relative_to(root))] + [str(p.relative_to(root)) for p,_ in staged]}
