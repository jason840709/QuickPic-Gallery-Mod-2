# SPDX-License-Identifier: MIT
"""Opt-in display-only E-ink rendering hooks for the pinned legacy classes.

Apply after patch_reader. EinkRender helpers and preference resources are
supplied by the maintenance build; no decompiled legacy sources are bundled.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET


HELPER = 'Lcom/alensw/jni/EinkRender;'
VIEW = 'Lcom/alensw/ui/view/j;'
PICTURE = 'Lcom/alensw/b/h/j;'
MOVIE = 'Lcom/alensw/b/h/d;'
TILES = 'Lcom/alensw/b/h/n;'
LOADER = 'Lcom/alensw/a/at;'
MATRIX_DRAW = ('Landroid/graphics/Canvas;->drawBitmap('
               'Landroid/graphics/Bitmap;Landroid/graphics/Matrix;Landroid/graphics/Paint;)V')
HELPER_DRAW = (HELPER + '->draw(Landroid/graphics/Canvas;Landroid/graphics/Bitmap;'
               'Landroid/graphics/Matrix;Landroid/graphics/Paint;)V')
BITMAP_SIGNATURE = ('.method private a(Landroid/graphics/Canvas;' + PICTURE +
                    'Landroid/graphics/Matrix;Landroid/graphics/RectF;Z)V')
PICTURE_SIGNATURE = ('.method public a(Landroid/graphics/Canvas;Landroid/graphics/Matrix;' +
                     PICTURE + 'Landroid/graphics/Matrix;Landroid/graphics/Paint;Z)V')


def method(text, signature):
    matches = list(re.finditer('^' + re.escape(signature) + '$', text, re.M))
    if len(matches) != 1:
        raise ValueError('Expected one E-ink method: ' + signature)
    start = matches[0].start()
    end = text.index('.end method', start) + len('.end method')
    return start, end, text[start:end]


def once(text, old, new, description):
    if text.count(old) != 1:
        raise ValueError('Unexpected E-ink seam: ' + description)
    return text.replace(old, new, 1)


def change(text, signature, transform):
    start, end, part = method(text, signature)
    return text[:start] + transform(part) + text[end:]


def head(part, code):
    match = re.search(r'^    \.locals (\d+)\n', part, re.M)
    if match is None or int(match[1]) < 1:
        raise ValueError('E-ink initialization requires an existing local register')
    pos = match.end()
    while re.match(r'\s*\.annotation\b', part[pos:]):
        pos = part.index('.end annotation', pos) + len('.end annotation')
    return part[:pos] + '\n' + code + '\n' + part[pos:]


def wrap(text, signature, renamed, wrapper):
    if re.search(r'^\.method .*\b' + re.escape(renamed) + r'\(', text, re.M):
        raise ValueError('E-ink original method already exists: ' + renamed)
    descriptor = signature[signature.index('('):]
    replacement = '.method private ' + renamed + descriptor
    text = change(text, signature, lambda part: once(part, signature, replacement, renamed))
    return text.rstrip() + '\n\n' + wrapper.strip() + '\n'


def suspended_wrapper(signature, renamed, returns_object=False, movie_only=False):
    descriptor = signature[signature.index('('):]
    call = 'invoke-direct/range {p0 .. p5}' if movie_only else 'invoke-direct {p0, p1}'
    guard = (f'    instance-of v0, p2, {MOVIE}\n'
             '    if-eqz v0, :eink_static_bitmap\n') if movie_only else ''
    result = '    move-result-object v1\n' if returns_object else ''
    done = '    return-object v1' if returns_object else '    return-void'
    static = (f'\n    :eink_static_bitmap\n    {call}, {VIEW}->{renamed}{descriptor}\n'
              '    return-void\n') if movie_only else ''
    return f'''{signature}
    .locals 2
{guard}    invoke-static {{}}, {HELPER}->suspend()I
    move-result v0
    :eink_suspend_start
    {call}, {VIEW}->{renamed}{descriptor}
{result}    :eink_suspend_end
    invoke-static {{v0}}, {HELPER}->leave(I)V
{done}
    :eink_suspend_error
    move-exception v1
    invoke-static {{v0}}, {HELPER}->leave(I)V
    throw v1
    .catchall {{:eink_suspend_start .. :eink_suspend_end}} :eink_suspend_error
{static}.end method'''


def patch_loader(text, app):
    init = (f'    invoke-static {{}}, {app}->a(){app}\n'
            '    move-result-object v0\n'
            f'    invoke-static {{v0}}, {HELPER}->init(Landroid/content/Context;)V')

    def metadata(part):
        assignment = f'    iput-boolean v0, p0, {LOADER}->g:Z'
        part = once(part, assignment,
                    f'    invoke-static {{v0}}, {HELPER}->useTiles(Z)Z\n'
                    '    move-result v0\n' + assignment, 'region decoder selection')
        return head(part, init)

    text = change(text, '.method private h()Lcom/alensw/b/c/f;', metadata)

    def complete(part):
        if part.count('    const/4 v4, 0x0') != 1:
            raise ValueError('Unexpected E-ink complete-loader zero register')
        old = ('    invoke-static {p1, v4, v3}, Lcom/alensw/b/h/b;->a('
               'Lcom/alensw/b/c/f;ILcom/alensw/b/h/a;)Landroid/graphics/Bitmap;\n\n'
               '    move-result-object v0')
        new = f'''    invoke-static {{}}, {HELPER}->decodeLimit()I
    move-result v0
    if-eqz v0, :eink_original_decode_limit
    invoke-virtual {{v3, v4}}, Lcom/alensw/b/h/a;->a(Z)V
    invoke-static {{p2, v3}}, Lcom/alensw/jni/EinkDecode;->decode(Landroid/net/Uri;Landroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;
    move-result-object v0
    if-nez v0, :eink_complete_decoded
    invoke-static {{}}, {HELPER}->decodeLimit()I
    move-result v0
    invoke-static {{p1, v0, v3}}, Lcom/alensw/b/h/b;->a(Lcom/alensw/b/c/f;ILcom/alensw/b/h/a;)Landroid/graphics/Bitmap;
    move-result-object v0
    goto :eink_complete_decoded
    :eink_original_decode_limit
{old}
    :eink_complete_decoded'''
        return head(once(part, old, new, 'first complete-image decode'), init)

    return change(text, '.method private c(Lcom/alensw/b/c/f;Landroid/net/Uri;)' + PICTURE, complete)


def patch_view(text):
    def bitmap(part):
        for registers in ('v0, v6, v7, v1', 'v0, v6, p3, v1', 'p1, v6, p3, v0'):
            part = once(part, f'    invoke-virtual {{{registers}}}, {MATRIX_DRAW}',
                        f'    invoke-static {{{registers}}}, {HELPER_DRAW}',
                        'complete source bitmap ' + registers)
        return part

    text = change(text, BITMAP_SIGNATURE, bitmap)
    name = 'quickpicEinkOriginalBitmapDraw'
    text = wrap(text, BITMAP_SIGNATURE, name,
                suspended_wrapper(BITMAP_SIGNATURE, name, movie_only=True))
    for signature, name, result in (
            ('.method public a(Landroid/graphics/Bitmap$Config;)' + PICTURE,
             'quickpicEinkOriginalCapture', True),
            ('.method public c(Z)V', 'quickpicEinkOriginalFreeze', False)):
        text = wrap(text, signature, name, suspended_wrapper(signature, name, result))

    if '.method public dispatchTouchEvent(Landroid/view/MotionEvent;)Z' in text:
        raise ValueError('Unexpected existing E-ink touch dispatcher')
    text += f"""
