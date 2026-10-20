"""Configure optional private date index after Application initialization."""
from pathlib import Path
import re

def patch(root):
    paths=list(Path(root).glob("smali*/**/QuickApp.smali"))
    if len(paths)!=1: raise ValueError("Expected exactly one Application")
    path=paths[0];text=path.read_text()
    start=text.index(".method public onCreate()V");end=text.index(".end method",start)
    body=text[start:end]
    call="    invoke-static {p0}, Lcom/alensw/jni/MediaDates;->configure(Landroid/content/Context;)V\n    invoke-static {p0}, Lcom/alensw/jni/DateSort;->configure(Landroid/content/Context;)V\n"
    if "MediaDates;->configure" not in body:
        body,count=re.subn(r"(    invoke-super \{p0\}, L[^;]+;->onCreate\(\)V\n)",lambda m:m[1]+call,body)
        if count!=1: raise ValueError("Unexpected Application initialization")
        path.write_text(text[:start]+body+text[end:])
    return str(path.relative_to(root))
