#!/usr/bin/env python3
"""Outline the few glyphs the code icons letter with, so icons never depend on an installed font.

Fonts (all SIL Open Font License 1.1, from the @fontsource npm packages):
  dohyeon   Do Hyeon (Woowahan Brothers' BM Hanna lineage, Google Fonts)  -> 배다른민족 lettering
  nunito900 Nunito Black                                                  -> 쿠빵, 모모모 rounded wordmarks
  inter700  Inter Bold                                                    -> 링크드아웃 'out', 음성 녹음 'REC'
Usage: python3 tools/icons/outline_glyphs.py <dir with unpacked @fontsource packages>
Writes tools/icons/glyphs.json: {font: {"upm": n, "glyphs": {char: {"d": path in font units (y up), "adv": n}}}}
"""
import glob, json, pathlib, sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen

WANT = {
    "dohyeon": ("fontsource-do-hyeon-*/package/files/do-hyeon-*-400-normal.woff2", "배다민"),
    "nunito900": ("fontsource-nunito-*/package/files/nunito-latin-900-normal.woff2", "kubangmo"),
    "inter700": ("fontsource-inter-*/package/files/inter-latin-700-normal.woff2", "outREC"),
}


def main(src):
    out = {}
    for key, (pattern, chars) in WANT.items():
        entry = {"glyphs": {}}
        for f in sorted(glob.glob(str(pathlib.Path(src) / pattern))):
            font = TTFont(f)
            cmap, gs = font.getBestCmap(), font.getGlyphSet()
            entry["upm"] = font["head"].unitsPerEm
            for ch in chars:
                if ch in entry["glyphs"] or ord(ch) not in cmap:
                    continue
                name = cmap[ord(ch)]
                pen = SVGPathPen(gs)
                gs[name].draw(pen)
                entry["glyphs"][ch] = {"d": pen.getCommands(), "adv": font["hmtx"][name][0]}
        missing = [c for c in chars if c not in entry["glyphs"]]
        assert not missing, (key, missing)
        out[key] = entry
    dst = pathlib.Path(__file__).with_name("glyphs.json")
    dst.write_text(json.dumps(out, ensure_ascii=False) + "\n")
    print("wrote", dst, {k: len(v["glyphs"]) for k, v in out.items()})


if __name__ == "__main__":
    main(sys.argv[1])
