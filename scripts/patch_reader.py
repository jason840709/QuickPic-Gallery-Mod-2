# SPDX-License-Identifier: MIT
"""Opt-in transactional reader patch for an externally supplied decode.
Apply after patch_animation; no decompiled legacy sources are bundled.
"""
from pathlib import Path
import re
HELPER = 'Lcom/alensw/jni/ReaderMode;'
HOST = 'Lcom/alensw/jni/ReaderMode$Host;'
FRAME = 'Lcom/alensw/jni/ReaderMode$Frame;'
PIC = 'Lcom/alensw/b/h/j;'
VIEW = 'Lcom/alensw/ui/view/j;'
MOVIE = 'Lcom/alensw/b/h/d;'

def method(text, signature):
    found = list(re.finditer('^' + re.escape(signature) + '$', text, re.M))
    if len(found) != 1: raise ValueError('Expected one method: ' + signature)
    start = found[0].start()
    end = text.index('.end method', start) + len('.end method')
    return start, end, text[start:end]

def change(text, signature, transform):
    start, end, part = method(text, signature)
    return text[:start] + transform(part) + text[end:]

def once(text, old, new, description):
    if text.count(old) != 1: raise ValueError('Unexpected reader seam: ' + description)
    return text.replace(old, new, 1)

def head(part, marker, code, minimum=1):
    if marker in part: return part
    match = re.search(r'^    \.locals (\d+)[ \t]*$', part, re.M)
    if not match: raise ValueError('Reader hook requires .locals')
    part = part[:match.start()] + '    .locals ' + str(max(minimum, int(match[1]))) + part[match.end():]
    match = re.search(r'^    \.locals \d+\n', part, re.M)
    pos = match.end()
    while re.match(r'\s*\.annotation\b', part[pos:]):
        pos = part.index('.end annotation', pos) + len('.end annotation')
    return part[:pos] + '\n' + code + '\n' + part[pos:]

def implements(text, interface):
    if '.implements ' + interface in text: return text
    text, count = re.subn(r'(^\.super [^\n]+\n)', lambda m: m[1] + '.implements ' + interface + '\n', text, count=1, flags=re.M)
    if count != 1: raise ValueError('Missing class superclass')
    return text

def add_method(text, signature, source):
    if signature in text: return text
    return text.rstrip() + '\n\n' + source.strip() + '\n'

