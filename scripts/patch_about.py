"""Normalize QuickPic About resources. Usage: patch_about.py DECODED_ROOT [--dry-run].

Uses existing resource names/icons; launcher app_name and non-About preferences
are kept intact. Run against a build copy, never decoded/baseline.
"""
from copy import deepcopy
from pathlib import Path
from xml.sax.saxutils import escape
import argparse
import json
import re
import xml.etree.ElementTree as ET

NS = "http://schemas.android.com/apk/res/android"
A = "{" + NS + "}"
ET.register_namespace("android", NS)
REPO = "https://github.com/jason840709/QuickPic-Gallery-Mod-2"
UPSTREAM = "https://github.com/WSTxda/QP-Gallery-Releases"
CREDIT_TITLES = {
    "@string/caio_name", "@string/pm_name", "@string/oldosfan_name",
    "@string/vega_name", "@string/thebear_name", "@string/app_translator",
}
# Keep resource IDs stable, but remove unused donation/contact values as well as
# the corresponding preferences. Historical contributor names/roles remain.
RETIRED_STRINGS = {
    "paypal", "paypal_desc", "donate_url", "email_url", "telegram_url",
    "channel_url", "xda_url", "twitter_url", "instagram_url", "caio_url",
    "pm_url", "oldosfan_url", "vega_url", "thebear_url",
}
FORBIDDEN_URL = re.compile(
    r"mailto:|https?://(?:[^/]*\.)?(?:t\.me|telegram\.[^/]+|"
    r"telegra\.ph|twitter\.com|x\.com|instagram\.com|paypal\.[^/]+|"
    r"xda-developers\.com|bit\.ly)(?:/|$)", re.I
)


def preference(icon, title, summary=None, key=None, url=None):
    attrs = {A + "icon": "@drawable/" + icon, A + "title": title}
    if summary is not None:
        attrs[A + "summary"] = summary
    if key is not None:
        attrs[A + "key"] = key
    node = ET.Element("Preference", attrs)
    if url is not None:
        ET.SubElement(node, "intent", {
            A + "action": "android.intent.action.VIEW", A + "data": url,
        })
    return node


def normalize_preferences(text):
    root = ET.fromstring(text)
    about = next((n for n in root if n.tag == "PreferenceScreen"
                  and n.get(A + "title") == "@string/about"), None)
    if about is None:
        raise ValueError("Missing About PreferenceScreen with @string/about")
    header = next((n for n in about if n.get(A + "layout") == "@layout/about_version"), None)
    if header is None:
        raise ValueError("Missing existing About header")
    header = deepcopy(header)
    header.tail = None
    # Preserve baseline credits, including locale-specific translator summaries,
    # without reviving their old contact/social intents on repeat runs.
    historical = []
    for n in about.iter("Preference"):
        if n.get(A + "title") in CREDIT_TITLES:
            copied = deepcopy(n)
            for child in list(copied):
                if child.tag == "intent":
                    copied.remove(child)
            copied.tail = None
            historical.append(copied)
    new_about = ET.Element("PreferenceScreen", dict(about.attrib))
    new_about.set(A + "title", "@string/about")
    new_about.set(A + "summary", "@string/about_desc")
    new_about.append(header)
    dev = preference("ic_developer_page", "@string/wstxda", "@string/developer", "jason_developer")
    dev.set(A + "selectable", "false")
    new_about.append(dev)
    new_about.append(preference("ic_github", "@string/github", REPO,
                                "jason_github", "@string/github_url"))
    credits = ET.SubElement(new_about, "PreferenceScreen", {
        A + "icon": "@drawable/ic_contributors_page",
        A + "title": "@string/contributors",
        A + "summary": "@string/contributors_desc",
        A + "key": "jason_contributors",
    })
    credits.append(preference("ic_github", "Contributors", "CONTRIBUTORS.md",
                              "jason_contributors_document", REPO + "/blob/main/CONTRIBUTORS.md"))
    credits.append(preference("ic_mod_base", "WSTxda", "QuickPic Gallery Mod / upstream releases",
                              "jason_upstream", UPSTREAM))
    credits.append(preference("ic_mod_base", "@string/app_name_alt", "@string/mod_base",
                              "jason_quickpic_base"))
    credits[-1].set(A + "selectable", "false")
    credits.extend(historical)
    new_about.append(preference("ic_faq", "@string/faq", "@string/faq_desc",
                                "jason_faq", "@string/faq_url"))
    # Locate only the original About span. Keep every other preference byte-for-
    # byte intact, including API-specific security/storage/viewer differences.
    opening = re.search(r'<PreferenceScreen\b[^>]*android:title="@string/about"[^>]*>', text)
    if opening is None:
        raise ValueError("Cannot locate About XML span")
    depth = 1
    ending = None
    for tag in re.finditer(r"</?PreferenceScreen\b[^>]*>", text[opening.end():]):
        token = tag.group()
        depth += -1 if token.startswith("</") else (0 if token.endswith("/>") else 1)
        if depth == 0:
            ending = opening.end() + tag.end()
            break
    if ending is None:
        raise ValueError("Unbalanced About PreferenceScreen")
    ET.indent(new_about, space="    ", level=1)
    replacement = ET.tostring(new_about, encoding="unicode")
    # Namespace already belongs to the existing root.
    replacement = replacement.replace(' xmlns:android="' + NS + '"', "", 1)
    return text[:opening.start()] + replacement + text[ending:]


