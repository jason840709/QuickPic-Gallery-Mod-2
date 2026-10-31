# SPDX-License-Identifier: MIT
"""Give native switches an opaque track and a quiet checked-thumb mark across every application theme.

Only presentation changes: SwitchPreference retains its original keys, defaults,
dependencies, listeners, and Android accessibility / keyboard behavior.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.strip() + '\n', encoding='utf-8')


def patch(root):
    root = Path(root)
    res = root / 'res'
    ET.register_namespace('android', A[1:-1])
    count = 0
    for path in sorted(res.glob('xml*/*.xml')):
        tree = ET.parse(path)
        switches = [n for n in tree.getroot().iter()
                    if n.tag in ('SwitchPreference', 'android.preference.SwitchPreference')]
        if not switches:
            continue
        for switch in switches:
            switch.set(A + 'switchTextOn', '@string/quickpic_switch_on')
            switch.set(A + 'switchTextOff', '@string/quickpic_switch_off')
        count += len(switches)
        tree.write(path, encoding='utf-8', xml_declaration=True)
    if not count:
        raise ValueError('No native switch preferences found')

    # Include night and API-qualified styles: a resource variant may replace its
    # unqualified style completely. No colorAccent change to other controls.
    for path in sorted(res.glob('values*/styles.xml')):
        tree = ET.parse(path)
        for style in tree.getroot().findall('style'):
            if not style.get('name', '').startswith('Theme.'):
                continue
            item = next((n for n in style if n.get('name') == 'android:switchStyle'), None)
            if item is None:
                item = ET.SubElement(style, 'item', {'name': 'android:switchStyle'})
            item.text = '@style/QuickPicSwitch'
        tree.write(path, encoding='utf-8', xml_declaration=True)

    write(res / 'values/quickpic_switch.xml', '''<resources>
    <string name="quickpic_switch_on">ON</string>
    <string name="quickpic_switch_off">OFF</string>
    <style name="QuickPicSwitch" parent="@android:style/Widget.Material.CompoundButton.Switch">
        <item name="android:thumb">@drawable/quickpic_switch_thumb</item>
        <item name="android:track">@drawable/quickpic_switch_track</item>
        <item name="android:thumbTint">@null</item>
        <item name="android:trackTint">@null</item>
        <item name="android:splitTrack">false</item>
        <item name="android:showText">false</item>
        <item name="android:textOn">@string/quickpic_switch_on</item>
        <item name="android:textOff">@string/quickpic_switch_off</item>
        <item name="android:switchTextAppearance">@style/QuickPicSwitchText</item>
        <item name="android:switchMinWidth">64dp</item>
        <item name="android:thumbTextPadding">4dp</item>
    </style>
    <style name="QuickPicSwitchText" parent="@android:style/TextAppearance.Material">
        <item name="android:textColor">@color/quickpic_switch_text</item>
        <item name="android:textSize">12sp</item>
        <item name="android:textStyle">bold</item>
    </style>
</resources>''')
    for folder in res.glob('values*'):
        if 'zh' not in folder.name:
            continue
        traditional = any(x in folder.name for x in ('TW', 'HK', 'MO', 'Hant'))
        on, off = ('開', '關') if traditional else ('开', '关')
        write(folder / 'quickpic_switch.xml', '<resources><string name="quickpic_switch_on">' + on
              + '</string><string name="quickpic_switch_off">' + off + '</string></resources>')

    write(res / 'color/quickpic_switch_text.xml', '''<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:color="#ff777777" />
    <item android:color="#ff171717" />
</selector>''')
    for name, size, corners, states in (
        ('track', (64, 24), 12, [
            ('android:state_enabled="false" android:state_checked="true"', '#ff8fa9c8', '#ff777777'),
            ('android:state_enabled="false"', '#ffdddddd', '#ff999999'),
            ('android:state_checked="true"', '#ff1967d2', '#ff0c3b80'),
            ('', '#ffd2d6dc', '#ff62676e')]),
        ('thumb', (32, 32), 16, [
            ('android:state_enabled="false" android:state_checked="true"', '#ffeeeeee', '#ff999999'),
            ('android:state_enabled="false"', '#ffeeeeee', '#ff999999'),
            ('android:state_checked="true"', '#ffffffff', '#ff405e7b'),
            ('', '#ffffffff', '#ff62676e')]),
    ):
        items = []
        for state, fill, stroke in states:
            shape = ('<shape android:shape="rectangle"><size android:width="%sdp" android:height="%sdp" />'
                     '<corners android:radius="%sdp" /><solid android:color="%s" />'
                     '<stroke android:width="1dp" android:color="%s" /></shape>') % (*size, corners, fill, stroke)
            if name == 'track':
                shape = '<inset android:insetTop="4dp" android:insetBottom="4dp">' + shape + '</inset>'
            if name == 'thumb' and 'state_checked="true"' in state:
                tick = 'quickpic_switch_tick_disabled' if 'state_enabled="false"' in state else 'quickpic_switch_tick'
                # The overlay has full thumb bounds with an inset path, not an
                # InsetDrawable. Reported inset padding makes Switch clip its thumb.
                shape = ('<layer-list><item>' + shape + '</item><item '
                         'android:drawable="@drawable/' + tick + '" /></layer-list>')
            items.append('<item ' + state + '>' + shape + '</item>')
        write(res / ('drawable/quickpic_switch_' + name + '.xml'),
              '<selector xmlns:android="http://schemas.android.com/apk/res/android">\n'
              + '\n'.join(items) + '\n</selector>')

    # A small rounded tick inside the existing thumb; state labels remain for
    # accessibility but are not painted over the switch. Uses API 21 vectors.
    for suffix, color in (('', '#ff455a70'), ('_disabled', '#ff8e969f')):
        write(res / ('drawable/quickpic_switch_tick' + suffix + '.xml'),
              '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
              'android:width="32dp" android:height="32dp" android:viewportWidth="32" android:viewportHeight="32">'
              '<path android:pathData="M10,16 L14,20 L23,11" android:fillColor="#00000000" '
              'android:strokeColor="' + color + '" android:strokeWidth="1.5" '
              'android:strokeLineCap="round" android:strokeLineJoin="round" /></vector>')
