"""Patch decoded QuickPic viewer menus. Usage: patch_viewer.py DECODED_ROOT.

Run before apktool resource rebuild. Replacement ViewerControls smali must be
merged from rebuild/src by the existing Java/D8 pipeline.
"""
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
HELPER = "Lcom/alensw/jni/ViewerControls;"


def method(text, signature):
    start = text.index(signature)
    end = text.index(".end method", start) + len(".end method")
    return start, end, text[start:end]


def patch_controller(path, name):
    text = path.read_text()
    signature = ".method public a(Landroid/view/Menu;)Z"
    start, end, body = method(text, signature)
    if f"{HELPER}->configure" not in body:
        hook = (f"    iget-object v0, p0, Lcom/alensw/ui/c/{name};->C:Lcom/alensw/ui/c/cx;\n"
                f"    invoke-static {{v0, p1}}, {HELPER}->configure(Landroid/content/Context;Landroid/view/Menu;)V\n\n")
        body, count = re.subn(r"(?m)^    return v\d+$", lambda match: hook + match.group(0), body)
        assert count == 1, f"Unexpected menu return structure: {name}"
        text = text[:start] + body + text[end:]
    signature = ".method public a(Landroid/view/MenuItem;)Z"
    start, end, body = method(text, signature)
    if f"{HELPER}->showDialog" not in body:
        hook = ("\n    invoke-interface {p1}, Landroid/view/MenuItem;->getItemId()I\n"
                "    move-result v0\n"
                "    const v1, 0xf0a010\n"
                "    if-ne v0, v1, :viewer_interval_continue\n"
                f"    iget-object v0, p0, Lcom/alensw/ui/c/{name};->C:Lcom/alensw/ui/c/cx;\n"
                f"    invoke-static {{v0, p0}}, {HELPER}->showDialog(Landroid/content/Context;Ljava/lang/Object;)V\n"
                "    const/4 v0, 0x1\n"
                "    return v0\n"
                "    :viewer_interval_continue\n")
        body, count = re.subn(r"(    \.locals \d+\n)", lambda match: match.group(1) + hook, body, count=1)
        assert count == 1, f"No locals declaration: {name}"
        text = text[:start] + body + text[end:]
    path.write_text(text)


def patch_menu(path):
    ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
    tree = ET.parse(path)
    root = tree.getroot()
    items = list(root)
    by_id = {item.get(ANDROID + "id"): item for item in items}
    first = [by_id["@id/slideshow"], by_id["@id/delete"], by_id["@id/edit"], by_id["@id/share"], by_id["@id/set_as"]]
    for item in items:
        item_id = item.get(ANDROID + "id")
        if item_id == "@id/slideshow":
            item.set(ANDROID + "showAsAction", "always")
        elif item_id in ("@id/delete", "@id/share", "@id/edit", "@id/rotate"):
            item.set(ANDROID + "showAsAction", "never")
    root[:] = first + [item for item in items if item not in first]
    for index, item in enumerate(root):
        item.set(ANDROID + "orderInCategory", "0" if item.get(ANDROID + "id") == "@id/slideshow" else str(20 + index))
    ET.indent(tree, space="    ")
    tree.write(path, encoding="utf-8", xml_declaration=True)


# The existing resource ID is used by ViewerControls and must stay unchanged.
TIMER_ICON = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#ff000000" android:pathData="M9,1h6v2H9z M11,8h2v6h-2z M19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61z M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7s7,3.13 7,7s-3.13,7 -7,7z" />
</vector>
'''


def patch_interval_icon(root):
    root = Path(root)
    paths = sorted((root / "res").glob("drawable*/ic_slide_interval.xml"))
    if not paths or not (root / "res/drawable/ic_slide_interval.xml").is_file():
        raise ValueError("Missing interval drawable; cannot replace the old gallery icon")
    for path in paths:
        path.write_text(TIMER_ICON)
    return [str(path.relative_to(root)) for path in paths]


def patch(root):
    root = Path(root)
    paths = patch_interval_icon(root)
    for name in ("as", "dp"):
        path = root / f"smali/com/alensw/ui/c/{name}.smali"
        patch_controller(path, name)
        paths.append(str(path.relative_to(root)))
    path = root / "res/menu/picture.xml"
    patch_menu(path)
    paths.append(str(path.relative_to(root)))
    for path in sorted((root / "res").glob("values*/strings.xml")):
        text = path.read_text()
        folder = path.parent.name
        label = ("投影片間隔" if folder.startswith("values-zh") and folder not in ("values-zh", "values-zh-rCN")
                 else "幻灯片间隔" if folder.startswith("values-zh") else "Slideshow interval")
        text, count = re.subn(r'(<string name="slideshow_interval">)[^<]*(</string>)',lambda m:m[1]+label+m[2],text)
        if count: path.write_text(text); paths.append(str(path.relative_to(root)))
    return paths


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_viewer.py DECODED_ROOT")
    print(json.dumps(patch(sys.argv[1]), ensure_ascii=False))