# Existing E-Ink feature record, retained from the supplied derivative.
# The storage-path example uses a generic Android directory.
HISTORICAL_EINK_EN = 'This version has been optimized for E-Ink displays:\n\n1. Added "No Animation" option for page transitions\n · Go to Settings → Animation and select "None"\n · Perfect for E-Ink screens - reduces ghosting and speeds up page turning\n\n2. Quick page navigation via corner taps\n · Tap bottom-left corner: Previous image\n · Tap bottom-right corner: Next image\n · Convenient one-handed operation without swiping\n\n3. Include/Exclude folder path editing\n · In Include or Exclude folders list\n · Each path has an edit button (pencil icon) on the right\n · Tap to modify the path directly\n · Note: Do NOT add trailing slash /\n · Example: /storage/emulated/0/Pictures (correct)\n\n4. Set image as E-Ink screensaver (v2 NEW)\n · Open any image → tap menu (three dots) → "Set as"\n · Choose "Set as screensaver" from the dialog\n · Works with E-Ink devices that support XRZ screensaver'
HISTORICAL_EINK_TW = '本版本針對電子墨水屏進行了以下優化：\n\n1. 翻頁動畫新增「無動畫」選項\n · 在設定 → 轉場動畫中，選擇「無」可完全關閉翻頁動畫\n · 適合電子墨水屏，減少殘影，提升翻頁速度\n\n2. 點擊左下角和右下角快速翻頁\n · 點擊螢幕左下角：上一張圖片\n · 點擊螢幕右下角：下一張圖片\n · 方便單手操作，無需滑動\n\n3. 包含/排除資料夾路徑編輯\n · 在包含資料夾或排除資料夾列表中\n · 每個路徑右側有編輯按鈕（筆圖示）\n · 點擊可直接修改路徑\n · 注意：路徑末尾不要加斜線 /\n · 範例：/storage/emulated/0/Pictures（正確）\n\n4. 設置圖片為屏保（v2 新增）\n · 開啟圖片 → 點選右上角選單（三個點）→「設為」\n · 在彈出對話框中選擇「設置為屏保」\n · 適用於支援 XRZ 屏保的電子墨水設備'

def replacement_strings(path):
    chinese = path.parent.name.startswith("values-zh")
    simplified = path.parent.name in ("values-zh", "values-zh-rCN")
    strings = {
        "app_name_full": "QuickPic Gallery Mod 2",
        "version_number": "11.0.2",
        "build_type": "Jason Chen",
        "wstxda": "Jason Chen",
        "github_url": REPO + "/blob/main/README.md",
        "faq_url": REPO + "/blob/main/FAQ.md",
        "about_desc": "Developer, sources and FAQ",
        "contributors_desc": "Contributors and upstream sources",
        "faq_desc": "Usage and known limitations · FAQ.md",
        "eink_features_title": "E-Ink Optimized Features",
        "eink_features_content": (
            "E-Ink feature record before 11.0.0\\n\\n" + HISTORICAL_EINK_EN.replace("\n", "\\n") + "\\n\\nChanges in 11.0.0\\n"
            "64-bit support; tested on a 64-bit phone\\n"
            "Screensaver PNG: XPhoto first, chooser if XPhoto cannot open it\\n"
            "Phone toolbar: delete and slideshow; sharing in the menu\\n"
            "Tablet toolbar adds an interval button; set seconds in the viewer\\n"
            "App name: 快圖; screensaver folder: QuickPic-Screensaver\\n\\n"
            "Changes in 11.0.1\\nAnimated WebP is detected and played automatically; authored timing, blending and loop counts are preserved"
            "\\n\\nChanges in 11.0.2\\nShow hidden items on opening the app; existing password protection is retained\\nNew installations use no slideshow transition; existing choices are kept\\nFix the modern slideshow path applying a fade for None"
        ),
    }
    if chinese:
        strings.update({
            "about_desc": "開發者、來源與常見問題",
            "contributors_desc": "貢獻者與上游來源",
            "faq_desc": "操作方式與已知限制 · FAQ.md",
            "eink_features_title": "E-Ink 優化版本功能說明",
            "eink_features_content": (
                "11.0.0 前的 E-Ink 優化紀錄\\n\\n" + HISTORICAL_EINK_TW.replace("\n", "\\n") + "\\n\\n11.0.0 新增功能\\n"
                "支援 64 位元；已用 64 位元手機經過測試\\n"
                "螢幕保護 PNG 優先以 XPhoto 開啟；無法開啟時可選其他 App\\n"
                "手機工具列：垃圾桶、投影片；分享移到三點選單\\n"
                "平板工具列另有「顯示間隔」；看圖片時可直接設定秒數\\n"
                "App 名稱統一為「快圖」；輸出資料夾為 QuickPic-Screensaver\\n\\n"
                "11.0.1 更新\\n自動辨識並播放動畫 WebP，依原始幀時長、透明混合與循環次數播放"
                "\\n\\n11.0.2 更新\\n開啟 App 時預設顯示隱藏項目；保留既有密碼保護\\n首次安裝的投影片轉場預設為「無」，更新保留原有選擇\\n修正部分投影片介面在選「無」時仍套用淡入淡出的問題"
            ),
        })
        if simplified:
            strings.update({
                "about_desc": "开发者、来源与常见问题",
                "contributors_desc": "贡献者与上游来源",
                "faq_desc": "操作方式与已知限制 · FAQ.md",
                "eink_features_title": "E-Ink 优化版本功能说明",
                "eink_features_content": (
                    "11.0.0 前的 E-Ink 優化紀錄\\n\\n" + HISTORICAL_EINK_TW.replace("\n", "\\n") + "\\n\\n11.0.0 新增功能\\n"
                    "支持 64 位；已用 64 位手机经过测试\\n"
                    "屏保 PNG 优先用 XPhoto 打开；无法打开时可选其他 App\\n"
                    "手机工具栏：垃圾桶、幻灯片；分享移到三点菜单\\n"
                    "平板工具栏另有显示间隔；看图片时可直接设置秒数\\n"
                    "App 名称统一为快圖；输出文件夹为 QuickPic-Screensaver\\n\\n"
                    "11.0.1 更新\\n自动识别并播放动画 WebP，按原始帧时长、透明混合和循环次数播放"
                    "\\n\\n11.0.2 更新\\n打开 App 时默认显示隐藏项目；保留已有密码保护\\n首次安装的幻灯片转场默认为无，更新保留原有选择\\n修正部分幻灯片界面在选择无时仍淡入淡出的问题"
                ),
            })
    strings.update({key: "" for key in RETIRED_STRINGS})
    return strings


