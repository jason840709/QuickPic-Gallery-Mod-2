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
HISTORY_KEY = "jason_version_history"
HISTORY_LAYOUT = "quickpic_version_history"
LATEST_TITLE = "jason_history_latest_title"
LATEST_CONTENT = "jason_history_latest_content"
ARCHIVE_TITLE = "jason_history_archive_title"
NEW_HISTORY_STRINGS = {LATEST_TITLE, LATEST_CONTENT, ARCHIVE_TITLE}
CONSOLIDATED_VERSIONS = {"11.0." + str(v) for v in range(7, 14)}
HISTORY_VERSIONS = tuple("11.0." + str(v) for v in range(7)) + ("11.0.13", "11.0.14", "11.0.16", "11.0.18", "11.0.19", "11.0.20")
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


def screen_span(text, attribute, value):
    opening = re.search(r'<PreferenceScreen\b[^>]*android:' + re.escape(attribute)
                        + r'="' + re.escape(value) + r'"[^>]*>', text)
    if opening is None:
        raise ValueError("Cannot locate PreferenceScreen " + value)
    depth = 1
    for tag in re.finditer(r"</?PreferenceScreen\b[^>]*>", text[opening.end():]):
        token = tag.group()
        depth += -1 if token.startswith("</") else (0 if token.endswith("/>") else 1)
        if depth == 0:
            return opening.start(), opening.end() + tag.end()
    raise ValueError("Unbalanced PreferenceScreen " + value)


def screen_xml(screen):
    ET.indent(screen, space="    ", level=1)
    return ET.tostring(screen, encoding="unicode").replace(
        ' xmlns:android="' + NS + '"', "", 1)


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
    history = ET.Element("PreferenceScreen", {
        A + "icon": "@drawable/ic_updates",
        A + "title": "@string/eink_features_title",
        A + "key": HISTORY_KEY,
    })
    ET.SubElement(history, "Preference", {
        A + "layout": "@layout/" + HISTORY_LAYOUT,
        A + "selectable": "false",
        A + "key": "jason_version_history_content",
    })
    # Locate only the original About span. Keep every other preference byte-for-
    # byte intact, including API-specific security/storage/viewer differences.
    existing = [n for n in root if n.get(A + "key") == HISTORY_KEY]
    if len(existing) > 1:
        raise ValueError("Duplicate development history screens")
    if existing:
        start, ending = screen_span(text, "key", HISTORY_KEY)
        if text[:start].endswith("\n    "):
            start -= len("\n    ")
        text = text[:start] + text[ending:]
    start, ending = screen_span(text, "title", "@string/about")
    replacement = screen_xml(new_about) + "\n    " + screen_xml(history)
    return text[:start] + replacement + text[ending:]


# Existing E-Ink feature record, retained from the supplied derivative.
# The storage-path example uses a generic Android directory.
HISTORICAL_EINK_EN = 'This version has been optimized for E-Ink displays:\n\n1. Added "No Animation" option for page transitions\n · Go to Settings → Animation and select "None"\n · Perfect for E-Ink screens - reduces ghosting and speeds up page turning\n\n2. Quick page navigation via corner taps\n · Tap bottom-left corner: Previous image\n · Tap bottom-right corner: Next image\n · Convenient one-handed operation without swiping\n\n3. Include/Exclude folder path editing\n · In Include or Exclude folders list\n · Each path has an edit button (pencil icon) on the right\n · Tap to modify the path directly\n · Note: Do NOT add trailing slash /\n · Example: /storage/emulated/0/Pictures (correct)\n\n4. Set image as E-Ink screensaver (v2 NEW)\n · Open any image → tap menu (three dots) → "Set as"\n · Choose "Set as screensaver" from the dialog\n · Works with E-Ink devices that support XRZ screensaver'
HISTORICAL_EINK_TW = '本版本針對電子墨水屏進行了以下優化：\n\n1. 翻頁動畫新增「無動畫」選項\n · 在設定 → 轉場動畫中，選擇「無」可完全關閉翻頁動畫\n · 適合電子墨水屏，減少殘影，提升翻頁速度\n\n2. 點擊左下角和右下角快速翻頁\n · 點擊螢幕左下角：上一張圖片\n · 點擊螢幕右下角：下一張圖片\n · 方便單手操作，無需滑動\n\n3. 包含/排除資料夾路徑編輯\n · 在包含資料夾或排除資料夾列表中\n · 每個路徑右側有編輯按鈕（筆圖示）\n · 點擊可直接修改路徑\n · 注意：路徑末尾不要加斜線 /\n · 範例：/storage/emulated/0/Pictures（正確）\n\n4. 設置圖片為屏保（v2 新增）\n · 開啟圖片 → 點選右上角選單（三個點）→「設為」\n · 在彈出對話框中選擇「設置為屏保」\n · 適用於支援 XRZ 屏保的電子墨水設備'

