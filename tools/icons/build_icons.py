#!/usr/bin/env python3
"""App icon set: parody icons drawn in code (48x48 viewBox, rounded square).

Three kinds are drawn here: stock-app pictograms (the pictogram is the canonical icon), letter marks
(one or a few letters on a brand-like colour), and simple shapes. Mascot or illustration icons are
commissioned instead (docs/design/icon-commission.md); until the art arrives they get a code
placeholder marked `placeholder`. Parody rule: recognisable, never identical (letter swaps, shifted
shapes and colours) while hex values and styling follow the real icon as closely as possible.
No emoji or symbol-font glyphs anywhere: every pictogram here is drawn in this file (own work, CC0);
outside pictograms may be used only if CC0. Text is live text for the catalog; Android export outlines it.

    python3 tools/icons/build_icons.py   ->  docs/design/app-icons/<id>.svg + index.json
"""
import json, pathlib

OUT = pathlib.Path(__file__).resolve().parents[2] / "docs/design/app-icons"
FONT = "Noto Sans KR, Pretendard, Arial, sans-serif"
W = "#ffffff"


def bg(fill, grad=None):
    d = ""
    if grad:
        stops = "".join(f'<stop offset="{i / (len(grad) - 1):.2f}" stop-color="{c}"/>' for i, c in enumerate(grad))
        d = f'<defs><linearGradient id="g" x1="0" y1="1" x2="1" y2="0">{stops}</linearGradient></defs>'
        fill = "url(#g)"
    return d + f'<rect width="48" height="48" rx="11" fill="{fill}"/>'


def text(t, color=W, size=22, weight=800, y=None, x=24, spacing=0, family=FONT):
    y = y if y is not None else 24 + size * 0.36
    ls = f' letter-spacing="{spacing}"' if spacing else ""
    return f'<text x="{x}" y="{y:.1f}" text-anchor="middle" font-family="{family}" font-size="{size}" font-weight="{weight}" fill="{color}"{ls}>{t}</text>'


def lg(gid, stops, x1=0, y1=1, x2=1, y2=0):
    st = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<linearGradient id="{gid}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{st}</linearGradient>'


def rg(gid, stops, cx=0.3, cy=1.07, r=1.3):
    st = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<radialGradient id="{gid}" cx="{cx}" cy="{cy}" r="{r}">{st}</radialGradient>'


def defs(*g):
    return "<defs>" + "".join(g) + "</defs>"


SQ = '<rect width="48" height="48" rx="11"'
WHITE_TILE = SQ + ' fill="#ffffff"/>' + SQ + ' fill="none" stroke="#dadce0" stroke-width=".8"/>'


HANDSET = "M17.5 12.5c1-.6 2.2-.3 2.8.7l2 3.4c.5.9.3 2-.5 2.6l-1.6 1.2c1.2 2.6 3.2 4.6 5.8 5.8l1.2-1.6c.6-.8 1.7-1 2.6-.5l3.4 2c1 .6 1.3 1.8.7 2.8l-1.2 2c-.8 1.3-2.4 1.9-3.9 1.4-6.4-2-11.4-7-13.4-13.4-.5-1.5.1-3.1 1.4-3.9z"
BUBBLE = "M24 11c-7.7 0-14 4.9-14 11 0 3.8 2.5 7.2 6.3 9.2L15 37l6.2-3.9c.9.1 1.8.2 2.8.2 7.7 0 14-4.9 14-11s-6.3-11.3-14-11.3z"
PLANE = "M11 23.5 35.5 13c.9-.4 1.8.4 1.5 1.3l-6 21.4c-.3.9-1.4 1.3-2.1.6l-6-4.6-3.2 3.1c-.4.4-1.1.2-1.2-.4l-.6-5.6 11.4-10.2-14 8.6-4.7-1.6c-.9-.3-.9-1.6 0-1.9z"
HEART = "M24 37s-12-7.4-12-15.2C12 17.4 15 15 18.2 15c2.4 0 4.6 1.4 5.8 3.4 1.2-2 3.4-3.4 5.8-3.4 3.2 0 6.2 2.4 6.2 6.8C36 29.6 24 37 24 37z"
FLAME = "M24 38c-6.4 0-10.5-4.4-10.5-10.1 0-4.8 3.2-8 5.5-10.7.5-.6 1.4-.2 1.4.5 0 2 1 3.5 2.4 3.9-.3-4.8 2.1-9 5.6-11.4.6-.4 1.3.1 1.2.8-.6 3.9 1.2 6.2 3.2 8.6 1.6 1.9 2.7 4.3 2.7 7.6C35.5 33.6 30.6 38 24 38z"
NOTE = "M27 10h5c.4 2.9 2.6 5.2 5.5 5.5v5c-2 0-3.9-.6-5.5-1.6V29a8 8 0 1 1-8-8v5a3 3 0 1 0 3 3z"
PIN = "M24 9c-6.1 0-11 4.8-11 10.8C13 28 24 39 24 39s11-11 11-19.2C35 13.8 30.1 9 24 9z"