def host_methods(name, app):
    owner = 'Lcom/alensw/ui/c/' + name + ';'
    album = name == 'as'
    folder, foldertype = ('k', 'Lcom/alensw/bean/CommonFolder;') if album else ('m', 'Lcom/alensw/a/e;')
    indexfield, countmethod = ('k', 'o') if album else ('c', 'j')
    neighbor = 'n' if album else 'q'
    cancel, slideshow, toggle = ('o', 'z', 'I') if album else ('r', 'J', 'L')
    current = f'''    iget-object v0, p0, {owner}->l:Lcom/alensw/bean/CommonFile;
    invoke-virtual {{v0}}, Lcom/alensw/bean/CommonFile;->i()Landroid/net/Uri;
    move-result-object v0''' if album else f'    iget-object v0, p0, {owner}->o:Landroid/net/Uri;'
    uri = f'''    invoke-virtual {{v0, p1}}, {foldertype}->a(I)Lcom/alensw/bean/CommonFile;
    move-result-object v0
    invoke-virtual {{v0}}, Lcom/alensw/bean/CommonFile;->i()Landroid/net/Uri;
    move-result-object v0''' if album else f'''    invoke-virtual {{v0, p1}}, {foldertype}->d(I)Landroid/net/Uri;
    move-result-object v0'''
    imagecurrent = f'''    iget-object v0, p0, {owner}->l:Lcom/alensw/bean/CommonFile;
    invoke-virtual {{v0}}, Lcom/alensw/bean/CommonFile;->h()C
    move-result v0''' if album else f'    iget-char v0, p0, {owner}->n:C'
    image = f'''    invoke-virtual {{v0, p1}}, {foldertype}->a(I)Lcom/alensw/bean/CommonFile;
    move-result-object v0
    invoke-virtual {{v0}}, Lcom/alensw/bean/CommonFile;->h()C
    move-result v0''' if album else f'''    invoke-virtual {{v0, p1}}, {foldertype}->f(I)C
    move-result v0'''
    load = f'''    iget-object v0, p0, {owner}->l:Lcom/alensw/bean/CommonFile;
    if-ltz p1, :reader_load_current
    iget-object v1, p0, {owner}->{folder}:{foldertype}
    if-eqz v1, :reader_load_current
    invoke-virtual {{v1, p1}}, {foldertype}->a(I)Lcom/alensw/bean/CommonFile;
    move-result-object v0
    :reader_load_current
    sget-object v1, {app}->u:Lcom/alensw/a/ao;
    const/4 v2, 0x5
    invoke-virtual {{v1, v0, v2, p2}}, Lcom/alensw/a/ao;->a(Lcom/alensw/bean/CommonFile;ILandroid/os/Handler;)Lcom/alensw/a/as;
    move-result-object v0''' if album else f'''    iget-object v0, p0, {owner}->o:Landroid/net/Uri;
    iget-char v1, p0, {owner}->n:C
    if-ltz p1, :reader_load_current
    iget-object v2, p0, {owner}->{folder}:{foldertype}
    if-eqz v2, :reader_load_current
    invoke-virtual {{v2, p1}}, {foldertype}->d(I)Landroid/net/Uri;
    move-result-object v0
    invoke-virtual {{v2, p1}}, {foldertype}->f(I)C
    move-result v1
    :reader_load_current
    sget-object v2, {app}->u:Lcom/alensw/a/ao;
    const/4 v3, 0x5
    invoke-virtual {{v2, v0, v1, v3, p2}}, Lcom/alensw/a/ao;->a(Landroid/net/Uri;CILandroid/os/Handler;)Lcom/alensw/a/as;
    move-result-object v0'''
    commit = f'''    invoke-virtual {{v0, p2}}, {foldertype}->a(I)Lcom/alensw/bean/CommonFile;
    move-result-object v1
    iput-object v1, p0, {owner}->l:Lcom/alensw/bean/CommonFile;''' if album else f'''    invoke-virtual {{v0, p2}}, {foldertype}->d(I)Landroid/net/Uri;
    move-result-object v1
    iput-object v1, p0, {owner}->o:Landroid/net/Uri;
    invoke-virtual {{v0, p2}}, {foldertype}->f(I)C
    move-result v1
    iput-char v1, p0, {owner}->n:C'''
    return f'''
.method public readerContext()Landroid/content/Context;
    .locals 1
    iget-object v0, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    return-object v0
.end method

.method public readerIndex()I
    .locals 1
    iget-object v0, p0, {owner}->{folder}:{foldertype}
    if-eqz v0, :reader_index_single
    iget v0, v0, {foldertype}->{indexfield}:I
    return v0
    :reader_index_single
    const/4 v0, -0x1
    return v0
.end method

.method public readerCount()I
    .locals 1
    iget-object v0, p0, {owner}->{folder}:{foldertype}
    if-eqz v0, :reader_count_single
    invoke-virtual {{v0}}, {foldertype}->{countmethod}()I
    move-result v0
    return v0
    :reader_count_single
    const/4 v0, 0x0
    return v0
.end method

.method public readerUri(I)Landroid/net/Uri;
    .locals 1
    if-ltz p1, :reader_uri_current
    iget-object v0, p0, {owner}->{folder}:{foldertype}
    if-eqz v0, :reader_uri_current
{uri}
    return-object v0
    :reader_uri_current
{current}
    return-object v0
.end method

.method public readerImage(I)Z
    .locals 2
    if-ltz p1, :reader_image_current
    iget-object v0, p0, {owner}->{folder}:{foldertype}
    if-eqz v0, :reader_image_current
{image}
    goto :reader_image_type
    :reader_image_current
{imagecurrent}
    :reader_image_type
    const/16 v1, 0x49
    if-ne v0, v1, :reader_image_other
    const/4 v0, 0x1
    return v0
    :reader_image_other
    const/4 v0, 0x0
    return v0
.end method

.method public readerLoad(ILandroid/os/Handler;)Ljava/util/concurrent/Future;
    .locals 4
{load}
    return-object v0
.end method

.method public readerCommit(Ljava/lang/Object;I)V
    .locals 2
    if-ltz p2, :reader_commit_single
    iget-object v0, p0, {owner}->{folder}:{foldertype}
    if-eqz v0, :reader_commit_single
{commit}
    iput p2, v0, {foldertype}->{indexfield}:I
    :reader_commit_single
    iget-object v0, p0, {owner}->{neighbor}:{PIC}
    if-eqz v0, :reader_commit_display
    invoke-virtual {{v0}}, {PIC}->m()I
    const/4 v0, 0x0
    iput-object v0, p0, {owner}->{neighbor}:{PIC}
    :reader_commit_display
    check-cast p1, {PIC}
    const/4 v0, 0x1
    invoke-virtual {{p0, p1, v0}}, {owner}->a({PIC}Z)V
    return-void
.end method

.method public readerCancelLegacy()V
    .locals 0
    invoke-direct {{p0}}, {owner}->{cancel}()V
    invoke-direct {{p0}}, {owner}->quickpicReaderClearSlide()V
    return-void
.end method

.method public readerActive()Z
    .locals 2
    iget v0, p0, {owner}->H:I
    const/4 v1, 0x2
    if-ne v0, v1, :reader_inactive
    const/4 v0, 0x1
    return v0
    :reader_inactive
    const/4 v0, 0x0
    return v0
.end method

.method public readerSlideshow()Z
    .locals 1
    invoke-direct {{p0}}, {owner}->{slideshow}()Z
    move-result v0
    return v0
.end method

.method public readerStopSlideshow()V
    .locals 1
    invoke-direct {{p0}}, {owner}->{slideshow}()Z
    move-result v0
    if-eqz v0, :reader_slide_already_stopped
    invoke-direct {{p0}}, {owner}->{toggle}()V
    :reader_slide_already_stopped
    return-void
.end method

.method public readerSlideReady()Z
    .locals 1
    iget-object v0, p0, {owner}->quickpicReaderSlide:{PIC}
    if-eqz v0, :reader_no_slide
    const/4 v0, 0x1
    return v0
    :reader_no_slide
    const/4 v0, 0x0
    return v0
.end method

.method private quickpicReaderClearSlide()V
    .locals 1
    iget-object v0, p0, {owner}->quickpicReaderSlide:{PIC}
    if-eqz v0, :reader_slide_clear_done
    invoke-virtual {{v0}}, {PIC}->m()I
    const/4 v0, 0x0
    iput-object v0, p0, {owner}->quickpicReaderSlide:{PIC}
    :reader_slide_clear_done
    return-void
.end method
'''

