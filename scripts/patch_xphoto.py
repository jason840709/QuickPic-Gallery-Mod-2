"""Replace only the existing PNG-opening method; preserve all generation smali bytes."""
import argparse
import hashlib
import json
import re
from pathlib import Path

RELATIVE_PATH = Path("smali/com/alensw/ui/c/XPhotoScreenSaverBridge.smali")
METHOD = re.compile(
    rb"(?m)^\.method private static openXPhoto\(Landroid/content/Context;Ljava/io/File;\)V\r?\n"
    rb".*?^\.end method(?=\r?\n|$)",
    re.DOTALL,
)


def patch(decoded_root):
    path = Path(decoded_root) / RELATIVE_PATH
    before = path.read_bytes()
    matches = list(METHOD.finditer(before))
    if len(matches) != 1:
        raise ValueError("Expected exactly one original openXPhoto method in " + str(path))
    match = matches[0]
    newline = b"\r\n" if b"\r\n" in match.group() else b"\n"
    replacement = newline.join([
        b".method private static openXPhoto(Landroid/content/Context;Ljava/io/File;)V",
        b"    .locals 0",
        b"",
        b"    invoke-static {p0, p1}, Lcom/alensw/jni/ScreensaverOpener;->open(Landroid/content/Context;Ljava/io/File;)V",
        b"",
        b"    return-void",
        b".end method",
    ])
    after = before[:match.start()] + replacement + before[match.end():]
    # Fail closed if the replacement ever affects code/resources outside this method.
    new_match = METHOD.search(after)
    assert new_match is not None
    assert before[:match.start()] == after[:new_match.start()]
    assert before[match.end():] == after[new_match.end():]
    if after != before:
        path.write_bytes(after)
    untouched = before[:match.start()] + before[match.end():]
    return {
        "path": str(path),
        "changed": after != before,
        "before_sha256": hashlib.sha256(before).hexdigest(),
        "after_sha256": hashlib.sha256(after).hexdigest(),
        "outside_openXPhoto_sha256": hashlib.sha256(untouched).hexdigest(),
        "png_generation_preserved": True,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("decoded_root", type=Path)
    args = parser.parse_args()
    print(json.dumps(patch(args.decoded_root), ensure_ascii=False, indent=2))