ICONS = {
    # ---- 기본 앱: pictograms ----
    "alarm": bg("#3a6ff7") + '<circle cx="24" cy="25" r="12.5" fill="#fff"/><path d="M24 17v8.5l5.5 3.5" stroke="#3a6ff7" stroke-width="2.6" fill="none" stroke-linecap="round"/><path d="M12.5 15.5l4-4M35.5 15.5l-4-4" stroke="#fff" stroke-width="3" stroke-linecap="round"/>',
    "weather": bg(None, ["#2f8fe8", "#7cc6ff"]) + '<circle cx="19" cy="19" r="7" fill="#ffd34d"/><path d="M17 35h16a6 6 0 0 0 .6-12 8 8 0 0 0-15.3 2.2A5 5 0 0 0 17 35z" fill="#fff"/>',
    "memo": bg("#f6c445") + '<rect x="13" y="10" width="22" height="28" rx="3" fill="#fff"/><path d="M17 17h14M17 22h14M17 27h9" stroke="#e0a91c" stroke-width="2.4" stroke-linecap="round"/>',
    "flash": bg("#2b2f36") + '<path d="M26.5 8 15 27h8l-2.5 13L33 21h-8z" fill="#ffd60a"/>',
    "calc": bg("#30343b") + "".join(f'<rect x="{10 + c * 10}" y="{10 + r * 10}" width="8" height="8" rx="2" fill="{"#ff9f0a" if c == 2 else "#5b616b"}"/>' for r in range(3) for c in range(3)),
    "recorder": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/><circle cx="24" cy="24" r="8" fill="#ef4444"/><path d="M9 24h3M13 19v10M36 19v10M39 24h-3" stroke="#ef4444" stroke-width="2.4" stroke-linecap="round"/>',
    "phone": bg("#22b35b") + f'<path d="{HANDSET}" fill="#fff"/>',
    "sms": bg("#2f80ed") + f'<path d="{BUBBLE}" fill="#fff"/><circle cx="18" cy="22" r="2" fill="#2f80ed"/><circle cx="24" cy="22" r="2" fill="#2f80ed"/><circle cx="30" cy="22" r="2" fill="#2f80ed"/>',
    "camera": bg("#4b5563") + '<rect x="9" y="15" width="30" height="21" rx="5" fill="#fff"/><path d="M18 15l2-4h8l2 4" fill="#fff"/><circle cx="24" cy="25.5" r="6.5" fill="#4b5563"/><circle cx="24" cy="25.5" r="3.5" fill="#9ca3af"/>',
    "gallery": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/>' + "".join(
        f'<ellipse cx="24" cy="16" rx="5" ry="8" fill="{c}" transform="rotate({a} 24 24)" opacity=".9"/>' for a, c in zip(range(0, 360, 90), ["#ef4444", "#f59e0b", "#22c55e", "#3b82f6"])),
    "contacts": bg("#0ea5e9") + '<circle cx="24" cy="19" r="6.5" fill="#fff"/><path d="M12 37c1.2-6.4 6-10 12-10s10.8 3.6 12 10z" fill="#fff"/>',
    "settings": bg("#6b7280") + '<circle cx="24" cy="24" r="9" fill="none" stroke="#fff" stroke-width="5" stroke-dasharray="4.7 2.4"/><circle cx="24" cy="24" r="4" fill="#fff"/>',
    # ---- 아시아권 ----
    "music": bg("#00c73c") + '<circle cx="24" cy="26" r="11" fill="#fff"/><path d="M24 15c1-4 4-6 8-6-1 4-4 6-8 6z" fill="#b6f5c9"/><path d="M21 21v10l8-5z" fill="#00c73c"/>',
    "pay": bg("#0050ff") + text("t", size=34, weight=900, y=35, family="Arial Black, Arial, sans-serif"),
    "ctaxi": bg("#ffe100") + text("T", "#191919", size=32, weight=900, y=35, family="Arial Black, Arial, sans-serif"),
    "food": bg("#2ac1bc") + text("배다른", size=12.5, weight=900, y=22) + text("민족", size=12.5, weight=900, y=36),
    "mall": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/>' + "".join(
        f'<text x="{x}" y="30" text-anchor="middle" font-family="{FONT}" font-size="15" font-weight="900" fill="{c}">{ch}</text>' for x, ch, c in [(17, "쿠", "#e31837"), (31, "빵", "#f5a623")]),
    "portal": bg("#03c75a") + text("N", size=30, weight=900, y=35, family="Arial Black, Arial, sans-serif"),
    "superapp": bg("#07c160") + '<ellipse cx="20" cy="21" rx="10" ry="8.5" fill="#fff"/><ellipse cx="29.5" cy="28.5" rx="8.5" ry="7" fill="#e8fbe9"/><circle cx="17" cy="20" r="1.4" fill="#07c160"/><circle cx="23" cy="20" r="1.4" fill="#07c160"/><circle cx="27" cy="28" r="1.2" fill="#07c160"/><circle cx="32" cy="28" r="1.2" fill="#07c160"/>',
    "line": bg("#06c755") + f'<path d="{BUBBLE}" fill="#fff"/>' + text("LAING", "#06c755", size=7.6, weight=900, y=24.6, family="Arial Black, Arial, sans-serif"),
    "dance": bg("#0b0b0b") + f'<path d="{NOTE}" fill="#25f4ee" transform="translate(-1.6 -1.2)"/><path d="{NOTE}" fill="#fe2c55" transform="translate(1.6 1.2)"/><path d="{NOTE}" fill="#fff"/>',
    "chat": bg("#fee500") + f'<path d="{BUBBLE}" fill="#3c1e1e"/>' + text("TOK", "#fee500", size=9, weight=900, y=25.2, family="Arial Black, Arial, sans-serif"),
    # ---- 동남아권 ----
    "zalo": bg("#0068ff") + f'<path d="{BUBBLE}" fill="#fff"/>' + text("Zolo", "#0068ff", size=9.5, weight=900, y=25.4, family="Arial Black, Arial, sans-serif"),
    "momo": bg("#a50064") + text("mo", size=13, weight=900, y=22, family="Arial Black, Arial, sans-serif") + text("mom", size=13, weight=900, y=35, family="Arial Black, Arial, sans-serif"),
    "glints": '<rect width="48" height="48" rx="11" fill="#fff4d6"/><circle cx="24" cy="24" r="15" fill="#f9b707"/>' + text("g", size=26, weight=900, y=31),
    "bigo": bg("#00c8f8") + text("bigoo", size=11, weight=900, y=28, family="Arial Black, Arial, sans-serif"),
    "joox": bg("#00c86f") + text("J", size=30, weight=900, y=35, x=21, family="Arial Black, Arial, sans-serif") + '<circle cx="32" cy="14" r="3" fill="#fff"/>',
    "agoda": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/>' + "".join(
        f'<circle cx="{11 + i * 6.5}" cy="34" r="2.6" fill="{c}"/>' for i, c in enumerate(["#e93b3b", "#f6a623", "#7ac143", "#5392f9", "#9b59b6"])) + text("a", "#222", size=24, weight=800, y=26),
    "michat": bg("#ff6f61") + f'<path d="{BUBBLE}" fill="#fff"/>' + text("m", "#ff6f61", size=17, weight=900, y=27.5),
    "lazada": bg(None, ["#f1146d", "#ff7a1a"]) + f'<path d="{HEART}" fill="#fff"/><path d="M18 21h12l-1.2 9h-9.6z" fill="#f1146d"/>',
    "jobs": bg("#0d3880") + text("J", size=26, weight=900, y=33, x=20, family="Arial Black, Arial, sans-serif") + '<rect x="27" y="22" width="12" height="9" rx="2" fill="#fff"/><path d="M30 22v-2h6v2" stroke="#fff" stroke-width="1.6" fill="none"/>',
    "grab": bg("#00b14f") + text("Grabb", size=12, weight=900, y=28.5, family="Arial Black, Arial, sans-serif"),
    "shopee": bg("#ee4d2d") + '<path d="M13 18h22l-2 19H15z" fill="#fff"/><path d="M19 18a5 5 0 0 1 10 0" stroke="#fff" stroke-width="2.4" fill="none"/>' + text("S", "#ee4d2d", size=13, weight=900, y=32),
    # ---- 서양권 ----
    "airbnb": bg("#ff5a5f") + '<path d="M24 11c-2 0-3.2 1.6-4.3 4L13.5 29c-1.6 3.7.8 7 4.3 7 2.4 0 4.3-1.5 6.2-3.8 1.9 2.3 3.8 3.8 6.2 3.8 3.5 0 5.9-3.3 4.3-7L28.3 15c-1.1-2.4-2.3-4-4.3-4zm0 19c-2.2-2.8-3.6-5.2-3.6-7a3.6 3.6 0 0 1 7.2 0c0 1.8-1.4 4.2-3.6 7z" fill="#fff"/>',
    "pin": bg("#e60023") + '<circle cx="24" cy="24" r="14" fill="#fff"/>' + text("P", "#e60023", size=22, weight=900, y=32, family="Georgia, serif") + '<path d="M22 31l-2 8" stroke="#e60023" stroke-width="2.4" stroke-linecap="round"/>',
    "meet": bg("#2d8cff") + '<rect x="10" y="17" width="20" height="14" rx="4" fill="#fff"/><path d="M31 21.5l7-4v13l-7-4z" fill="#fff"/>',
    "career": bg("#0a66c2") + text("out", size=15, weight=900, y=29.5, family="Arial Black, Arial, sans-serif"),
    "paypal": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/>' + text("P", "#009cde", size=28, weight=900, y=35, x=27, family="Arial Black, Arial, sans-serif") + text("P", "#003087", size=28, weight=900, y=32, x=22, family="Arial Black, Arial, sans-serif"),
    "match": bg(None, ["#fd297b", "#ff655b"]) + f'<path d="{FLAME}" fill="#fff"/>',
    "ride": bg("#000000") + text("Uba", size=16, weight=800, y=29.5),
    "beat": bg("#121212") + '<circle cx="24" cy="24" r="15" fill="#1ed760"/><path d="M15 19.5c6-2 12.5-1.4 18 1.6M16 24.6c5-1.5 10.4-1 14.8 1.4M17 29.4c4-1.1 8.2-.7 11.6 1.1" stroke="#121212" stroke-width="2.6" fill="none" stroke-linecap="round"/><circle cx="33.5" cy="31" r="2" fill="#121212"/>',  # 음표 점
    "why": bg("#000000") + '<path d="M13 11h6l5 9 5-9h6l-8.5 14V37h-5V25z" fill="#fff"/>',
    # ---- 글로벌 ----
    "mega": bg("#131a22") + text("a", size=26, weight=800, y=28) + '<path d="M13 32c7 4 15 4 22 0" stroke="#ff9900" stroke-width="2.6" fill="none" stroke-linecap="round"/><path d="M15 29.5l-2.6 2.5 3.4 1" stroke="#ff9900" stroke-width="2.2" fill="none" stroke-linecap="round"/>',  # 화살표 반대
    "ott": bg("#000000") + '<path d="M15 7h6l6.5 19V7h5.5v28h-6L20.5 18v17H15z" fill="#e50914"/><path d="M19.5 35v6M28.5 35v6" stroke="#c9ced6" stroke-width="2.6" stroke-linecap="round"/>',  # 플러그 핀
    "secret": bg(None, ["#1d93d2", "#38b0e3"]) + f'<path d="{PLANE}" fill="#fff" transform="translate(2 -2)"/><path d="M6 38c4-1 7-3 9-6" stroke="#fff" stroke-width="1.8" stroke-dasharray="2 2.4" fill="none" stroke-linecap="round"/>',  # 궤적
    "mail": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/><path d="M10 15v18h5V21l9 7 9-7v12h5V15l-14 10.5z" fill="#ea4335"/><path d="M10 15v18h5V19z" fill="#4285f4"/><path d="M33 19v14h5V15z" fill="#34a853"/><path d="M38 15l-5 4" stroke="#fbbc04" stroke-width="4"/><circle cx="12.5" cy="12" r="3.2" fill="#9ca3af"/><circle cx="35.5" cy="12" r="3.2" fill="#9ca3af"/><circle cx="12.5" cy="12" r="1.5" fill="#f9a8d4"/><circle cx="35.5" cy="12" r="1.5" fill="#f9a8d4"/>',  # 쥐 귀
    "maps": bg("#ffffff") + f'<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/><path d="{PIN}" fill="#34a853"/><path d="M24 9c-6.1 0-11 4.8-11 10.8 0 1.6.4 3.2 1 4.8L24 15z" fill="#4285f4"/><path d="M24 9c3.5 0 6.6 1.6 8.6 4.1L24 19.8z" fill="#ea4335"/><circle cx="24" cy="20" r="4" fill="#fff"/>',
    "store": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/><path d="M15 10.5 30 24 15 37.5z" fill="#00a173"/><path d="M15 10.5 30 24l5-4.5z" fill="#4285f4"/><path d="M15 37.5 30 24l5 4.5z" fill="#ea4335"/><path d="M30 24l5-4.5 3 2c1.4 1 1.4 3 0 4l-3 3z" fill="#fbbc04"/>',
    "tube": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/><rect x="7" y="13" width="34" height="23" rx="7" fill="#ff0000"/>' + text("너", size=15, weight=900, y=30),
    "photo": bg(None, ["#feda75", "#fa7e1e", "#d62976", "#962fbf", "#4f5bd5"]) + '<rect x="12" y="12" width="24" height="24" rx="7" fill="none" stroke="#fff" stroke-width="3"/><circle cx="24" cy="24" r="5.5" fill="none" stroke="#fff" stroke-width="3"/><path d="M31.5 13.5l.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9z" fill="#fff"/>',  # 반짝이
    "feed": bg("#1877f2") + text("t", size=34, weight=900, y=40, x=26, family="Arial Black, Arial, sans-serif"),
    "green": bg("#25d366") + '<g transform="matrix(-1 0 0 1 48 0)"><path d="M24 10a14 14 0 0 0-12 21.2L10 38l7-1.9A14 14 0 1 0 24 10z" fill="#fff"/></g>' + f'<path d="{HANDSET}" fill="#25d366" transform="translate(6.2 6.4) scale(.74)"/>',  # 꼬리 반대
    "search": bg("#ffffff") + '<rect width="48" height="48" rx="11" fill="none" stroke="#e5e7eb"/>' + "".join(
        f'<circle cx="24" cy="24" r="10" fill="none" stroke="{c}" stroke-width="5" stroke-dasharray="{d}" stroke-dashoffset="{o}" transform="rotate(-40 24 24)"/>'
        for c, d, o in [("#ea4335", "16 47", "0"), ("#fbbc04", "12 51", "-16"), ("#34a853", "16 47", "-28"), ("#4285f4", "12 51", "-44")]) + '<path d="M24 23.5h10" stroke="#4285f4" stroke-width="5"/>',
}