def replacement_strings(path):
    chinese = path.parent.name.startswith("values-zh")
    simplified = path.parent.name in ("values-zh", "values-zh-rCN")
    strings = {
        "app_name_full": "QuickPic Gallery Mod II",
        "version_number": "11.0.20",
        "build_type": "Jason Chen",
        "wstxda": "Jason Chen",
        "github_url": REPO + "/blob/main/README.md",
        "faq_url": REPO + "/blob/main/FAQ.md",
        "about_desc": "Developer, sources and FAQ",
        "contributors_desc": "Contributors and upstream sources",
        "faq_desc": "Usage and known limitations · FAQ.md",
        "eink_features_title": "Version development history",
        "eink_features_content": (
            "Changes in 11.0.0\\n" + HISTORICAL_EINK_EN.replace("\n", "\\n") + "\\n\\n"
            "64-bit support; tested on a 64-bit phone\\n"
            "Screensaver PNG: XPhoto first, chooser if XPhoto cannot open it\\n"
            "Phone toolbar: delete and slideshow; sharing in the menu\\n"
            "Tablet toolbar adds an interval button; set seconds in the viewer\\n"
            "App name: 快圖; screensaver folder: QuickPic-Screensaver\\n\\n"
            "Changes in 11.0.1\\nAnimated WebP is detected and played automatically; authored timing, blending and loop counts are preserved"
            "\\n\\nChanges in 11.0.2\\nShow hidden items on opening the app; existing password protection is retained\\nNew installations use no slideshow transition; existing choices are kept\\nFix the modern slideshow path applying a fade for None"
            "\\n\\nChanges in 11.0.3\\nCorrect the startup cache method signature to prevent a startup crash"
        ),
    }
    if chinese:
        strings.update({
            "about_desc": "開發者、來源與常見問題",
            "contributors_desc": "貢獻者與上游來源",
            "faq_desc": "操作方式與已知限制 · FAQ.md",
            "eink_features_title": "版本開發紀錄",
            "eink_features_content": (
                "11.0.0 新增功能\\n" + HISTORICAL_EINK_TW.replace("\n", "\\n") + "\\n\\n"
                "支援 64 位元；已用 64 位元手機經過測試\\n"
                "螢幕保護 PNG 優先以 XPhoto 開啟；無法開啟時可選其他 App\\n"
                "手機工具列：垃圾桶、投影片；分享移到三點選單\\n"
                "平板工具列另有「顯示間隔」；看圖片時可直接設定秒數\\n"
                "App 名稱統一為「快圖」；輸出資料夾為 QuickPic-Screensaver\\n\\n"
                "11.0.1 更新\\n自動辨識並播放動畫 WebP，依原始幀時長、透明混合與循環次數播放"
                "\\n\\n11.0.2 更新\\n開啟 App 時預設顯示隱藏項目；保留既有密碼保護\\n首次安裝的投影片轉場預設為「無」，更新保留原有選擇\\n修正部分投影片介面在選「無」時仍套用淡入淡出的問題"
                "\\n\\n11.0.3 更新\\n修正啟動時隱藏項目流程的內部方法簽名，避免啟動閃退"
            ),
        })
        if simplified:
            strings.update({
                "about_desc": "开发者、来源与常见问题",
                "contributors_desc": "贡献者与上游来源",
                "faq_desc": "操作方式与已知限制 · FAQ.md",
                "eink_features_title": "版本开发记录",
                "eink_features_content": (
                    "11.0.0 新增功能\\n" + HISTORICAL_EINK_TW.replace("\n", "\\n") + "\\n\\n"
                    "支持 64 位；已用 64 位手机经过测试\\n"
                    "屏保 PNG 优先用 XPhoto 打开；无法打开时可选其他 App\\n"
                    "手机工具栏：垃圾桶、幻灯片；分享移到三点菜单\\n"
                    "平板工具栏另有显示间隔；看图片时可直接设置秒数\\n"
                    "App 名称统一为快圖；输出文件夹为 QuickPic-Screensaver\\n\\n"
                    "11.0.1 更新\\n自动识别并播放动画 WebP，按原始帧时长、透明混合和循环次数播放"
                    "\\n\\n11.0.2 更新\\n打开 App 时默认显示隐藏项目；保留已有密码保护\\n首次安装的幻灯片转场默认为无，更新保留原有选择\\n修正部分幻灯片界面在选择无时仍淡入淡出的问题"
                    "\\n\\n11.0.3 更新\\n修正启动时隐藏项目流程的内部方法签名，避免启动闪退"
                ),
            })
    if chinese:
        addition = ("\\n\\n11.0.4 更新\\n優先掃描新加入與最近變更的資料夾；啟動時一併使用一般與隱藏資料夾的路徑快取。\\n日期排序拆成建立日期（加入時間）與修改日期。無可靠建立時間的既有檔案初次以修改時間建立基準；之後新檔案記錄首次加入時間。")
    else:
        addition = ("\\n\\nChanges in 11.0.4\\nPrioritize new and recently changed folders; reuse normal and hidden folder path caches.\\nSeparate creation (added) and modification sorting. Existing files without a reliable creation time initially use modification time; later additions retain their first observed time.")
    strings["eink_features_content"] += addition
    if simplified:
        addition = ("\\n\\n11.0.5 更新\\n设置页面新增「版本开发记录」，可离线查看完整 E-Ink 说明与历代更新；关于页面保留应用与开发者等基本信息。\\n日期排序集中显示修改、创建与拍摄日期。图片工具栏保留幻灯片和间隔按钮；删除、编辑和分享排在菜单前方。\\n「幻灯片间隔」可直接输入秒数，并单独记住上次自定义值；间隔与路径编辑图标随主题调整，文件夹路径编辑窗口显示正确标题。")
    elif chinese:
        addition = ("\\n\\n11.0.5 更新\\n設定頁面新增「版本開發紀錄」，可離線查看完整 E-Ink 說明與歷代更新；關於頁面保留 App 與開發者等基本資訊。\\n日期排序集中顯示修改、建立與拍攝日期。圖片工具列保留投影片和間隔按鈕；刪除、編輯和分享排在選單前方。\\n「投影片間隔」可直接輸入秒數，並另外記住上次自訂值；間隔與路徑編輯圖示隨佈景調整，資料夾路徑編輯視窗顯示正確標題。")
    else:
        addition = ("\\n\\nChanges in 11.0.5\\nAdd offline Version development history to Settings and keep About concise.\\nGroup modified, created and taken date sorting. Keep slideshow and interval controls on the image toolbar; place delete, edit and share first in the menu.\\nEnter slideshow interval seconds directly and remember the last custom value separately. Adapt interval and folder edit icons to the theme and correct the folder path dialog title.")
    strings["eink_features_content"] += addition
    if simplified:
        latest = ("11.0.6 更新\\n幻灯片间隔改用计时器图标。自定义秒数输入框缩短、与选项文字对齐，并显示 s 单位。\\n点击自定义选项即可套用已保存的秒数；输入新值后，点击「套用自定义」保存并生效。\\n版本开发记录先显示最新更新，再列出完整记录；E-Ink 优化说明归入 11.0.0。")
        strings.update({LATEST_TITLE: "最新版本更新内容", ARCHIVE_TITLE: "完整版本开发记录"})
    elif chinese:
        latest = ("11.0.6 更新\\n投影片間隔改用計時器圖示。自訂秒數輸入框縮短、與選項文字對齊，並顯示 s 單位。\\n點選自訂選項即可套用已保存的秒數；輸入新值後，按「套用自訂」保存並生效。\\n版本開發紀錄先顯示最新更新，再列出完整紀錄；E-Ink 優化說明歸入 11.0.0。")
        strings.update({LATEST_TITLE: "最新版本更新內容", ARCHIVE_TITLE: "完整版本開發紀錄"})
    else:
        latest = ("Changes in 11.0.6\\nUse a timer icon for slideshow interval. Make the custom field shorter, align it with the option text and show the s unit.\\nTap Custom to apply the saved interval immediately. After editing the field, choose Apply custom to save and apply the new value.\\nShow the latest changes before the full version history, with E-Ink features included in 11.0.0.")
        strings.update({LATEST_TITLE: "Latest version changes", ARCHIVE_TITLE: "Complete version history"})
    # Keep 11.0.6 in the archive before adding the next release.
    strings["eink_features_content"] += "\\n\\n" + latest
    if simplified:
        latest = "11.0.13 更新\\n相较11.0.6，改善单张、多选及整个文件夹的删除流程；本机与存储卡的图片删除已完成实测，速度已改善。\\n按可用媒体记录精确删除与清理，省略重复工作；删除文件夹会一并移除非图片、隐藏文件和子文件夹。\\n保留删除进度，移除删除后的诊断报告。"
    elif chinese:
        latest = "11.0.13 更新\\n相較11.0.6，改善單張、多選及整個資料夾的刪除流程；本機與記憶卡的圖片刪除已完成實測，速度已改善。\\n依可用媒體紀錄精確刪除與清理，省略重複工作；刪除資料夾會一併移除非圖片、隱藏檔和子資料夾。\\n保留刪除進度，移除刪除後的診斷報告。"
    else:
        latest = "Changes in 11.0.13\\nImprove individual, selected-image and whole-folder deletion compared with 11.0.6. Internal-storage and SD-card image deletion have been tested with improved speed.\\nUse available media records for precise deletion and cleanup, skipping redundant work. Whole-folder deletion includes non-images, hidden files and subfolders.\\nKeep deletion progress and remove post-deletion diagnostic reports."
    strings["eink_features_content"] += "\\n\\n" + latest
    if simplified:
        latest = "11.0.14 更新\\n新增阅读器模式：保留当前页面，等下一张完整图片加载好后再换页，不显示模糊预览或加载字样。\\n手动翻页与幻灯片均可使用；开启后会减少闪烁，但延迟会提高。"
    elif chinese:
        latest = "11.0.14 更新\\n新增閱讀器模式：保留目前頁面，等下一張完整圖片載入好後才換頁，不顯示模糊預覽或載入字樣。\\n手動翻頁與投影片均可使用；開啟後會減少閃爍，但延遲會提高。"
    else:
        latest = "Changes in 11.0.14\\nAdd Reader mode: keep the current page until the next complete image is ready, without a blurry preview or loading text.\\nWorks for manual page turns and slideshows. Reduces flicker with increased page-turn delay."
    strings["eink_features_content"] += "\\n\\n" + latest
    if simplified:
        latest = '11.0.16 更新\\n可自订屏保 PNG 的宽高，或检测屏幕像素；按比例置中裁切，并提供恢复默认。'
    elif chinese:
        latest = '11.0.16 更新\\n可自訂屏保 PNG 的寬高，或偵測螢幕像素；依比例置中裁切，並提供恢復預設。'
    else:
        latest = 'Changes in 11.0.16\\nSet PNG width and height or detect screen pixels; use a centered crop and restore defaults.'
    strings["eink_features_content"] += "\\n\\n" + latest
    if simplified:
        latest = '11.0.18 更新\\n新增 B1051C 屏保入口，图片转换为 PNG 后交给系统套用，原图保留；完成显示「设置成功」。'
    elif chinese:
        latest = '11.0.18 更新\\n新增 B1051C 屏保入口，圖片轉為 PNG 後交給系統套用，原圖保留；完成顯示「設定成功」。'
    else:
        latest = 'Changes in 11.0.18\\nSet the current image as a B1051C screensaver using a separate PNG; keep the original and show a completion message.'
    strings["eink_features_content"] += "\\n\\n" + latest
    if simplified:
        latest = '11.0.19 更新\\n设置→检视→图片新增「显示屏保选项」，可分别显示或隐藏普通与 B1051C 屏保入口；桌布选项保留。'
    elif chinese:
        latest = '11.0.19 更新\\n設定→檢視→圖片新增「顯示屏保選項」，可分別顯示或隱藏一般與 B1051C 屏保入口；桌布選項保留。'
    else:
        latest = 'Changes in 11.0.19\\nChoose which screensaver actions appear under Set as in Settings → View → Pictures. Wallpaper remains available.'
    strings[LATEST_CONTENT] = latest
    strings["eink_features_content"] += "\\n\\n" + latest
    if simplified:
        latest = "11.0.20 更新\n新增文石屏保入口；首次安装依机型选择默认屏保选项，可在设置中自行开关。一般屏保入口直接选择其他 App 开启 PNG。支持 B1051C 4.8.8 与 4.9.0 的原厂屏保接口。"
    elif chinese:
        latest = "11.0.20 更新\n新增文石屏保入口；首次安裝依機型選擇預設屏保選項，可在設定中自行開關。一般屏保入口直接選擇其他 App 開啟 PNG。支援 B1051C 4.8.8 與 4.9.0 的原廠屏保介面。"
    else:
        latest = "Changes in 11.0.20\nAdd the BOOX screensaver action, choose defaults on first installation only, and keep manual visibility settings. The generic action opens an app chooser. Support B1051C 4.8.8 and 4.9.0 screensaver interfaces."
    strings[LATEST_CONTENT] = latest
    strings["eink_features_content"] += "\\n\\n" + latest
    strings.update({key: "" for key in RETIRED_STRINGS})
    return strings


