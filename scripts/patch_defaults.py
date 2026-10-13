# SPDX-License-Identifier: MIT
"""Set E-Ink startup defaults without changing stored transition choices.

The hidden-items default respects the existing protected-hidden password gate.
No legacy source is bundled: this patch validates and edits a supplied decode.
"""
from pathlib import Path
import re


def method(text, signature):
    start = text.index(signature)
    end = text.index('.end method', start) + len('.end method')
    return start, end, text[start:end]


def replace_method(text, signature, change):
    start, end, part = method(text, signature)
    return text[:start] + change(part) + text[end:]


def hidden_method(app):
    return f'''\n.method public static applyHiddenDefault()V
    .locals 5

    sget-object v0, {app}->q:Lcom/alensw/a/s;
    if-eqz v0, :hidden_default_done
    sget-object v1, {app}->B:{app}
    invoke-static {{v1}}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;
    move-result-object v1
    const-string v2, "protect_hidden"
    const/4 v3, 0x1
    invoke-interface {{v1, v2, v3}}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
    move-result v2
    if-eqz v2, :hidden_default_allowed
    const-string v2, "password"
    const-string v4, ""
    invoke-interface {{v1, v2, v4}}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v2
    invoke-virtual {{v2}}, Ljava/lang/String;->length()I
    move-result v4
    if-eqz v4, :hidden_default_allowed
    sget-object v4, {app}->y:Ljava/lang/String;
    invoke-virtual {{v2, v4}}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-nez v4, :hidden_default_allowed
    const/4 v3, 0x0
    :hidden_default_allowed
    iget-boolean v1, v0, Lcom/alensw/a/s;->b:Z
    if-eq v1, v3, :hidden_default_done
    iput-boolean v3, v0, Lcom/alensw/a/s;->b:Z
    sget-object v0, {app}->o:Lcom/alensw/a/x;
    const/4 v1, 0x0
    invoke-virtual {{v0, v1}}, Lcom/alensw/a/x;->a(Z)Z
    :hidden_default_done
    return-void
.end method
'''


