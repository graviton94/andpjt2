"""Builds the in-app game page: app/src/main/assets/game/ (index.html + icons + fonts + images).
The page is the approved FX demo (docs/design/fx-demo) driven by the core engine through window.Game or the dev server.
  python3 tools/game-page/build.py
  gradle -p core run --args="serve 8765 ../app/src/main/assets/game"   # play it in a browser
"""
import json, pathlib, re, shutil
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/game"
FX = ROOT / "docs/design/fx-demo"

colors = {m[0]: "#" + m[1][2:] for m in re.findall(r'"([a-z0-9]+)" to AppStyle\("[^"]*", Color\(0xFF([0-9A-Fa-f]{6})\)\)', (ROOT / "app/src/main/java/com/graviton94/notificationcleaner/ui/AppStyle.kt").read_text())}
colors = {k: "#" + v for k, v in re.findall(r'"([a-z0-9]+)" to AppStyle\("[^"]*", Color\(0xFF([0-9A-Fa-f]{6})\)\)', (ROOT / "app/src/main/java/com/graviton94/notificationcleaner/ui/AppStyle.kt").read_text())}
page = (ROOT / "tools/game-page/game.tpl.html").read_text()
texts = {k: [v[3], v[4]] for k, v in json.loads((ROOT / "docs/content/app-texts.json").read_text()).items()}
page = page.replace("__COLORS__", json.dumps(colors)).replace("__TEXTS__", json.dumps(texts, ensure_ascii=False)).replace("__HANDSET__", repr((FX / "handset.b64").read_text().strip())).replace("__PHONE__", (FX / "phone-sfx.json").read_text())
assert "__" not in re.sub(r"__(proto|dirname)__", "", page.split("<script>")[1])[:0] + "", "placeholder left"
if OUT.exists(): shutil.rmtree(OUT)
(OUT / "icons").mkdir(parents=True)
(OUT / "index.html").write_text(page)
for f in (ROOT / "docs/design/app-icons").glob("*.svg"): shutil.copy(f, OUT / "icons" / f.name)
shutil.copytree(ROOT / "tools/brand/fonts/black-han-sans", OUT / "fonts/black-han-sans")
(OUT / "img").mkdir()
shutil.copy(ROOT / "docs/design/ui-assets/tutorial/hand-swipe.png", OUT / "img/hand-swipe.png")
shutil.copytree(ROOT / "docs/design/bm-screens/img", OUT / "img/bm")
print("built", OUT, len(page), "bytes,", len(colors), "colours,", len(list((OUT / "icons").glob("*.svg"))), "icons")
