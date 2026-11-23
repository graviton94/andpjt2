"""Cuts a delivered sprite sheet into the commission's items.

Usage: python3 tools/commissions/slice_sheet.py <group id> <sheet.png>
The sheet is split into the group's grid (any resolution, same aspect). In each cell the background is keyed out
(real alpha, else flat #FF00FF, else the corner colour), the item is cropped to its bounding box and fitted into the
item's output size inside an 80% safe area. Writes docs/design/ui-assets/<group>/<id>.png and a contact sheet.
"""
import json, pathlib, sys
import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
gid, src = sys.argv[1], sys.argv[2]
d = json.loads((ROOT / "docs/design/commissions.json").read_text())
g = next(x for x in d["pending"] + d.get("deliveredSheets", []) if x["id"] == gid)
sheet = Image.open(src).convert("RGBA")
a = np.asarray(sheet).astype(np.int16)

def key(cell):
    alpha = cell[..., 3]
    if (alpha < 250).mean() > 0.2:  # real transparency; drop the faint glow haze some generators add
        return np.where(alpha < 16, 0, alpha).astype(np.uint8)
    rgb = cell[..., :3]
    corners = np.array([rgb[0, 0], rgb[0, -1], rgb[-1, 0], rgb[-1, -1]])
    bg = np.array([255, 0, 255]) if np.abs(corners - [255, 0, 255]).sum(1).min() < 90 else np.median(corners, 0)
    dist = np.abs(rgb - bg).sum(-1)
    return np.clip((dist - 40) * 4, 0, 255).astype(np.uint8)

def main_run(mask, axis):
    """Rows (axis=1) or columns (axis=0) of the biggest contiguous block, so strays from neighbouring cells drop out."""
    prof = mask.sum(axis)
    runs, start = [], None
    for i, v in enumerate(list(prof) + [0]):
        if v and start is None: start = i
        if not v and start is not None: runs.append((start, i)); start = None
    return max(runs, key=lambda r: prof[r[0]:r[1]].sum()) if runs else (0, 0)

out_dir = ROOT / "docs/design/ui-assets" / gid
out_dir.mkdir(parents=True, exist_ok=True)
H, W = a.shape[:2]
cw, ch = W / g["cols"], H / g["rows"]
made = []
for i, it in enumerate(g["items"]):
    if it is None:
        continue
    r, c = divmod(i, g["cols"])
    x0, y0, x1, y1 = round(c * cw), round(r * ch), round((c + 1) * cw), round((r + 1) * ch)
    cell = a[y0:y1, x0:x1].copy()
    alpha = key(cell)
    m = alpha > 48
    if not m.any():
        print("empty cell", it["id"]); continue
    r0, r1 = main_run(m, 1); keep = np.zeros_like(m); keep[r0:r1] = True; m &= keep
    c0, c1 = main_run(m, 0); keep = np.zeros_like(m); keep[:, c0:c1] = True; m &= keep
    alpha = np.where(keep[..., None].squeeze() if False else (np.arange(alpha.shape[0])[:, None] >= r0) & (np.arange(alpha.shape[0])[:, None] < r1) & (np.arange(alpha.shape[1])[None, :] >= c0) & (np.arange(alpha.shape[1])[None, :] < c1), alpha, 0)
    ys, xs = np.nonzero(m)
    cell[..., 3] = np.minimum(cell[..., 3], alpha)
    img = Image.fromarray(cell.astype(np.uint8), "RGBA").crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    ow, oh = (it["out"], it["out"]) if isinstance(it["out"], int) else it["out"]
    scale = min(ow * 0.8 / img.width, oh * 0.8 / img.height)
    img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (ow, oh), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((ow - img.width) // 2, (oh - img.height) // 2))
    canvas.save(out_dir / f'{it["id"]}.png')
    made.append(canvas)
    print("saved", gid, it["id"], canvas.size)
if made:  # contact sheet on dark and light for review
    t = 160
    cs = Image.new("RGBA", (t * len(made), t * 2), (32, 26, 22, 255))
    cs.paste(Image.new("RGBA", (t * len(made), t), (238, 241, 247, 255)), (0, t))
    for i, m in enumerate(made):
        th = m.copy(); th.thumbnail((t - 16, t - 16))
        for row in (0, 1):
            cs.alpha_composite(th, (i * t + (t - th.width) // 2, row * t + (t - th.height) // 2))
    cs.save(out_dir / "_contact.png")
