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
    first = [by_id["@id/delete"], by_id["@id/slideshow"]]
    for item in items:
        item_id = item.get(ANDROID + "id")
        if item_id in ("@id/delete", "@id/slideshow"):
            item.set(ANDROID + "showAsAction", "always")
        elif item_id in ("@id/share", "@id/edit", "@id/rotate"):
            item.set(ANDROID + "showAsAction", "never")
    root[:] = first + [item for item in items if item not in first]
    ET.indent(tree, space="    ")
    tree.write(path, encoding="utf-8", xml_declaration=True)


def patch(root):
    root = Path(root)
    paths = []
    for name in ("as", "dp"):
        path = root / f"smali/com/alensw/ui/c/{name}.smali"
        patch_controller(path, name)
        paths.append(str(path.relative_to(root)))
    path = root / "res/menu/picture.xml"
    patch_menu(path)
    paths.append(str(path.relative_to(root)))
    return paths


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_viewer.py DECODED_ROOT")
    print(json.dumps(patch(sys.argv[1]), ensure_ascii=False))
