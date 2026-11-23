#!/usr/bin/env python3
"""Turn delivered icon art (docs/design/app-icons/raw/<id>.png, foreground on transparency) into
432 px adaptive-icon foregrounds (<id>-fg.png): optional fringe cleanup, then scaled and centred so
the art sits inside the 66% safe circle. Backgrounds are solid colours in BG below.
    python3 tools/icons/import_assets.py
"""
import pathlib
import numpy as np
from PIL import Image

DIR = pathlib.Path(__file__).resolve().parents[2] / "docs/design/app-icons"
SIZE, SAFE = 432, 0.66
BG = {"papago": "#E9FBEF", "duo": "#FFFFFF", "reddit": "#FF4500", "robin": "#0B0B0B", "ai": "#000000",
      "music": "#00CD3C", "jobs": "#0D3880", "glints": "#FF5C35"}
# Delivered with a noisy semi-transparent halo: keep only near-opaque pixels, then soften one pixel.
CLEAN = {"papago": 200}


def process(key):
    im = Image.open(DIR / "raw" / f"{key}.png").convert("RGBA")
    a = np.array(im)
    if key in CLEAN:
        al = a[..., 3]
        a[..., 3] = np.where(al >= CLEAN[key], 255, 0).astype(np.uint8)
        im = Image.fromarray(a)
        im.putalpha(im.getchannel("A").resize((im.width // 2, im.height // 2), Image.LANCZOS).resize(im.size, Image.LANCZOS))
        a = np.array(im)
    ys, xs = np.where(a[..., 3] > 40)
    cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
    r = (((xs - cx) ** 2 + (ys - cy) ** 2) ** .5).max()
    scale = (SAFE * SIZE / 2) / r
    big = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.alpha_composite(big, (round(SIZE / 2 - cx * scale), round(SIZE / 2 - cy * scale)))
    out.save(DIR / f"{key}-fg.png", optimize=True)
    return scale


if __name__ == "__main__":
    for k in BG:
        print(k, f"{process(k):.3f}")