# Brand-accurate restyling: real hex values and each icon's own treatment (ribbon shading, radial
# gradients, four-colour splits). Parody twists stay; only the look moves closer to the original.
ICONS.update({
    # Netflix: black tile, N built from two dark side ribbons and a bright diagonal with a shadow gradient
    "ott": defs(lg("gN", [(0, "#b20710"), (1, "#e50914")], 0, 0, 1, 1)) + bg("#000000") +
        '<path d="M15 8h6v26.5l-6 .9z" fill="#b1060f"/><path d="M27 8h6v26.9l-6-.9z" fill="#b1060f"/>'
        '<path d="M15 8h6l12 27.4c-1.9-.3-4-.5-6-.6z" fill="url(#gN)"/>'
        '<path d="M19.5 37v4.5M28.5 37v4.5" stroke="#c9ced6" stroke-width="2.4" stroke-linecap="round"/>',
    # Instagram: radial gradient tile #feda75 #fa7e1e #d62976 #962fbf #4f5bd5
    "photo": defs(rg("gI", [(0, "#fdf497"), (.05, "#fdf497"), (.45, "#fd5949"), (.6, "#d6249f"), (.9, "#285aeb")])) +
        SQ + ' fill="url(#gI)"/>' +
        '<rect x="12" y="12" width="24" height="24" rx="7" fill="none" stroke="#fff" stroke-width="3"/><circle cx="24" cy="24" r="5.5" fill="none" stroke="#fff" stroke-width="3"/>'
        '<path d="M31.5 13.5l.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9z" fill="#fff"/>',
    # Telegram: white tile, blue gradient disc #2aabee -> #229ed9
    "secret": defs(lg("gT", [(0, "#2aabee"), (1, "#229ed9")], 0, 0, 0, 1)) + WHITE_TILE +
        '<circle cx="24" cy="24" r="17" fill="url(#gT)"/>' + f'<path d="{PLANE}" fill="#fff" transform="translate(4.2 3) scale(.8)"/>'
        '<path d="M9.5 33.5c3-.6 5.2-2 6.6-4" stroke="#fff" stroke-width="1.6" stroke-dasharray="1.8 2" fill="none" stroke-linecap="round"/>',
    # Facebook 2023: #0866ff disc on white, lowercase t (parody of f)
    "feed": WHITE_TILE + '<circle cx="24" cy="24" r="17" fill="#0866ff"/>' + text("t", size=28, weight=900, y=37, x=25.5, family="Arial Black, Arial, sans-serif"),
    # WhatsApp: #25d366 tile, white bubble with tail mirrored, handset #25d366
    "green": bg("#25d366") + '<g transform="matrix(-1 0 0 1 48 0)"><path d="M24 10a14 14 0 0 0-12 21.2L10 38l7-1.9A14 14 0 1 0 24 10z" fill="#fff"/></g>' +
        f'<path d="{HANDSET}" fill="#25d366" transform="translate(6.2 6.4) scale(.74)"/>',
    # YouTube: white tile, #ff0000 rounded tube, parody "너"
    "tube": WHITE_TILE + '<rect x="7" y="13" width="34" height="23" rx="7.5" fill="#ff0000"/>' + text("너", size=15, weight=900, y=30),
    # Gmail 2020: M in #4285f4 #34a853 #ea4335 #fbbc04 #c5221f, plus mouse ears
    "mail": WHITE_TILE +
        '<path d="M10.5 17.5v15.5c0 1 .8 1.8 1.8 1.8H16V22.5z" fill="#4285f4"/><path d="M32 22.5v12.3h3.7c1 0 1.8-.8 1.8-1.8V17.5z" fill="#34a853"/>'
        '<path d="M16 22.5V14l8 6 8-6v8.5l-8 6z" fill="#ea4335"/><path d="M32 14v8.5l5.5-4.1V16c0-2.2-2.5-3.4-4.2-2.1z" fill="#fbbc04"/>'
        '<path d="M10.5 16v1.5l5.5 5V14l-1.3-1c-1.7-1.3-4.2-.1-4.2 2.1z" fill="#c5221f"/>'
        '<circle cx="11.5" cy="10.5" r="3" fill="#9aa0a6"/><circle cx="36.5" cy="10.5" r="3" fill="#9aa0a6"/><circle cx="11.5" cy="10.5" r="1.4" fill="#f9a8d4"/><circle cx="36.5" cy="10.5" r="1.4" fill="#f9a8d4"/>',
    # Google Maps 2020 pin: #34a853 body, #fbbc04 #4285f4 #ea4335 #1a73e8 segments
    "maps": WHITE_TILE + f'<path d="{PIN}" fill="#34a853"/>'
        '<path d="M24 9c-3.3 0-6.3 1.4-8.3 3.7L24 21z" fill="#1a73e8"/><path d="M15.7 12.7A10.7 10.7 0 0 0 13 19.8c0 2.2.6 4.2 1.6 6.2L24 21z" fill="#ea4335"/>'
        '<path d="M24 9c3.5 0 6.6 1.6 8.6 4.1L24 21z" fill="#4285f4"/><path d="M14.6 26c1.4 2.8 3.5 5.6 5.3 7.7L24 21z" fill="#fbbc04"/><circle cx="24" cy="19.5" r="3.6" fill="#fff"/>',
    # Google Play 2022: four-colour triangle #00d7fe #00f076 #ffd109 #ff3a44 family -> brand #4285f4 #34a853 #fbbc04 #ea4335
    "store": WHITE_TILE + '<path d="M14 10.6c-.3.4-.5 1-.5 1.6v23.6c0 .6.2 1.2.5 1.6L27.3 24z" fill="#4285f4"/>'
        '<path d="M31.7 28.4 27.3 24 14 37.4c.6.6 1.6.7 2.4.2z" fill="#ea4335"/><path d="M31.7 19.6 16.4 10.4c-.8-.5-1.8-.4-2.4.2L27.3 24z" fill="#34a853"/>'
        '<path d="m31.7 19.6-4.4 4.4 4.4 4.4 5.2-3c1.4-.8 1.4-2.8 0-3.6z" fill="#fbbc04"/>',
    # Google G: #ea4335 top, #fbbc05 left, #34a853 bottom, #4285f4 right with bar (lowercase g parody kept)
    "search": WHITE_TILE +
        '<path d="M34.6 22.2H24v4.3h6.1a6.6 6.6 0 0 1-2.8 4.2v3.5h4.6c2.7-2.5 4.2-6.1 4.2-10.4 0-.5 0-1.1-.1-1.6z" fill="#4285f4"/>'
        '<path d="M24 37.5c3.8 0 7-1.3 9.3-3.4l-4.6-3.5c-1.2.8-2.8 1.3-4.7 1.3-3.6 0-6.7-2.4-7.8-5.7h-4.7v3.6A14 14 0 0 0 24 37.5z" fill="#34a853"/>'
        '<path d="M16.2 26.2a8.4 8.4 0 0 1 0-5.4v-3.6h-4.7a14 14 0 0 0 0 12.6z" fill="#fbbc05"/>'
        '<path d="M24 15.6c2.1 0 3.9.7 5.4 2.1l4-4A14 14 0 0 0 11.5 17.2l4.7 3.6c1.1-3.3 4.2-5.2 7.8-5.2z" fill="#ea4335"/>',
    # Amazon shopping: #232f3e tile, white a, #ff9900 smile with arrow reversed
    "mega": bg("#232f3e") + text("a", size=26, weight=800, y=28) +
        '<path d="M13 32c7 4 15 4 22 0" stroke="#ff9900" stroke-width="2.6" fill="none" stroke-linecap="round"/><path d="M15 29.5l-2.6 2.5 3.4 1" stroke="#ff9900" stroke-width="2.2" fill="none" stroke-linecap="round"/>',
    # Spotify: #121212 tile, #1ed760 disc, three arcs + note dot
    "beat": bg("#121212") + '<circle cx="24" cy="24" r="15.5" fill="#1ed760"/><path d="M15 19.5c6-2 12.5-1.4 18 1.6M16 24.6c5-1.5 10.4-1 14.8 1.4M17 29.4c4-1.1 8.2-.7 11.6 1.1" stroke="#121212" stroke-width="2.6" fill="none" stroke-linecap="round"/><circle cx="33.5" cy="31" r="2" fill="#121212"/>',
    # TikTok: #000 tile, cyan #25f4ee and red #fe2c55 offsets
    "dance": bg("#000000") + f'<path d="{NOTE}" fill="#25f4ee" transform="translate(-1.6 -1.2)"/><path d="{NOTE}" fill="#fe2c55" transform="translate(1.6 1.2)"/><path d="{NOTE}" fill="#fff"/>',
    # KakaoTalk: #fee500 tile, bubble #3a1d1d, TOK in #fee500
    "chat": bg("#fee500") + f'<path d="{BUBBLE}" fill="#3a1d1d"/>' + text("TOK", "#fee500", size=9, weight=900, y=25.2, family="Arial Black, Arial, sans-serif"),
    # Tinder: white tile, gradient flame #fd267a -> #ff7854
    "match": defs(lg("gF", [(0, "#fd267a"), (1, "#ff7854")], 0, 1, 1, 0)) + WHITE_TILE + f'<path d="{FLAME}" fill="url(#gF)"/>',
    # Pinterest: white tile, #e60023 disc, white P with pin tail
    "pin": WHITE_TILE + '<circle cx="24" cy="24" r="16" fill="#e60023"/>' + text("P", size=22, weight=900, y=32, family="Georgia, serif") +
        '<path d="M22 31l-2 8" stroke="#fff" stroke-width="2.4" stroke-linecap="round"/>',
    # Zoom 2023: #0b5cff tile, white camera
    "meet": bg("#0b5cff") + '<rect x="10" y="17" width="20" height="14" rx="4" fill="#fff"/><path d="M31 21.5l7-4v13l-7-4z" fill="#fff"/>',
    # Airbnb Rausch #ff385c
    "airbnb": bg("#ff385c") + '<path d="M24 11c-2 0-3.2 1.6-4.3 4L13.5 29c-1.6 3.7.8 7 4.3 7 2.4 0 4.3-1.5 6.2-3.8 1.9 2.3 3.8 3.8 6.2 3.8 3.5 0 5.9-3.3 4.3-7L28.3 15c-1.1-2.4-2.3-4-4.3-4zm0 19c-2.2-2.8-3.6-5.2-3.6-7a3.6 3.6 0 0 1 7.2 0c0 1.8-1.4 4.2-3.6 7z" fill="#fff"/>',
    # PayPal: white tile, #003087 and #009cde overlapping P, #012169 shadow
    "paypal": WHITE_TILE + text("P", "#009cde", size=28, weight=900, y=35, x=27, family="Arial Black, Arial, sans-serif") +
        text("P", "#012169", size=28, weight=900, y=33, x=23, family="Arial Black, Arial, sans-serif") + text("P", "#003087", size=28, weight=900, y=32, x=22, family="Arial Black, Arial, sans-serif"),
    # Lazada heart gradient #f57224 -> #f70b7b -> #3e1c96
    "lazada": defs(lg("gL", [(0, "#f57224"), (.5, "#f70b7b"), (1, "#3e1c96")], 0, 1, 1, 0)) + SQ + ' fill="url(#gL)"/>' +
        f'<path d="{HEART}" fill="#fff"/><path d="M18 21h12l-1.2 9h-9.6z" fill="#f70b7b"/>',
    # Shopee #ee4d2d with bag gradient
    "shopee": defs(lg("gS", [(0, "#f53d2d"), (1, "#ff6633")], 0, 0, 0, 1)) + SQ + ' fill="url(#gS)"/>' +
        '<path d="M13 18h22l-2 19H15z" fill="#fff"/><path d="M19 18a5 5 0 0 1 10 0" stroke="#fff" stroke-width="2.4" fill="none"/>' + text("S", "#ee4d2d", size=13, weight=900, y=32),
    # WeChat #07c160 / #2dc100
    "superapp": defs(lg("gW", [(0, "#2dc100"), (1, "#07c160")], 0, 0, 0, 1)) + SQ + ' fill="url(#gW)"/>' +
        '<ellipse cx="20" cy="21" rx="10" ry="8.5" fill="#fff"/><ellipse cx="29.5" cy="28.5" rx="8.5" ry="7" fill="#e8fbe9"/><circle cx="17" cy="20" r="1.4" fill="#07c160"/><circle cx="23" cy="20" r="1.4" fill="#07c160"/><circle cx="27" cy="28" r="1.2" fill="#07c160"/><circle cx="32" cy="28" r="1.2" fill="#07c160"/>',
    # Naver #03c75a, Toss #0064ff, Baemin #2ac1bc, Melon #00cd3c, Coupang wordmark colours, Kakao T #fee500 on #191919
    "portal": bg("#03c75a") + '<path d="M14.5 13h7l5 9.5V13h7v22h-7l-5-9.5V35h-7z" fill="#fff"/>',
    "pay": defs(lg("gP", [(0, "#0064ff"), (1, "#3182f6")], 0, 0, 0, 1)) + SQ + ' fill="url(#gP)"/>' + text("t", size=34, weight=900, y=35, family="Arial Black, Arial, sans-serif"),
    "music": bg("#00cd3c") + '<circle cx="24" cy="26" r="11" fill="#fff"/><path d="M24 15c1-4 4-6 8-6-1 4-4 6-8 6z" fill="#b6f5c9"/><path d="M21 21v10l8-5z" fill="#00cd3c"/>',
    "ctaxi": bg("#191919") + '<rect x="8" y="8" width="32" height="32" rx="9" fill="#fee500"/>' + text("T", "#191919", size=26, weight=900, y=33, family="Arial Black, Arial, sans-serif"),
    "mall": WHITE_TILE + "".join(f'<text x="{x}" y="31" text-anchor="middle" font-family="{FONT}" font-size="15" font-weight="900" fill="{c}">{ch}</text>'
        for x, ch, c in [(16.5, "쿠", "#e52528"), (31.5, "빵", "#f4a21b")]) + '<path d="M10 35h28" stroke="#5b7bd5" stroke-width="2" stroke-linecap="round"/>',
    # Zoom-like flat brands
    "career": bg("#0a66c2") + text("out", size=15, weight=900, y=29.5, family="Arial Black, Arial, sans-serif"),
    "ride": bg("#000000") + text("Uba", size=16, weight=800, y=29.5),
    "why": bg("#000000") + '<path d="M13 11h6l5 9 5-9h6l-8.5 14V37h-5V25z" fill="#fff"/>',
    "grab": bg("#00b14f") + text("Grabb", size=12, weight=900, y=28.5, family="Arial Black, Arial, sans-serif"),
    "line": bg("#06c755") + f'<path d="{BUBBLE}" fill="#fff"/>' + text("LAING", "#06c755", size=7.6, weight=900, y=24.6, family="Arial Black, Arial, sans-serif"),
    "zalo": bg("#0068ff") + f'<path d="{BUBBLE}" fill="#fff"/>' + text("Zolo", "#0068ff", size=9.5, weight=900, y=25.4, family="Arial Black, Arial, sans-serif"),
    "momo": bg("#a50064") + text("mo", size=13, weight=900, y=22, family="Arial Black, Arial, sans-serif") + text("mom", size=13, weight=900, y=35, family="Arial Black, Arial, sans-serif"),
    "food": bg("#2ac1bc") + text("배다른", size=12.5, weight=900, y=22) + text("민족", size=12.5, weight=900, y=36),
})

# Mascot / illustration icons: commissioned (docs/design/icon-commission.md). Placeholders until delivery.
COMMISSION = {
    "papago": ("#03c75a", "마"), "duo": ("#58cc02", "듀"), "edit": ("#111111", "컷"), "travel": ("#0194f3", "트"),
    "reddit": ("#ff4500", "래"), "robin": ("#0b0b0b", "로"), "ai": ("#000000", "챗"),
}
for k, (c, ch) in COMMISSION.items():
    ICONS[k] = bg(c) + text(ch, size=20, weight=900, y=31) + '<rect x="3" y="3" width="42" height="42" rx="9" fill="none" stroke="#fff" stroke-width="1.5" stroke-dasharray="3 3" opacity=".7"/>'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    index = {}
    for k, body in ICONS.items():
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">{body}</svg>'
        (OUT / f"{k}.svg").write_text(svg + "\n")
        index[k] = {"kind": "placeholder" if k in COMMISSION else "code"}
    (OUT / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1) + "\n")
    print(len(ICONS), "icons,", len(COMMISSION), "placeholders")


if __name__ == "__main__":
    main()