def patch_controller(text, name, app):
    owner = 'Lcom/alensw/ui/c/' + name + ';'
    album = name == 'as'
    pending, future, toggle, dispatch = ('S', 'L', 'I', 'y') if album else ('T', 'M', 'L', 'I')
    text = implements(text, HOST)
    field = '.field private quickpicReaderSlide:' + PIC
    if field not in text:
        pos = text.index('.method ')
        text = text[:pos] + field + '\n\n' + text[pos:]
    if '.method public readerContext()Landroid/content/Context;' not in text:
        text = text.rstrip() + '\n' + host_methods(name, app)
    text = change(text, '.method protected a(' + PIC + 'Z)V', lambda p: head(p, ':reader_display_legacy', f'''    invoke-static {{p0, p1}}, {HELPER}->beforeDisplay({HOST}Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :reader_display_legacy
    return-void
    :reader_display_legacy'''))
    for signature in ('.method public b()V', '.method public g()V'):
        text = change(text, signature, lambda p: head(p, '->cancel(' + HOST, f'''    invoke-static {{p0}}, {HELPER}->cancel({HOST})V
    invoke-direct {{p0}}, {owner}->quickpicReaderClearSlide()V'''))
    text = change(text, '.method private ' + toggle + '()V', lambda p: head(p, '->quickpicReaderClearSlide()', f'    invoke-direct {{p0}}, {owner}->quickpicReaderClearSlide()V', 0))
    def resume(part):
        if '->resume(' + HOST in part: return part
        if part.count('    return-void') < 1: raise ValueError('Unexpected viewer resume returns: ' + name)
        return part.replace('    return-void', f'    invoke-static {{p0}}, {HELPER}->resume({HOST})V\n    return-void')
    text = change(text, '.method public a()V', resume)
    scheduler = 'p' if album else 's'
    text = change(text, '.method private ' + scheduler + '()V', lambda p: head(p, ':reader_schedule_legacy', f'''    iget-object v0, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    invoke-static {{v0}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :reader_schedule_legacy
    const/4 v0, -0x1
    invoke-virtual {{p0, v0}}, {owner}->readerImage(I)Z
    move-result v0
    if-eqz v0, :reader_schedule_legacy
    return-void
    :reader_schedule_legacy'''))
    def slide_load(part):
        if ':reader_slide_watch_done' in part: return part
        seam = '    if-nez p1, :cond_3\n'
        part = once(part, seam, seam + f'''
    iget-object v2, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    invoke-static {{v2}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v2
    if-nez v2, :cond_3
''', name + ' initial slide identity')
        part = once(part, '    const/4 v2, 0x2\n', f'''    iget-object v2, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    invoke-static {{v2}}, {HELPER}->slideshowRequest(Landroid/content/Context;)I
    move-result v2
''', name + ' slide request')
        seam = f'    iput-object v0, p0, {owner}->{future}:Lcom/alensw/a/as;\n'
        return once(part, seam, seam + f'''
    iget-object v1, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    invoke-static {{v1}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v1
    if-eqz v1, :reader_slide_watch_done
    iget v1, p0, {owner}->{pending}:I
    invoke-virtual {{p0, v1}}, {owner}->readerImage(I)Z
    move-result v1
    if-eqz v1, :reader_slide_watch_done
    invoke-static {{p0, v0}}, {HELPER}->watchSlide({HOST}Ljava/util/concurrent/Future;)V
    :reader_slide_watch_done
''', name + ' slide future')
    text = change(text, '.method private b(Z)V', slide_load)
    text = change(text, '.method private b(' + PIC + ')V', lambda p: head(p, ':reader_slide_received_legacy', f'''    iget-object v0, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    invoke-static {{v0}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :reader_slide_received_legacy
    iget v0, p0, {owner}->{pending}:I
    invoke-virtual {{p0, v0}}, {owner}->readerImage(I)Z
    move-result v0
    if-eqz v0, :reader_slide_received_legacy
    invoke-virtual {{p1}}, {PIC}->readerReady()Z
    move-result v0
    if-eqz v0, :reader_slide_received_drop
    iget v0, p0, {owner}->{pending}:I
    invoke-virtual {{p0, v0}}, {owner}->readerUri(I)Landroid/net/Uri;
    move-result-object v0
    invoke-virtual {{p1, v0}}, {PIC}->a(Landroid/net/Uri;)Z
    move-result v0
    if-eqz v0, :reader_slide_received_drop
    invoke-direct {{p0}}, {owner}->quickpicReaderClearSlide()V
    invoke-virtual {{p1}}, {PIC}->c(){PIC}
    move-result-object v0
    iput-object v0, p0, {owner}->quickpicReaderSlide:{PIC}
    invoke-static {{p0}}, {HELPER}->slideReceived({HOST})V
    goto :reader_slide_received_legacy
    :reader_slide_received_drop
    return-void
    :reader_slide_received_legacy'''))
    def advance(part):
        return head(part, ':reader_slide_advance_legacy', f'''    iget-object v0, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    invoke-static {{v0}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :reader_slide_advance_legacy
    iget v2, p0, {owner}->{pending}:I
    if-ltz v2, :reader_slide_advance_done
    invoke-virtual {{p0}}, {owner}->readerCount()I
    move-result v0
    if-ge v2, v0, :reader_slide_advance_done
    invoke-virtual {{p0, v2}}, {owner}->readerImage(I)Z
    move-result v0
    if-eqz v0, :reader_slide_advance_legacy
    iget-object v1, p0, {owner}->quickpicReaderSlide:{PIC}
    if-eqz v1, :reader_slide_advance_done
    invoke-virtual {{v1}}, {PIC}->readerReady()Z
    move-result v0
    if-eqz v0, :reader_slide_advance_done
    invoke-virtual {{p0, v2}}, {owner}->readerUri(I)Landroid/net/Uri;
    move-result-object v0
    invoke-virtual {{v1, v0}}, {PIC}->a(Landroid/net/Uri;)Z
    move-result v0
    if-eqz v0, :reader_slide_advance_done
    const/4 v0, 0x0
    iput-object v0, p0, {owner}->quickpicReaderSlide:{PIC}
    :reader_slide_commit_start
    iget-object v0, p0, {owner}->f:Lcom/alensw/ui/view/PictureView;
    invoke-virtual {{v0}}, Landroid/view/View;->clearAnimation()V
    invoke-virtual {{v0}}, Lcom/alensw/ui/view/PictureView;->h()V
    iget-object v0, p0, {owner}->g:Landroid/widget/ImageView;
    invoke-virtual {{v0}}, Landroid/view/View;->clearAnimation()V
    const/4 v3, 0x4
    invoke-virtual {{v0, v3}}, Landroid/view/View;->setVisibility(I)V
    const/4 v0, 0x0
    invoke-direct {{p0, v0}}, {owner}->d(Z)V
    invoke-static {{p0, v1, v2}}, {HELPER}->commitSlide({HOST}Ljava/lang/Object;I)V
    :reader_slide_commit_end
    invoke-virtual {{v1}}, {PIC}->m()I
    const/4 v0, 0x1
    invoke-direct {{p0, v0}}, {owner}->b(Z)V
    :reader_slide_advance_done
    return-void
    :reader_slide_commit_error
    move-exception v0
    invoke-virtual {{v1}}, {PIC}->m()I
    throw v0
    .catchall {{:reader_slide_commit_start .. :reader_slide_commit_end}} :reader_slide_commit_error
    :reader_slide_advance_legacy''', 4)
    text = deletion_next(change(text, '.method private ' + dispatch + '()V', advance), name)
    def slide_menu(part):
        if ':reader_menu_slide_index' in part: return part
        seam = '    if-nez v2, :cond_3\n'
        return once(part, seam, seam + f'''\n    iget-object v2, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    invoke-static {{v2}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v2
    if-nez v2, :cond_3
    :reader_menu_slide_index
''', name + ' slideshow menu identity')
    return change(text, '.method public a(Landroid/view/MenuItem;)Z', slide_menu)

