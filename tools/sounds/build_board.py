"""Sound board artifact: docs/design/sounds.json → tools/sounds/board/index.html + audio/*.mp3.
Synth renders come from tools/sounds/render.js (docs/design/sounds/*.wav), phone samples from
docs/design/fx-demo/phone-sfx.json, music previews from docs/design/music/preview-*.mp3. Needs ffmpeg.
python3 tools/sounds/build_board.py  → then publish tools/sounds/board/ (index.html + audio/)."""
import base64, html, json, pathlib, shutil, struct, subprocess, wave

ROOT = pathlib.Path(__file__).resolve().parents[2]
REG = json.loads((ROOT / "docs/design/sounds.json").read_text(encoding="utf-8"))
SRC = ROOT / "docs/design/sounds"
BOARD = ROOT / "tools/sounds/board"
AUD = BOARD / "audio"
SAMPLES = json.loads((ROOT / "docs/design/fx-demo/phone-sfx.json").read_text())
STATUS = {"live": "게임에 들어감", "review": "들어 봐 주세요", "redo": "수정 요청", "planned": "예정"}


def ff(*args):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", *args], check=True)


def peaks(path, n=72):
    tmp = BOARD / "_peaks.wav"
    ff("-i", str(path), "-ac", "1", "-ar", "8000", "-f", "wav", str(tmp))
    with wave.open(str(tmp)) as w:
        raw = w.readframes(w.getnframes())
    tmp.unlink()
    a = struct.unpack(f"<{len(raw) // 2}h", raw)
    step = max(1, len(a) // n)
    p = [max((abs(x) for x in a[i:i + step]), default=0) / 32768 for i in range(0, step * n, step)]
    top = max(p) or 1
    return [round(v / top, 3) for v in p], len(a) / 8000


def make_audio():
    shutil.rmtree(BOARD, ignore_errors=True)
    AUD.mkdir(parents=True)
    out = {}
    for g in REG["groups"]:
        for it in g["items"]:
            dst = AUD / f"{it['id']}.mp3"
            if it["kind"] == "synth":
                # trim the silent tail, even the loudness, small mono MP3
                ff("-i", str(SRC / f"{it['id']}.wav"), "-af", "silenceremove=stop_periods=-1:stop_duration=0.25:stop_threshold=-55dB,loudnorm=I=-18:TP=-1.5",
                   "-ac", "1", "-b:a", "96k", str(dst))
            elif it["kind"] == "sample":
                ogg = BOARD / "_s.ogg"
                ogg.write_bytes(base64.b64decode(SAMPLES[it["id"]].split(",", 1)[1]))
                ff("-i", str(ogg), "-ac", "1", "-b:a", "96k", str(dst))
                ogg.unlink()
            else:
                shutil.copy(ROOT / f"docs/design/music/preview-{it['id'].split('.')[1]}.mp3", dst)
            out[it["id"]] = peaks(dst)
    return out


def wave_svg(p):
    w = len(p)
    bars = "".join(f'<rect x="{i * 3}" y="{(1 - v) * 14:.1f}" width="2" height="{max(.6, v * 28):.1f}" rx="1"/>' for i, v in enumerate(p))
    return f'<svg viewBox="0 0 {w * 3} 28" preserveAspectRatio="none" aria-hidden="true">{bars}</svg>'


def page(pk):
    e = html.escape
    total = sum(len(g["items"]) for g in REG["groups"])
    counts = {k: sum(1 for g in REG["groups"] for i in g["items"] if i["status"] == k) for k in STATUS}
    groups = []
    for g in REG["groups"]:
        cards = []
        for it in g["items"]:
            p, secs = pk[it["id"]]
            big = it["kind"] == "bgm"
            meta = f'<span class="k">{e(it.get("spec") or it.get("call") or it["id"])}</span>'
            cards.append(f'''<article class="snd {"big" if big else ""}" data-status="{it["status"]}" data-group="{g["id"]}">
<button class="play" data-src="audio/{e(it["id"])}.mp3" aria-label="{e(it["name"])} 재생"><i></i></button>
<div class="body"><div class="row1"><b>{e(it["name"])}</b><span class="st {it["status"]}">{STATUS[it["status"]]}</span></div>
<div class="when">{e(it["when"])}</div><div class="wv"><div class="bar">{wave_svg(p)}</div><span class="len">{secs:.1f}s</span></div>{meta}</div></article>''')
        groups.append(f'<section class="grp" id="{g["id"]}"><header><h2>{e(g["name"])}</h2><span>{len(g["items"])}</span></header><p class="gd">{e(g["desc"])}</p><div class="cards">{"".join(cards)}</div></section>')
    nav = "".join(f'<a href="#{g["id"]}">{e(g["name"])}</a>' for g in REG["groups"])
    return TEMPLATE.replace("__TOTAL__", str(total)).replace("__REVIEW__", str(counts["review"])).replace("__LIVE__", str(counts["live"])) \
        .replace("__NAV__", nav).replace("__GROUPS__", "".join(groups))


TEMPLATE = (pathlib.Path(__file__).parent / "board.tpl.html").read_text(encoding="utf-8")
(BOARD.parent / "board").mkdir(exist_ok=True)
pk = make_audio()
(BOARD / "index.html").write_text(page(pk), encoding="utf-8")
print("board:", BOARD / "index.html", len(list(AUD.glob("*.mp3"))), "audio files", sum(f.stat().st_size for f in AUD.glob("*.mp3")) // 1024, "KB")