.method public dispatchTouchEvent(Landroid/view/MotionEvent;)Z
    .locals 1
    invoke-super {{p0, p1}}, Landroid/view/View;->dispatchTouchEvent(Landroid/view/MotionEvent;)Z
    move-result v0
    invoke-static {{p0, p1, v0}}, {HELPER}->touchResult(Landroid/view/View;Landroid/view/MotionEvent;Z)V
    return v0
.end method
"""
    signature = '.method public onDraw(Landroid/graphics/Canvas;)V'
    wrapper = f'''{signature}
    .locals 2
    invoke-static {{p0}}, {HELPER}->enter(Landroid/view/View;)I
    move-result v0
    :eink_draw_start
    iget-object v1, p0, {VIEW}->b:{PICTURE}
    instance-of v1, v1, {TILES}
    if-eqz v1, :eink_draw_picture
    invoke-static {{}}, {HELPER}->suspend()I
    :eink_draw_picture
    invoke-direct {{p0, p1}}, {VIEW}->quickpicEinkOriginalDraw(Landroid/graphics/Canvas;)V
    :eink_draw_end
    invoke-static {{v0}}, {HELPER}->leave(I)V
    return-void
    :eink_draw_error
    move-exception v1
    invoke-static {{v0}}, {HELPER}->leave(I)V
    throw v1
    .catchall {{:eink_draw_start .. :eink_draw_end}} :eink_draw_error
.end method'''
    text = wrap(text, signature, 'quickpicEinkOriginalDraw', wrapper)
    return change(text, '.method protected onDetachedFromWindow()V',
                  lambda part: head(part, f'    invoke-static {{}}, {HELPER}->clear()V'))


def patch_picture(text):
    def draw(part):
        for registers in ('p1, v0, p2, p5', 'p1, v0, p4, p5'):
            part = once(part, f'    invoke-virtual {{{registers}}}, {MATRIX_DRAW}',
                        f'    invoke-static {{{registers}}}, {HELPER_DRAW}',
                        'fallback source bitmap ' + registers)
        return part
    return change(text, PICTURE_SIGNATURE, draw)


def patch(root):
    root = Path(root)
    app_paths = list((root / 'smali').glob('**/QuickApp.smali'))
    if len(app_paths) != 1:
        raise ValueError('Expected one QuickApp class for E-ink initialization')
    app_text = app_paths[0].read_text()
    match = re.search(r'^\.class .* (L[^;]+;)$', app_text, re.M)
    if match is None:
        raise ValueError('Missing QuickApp descriptor')
    app = match[1]
    method(app_text, '.method public static a()' + app)
    transforms = (
        ('com/alensw/a/at.smali', lambda text: patch_loader(text, app)),
        ('com/alensw/ui/view/j.smali', patch_view),
        ('com/alensw/b/h/j.smali', patch_picture),
    )
    originals = {root / 'smali' / relative: (root / 'smali' / relative).read_text()
                 for relative, _ in transforms}
    if any(HELPER in text for text in originals.values()):
        raise ValueError('E-ink render hooks already present; use a fresh decoded input')
    staged = {root / 'smali' / relative: transform(originals[root / 'smali' / relative])
              for relative, transform in transforms}
    for path, text in staged.items():
        path.write_text(text)
    patch_preferences(root)
    return {'paths': [str(path.relative_to(root)) for path in staged],
            'preference': 'eink_render_mode', 'default': 0, 'source_bitmap_calls': 5,
            'tile_calls': 0, 'animation_calls': 0, 'legacy_when_disabled': True,
            'active_load': 'bounded complete bitmap; reopen picture after mode changes',
            'captures': 'suspended', 'frozen_buffer': 'original during transition'}


def patch_preferences(root):
    a='{http://schemas.android.com/apk/res/android}'
    ET.register_namespace('android',a[1:-1])
    for path in sorted((Path(root)/'res').glob('xml*/main_pref.xml')):
        tree=ET.parse(path);top=tree.getroot();parents={c:p for p in top.iter()for c in p}
        node=next((n for n in top.iter()if n.get(a+'key')=='reader_mode'),None)
        if node is None:raise ValueError('Missing image settings entry')
        group=parents[node]
        pref=ET.Element('com.alensw.jni.EinkPreference',{a+'key':'eink_render_mode',a+'icon':'@drawable/ic_invisible'})
        group.insert(list(group).index(node)+1,pref)
        tree.write(path,encoding='utf-8',xml_declaration=True)