def deletion_next(text, name):
    owner = 'Lcom/alensw/ui/c/' + name + ';'
    album = name == 'as'
    folder, kind, field, count, remove, action = ('k','Lcom/alensw/bean/CommonFolder;','k','o','c','n') if album else ('m','Lcom/alensw/a/e;','c','j','h','q')
    # Settle removal before choosing the replacement page; keep the displayed
    # URI until readerCommit so commands cannot target an unseen new image.
    return change(text, '.method private ' + action + '()V', lambda part: head(part, ':reader_remove_legacy', f'''    iget-object v0, p0, {owner}->C:Lcom/alensw/ui/c/cx;
    invoke-static {{v0}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :reader_remove_legacy
    iget-object v0, p0, {owner}->{folder}:{kind}
    invoke-virtual {{v0}}, {kind}->{count}()I
    move-result v1
    const/4 v2, 0x1
    if-le v1, v2, :reader_remove_legacy
    iget v2, v0, {kind}->{field}:I
    if-ltz v2, :reader_remove_legacy
    if-ge v2, v1, :reader_remove_legacy
    invoke-virtual {{p0}}, {owner}->readerStopSlideshow()V
    invoke-static {{p0}}, {HELPER}->cancel({HOST})V
    invoke-virtual {{p0}}, {owner}->readerCancelLegacy()V
    invoke-virtual {{v0, v2}}, {kind}->{remove}(I)Z
    invoke-virtual {{v0}}, {kind}->{count}()I
    move-result v1
    if-lez v1, :reader_remove_close
    add-int/lit8 v1, v1, -0x1
    invoke-static {{v2, v1}}, Ljava/lang/Math;->min(II)I
    move-result v1
    iput v1, v0, {kind}->{field}:I
    invoke-virtual {{p0, v1}}, {owner}->readerImage(I)Z
    move-result v2
    if-eqz v2, :reader_remove_video
    invoke-static {{p0, v1}}, {HELPER}->reloadTarget({HOST}I)V
    return-void
    :reader_remove_video
    const/4 v0, 0x0
    invoke-virtual {{p0, v0, v1}}, {owner}->readerCommit(Ljava/lang/Object;I)V
    iget-object v0, p0, {owner}->b:Landroid/os/Handler;
    const/16 v1, 0xbb8
    invoke-virtual {{v0, v1}}, Landroid/os/Handler;->sendEmptyMessage(I)Z
    return-void
    :reader_remove_close
    invoke-virtual {{p0}}, {owner}->E()V
    return-void
    :reader_remove_legacy''', 3))


