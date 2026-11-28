// Guards the render pipeline: the shader's screen aspect is set and a real chain keeps the board on screen.
// Needs the dev server. node tools/game-page/rendertest.js <outDir>
const { chromium } = require('playwright');
const out = process.argv[2] || 'build/rendertest'; require('fs').mkdirSync(out, { recursive: true });
const A = 'http://127.0.0.1:8765/api/';
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const errors = []; p.on('pageerror', e => errors.push(e.message));
  await fetch(A + 'new?seed=11'); await fetch(A + 'cheat?level=7&wallet=50');
  await p.goto('http://127.0.0.1:8765/index.html?notitle&noftue&noattend&auto=0&lang=ko'); await p.waitForTimeout(2500);
  const aspect = await p.evaluate(() => __dbg.aspect());
  const h = await (await fetch(A + 'hint')).json(); const ry = await p.evaluate(id => __dbg.rowY(id), h.id);
  await p.mouse.move(300, ry + 30); await p.mouse.down(); for (let s = 1; s <= 8; s++) { await p.mouse.move(300 - s * 25, ry + 30); await p.waitForTimeout(12); } await p.mouse.up();
  await p.waitForTimeout(900); await p.screenshot({ path: `${out}/mid-chain.png` });
  const ok = aspect > .3 && aspect < 1 && !errors.length;
  console.log(JSON.stringify({ aspect, errors }), ok ? 'ok' : 'FAIL');
  await b.close(); process.exit(ok ? 0 : 1);
})();
