#!/usr/bin/env python3
"""Insert only the compatibility hooks into a decoded, supported base APK."""
from pathlib import Path
import re

APP = "smali/com/alensw/PicFolderXPhotoPNG1/QuickApp.smali"
URI = "smali/com/alensw/ui/activity/a.smali"


def span(text, signature):
    if text.count(signature) != 1:
        raise ValueError("Expected exactly one method: " + signature)
    start = text.index(signature)
    end = text.index(".end method", start) + len(".end method")
    return start, end, text[start:end]


def patch(decoded_root):
    root = Path(decoded_root)
    changes = {}
    app = root / APP
    text = app.read_text(encoding="utf-8")
    start, end, body = span(text, ".method public onCreate()V")
    if "QuickPicExif;->setCacheDirectory" in body:
        raise ValueError("Core hooks already applied; start from the supported original base")
    locals_match = re.search(r"(?m)^    \.locals (\d+)$", body)
    if not locals_match or int(locals_match.group(1)) < 1:
        raise ValueError("Application hook requires a local register")
    hook = ("\n    invoke-virtual {p0}, Landroid/content/Context;->getCacheDir()Ljava/io/File;\n"
            "    move-result-object v0\n"
            "    invoke-static {v0}, Lcom/alensw/jni/QuickPicExif;->setCacheDirectory(Ljava/io/File;)V\n")
    body, count = re.subn(r"(    invoke-super \{p0\}, L[^;]+;->onCreate\(\)V\n)",
                         lambda match: match.group(1) + hook, body)
    if count != 1:
        raise ValueError("Unexpected Application onCreate structure")
    changes[app] = text[:start] + body + text[end:]
    uri = root / URI
    text = uri.read_text(encoding="utf-8")
    for signature in (".method public static a(Landroid/content/Context;Landroid/content/Intent;)Z",
                      ".method public static a(Landroid/app/Activity;Landroid/content/Intent;I)Z"):
        start, end, body = span(text, signature)
        if "CompatIntents;->prepare" in body:
            raise ValueError("URI hooks already applied")
        body, count = re.subn(r"(?m)^(    :try_start_0\n)", lambda match: match.group(1) +
                             "\n    invoke-static {p0, p1}, Lcom/alensw/jni/CompatIntents;->prepare(Landroid/content/Context;Landroid/content/Intent;)V\n", body)
        if count != 1:
            raise ValueError("Unexpected URI try block")
        text = text[:start] + body + text[end:]
    changes[uri] = text
    result_pattern = re.compile(r"(    invoke-virtual \{([^}]+)\}, L[^;]+;->setResult\(ILandroid/content/Intent;\)V)")
    def prepare(match):
        registers = [item.strip() for item in match.group(2).split(",")]
        if len(registers) != 3:
            raise ValueError("Unexpected setResult registers")
        return ("    invoke-static {" + registers[0] + ", " + registers[-1] +
                "}, Lcom/alensw/jni/CompatIntents;->prepareResult(Landroid/app/Activity;Landroid/content/Intent;)V\n" + match.group(1))
    result_count = 0
    for path in sorted((root / "smali/com/alensw").rglob("*.smali")):
        text = changes.get(path, path.read_text(encoding="utf-8"))
        result, count = result_pattern.subn(prepare, text)
        if count:
            changes[path] = result
            result_count += count
    if result_count != 5:
        raise ValueError("Expected five result hooks; found " + str(result_count))
    # Validate the complete plan before changing any original file.
    for path, text in changes.items():
        path.write_text(text, encoding="utf-8")
    return {"changed_files": [str(path.relative_to(root)) for path in changes],
            "external_result_hooks": result_count}