def patch_frame(text):
    text = implements(text, FRAME)
    for signature, source in (
        ('.method public readerUri()Landroid/net/Uri;', f'''.method public readerUri()Landroid/net/Uri;
    .locals 1
    iget-object v0, p0, {PIC}->i:Landroid/net/Uri;
    return-object v0
.end method'''),
        ('.method public readerReady()Z', f'''.method public readerReady()Z
    .locals 2
    iget v0, p0, {PIC}->d:I
    const/4 v1, 0x2
    if-ne v0, v1, :reader_frame_not_ready
    invoke-virtual {{p0}}, {PIC}->j()Z
    move-result v0
    return v0
    :reader_frame_not_ready
    const/4 v0, 0x0
    return v0
.end method'''),
        ('.method public readerRelease()V', f'''.method public readerRelease()V
    .locals 0
    invoke-virtual {{p0}}, {PIC}->m()I
    return-void
.end method''')):
        text = add_method(text, signature, source)
    return text

def patch_movie(text):
    text = add_method(text, '.method public readerReady()Z', f'''.method public readerReady()Z
    .locals 1
    iget v0, p0, {MOVIE}->j:I
    if-eqz v0, :reader_movie_not_ready
    iget v0, p0, {MOVIE}->n:I
    if-ltz v0, :reader_movie_not_ready
    const/4 v0, 0x1
    return v0
    :reader_movie_not_ready
    const/4 v0, 0x0
    return v0
.end method''')
    return add_method(text, '.method public readerPrime()Z', f'''.method public readerPrime()Z
    .locals 4
    monitor-enter p0
    :reader_prime_start
    iget v0, p0, {MOVIE}->j:I
    if-eqz v0, :reader_prime_failed
    iget-object v1, p0, {MOVIE}->q:[I
    if-eqz v1, :reader_prime_failed
    const/4 v2, 0x0
    aget v1, v1, v2
    invoke-direct {{p0, v2, v1, v0}}, {MOVIE}->a(III)I
    move-result v3
    if-lez v3, :reader_prime_failed
    invoke-direct {{p0, v2, v1, v0}}, {MOVIE}->b(III)Z
    move-result v3
    if-eqz v3, :reader_prime_failed
    iput v2, p0, {MOVIE}->n:I
    const/4 v0, 0x1
    :reader_prime_end
    monitor-exit p0
    return v0
    :reader_prime_failed
    const/4 v0, 0x0
    monitor-exit p0
    return v0
    :reader_prime_error
    move-exception v0
    monitor-exit p0
    throw v0
    .catchall {{:reader_prime_start .. :reader_prime_end}} :reader_prime_error
.end method''')