def patch(root):
    root = Path(root)
    app_paths = list((root / 'smali').glob('**/QuickApp.smali'))
    if len(app_paths) != 1:
        raise ValueError('Expected one QuickApp class')
    app_path = app_paths[0]
    app_text = app_path.read_text()
    app = re.search(r'^\.class .* (L[^;]+;)$', app_text, re.M).group(1)
    call = '    invoke-static {}, ' + app + '->applyHiddenDefault()V\n'
    changed = []
    if '.method public static applyHiddenDefault()V' not in app_text:
        for member in ('q:Lcom/alensw/a/s;', 'o:Lcom/alensw/a/x;', 'y:Ljava/lang/String;', 'B:' + app):
            if member not in app_text:
                raise ValueError('Unexpected QuickApp state: ' + member)
        app_text += hidden_method(app)
    else:
        # Replace our own helper so maintained bases also receive signature fixes.
        app_text = replace_method(app_text, '.method public static applyHiddenDefault()V', lambda _: hidden_method(app).strip())
    def init_hook(part):
        if '->applyHiddenDefault()V' in part:
            return part
        seam = re.search(r'    sput-object (v\d+), ' + re.escape(app) + r'->q:Lcom/alensw/a/s;\n', part)
        if not seam:
            raise ValueError('Missing folder-state initialization')
        return part[:seam.end()] + '\n' + call + part[seam.end():]
    app_text = replace_method(app_text, '.method private g()Z', init_hook)
    def foreground_hook(part):
        if '->applyHiddenDefault()V' in part:
            return part
        if 'Lcom/alensw/a/s;->a()V' not in part:
            raise ValueError('Unexpected foreground lifecycle')
        return re.sub(r'(    \.locals \d+\n)', lambda m:m.group(1) + '\n' + call, part, count=1)
    app_text = replace_method(app_text, '.method protected b()V', foreground_hook)
    app_path.write_text(app_text)
    changed.append(str(app_path.relative_to(root)))
    # This callback reaches the hook only after the original password comparison
    # succeeded and the original "password" credential has been remembered.
    callback = root / 'smali/com/alensw/ui/a/ac.smali'
    text = callback.read_text()
    def authenticated_hook(part):
        if '->applyHiddenDefault()V' in part:
            return part
        seam = '    sput-object p2, ' + app + '->y:Ljava/lang/String;\n'
        if part.count(seam) != 1 or '    if-eqz v0, :cond_2' not in part:
            raise ValueError('Unexpected successful password callback')
        return part.replace(seam, seam + '\n' + call, 1)
    text = replace_method(text, '.method public a(Lcom/alensw/ui/view/PasswordView;Ljava/lang/String;)Z', authenticated_hook)
    callback.write_text(text)
    changed.append(str(callback.relative_to(root)))
    for controller, field, default_reg in (('as', 'P', 'v4'), ('dp', 'Q', 'v3')):
        path = root / ('smali/com/alensw/ui/c/' + controller + '.smali')
        text = path.read_text()
        pattern = (r'    const-string v0, "slideshow_animation"\s+'
                   r'    const-string ' + default_reg + r', "([^\"]*)"\s+'
                   r'    invoke-interface \{([^}]+)\}, Landroid/content/SharedPreferences;->getString\(Ljava/lang/String;Ljava/lang/String;\)Ljava/lang/String;\s+'
                   r'    move-result-object v0\s+'
                   r'(?:    const/4 ' + default_reg + r', 0x6\s+)?'
                   r'    invoke-static \{v0, (v\d+)\}, Lcom/alensw/b/l/b;->a\(Ljava/lang/String;I\)I\s+'
                   r'    move-result v0\s+'
                   r'    iput v0, p0, Lcom/alensw/ui/c/' + controller + r';->' + field + r':I')
        def default_read(m):
            if m.group(1) not in ('', '6'):
                raise ValueError('Unexpected stored-choice fallback')
            return ('    const-string v0, "slideshow_animation"\n\n'
                    '    const-string ' + default_reg + ', "6"\n\n'
                    '    invoke-interface {' + m.group(2) + '}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;\n\n'
                    '    move-result-object v0\n\n'
                    '    const/4 ' + default_reg + ', 0x6\n\n'
                    '    invoke-static {v0, ' + default_reg + '}, Lcom/alensw/b/l/b;->a(Ljava/lang/String;I)I\n\n'
                    '    move-result v0\n\n'
                    '    iput v0, p0, Lcom/alensw/ui/c/' + controller + ';->' + field + ':I')
        text, count = re.subn(pattern, default_read, text)
        if count != 1:
            raise ValueError('Expected one transition preference read: ' + controller)
        if controller == 'as':
            def none_transition(part):
                if ':defaults_transition' in part:
                    return part
                seam = '    iget v0, p0, Lcom/alensw/ui/c/as;->P:I\n'
                if part.count(seam) != 1 or '    .locals 4' not in part:
                    raise ValueError('Unexpected slideshow dispatcher')
                return part.replace(seam, seam + '''\n    const/4 v2, 0x6
    if-ne v0, v2, :defaults_transition
    iget-object v0, p0, Lcom/alensw/ui/c/as;->ab:Landroid/view/animation/Animation$AnimationListener;
    const/4 v2, 0x0
    invoke-interface {v0, v2}, Landroid/view/animation/Animation$AnimationListener;->onAnimationEnd(Landroid/view/animation/Animation;)V
    return-void
    :defaults_transition
''', 1)
            text = replace_method(text, '.method private w()V', none_transition)
        path.write_text(text)
        changed.append(str(path.relative_to(root)))
    xmls = sorted((root / 'res').glob('xml*/main_pref.xml'))
    if not xmls:
        raise ValueError('Missing preference XML')
    for path in xmls:
        text = path.read_text()
        text, count = re.subn(r'(<[^>]*android:key="slideshow_animation"[^>]*android:defaultValue=")[^\"]*(")', r'\g<1>6\2', text)
        # Attributes may use defaultValue before key in externally decoded bases.
        if count == 0:
            text, count = re.subn(r'(<[^>]*android:defaultValue=")[^\"]*("[^>]*android:key="slideshow_animation")', r'\g<1>6\2', text)
        if count != 1:
            raise ValueError('Expected one transition default: ' + str(path.relative_to(root)))
        path.write_text(text)
        changed.append(str(path.relative_to(root)))
    return {'paths': changed, 'hidden_default': 'show unless protected and unauthenticated', 'slideshow_default': '6 (None)', 'existing_transition_choices': 'retained'}