def history_text(value):
    """Decode Android quoting/newlines without changing the supplied prose."""
    value = value or ""
    if len(value) >= 2 and value.startswith('"') and value.endswith('"'):
        value = value[1:-1]
    return value.replace("\r\n", "\n").replace("\\n", "\n").replace('\\"', '"').replace("\\'", "'")


def history_sections(value):
    """Separate version headings; unversioned E-Ink notes belong to 11.0.0."""
    value = history_text(value)
    pattern = re.compile(r"(?m)^((?:Changes in )?(11\.\d+\.\d+)(?: (?:更新|新增功能))?)\n")
    matches = list(pattern.finditer(value))
    preamble = value[:matches[0].start()] if matches else value
    preamble = re.sub(r"^(?:E-Ink feature record before 11\.0\.0|11\.0\.0 前的 E-Ink (?:優化紀錄|优化记录))\n\n", "", preamble)
    sections = {}
    for index, match in enumerate(matches):
        version = match.group(2)
        if version in sections:
            raise ValueError("Duplicate history record for " + version)
        end = matches[index + 1].start() if index + 1 < len(matches) else len(value)
        sections[version] = (match.group(1), value[match.end():end].rstrip("\n"))
    return preamble.rstrip("\n"), sections


def merge_history(existing, canonical):
    """Keep published history and consolidate deletion development into 11.0.13.

    User-requested 11.0.7–13 consolidation replaces those supplied entries with
    the current concise canonical 13 summary; unrelated and future prose survives.
    Original development evidence remains outside the App history.
    """
    preamble, supplied = history_sections(existing)
    _, known = history_sections(canonical)
    sections = dict(known)
    sections.update({version: body for version, body in supplied.items()
                     if version not in CONSOLIDATED_VERSIONS})
    if preamble:
        heading, body = sections["11.0.0"]
        if preamble not in body:
            body = preamble + "\n\n" + body
        sections["11.0.0"] = (heading, body)
    text = "\n\n".join(heading + "\n" + body for version, (heading, body) in
                         sorted(sections.items(), key=lambda pair: tuple(map(int, pair[0].split(".")))))
    # Quoting preserves indentation, apostrophes and literal quotes through aapt.
    return '"' + text.replace("\n", "\\n").replace('"', '\\"') + '"'


