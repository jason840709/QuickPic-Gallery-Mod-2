# SPDX-License-Identifier: MIT
"""Use detector-validated release for page zones, preserving native gesture cleanup."""
from pathlib import Path
import hashlib
import re

PICTURE = 'Lcom/alensw/ui/view/PictureView;'
HOST = 'Lcom/alensw/jni/TapPageNavigation$Host;'
HELPER = 'Lcom/alensw/jni/TapPageNavigation;'
TOUCH_SIGNATURE = '.method public onTouchEvent(Landroid/view/MotionEvent;)Z'
CALLBACK_SIGNATURE = '.method public b(FF)V'
# Fingerprint of the shipped legacy method, not a bundled copy of its source.
LEGACY_TOUCH_SHA256 = '3e6d49105dd9d247dd58e5286daf83b5afb5b0e7402b958d13f2f4ff357da354'

TOUCH_DELEGATE = '''.method public onTouchEvent(Landroid/view/MotionEvent;)Z
    .locals 1

    invoke-static {p0, p1}, Lcom/alensw/jni/TapPageNavigation;->track(Landroid/view/View;Landroid/view/MotionEvent;)V
    iget-object v0, p0, Lcom/alensw/ui/view/PictureView;->a:Lcom/alensw/ui/view/w;
    invoke-virtual {v0, p1}, Lcom/alensw/ui/view/w;->a(Landroid/view/MotionEvent;)Z
    move-result v0
    return v0
.end method'''

PREVIOUS_DELEGATE = TOUCH_DELEGATE.replace('    invoke-static {p0, p1}, Lcom/alensw/jni/TapPageNavigation;->track(Landroid/view/View;Landroid/view/MotionEvent;)V\n', '')

CALLBACK_HOOK = '''    # quickpic-confirmed-tap-pages
    iget-object v0, p0, Lcom/alensw/ui/view/bb;->a:Lcom/alensw/ui/view/PictureView;
    invoke-static {v0, v0, p1, p2}, Lcom/alensw/jni/TapPageNavigation;->onConfirmedTap(Landroid/view/View;Lcom/alensw/jni/TapPageNavigation$Host;FF)Z
    move-result v0
    if-eqz v0, :quickpic_tap_original
    return-void
    :quickpic_tap_original

'''

HOST_METHODS = '''
.method public quickpicTapReady()Z
    .locals 1
    iget-object v0, p0, Lcom/alensw/ui/view/PictureView;->K:Lcom/alensw/ui/view/q;
    if-eqz v0, :quickpic_tap_not_ready
    const/4 v0, 0x1
    return v0
    :quickpic_tap_not_ready
    const/4 v0, 0x0
    return v0
.end method

.method public quickpicTapControlsHit(FF)Z
    .locals 4
    invoke-virtual {p0}, Lcom/alensw/ui/view/PictureView;->getContext()Landroid/content/Context;
    move-result-object v0
    instance-of v1, v0, Lcom/alensw/ui/c/cx;
    if-eqz v1, :quickpic_tap_unknown_context
    check-cast v0, Lcom/alensw/ui/c/cx;
    const/4 v1, 0x2
    new-array v1, v1, [I
    invoke-virtual {p0, v1}, Lcom/alensw/ui/view/PictureView;->getLocationInWindow([I)V
    const/4 v2, 0x0
    aget v2, v1, v2
    int-to-float v2, v2
    add-float v2, v2, p1
    const/4 v3, 0x1
    aget v1, v1, v3
    int-to-float v1, v1
    add-float v1, v1, p2
    invoke-virtual {v0, v2, v1}, Lcom/alensw/ui/c/cx;->a(FF)Z
    move-result v0
    return v0
    :quickpic_tap_unknown_context
    const/4 v0, 0x1
    return v0
.end method

.method public quickpicTapTurn(I)V
    .locals 1
    iget-object v0, p0, Lcom/alensw/ui/view/PictureView;->K:Lcom/alensw/ui/view/q;
    if-eqz v0, :quickpic_tap_turn_done
    invoke-interface {v0, p1}, Lcom/alensw/ui/view/q;->b(I)V
    :quickpic_tap_turn_done
    return-void
.end method

.method public quickpicTapToggleControls()Z
    .locals 2
    invoke-virtual {p0}, Lcom/alensw/ui/view/PictureView;->getContext()Landroid/content/Context;
    move-result-object v0
    instance-of v1, v0, Lcom/alensw/ui/c/cx;
    if-eqz v1, :quickpic_tap_toggle_unavailable
    check-cast v0, Lcom/alensw/ui/c/cx;
    invoke-virtual {v0}, Lcom/alensw/ui/c/cx;->v()Z
    const/4 v0, 0x1
    return v0
    :quickpic_tap_toggle_unavailable
    const/4 v0, 0x0
    return v0
.end method
'''


def normalized(text):
    return '\n'.join(line.split('#', 1)[0].strip() for line in text.splitlines()
                     if line.split('#', 1)[0].strip())


def change_method(text, signature, transform):
    matches = list(re.finditer(r'(?ms)^' + re.escape(signature) + r'\n.*?^\.end method', text))
    if len(matches) != 1:
        raise ValueError('Expected one tap method: ' + signature)
    match = matches[0]
    return text[:match.start()] + transform(match.group()) + text[match.end():]


