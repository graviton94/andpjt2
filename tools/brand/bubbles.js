// Comic bubble + sound word, with the word's INK (not its line box) centred on the bubble's measured interior.
// bubbles.json: interior centre (cx, cy) and size (w, h) as fractions of each comic-fx image (from the slicer output).
const BUB = {"burst-big":{"cx":0.5024,"cy":0.4824,"w":0.4424,"h":0.3848},"burst-small":{"cx":0.5073,"cy":0.4849,"w":0.4307,"h":0.2354},
  "bubble-round":{"cx":0.499,"cy":0.478,"w":0.6543,"h":0.4775},"impact-flash":{"cx":0.5029,"cy":0.4795,"w":0.2031,"h":0.1973}};
const ASSET = '../../docs/design/ui-assets/comic-fx/';
// opts: {x, y, w, rot, text, family, weight, color, stroke, shadow, fill | fillW/fillH (share of interior; >1 lets the word bite into the rim), tilt, z}
function bubble(o) {
  const b = BUB[o.name];
  return `<div class="bub" data-o='${JSON.stringify(o).replace(/'/g, "&#39;")}' style="position:absolute;left:${o.x}px;top:${o.y}px;width:${o.w}px;height:${o.w}px;transform:rotate(${o.rot || 0}deg);z-index:${o.z ?? "auto"}">
    <img src="${ASSET}${o.name}.png" style="position:absolute;inset:0;width:100%;height:100%">
    <span class="bw" style="position:absolute;left:${b.cx * 100}%;top:${b.cy * 100}%;white-space:nowrap;line-height:1;transform-origin:0 0"></span></div>`;
}
function fitBubbles() {
  const ctx = document.createElement('canvas').getContext('2d');
  for (const el of document.querySelectorAll('.bub')) {
    const o = JSON.parse(el.dataset.o), b = BUB[o.name], span = el.querySelector('.bw');
    const fam = `${o.weight || 400} SIZEpx ${o.family}`;
    const metrics = size => { ctx.font = fam.replace('SIZE', size); return ctx.measureText(o.text); };
    // fit: ink box inside fill × interior (stroke counted)
    const iw = b.w * o.w * (o.fillW || o.fill || 0.86), ih = b.h * o.w * (o.fillH || o.fill || 0.86), st = o.stroke ?? 10;
    let size = 400, m;
    for (; size > 8; size -= 2) {
      m = metrics(size);
      const w = m.actualBoundingBoxLeft + m.actualBoundingBoxRight + st, h = m.actualBoundingBoxAscent + m.actualBoundingBoxDescent + st + (o.shadow ?? 8);
      const t = Math.abs((o.tilt || 0) * Math.PI / 180), rw = w * Math.cos(t) + h * Math.sin(t), rh = w * Math.sin(t) + h * Math.cos(t);
      if (rw <= iw && rh <= ih) break;
    }
    // line box of height = size: baseline sits at size/2 + (fontAscent - fontDescent)/2
    const base = size / 2 + (m.fontBoundingBoxAscent - m.fontBoundingBoxDescent) / 2;
    const inkX = (m.actualBoundingBoxRight - m.actualBoundingBoxLeft) / 2;
    const inkY = base - (m.actualBoundingBoxAscent - m.actualBoundingBoxDescent) / 2 + (o.shadow ?? 8) / 2;
    Object.assign(span.style, { font: fam.replace('SIZE', size), lineHeight: '1', color: o.color || '#ff3b30',
      webkitTextStroke: `${st}px #2a0c02`, paintOrder: 'stroke fill', textShadow: `0 ${o.shadow ?? 8}px 0 #2a0c02`,
      transform: `rotate(${o.tilt || 0}deg) translate(${-inkX}px, ${-inkY}px)` });
    span.textContent = o.text;
  }
}