def patch_loader(text):
    def prime(part):
        if ':reader_prime_complete' in part: return part
        if '->isCandidate(' not in part or 'Lcom/alensw/b/h/n;->a(' not in part:
            raise ValueError('Reader loader requires existing animation routing')
        seam = '    invoke-direct {p0, v0}, Lcom/alensw/a/at;->a(' + PIC + ')V\n'
        if part.count(seam) != 2: raise ValueError('Unexpected complete-image posts')
        code = f'''    iget v1, p0, Lcom/alensw/a/at;->b:I
    const/4 v2, 0x5
    if-ne v1, v2, :reader_prime_complete
    instance-of v1, v0, {MOVIE}
    if-eqz v1, :reader_prime_complete
    check-cast v0, {MOVIE}
    invoke-virtual {{v0}}, {MOVIE}->readerPrime()Z
    move-result v1
    if-nez v1, :reader_prime_complete
    invoke-virtual {{v0}}, {PIC}->m()I
    const/4 v0, 0x0
    goto :goto_1
    :reader_prime_complete
'''
        return part.replace(seam, code + '\n' + seam, 1)
    return change(text, '.method private c(Lcom/alensw/b/c/f;Landroid/net/Uri;)' + PIC, prime)

def patch_picture_view(text):
    def loading(part):
        if ':reader_loading_legacy' in part: return part
        code = f'''    :cond_4
    invoke-virtual {{p0}}, Landroid/view/View;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-static {{v0}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :reader_loading_legacy
    return-void
    :reader_loading_legacy
    iget-object v0, p0, {VIEW}->O:Landroid/graphics/Paint;'''
        part, count = re.subn(r'    :cond_4\n\s*    iget-object v0, p0, ' + re.escape(VIEW) + r'->O:Landroid/graphics/Paint;', lambda _: code, part, count=1)
        if count != 1: raise ValueError('Unexpected loading-text branch')
        return part
    text = change(text, '.method protected a(Landroid/graphics/Canvas;ZZ)V', loading)
    def turn(part):
        if ':reader_gesture_legacy' in part: return part
        seam = '    :cond_4\n'
        if 'Lcom/alensw/ui/view/l;' not in part or part.count(seam) != 1:
            raise ValueError('Unexpected gesture page-turn dispatcher')
        return part.replace(seam, f'''    :cond_4
    invoke-virtual {{p0}}, Landroid/view/View;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-static {{v0}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :reader_gesture_legacy
    iget-object v0, p0, {VIEW}->K:Lcom/alensw/ui/view/q;
    invoke-interface {{v0, v4}}, Lcom/alensw/ui/view/q;->b(I)V
    const/4 v0, 0x1
    return v0
    :reader_gesture_legacy
''', 1)
    text = change(text, '.method public a(F)Z', turn)
    text = change(text, '.method public c(FF)V', lambda p: head(p, ':reader_drag_legacy', f'''    invoke-virtual {{p0}}, Landroid/view/View;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-static {{v0}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :reader_drag_legacy
    iget-object v0, p0, {VIEW}->P:Landroid/graphics/RectF;
    iget-object v1, p0, {VIEW}->Q:Landroid/graphics/RectF;
    invoke-virtual {{v0}}, Landroid/graphics/RectF;->width()F
    move-result v2
    invoke-virtual {{v1}}, Landroid/graphics/RectF;->width()F
    move-result v3
    cmpg-float v2, v2, v3
    if-gtz v2, :reader_drag_clamp
    const/4 p1, 0x0
    goto :reader_drag_legacy
    :reader_drag_clamp
    iget v2, v1, Landroid/graphics/RectF;->right:F
    iget v3, v0, Landroid/graphics/RectF;->right:F
    sub-float/2addr v2, v3
    invoke-static {{p1, v2}}, Ljava/lang/Math;->max(FF)F
    move-result p1
    iget v2, v1, Landroid/graphics/RectF;->left:F
    iget v3, v0, Landroid/graphics/RectF;->left:F
    sub-float/2addr v2, v3
    invoke-static {{p1, v2}}, Ljava/lang/Math;->min(FF)F
    move-result p1
    :reader_drag_legacy''', 4))
    def neighbors(part):
        if ':reader_neighbor_legacy' in part: return part
        seam = '    invoke-virtual {p0, p1, v6, v5}, ' + VIEW + '->a(Landroid/graphics/Canvas;ZZ)V\n'
        return once(part, seam, seam + f'''
    invoke-virtual {{p0}}, Landroid/view/View;->getContext()Landroid/content/Context;
    move-result-object v2
    invoke-static {{v2}}, {HELPER}->enabled(Landroid/content/Context;)Z
    move-result v2
    if-eqz v2, :reader_neighbor_legacy
    invoke-virtual {{p1}}, Landroid/graphics/Canvas;->restore()V
    return-void
    :reader_neighbor_legacy
''', 'neighbor painting')
    return change(text, '.method public onDraw(Landroid/graphics/Canvas;)V', neighbors)

