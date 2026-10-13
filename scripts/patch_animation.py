"""Enable WebP candidates in the two actual full-image loader routes."""
from pathlib import Path
import re
HELPER='Lcom/alensw/jni/AnimationFormats;'
CLOCK='Lcom/alensw/jni/AnimationPlayback;'

def body(text,signature):
    start=text.index(signature);end=text.index('.end method',start)+len('.end method')
    return start,end,text[start:end]

def patch(root):
    root=Path(root);path=root/'smali/com/alensw/a/at.smali';text=path.read_text();routes=0
    for name in ('a','b','c'):
        signature='.method private '+name+'(Lcom/alensw/b/c/f;Landroid/net/Uri;)Lcom/alensw/b/h/j;'
        start,end,part=body(text,signature)
        if HELPER+'->isCandidate' in part:continue
        pattern=(r'(    const-string (v\d+), "image/gif"\s+)'
                 r'(    iget-object (v\d+), v\d+, Lcom/alensw/b/h/a;->outMimeType:Ljava/lang/String;\s+)'
                 r'    invoke-virtual \{\2, \4\}, Ljava/lang/String;->equals\(Ljava/lang/Object;\)Z')
        part,n=re.subn(pattern,lambda m:m.group(1)+m.group(3)+'    invoke-static {'+m.group(4)+'}, '+HELPER+'->isCandidate(Ljava/lang/String;)Z',part)
        if n!=1:raise ValueError('Unexpected GIF route structure in loader '+name)
        text=text[:start]+part+text[end:];routes+=1
    path.write_text(text)
    for controller,method,flag,helper in (
            ('as','s','s','needsAsUpgrade'), ('dp','v','u','needsDpUpgrade')):
        viewpath=root/('smali/com/alensw/ui/c/'+controller+'.smali')
        viewtext=viewpath.read_text()
        start,end,part=body(viewtext,'.method private '+method+'()Z')
        if HELPER+'->'+helper not in part:
            if part.count('"image/gif"')!=1 or '->getPictureType()I' not in part or '->getMimeType()Ljava/lang/String;' not in part:
                raise ValueError('Unexpected manual-viewer promotion predicate: '+controller)
            part=('.method private '+method+'()Z\n    .locals 3\n\n'
                  '    iget-boolean v0, p0, Lcom/alensw/ui/c/'+controller+';->'+flag+':Z\n'
                  '    iget-object v1, p0, Lcom/alensw/ui/c/'+controller+';->f:Lcom/alensw/ui/view/PictureView;\n'
                  '    invoke-virtual {v1}, Lcom/alensw/ui/view/PictureView;->getPictureType()I\n'
                  '    move-result v1\n'
                  '    iget-object v2, p0, Lcom/alensw/ui/c/'+controller+';->f:Lcom/alensw/ui/view/PictureView;\n'
                  '    invoke-virtual {v2}, Lcom/alensw/ui/view/PictureView;->getMimeType()Ljava/lang/String;\n'
                  '    move-result-object v2\n'
                  '    invoke-static {v0, v1, v2}, '+HELPER+'->'+helper+'(ZILjava/lang/String;)Z\n'
                  '    move-result v0\n    return v0\n.end method')
            viewtext=viewtext[:start]+part+viewtext[end:];viewpath.write_text(viewtext)
    # The animated bitmap wrapper otherwise reports every format as image/gif.
    path=root/'smali/com/alensw/b/h/d.smali';text=path.read_text()
    start,end,part=body(text,'.method private a(ILandroid/os/Handler;)V')
    if HELPER+'->mimeForHandle' not in part:
        prefix=('\n    invoke-static {p1}, '+HELPER+'->mimeForHandle(I)Ljava/lang/String;\n'
                '    move-result-object v0\n'
                '    iput-object v0, p0, Lcom/alensw/b/h/j;->h:Ljava/lang/String;\n')
        part,n=re.subn(r'(    \.locals \d+\n)',lambda m:m.group(1)+prefix,part,count=1)
        if n!=1:raise ValueError('Missing animation initializer locals')
        text=text[:start]+part+text[end:];path.write_text(text)
    # A non-animated WebP is a normal static fallback, not a load error.
    text=path.read_text()
    start,end,part=body(text,'.method public static a(Lcom/alensw/b/c/f;Landroid/net/Uri;Landroid/os/Handler;)Lcom/alensw/b/h/d;')
    marker='    const-string v2, "load error"'
    if marker in part:
        pattern=r'(    if-nez v1, :cond_1\n)\s+new-instance v0, Ljava/lang/RuntimeException;\s+const-string v2, "load error"\s+invoke-direct \{v0, v2\}, Ljava/lang/RuntimeException;-><init>\(Ljava/lang/String;\)V\s+throw v0'
        part,n=re.subn(pattern,lambda m:m.group(1)+'\n    goto :cond_0',part)
        if n!=1:raise ValueError('Unexpected zero-handle static fallback')
        text=text[:start]+part+text[end:];path.write_text(text)
    # Wrap only the legacy worker's scheduling seam. Pixel decode, UI post,
    # cancellation flag and the two existing buffer callbacks remain intact.
    path=root/'smali/com/alensw/b/h/g.smali';text=path.read_text()
    start,end,part=body(text,'.method public d()Ljava/lang/Void;')
    if CLOCK+'->beforeFrame' not in part:
        dtext=(root/'smali/com/alensw/b/h/d.smali').read_text()
        for field in ('j','k','m'):
            if '.field private '+field+':I' not in dtext:raise ValueError('Unexpected animation controller field')
        if '.field final synthetic a:Lcom/alensw/b/h/d;' not in text:raise ValueError('Unexpected worker owner')
        if '    .locals 7\n' not in part:raise ValueError('Unexpected worker locals')
        part=part.replace('    .locals 7\n','    .locals 8\n',1)
        old='    if-eqz v0, :cond_0\n\n    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J'
        new=('    if-eqz v0, :cond_0\n\n'
             '    invoke-static {p0}, '+CLOCK+'->beforeFrame(Ljava/lang/Object;)Z\n'
             '    move-result v1\n    if-eqz v1, :cond_3\n\n'
             '    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J')
        if part.count(old)!=1:raise ValueError('Unexpected worker dequeue seam')
        part=part.replace(old,new,1)
        old=('    invoke-static {v1, v4, v5, v6}, Lcom/alensw/b/h/d;->b(Lcom/alensw/b/h/d;III)I\n\n'
             '    move-result v1')
        if part.count(old)!=1:raise ValueError('Unexpected worker decode seam')
        part=part.replace(old,old+'\n\n    move v7, v1',1)
        old='    invoke-static {v0, v1}, Ljava/lang/Math;->max(II)I\n\n    move-result v0'
        if part.count(old)!=1:raise ValueError('Unexpected worker wait seam')
        part=part.replace(old,old+'\n\n    invoke-static {p0, v7, v0}, '+CLOCK+'->delayAfterFrame(Ljava/lang/Object;II)I\n'
                          '    move-result v0\n    if-ltz v0, :cond_3',1)
        text=text[:start]+part+text[end:];path.write_text(text)
    return {'loader_candidate_routes_added':routes,'paths':['smali/com/alensw/a/at.smali','smali/com/alensw/b/h/d.smali','smali/com/alensw/b/h/g.smali','smali/com/alensw/ui/c/as.smali','smali/com/alensw/ui/c/dp.smali']}
