"""Builds the asset commission board page from docs/design/commissions.json.

Usage: python3 tools/commissions/build_board.py OUT.html
Delivered groups are moved from "pending" to "delivered" in the JSON, then the page is rebuilt and republished.
"""
import html, json, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
d = json.loads((ROOT / "docs/design/commissions.json").read_text())
PRI = {1: "1순위 · 그레이박스 상점", 2: "2순위 · 프리미엄 화면", 3: "3순위 · 메타", 4: "4순위 · 소프트런치 전"}

def card(g):
    full = d["spec"] + "\n\n" + g["prompt"]
    files = "".join(f'<li><code>{html.escape(g["id"])}/{html.escape(f["id"])}.png</code><span>{html.escape(f["note"])}</span></li>' for f in g["files"])
    return f'''<article class="card">
  <header><div><p class="pri">{PRI[g["priority"]]}</p><h2>{html.escape(g["title"])}</h2></div>
  <button type="button" class="copy" data-copy="{html.escape(full, quote=True)}">프롬프트 복사</button></header>
  <p class="meta">파일 {len(g["files"])}개 · {html.escape(g.get("size",""))} 투명 PNG · 폴더 <code>docs/design/ui-assets/{html.escape(g["id"])}/</code></p>
  <ul class="files">{files}</ul>
  <details><summary>프롬프트 보기</summary><pre>{html.escape(full)}</pre></details>
</article>'''

cards = "\n".join(card(g) for g in d["pending"]) or '<p class="empty">대기 중인 발주가 없습니다.</p>'
done = "".join(f'<li><span>{html.escape(x["date"])}</span>{html.escape(x["title"])}</li>' for x in d["delivered"])
total = sum(len(g["files"]) for g in d["pending"])
page = f'''<!DOCTYPE html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>알림 청소부 에셋 발주 보드</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;700;900&display=swap">
<style>
:root{{color-scheme:light dark;--bg:light-dark(#EEF1F7,#100D0A);--ink:light-dark(#16181D,#F6F1EA);--sub:light-dark(#4B5160,#B3A89B);--card:light-dark(#FFFFFF,#201B16);--line:light-dark(#DDE2EC,#3A332C);--acc:light-dark(#3867F0,#EFA03F);--onacc:light-dark(#FFFFFF,#1E1408);--code:light-dark(#F3F5FA,#16120E)}}
:root[data-theme=light]{{color-scheme:light}}:root[data-theme=dark]{{color-scheme:dark}}
*{{box-sizing:border-box;min-width:0}}
body{{margin:0;background:var(--bg);color:var(--ink);font:16px/1.6 'Noto Sans KR',system-ui,sans-serif}}
main{{max-width:880px;margin:0 auto;padding:clamp(20px,4vw,44px) max(4vw,16px)}}
h1{{margin:0 0 6px;font-size:clamp(28px,4vw,38px);font-weight:900;letter-spacing:-.02em;line-height:1.1}}
.lead{{margin:0 0 24px;color:var(--sub);max-width:64ch}}
.card{{background:var(--card);border:1px solid var(--line);border-radius:20px;padding:18px 20px;margin:0 0 16px}}
.card header{{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;flex-wrap:wrap}}
h2{{margin:0;font-size:19px}} .pri{{margin:0 0 2px;font-size:12.5px;font-weight:700;color:var(--acc)}}
.meta{{margin:8px 0;color:var(--sub);font-size:14px}}
button{{font:inherit;font-weight:700;font-size:14px;min-height:44px;padding:0 18px;border-radius:999px;border:0;background:var(--acc);color:var(--onacc);cursor:pointer}}
button:focus-visible{{outline:3px solid var(--acc);outline-offset:2px}}
.files{{list-style:none;margin:0;padding:0;display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:4px 16px;font-size:14px}}
.files li{{display:flex;gap:8px;align-items:baseline}} .files span{{color:var(--sub)}}
code{{font-size:12.5px;background:var(--code);padding:1px 5px;border-radius:5px}}
details{{margin-top:10px}} summary{{cursor:pointer;color:var(--sub);font-size:14px}}
pre{{white-space:pre-wrap;background:var(--code);border-radius:12px;padding:12px;font-size:13px;line-height:1.5;overflow-wrap:anywhere}}
.done{{color:var(--sub);font-size:14px;padding-left:18px}} .done span{{font-variant-numeric:tabular-nums;margin-right:8px}}
.toast{{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:var(--ink);color:var(--bg);padding:10px 16px;border-radius:999px;font-size:14px;opacity:0;transition:opacity .2s;pointer-events:none}}
.toast.on{{opacity:1}}
</style></head><body><main>
<h1>에셋 발주 보드</h1>
<p class="lead">대기 중인 발주 {len(d["pending"])}묶음 · 파일 {total}개. "프롬프트 복사"는 공통 사양과 묶음 프롬프트를 한 번에 복사합니다. 납품 파일을 대화에 올리면 반입 후 이 목록에서 지웁니다.</p>
{cards}
<h2 style="margin-top:28px">반입 완료</h2>
<ul class="done">{done}</ul>
</main><div class="toast" role="status" aria-live="polite"></div>
<script>
{{
const toast = document.querySelector('.toast');
document.querySelectorAll('.copy').forEach(b => b.addEventListener('click', async () => {{
  const text = b.dataset.copy;
  try {{ await navigator.clipboard.writeText(text); }}
  catch (e) {{ const t = document.createElement('textarea'); t.value = text; document.body.append(t); t.select(); document.execCommand('copy'); t.remove(); }}
  toast.textContent = '복사했어요'; toast.classList.add('on'); setTimeout(() => toast.classList.remove('on'), 1400);
}}));
}}
</script></body></html>'''
pathlib.Path(sys.argv[1]).write_text(page)
print("pending", len(d["pending"]), "files", total)