def normalize_strings(text, path):
    replacements = replacement_strings(path)

    def replace(match):
        name = match.group(2)
        if name not in replacements:
            return match.group()
        return "<string" + match.group(1).rstrip() + ">" + escape(replacements[name]) + "</string>"

    # apktool emits empty resource values as self-closing elements on a second
    # decode. Match both forms without swallowing the next string element.
    return re.sub(r'<string\b([^>]*\bname="([^"]+)"[^>]*?)(?:\s*/>|>(.*?)</string>)',
                  replace, text, flags=re.S)


def normalize_header(text):
    root = ET.fromstring(text)
    targets = [n for n in root.iter() if n.get(A + "text") == "@string/build_type"]
    if len(targets) != 1:
        raise ValueError("Unexpected About header build_type layout")
    # The old beta label forced ALL CAPS; retain Jason Chen capitalization.
    return re.sub(r'(<TextView\b[^>]*android:text="@string/build_type"[^>]*?)'
                  r'android:textAllCaps="(?:true|false)"',
                  r'\1android:textAllCaps="false"', text)


def plan(root):
    root = Path(root)
    changes = {}
    for path in sorted((root / "res").glob("xml*/main_pref.xml")):
        text = path.read_text()
        result = normalize_preferences(text)
        assert normalize_preferences(result) == result, path
        changes[path] = result
    if not changes:
        raise ValueError("No main_pref.xml resources")
    for path in sorted((root / "res").glob("values*/strings.xml")):
        text = path.read_text()
        result = normalize_strings(text, path)
        assert normalize_strings(result, path) == result, path
        old_tree, new_tree = ET.fromstring(text), ET.fromstring(result)
        assert [n.get("name") for n in old_tree] == [n.get("name") for n in new_tree], path
        old_launcher = next(n.text for n in old_tree if n.get("name") == "app_name")
        assert next(n.text for n in new_tree if n.get("name") == "app_name") == old_launcher, path
        for node in new_tree:
            name = node.get("name")
            if name in ("version_number", "app_name_full", "build_type", "wstxda"):
                assert node.text == replacement_strings(path)[name], path
            if name in RETIRED_STRINGS:
                assert not node.text, path
        if text != result:
            changes[path] = result
    path = root / "res/layout/about_version.xml"
    changes[path] = normalize_header(path.read_text())
    for path, result in changes.items():
        parsed = ET.fromstring(result)
        if path.name == "main_pref.xml":
            about = next(n for n in parsed if n.get(A + "title") == "@string/about")
            for node in about.iter():
                assert not FORBIDDEN_URL.search(node.get(A + "data", "")), path
            assert all(n.get(A + "title") != "@string/paypal" for n in about.iter()), path
    return changes


def patch(root, dry_run=False):
    root = Path(root).resolve()
    changes = plan(root)
    if not dry_run:
        baseline = Path(__file__).resolve().parent.parent / "decoded/baseline"
        if root == baseline:
            raise ValueError("Refusing to modify decoded/baseline; use a build copy")
        for path, text in changes.items():
            path.write_text(text)
    return [str(path.relative_to(root)) for path in changes]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("decoded_root")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    print(json.dumps({"dry_run": args.dry_run,
                      "validated_files": patch(args.decoded_root, args.dry_run)}, ensure_ascii=False))