def patch_preferences(root, staged):
    xmls = sorted((root / 'res').glob('xml*/main_pref.xml'))
    if not xmls: raise ValueError('Missing preference screens')
    for path in xmls:
        text = path.read_text()
        if 'android:key="reader_mode"' not in text:
            item = '\n            <SwitchPreference android:icon="@drawable/ic_invisible" android:title="@string/reader_mode_title" android:key="reader_mode" android:summary="@string/reader_mode_summary" android:defaultValue="false" />'
            text, count = re.subn(r'(<PreferenceCategory\b[^>]*android:title="@string/image_category"[^>]*>)', lambda m: m[1] + item, text)
            if count != 1: raise ValueError('Expected one image preference category: ' + str(path))
        staged[path] = text
    strings = sorted((root / 'res').glob('values*/strings.xml'))
    if not strings: raise ValueError('Missing localized string tables')
    for path in strings:
        text = path.read_text()
        chinese = path.parent.name.startswith('values-zh')
        simplified = path.parent.name in ('values-zh','values-zh-rCN')
        title = ('阅读器模式' if simplified else '閱讀器模式') if chinese else 'Reader mode'
        summary = ('这会减少闪烁，但是延迟肯定会提高' if simplified else '這會減少閃爍，但是延遲肯定會提高') if chinese else 'This reduces flicker, but definitely increases latency.'
        for name, value in (('reader_mode_title', title), ('reader_mode_summary', summary)):
            pattern = r'(<string name="' + name + r'">)[^<]*(</string>)'
            if re.search(pattern, text): text = re.sub(pattern, lambda m: m[1] + value + m[2], text)
            else: text = once(text, '</resources>', f'    <string name="{name}">{value}</string>\n</resources>', 'string resources')
        staged[path] = text

