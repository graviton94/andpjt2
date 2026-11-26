"""Keep only the Noto Sans TC (900) chunks that cover the CJK text used in icon.html and feature.html.
Usage: python3 tools/brand/subset_tc.py <unpacked @fontsource/noto-sans-tc package dir>"""
import re, sys, shutil, pathlib
here = pathlib.Path(__file__).parent
src, dst = pathlib.Path(sys.argv[1]), here / "fonts/noto-sans-tc"
text = {c for f in ("icon.html", "feature.html") for c in (here / f).read_text() if ord(c) > 0x2E80}
def covers(rng):
    for part in rng.split(","):
        part = part.strip().replace("U+", "")
        a, b = (int(x, 16) for x in part.split("-")) if "-" in part else (int(part, 16),) * 2
        if any(a <= ord(c) <= b for c in text): return True
shutil.rmtree(dst / "files", ignore_errors=True); (dst / "files").mkdir(parents=True)
keep = []
for b in re.findall(r"/\*.*?\*/\s*@font-face\s*\{.*?\}", (src / "900.css").read_text(), re.S):
    m = re.search(r"unicode-range:\s*([^;]+);", b)
    if m and covers(m.group(1)):
        b = re.sub(r", url\([^)]*\.woff\) format\('woff'\)", "", b)
        f = re.search(r"url\(\./files/([^)]+\.woff2)\)", b).group(1)
        shutil.copy(src / "files" / f, dst / "files" / f); keep.append(b)
(dst / "index.css").write_text("\n".join(keep) + "\n"); print(len(keep), "chunks for", len(text), "chars")