def normalize_strings(text, path):
    replacements = replacement_strings(path)

    def replace(match):
        name = match.group(2)
        if name not in replacements:
            return match.group()
        value = replacements[name]
        if name == "eink_features_content":
            value = merge_history(ET.fromstring(match.group()).text, value)
        elif name == LATEST_CONTENT:
            value = '"' + value.replace('"', '\\"') + '"'
        return "<string" + match.group(1).rstrip() + ">" + escape(value) + "</string>"

    # apktool emits empty resource values as self-closing elements on a second
    # decode. Match both forms without swallowing the next string element.
    result = re.sub(r'<string\b([^>]*\bname="([^"]+)"[^>]*?)(?:\s*/>|>(.*?)</string>)',
                    replace, text, flags=re.S)
    names = {n.get("name") for n in ET.fromstring(result)}
    if "eink_features_content" in names or path.parent.name in ("values-zh", "values-zh-rCN", "values-zh-rTW"):
        missing = []
        for name in ("eink_features_title", "eink_features_content", LATEST_TITLE, LATEST_CONTENT, ARCHIVE_TITLE):
            if name not in names:
                value = replacements[name]
                if name == "eink_features_content":
                    value = merge_history("", value)
                elif name == LATEST_CONTENT:
                    value = '"' + value.replace('"', '\\"') + '"'
                missing.append('    <string name="' + name + '">' + escape(value) + '</string>\n')
        result = result.replace("</resources>", "".join(missing) + "</resources>")
    return result