def patch(root):
    root = Path(root)
    app_paths = list((root / 'smali').glob('**/QuickApp.smali'))
    if len(app_paths) != 1: raise ValueError('Expected one QuickApp class')
    app = re.search(r'^\.class .* (L[^;]+;)$', app_paths[0].read_text(), re.M).group(1)
    staged = {}
    for name, listener in (('as', 'be'), ('dp', 'eb')):
        path = root / ('smali/com/alensw/ui/c/' + name + '.smali')
        staged[path] = patch_controller(path.read_text(), name, app)
        path = root / ('smali/com/alensw/ui/c/' + listener + '.smali')
        owner = 'Lcom/alensw/ui/c/' + name + ';'
        listener_owner = 'Lcom/alensw/ui/c/' + listener + ';'
        def turn(part, owner=owner, listener_owner=listener_owner):
            return head(part, ':reader_turn_legacy', f'''    iget-object v0, p0, {listener_owner}->a:{owner}
    invoke-static {{v0, p1}}, {HELPER}->turn({HOST}I)Z
    move-result v0
    if-eqz v0, :reader_turn_legacy
    return-void
    :reader_turn_legacy''')
        staged[path] = change(path.read_text(), '.method public b(I)V', turn)
    for relative, transform in (
            ('com/alensw/b/h/j.smali', patch_frame),
            ('com/alensw/b/h/d.smali', patch_movie),
            ('com/alensw/a/at.smali', patch_loader),
            ('com/alensw/ui/view/j.smali', patch_picture_view)):
        path = root / 'smali' / relative
        staged[path] = transform(path.read_text())
    patch_preferences(root, staged)
    for path, text in staged.items(): path.write_text(text)
    return {'paths': [str(path.relative_to(root)) for path in staged],
            'request': 5, 'preference': 'reader_mode', 'default': False,
            'ready': 'type 2 bitmap or primed animated frame',
            'legacy_when_disabled': True}
