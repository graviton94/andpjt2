#!/usr/bin/env python3
"""Rebuild the data parts of docs/content/notification-catalog.html from the engine.

Reads core Content.kt (apps, tiers, regions, categories, unlock and pro levels) and
docs/content/app-texts.json (glyph, colour, icon concept, unique effect, three lines),
then rewrites the generated blocks of the catalog between /*GEN:name*/ ... /*END:name*/.
Run after any change to the roster or unlock levels:  python3 tools/catalog/build_catalog.py
"""
import json, re, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
CONTENT = ROOT / "core/src/main/kotlin/com/graviton94/notificationcleaner/core/Content.kt"
TEXTS = ROOT / "docs/content/app-texts.json"
HTML = ROOT / "docs/content/notification-catalog.html"
ICONS = ROOT / "docs/design/app-icons"

REGION = {"ST": "ST", "AS": "AS", "SE": "SE", "WE": "WE", "GL": "GL"}
# Non-app unlocks by level (cards, slots, caps, docs-only content in <b>). Apps and pro versions are added from the engine.
EXTRA = {
    1: "덱 3 · 스팸 문자 · 오프라인 8시간", 2: "스팸 전화", 3: "덱 최대 4 · 상점", 4: "업데이트 카드", 5: "<b>생일 축하 카드</b>",
    6: "이벤트 슬롯 1", 7: "오프라인 10시간", 8: "과부하 깊이 · 덱 최대 5", 9: "<b>배터리 부족(돌발)</b>",
    10: "샌드위치 · 덱 최소 4", 11: "<b>일일 도전</b>", 12: "넓은 알림창", 13: "<b>방해 금지 카드</b>",
    14: "@모두 공지 · 이벤트 슬롯 2", 15: "<b>알림훈련 카드</b>", 16: "인증번호 카드", 18: "<b>꾸미기: 테마 1</b>",
    19: "과부하 깊이 최대 7", 20: "와일드 · 덱 5~6", 21: "<b>저장공간 부족(돌발)</b>", 22: "앱스토어 세일 카드",
    24: "샌드위치 최대 6", 25: "공장 초기화", 27: "단체 문자 카드", 28: "<b>꾸미기: 파쇄 효과 1</b>", 30: "덱 6~7",
    35: "오프라인 12시간", 37: "펌웨어 업데이트 카드", 38: "이벤트 슬롯 3", 39: "넓은 알림창 최대 3", 40: "덱 7~8",
    41: "<b>꾸미기: 테마 2</b>", 47: "샌드위치 최대 8", 49: "<b>다크 모드 카드</b>", 50: "덱 최대 9",
    51: "<b>앱 충돌(돌발)</b>", 52: "<b>일일 도전 2회</b>", 53: "<b>꾸미기: 테마 3</b>", 54: "과부하 깊이 최대 8",
    60: "클라우드 동기화(2차 환생) · <b>자동 강화</b>", 61: "행운 알림 카드", 62: "덱 최소 8", 63: "이벤트 슬롯 4",
    64: "<b>꾸미기: 테마 4</b>", 69: "와일드 최대 8", 71: "<b>효과음 팩 2</b>", 72: "<b>파쇄 효과 2</b>", 73: "덱 최대 10",
    75: "<b>자동 덱 편성</b>", 83: "과부하 깊이 최대 9", 84: "<b>칭호: 알림 장인</b>", 88: "<b>효과음 팩 3</b>",
    93: "<b>꾸미기: 테마 5</b>", 100: "<b>칭호: 받은편지함 0</b>", 108: "<b>파쇄 효과 3</b>", 116: "<b>꾸미기: 테마 6</b>",
    124: "<b>효과음 팩 4</b>", 140: "<b>칭호: 글로벌 인싸</b>", 152: "<b>꾸미기: 테마 7</b>", 170: "<b>파쇄 효과 4</b>",
    180: "<b>칭호: 알림 없는 사람</b>", 192: "<b>칭호: 방해 금지의 달인</b>",
}


def parse_engine():
    src = CONTENT.read_text()
    apps = [dict(id=m[0], name=m[1], tier=int(m[2]), order=int(m[3]), reg=m[4], cat=m[5].lower(), lv=int(m[6]))
            for m in re.findall(r'app\("(\w+)", "([^"]+)", (\d+), (\d+), (\w+), Category\.(\w+), (\d+)\)', src) if int(m[2]) <= 10]  # launch content only; update packs live in 14 live-updates
    pro = {a: int(l) for a, l in re.findall(r'"(\w+)" to (\d+)', src[src.index("val launchPro"):src.index("var proLevel")])}
    starter = re.search(r'val starter = listOf\(([^)]*)\)', src).group(1).replace('"', "").replace(" ", "").split(",")
    return apps, pro, starter


def js_str(s):
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"


def gen(html, name, body):
    pat = re.compile(r"/\*GEN:%s\*/.*?/\*END:%s\*/" % (name, name), re.S)
    assert pat.search(html), name
    return pat.sub(lambda _: "/*GEN:%s*/%s/*END:%s*/" % (name, body, name), html)


def main():
    apps, pro, starter = parse_engine()
    texts = json.loads(TEXTS.read_text())
    missing = [a["id"] for a in apps if a["id"] not in texts]
    assert not missing, f"texts missing: {missing}"
    rows = []
    for a in apps:
        g, col, concept, eff, ments = texts[a["id"]]
        rows.append("[" + ",".join([js_str(a["id"]), js_str(a["name"]), js_str(g), js_str(col), str(a["tier"]),
                                     js_str(a["reg"]), js_str(a["cat"]), str(a["lv"]), js_str(concept), js_str(eff),
                                     "[" + ",".join(js_str(m) for m in ments) + "]", str(pro[a["id"]])]) + "]")
    unlock = {lv: [v] for lv, v in EXTRA.items()}
    for a in apps:
        if a["id"] not in starter:
            unlock.setdefault(a["lv"], []).append(a["name"])
        unlock.setdefault(pro[a["id"]], []).append(a["name"] + " 프로")
    unlock.setdefault(1, []).insert(0, "시작 앱 " + " · ".join(a["name"] for a in apps if a["id"] in starter))
    u = ",".join(f"{lv}:{js_str(' · '.join(v))}" for lv, v in sorted(unlock.items()))
    html = HTML.read_text()
    html = gen(html, "apps", "\nconst STARTER=" + json.dumps(starter) + ";\nconst A=[\n" + ",\n".join(rows) + "];\n")
    html = gen(html, "unlocks", "const U={" + u + "};")
    icons = {}
    for a in apps:
        f = ICONS / f"{a['id']}.svg"
        if f.exists():  # gradient ids made unique per icon: many inline SVGs share one page
            svg = f.read_text().strip()
            svg = re.sub(r'id="(g\w*)"', lambda m: f'id="{m.group(1)}-{a["id"]}"', svg)
            icons[a["id"]] = re.sub(r"url\(#(g\w*)\)", lambda m: f"url(#{m.group(1)}-{a['id']})", svg)
    html = gen(html, "icons", "const ICON=" + json.dumps(icons, ensure_ascii=False) + ";")
    HTML.write_text(html)
    top = max(unlock)
    gaps = [l for l in range(1, top + 1) if l not in unlock]
    print(f"{len(apps)} apps · unlock table Lv1-{top} · levels without an unlock: {gaps[:40]}{' …' if len(gaps) > 40 else ''}")


if __name__ == "__main__":
    main()