def restore_touch(body):
    if normalized(body) in (normalized(TOUCH_DELEGATE), normalized(PREVIOUS_DELEGATE)):
        return TOUCH_DELEGATE
    digest = hashlib.sha256(normalized(body).encode()).hexdigest()
    if digest != LEGACY_TOUCH_SHA256:
        raise ValueError('Unrecognized PictureView touch method; refusing to erase other gesture changes')
    return TOUCH_DELEGATE


def hook_callback(body):
    if '# quickpic-confirmed-tap-pages' in body:
        if body.count(CALLBACK_HOOK) != 1:
            raise ValueError('Damaged confirmed-tap hook')
        return body
    if normalized(body).count('invoke-interface {v0, p1, p2}, Lcom/alensw/ui/view/q;->a(FF)V') != 1:
        raise ValueError('Confirmed-tap callback no longer calls the expected listener')
    anchor = '    .locals 1\n'
    if body.count(anchor) != 1:
        raise ValueError('Unexpected confirmed-tap local registers')
    return body.replace(anchor, '    .locals 1\n\n' + CALLBACK_HOOK, 1)



RELEASE_HOOK = """    # quickpic-validated-release-pages
    iget v1, p0, Lcom/alensw/ui/view/w;->a:I
    if-ne v1, v6, :quickpic_release_original
    iget-boolean v1, p0, Lcom/alensw/ui/view/w;->b:Z
    if-nez v1, :quickpic_release_original
    iget-object v1, p0, Lcom/alensw/ui/view/w;->z:Lcom/alensw/ui/view/y;
    instance-of v2, v1, Lcom/alensw/ui/view/bb;
    if-eqz v2, :quickpic_release_original
    check-cast v1, Lcom/alensw/ui/view/bb;
    iget-object v1, v1, Lcom/alensw/ui/view/bb;->a:Lcom/alensw/ui/view/PictureView;
    invoke-static {v1, v1, p1}, Lcom/alensw/jni/TapPageNavigation;->onTapRelease(Landroid/view/View;Lcom/alensw/jni/TapPageNavigation$Host;Landroid/view/MotionEvent;)Z
    move-result v1
    if-eqz v1, :quickpic_release_original
    iget-object v1, p0, Lcom/alensw/ui/view/w;->y:Landroid/os/Handler;
    iget-object v2, p0, Lcom/alensw/ui/view/w;->F:Ljava/lang/Runnable;
    invoke-virtual {v1, v2}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V
    iput-boolean v5, p0, Lcom/alensw/ui/view/w;->E:Z
    iput v5, p0, Lcom/alensw/ui/view/w;->a:I
    :quickpic_release_original

"""
RELEASE_ANCHOR = ("    iput-boolean v5, p0, Lcom/alensw/ui/view/w;->c:Z\n\n"
                  "    iget v1, p0, Lcom/alensw/ui/view/w;->a:I")


def hook_release(body):
    if '# quickpic-validated-release-pages' in body:
        if body.count(RELEASE_HOOK) != 1:
            raise ValueError('Damaged validated-release hook')
        return body
    if body.count(RELEASE_ANCHOR) != 1 or '    const/4 v6, 0x1' not in body or '    const/4 v5, 0x0' not in body:
        raise ValueError('Unexpected detector UP state branch')
    return body.replace(RELEASE_ANCHOR, RELEASE_HOOK + RELEASE_ANCHOR, 1)


def patch(decoded_root):
    root = Path(decoded_root)
    picture = root / 'smali/com/alensw/ui/view/PictureView.smali'
    callback = root / 'smali/com/alensw/ui/view/bb.smali'
    p = picture.read_text()
    if p.count('.class public ' + PICTURE) != 1 or '.super Lcom/alensw/ui/view/j;' not in p:
        raise ValueError('Unexpected PictureView identity')
    p = change_method(p, TOUCH_SIGNATURE, restore_touch)
    interface = '.implements ' + HOST
    if interface not in p:
        if 'quickpicTapReady' in p:
            raise ValueError('Tap Host methods without expected interface')
        p = p.replace('.super Lcom/alensw/ui/view/j;\n', '.super Lcom/alensw/ui/view/j;\n' + interface + '\n', 1)
        p = p.rstrip() + '\n\n' + HOST_METHODS.strip() + '\n'
    else:
        if p.count(interface) != 1 or normalized(HOST_METHODS) not in normalized(p):
            raise ValueError('Damaged tap Host bridge')
    c = callback.read_text()
    if '.field final synthetic a:' + PICTURE not in c:
        raise ValueError('Expected a PictureView-only callback')
    c = change_method(c, CALLBACK_SIGNATURE, hook_callback)
    detector = root / 'smali/com/alensw/ui/view/w.smali'
    w = change_method(detector.read_text(), '.method public a(Landroid/view/MotionEvent;)Z', hook_release)
    # All three files validate before writing; unrelated gesture callbacks remain untouched.
    picture.write_text(p)
    callback.write_text(c)
    detector.write_text(w)
    return ['smali/com/alensw/ui/view/PictureView.smali', 'smali/com/alensw/ui/view/bb.smali', 'smali/com/alensw/ui/view/w.smali']
