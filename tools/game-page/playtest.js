// Plays the game page like a finger would and saves frames: node tools/game-page/playtest.js <outDir> [swipes]
const { chromium } = require('playwright');
const out = process.argv[2] || 'build/playtest', N = +(process.argv[3] || 8);
require('fs').mkdirSync(out, { recursive: true });
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1, hasTouch: true });
  const errors = [];
  p.on('pageerror', e => errors.push('pageerror: ' + e.message));
  p.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await p.goto('http://127.0.0.1:8765/index.html');
  await p.waitForTimeout(1800);
  await p.screenshot({ path: `${out}/00-start.png` });
  // pick the hinted row (the engine's best move) and drag it left
  for (let i = 1; i <= N; i++) {
    const h = await (await p.request.get('http://127.0.0.1:8765/api/hint')).json();
    const st = await (await p.request.get('http://127.0.0.1:8765/api/snapshot')).json();
    const idx = st.rows.findIndex(r => r.id === h.id);
    const geo = await p.evaluate(() => ({ H: Math.max(740, Math.round(390 * innerHeight / innerWidth)) }));
    const y = (176 + Math.round((geo.H - 740) * .35) + Math.max(0, idx) * 72 + 30) * 844 / geo.H;
    await p.mouse.move(300, y); await p.mouse.down();
    for (let s = 1; s <= 8; s++) { await p.mouse.move(300 - s * 25, y); await p.waitForTimeout(12); }
    await p.mouse.up();
    for (const [ms, tag] of [[120, 'a'], [350, 'b'], [700, 'c'], [1300, 'd']]) { await p.waitForTimeout(ms - (tag === 'a' ? 0 : 0)); await p.screenshot({ path: `${out}/${String(i).padStart(2, '0')}${tag}.png` }); }
    await p.waitForTimeout(900);
  }
  console.log(errors.length ? errors.join('\n') : 'no errors');
  await b.close();
})();