def history_block(root):
    blocks = [n for n in root if any(
        child.get(A + "text") == "@string/eink_features_content" for child in n.iter())]
    if len(blocks) > 1:
        raise ValueError("Unexpected duplicate About history blocks")
    if blocks:
        refs = {n.get(A + "text") for n in blocks[0].iter()}
        if not {"@string/eink_features_title", "@string/eink_features_content"} <= refs:
            raise ValueError("Incomplete About history block")
        return blocks[0]
    return None


def history_layout(text, existing=None):
    block = history_block(ET.fromstring(text))
    if block is None:
        if existing is None:
            raise ValueError("Missing original history block and dedicated history layout")
        block = ET.fromstring(existing)
    refs = {n.get(A + "text") for n in block.iter()}
    old_refs = {"@string/eink_features_title", "@string/eink_features_content"}
    new_refs = {"@string/" + key for key in NEW_HISTORY_STRINGS} | {"@string/eink_features_content"}
    if not (old_refs <= refs or new_refs <= refs):
        raise ValueError("Incomplete dedicated history layout")
    if any(n.tag != "TextView" for n in block):
        raise ValueError("Unexpected non-text history layout child")
    title = next(n for n in block if n.tag == "TextView")
    body = next(n for n in block if n.get(A + "text") == "@string/eink_features_content")
    result = ET.Element(block.tag, dict(block.attrib))
    for key, template, top in ((LATEST_TITLE, title, None), (LATEST_CONTENT, body, "12dip"),
                               (ARCHIVE_TITLE, title, "24dip"), ("eink_features_content", body, "12dip")):
        node = deepcopy(template)
        node.tail = None
        node.set(A + "text", "@string/" + key)
        if top:
            node.set(A + "layout_marginTop", top)
        else:
            node.attrib.pop(A + "layout_marginTop", None)
        result.append(node)
    ET.indent(result, space="    ")
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(result, encoding="unicode") + "\n"


