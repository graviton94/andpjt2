"""Launcher icon from system-UI parts: a swiped notification card stack, app square, red badge.
Writes docs/design/brand/icon.svg and app/src/main/res/drawable/ic_launcher.xml from one shape list."""
import pathlib
ROOT = pathlib.Path(__file__).resolve().parents[2]

def rr(x1, y1, x2, y2, r):
    return (f"M{x1+r},{y1}H{x2-r}A{r},{r} 0 0 1 {x2},{y1+r}V{y2-r}A{r},{r} 0 0 1 {x2-r},{y2}"
            f"H{x1+r}A{r},{r} 0 0 1 {x1},{y2-r}V{y1+r}A{r},{r} 0 0 1 {x1+r},{y1}Z")
def circle(cx, cy, r):
    return f"M{cx-r},{cy}A{r},{r} 0 1 0 {cx+r},{cy}A{r},{r} 0 1 0 {cx-r},{cy}Z"
def star(cx, cy, r):
    k = r * 0.28
    return f"M{cx},{cy-r}L{cx+k},{cy-k}L{cx+r},{cy}L{cx+k},{cy+k}L{cx},{cy+r}L{cx-k},{cy+k}L{cx-r},{cy}L{cx-k},{cy-k}Z"

SHAPES = [
    ("#2A1A10", "M0,0H108V108H0Z"),
    ("#3D2617", circle(54, 54, 44)),
    ("#5A3A26", rr(26, 66, 78, 80, 6)),          # oldest card
    ("#8A5A36", rr(21, 52, 83, 70, 7)),          # middle card
    ("#FFD66B", rr(9, 33, 21, 36, 1.5)),          # swipe streaks
    ("#FFD66B", rr(4, 40, 20, 43, 1.5)),
    ("#FFD66B", rr(10, 47, 22, 50, 1.5)),
    ("#F6F1EA", rr(27, 28, 93, 54, 8)),          # front card, pushed right
    ("#FF8A1F", rr(32, 33, 48, 49, 4)),          # app square
    ("#FFFFFF", rr(36, 39, 44, 42, 1.5)),         # bell-ish bar on app square
    ("#FFFFFF", circle(40, 44.5, 1.6)),
    ("#7A6A5C", rr(52, 35, 84, 39, 2)),           # title line
    ("#B3A89B", rr(52, 43, 75, 46, 1.5)),         # body line
    ("#FF3B30", circle(86, 25, 10)),             # unread badge
    ("#FFFFFF", rr(84.4, 18.5, 87.6, 27.5, 1.6)),
    ("#FFFFFF", circle(86, 31, 1.8)),
    ("#FFD66B", star(22, 84, 7)),
    ("#FFB14A", star(86, 76, 5)),
    ("#FFD66B", star(64, 92, 3.5)),
]

svg = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">']
svg += [f'<path fill="{c}" d="{d}"/>' for c, d in SHAPES]
svg.append("</svg>")
out = ROOT / "docs/design/brand"; out.mkdir(parents=True, exist_ok=True)
(out / "icon.svg").write_text("\n".join(svg) + "\n")

xml = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
       '    android:width="108dp" android:height="108dp"',
       '    android:viewportWidth="108" android:viewportHeight="108">']
xml += [f'    <path android:fillColor="{c}" android:pathData="{d}" />' for c, d in SHAPES]
xml.append("</vector>")
(ROOT / "app/src/main/res/drawable/ic_launcher.xml").write_text("\n".join(xml) + "\n")
print("icon written")