def normalize_header(text, history_copied=False):
    root = ET.fromstring(text)
    targets = [n for n in root.iter() if n.get(A + "text") == "@string/build_type"]
    if len(targets) != 1:
        raise ValueError("Unexpected About header build_type layout")
    block = history_block(root)
    if block is not None:
        if not history_copied:
            raise ValueError("Copy development history before shortening About")
        index = list(root).index(block)
        root.remove(block)
        # Keep the divider before the old history block as the credits separator.
        if index < len(root) and root[index].tag == "include" and root[index].get("layout") == "@layout/pref_divider":
            root.remove(root[index])
    targets[0].set(A + "textAllCaps", "false")
    ET.indent(root, space="    ")
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + "\n"


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
    string_paths = sorted((root / "res").glob("values*/strings.xml"))
    resource_names = {n.get("name") for path in string_paths
                      for n in ET.fromstring(path.read_text())}
    for path in string_paths:
        text = path.read_text()
        result = normalize_strings(text, path)
        assert normalize_strings(result, path) == result, path
        old_tree, new_tree = ET.fromstring(text), ET.fromstring(result)
        old_names = [n.get("name") for n in old_tree]
        new_names = [n.get("name") for n in new_tree]
        # Existing IDs stay in place; only the three new section strings may be added.
        assert set(new_names) <= resource_names | NEW_HISTORY_STRINGS, path
        assert old_names == [name for name in new_names if name in old_names], path
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
    original_header = path.read_text()
    history_path = path.with_name(HISTORY_LAYOUT + ".xml")
    changes[history_path] = history_layout(original_header,
        history_path.read_text() if history_path.exists() else None)
    changes[path] = normalize_header(original_header, history_copied=True)
    assert normalize_header(changes[path]) == changes[path], path
    for path, result in changes.items():
        parsed = ET.fromstring(result)
        if path.name == "main_pref.xml":
            about = next(n for n in parsed if n.get(A + "title") == "@string/about")
            history = next(n for n in parsed if n.get(A + "key") == HISTORY_KEY)
            assert list(parsed).index(history) == list(parsed).index(about) + 1, path
            assert not any(n.tag == "intent" for n in history.iter()), path
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
