// Special rows end to end: incoming spam call → block, 행운 알림 spin, an event card line. node tools/game-page/eventtest.js <outDir>
const { chromium } = require('playwright');
const out = process.argv[2]; require('fs').mkdirSync(out, { recursive: true });
const API = 'http://127.0.0.1:8765/api/';
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 390, height: 844 } });
  const errors = []; p.on('pageerror', e => errors.push(e.message)); p.on('console', m => m.type() === 'error' && errors.push(m.text()));
  await p.goto('http://127.0.0.1:8765/index.html?notitle&auto=0&lang=ko'); await p.waitForTimeout(1500);
  await p.evaluate(() => window.__dbg.auto(false));
  const quiet = async () => { for (let i = 0; i < 40; i++) { const f = JSON.parse(await p.evaluate(() => window.__dbg.flags())); if (!f.busy && !f.boardBusy && f.phase === 'idle') return; await p.waitForTimeout(200); } };
  await quiet();
  const H = 844, row0 = 176 + Math.round((H - 740) * .35);
  const swipeRow = async id => { const y = (await p.evaluate(i => window.__dbg.rowY(i), id)) + 30; await p.mouse.move(300, y); await p.mouse.down(); for (let s = 1; s <= 8; s++) { await p.mouse.move(300 - s * 28, y); await p.waitForTimeout(12); } await p.mouse.up(); };
  // incoming call: the page picks it up on its next refresh
  await p.request.get(API + 'row?kind=Call');
  await p.evaluate(() => window.__dbg.refresh());
  for (let i = 0; i < 20 && !(await p.evaluate(() => window.__dbg.ev())); i++) await p.waitForTimeout(200);
  await p.waitForTimeout(700); await p.screenshot({ path: `${out}/1-call.png` });
  await p.mouse.click(110, row0 + 410); await p.waitForTimeout(250); await p.screenshot({ path: `${out}/2-blocked.png` });
  await p.waitForTimeout(2200);
  const calls = (await (await p.request.get(API + 'snapshot')).json()).rows.filter(r => r.kind === 'Call').length;
  // lucky alert
  await quiet();
  const lotto = (await (await p.request.get(API + 'row?kind=Lotto')).json()).rows[0].id;
  await p.evaluate(() => window.__dbg.refresh());
  await p.waitForTimeout(700); await p.screenshot({ path: `${out}/3-lotto-row.png` });
  await swipeRow(lotto); await p.waitForTimeout(800); await p.screenshot({ path: `${out}/4-lotto-spin.png` });
  await p.waitForTimeout(1300); await p.screenshot({ path: `${out}/5-lotto-result.png` });
  await p.waitForTimeout(2500);
  await quiet();
  const card = (await (await p.request.get(API + 'row?kind=GroupText')).json()).rows[0].id;
  await p.evaluate(() => window.__dbg.refresh());
  await p.waitForTimeout(700); await swipeRow(card); await p.waitForTimeout(400); await p.screenshot({ path: `${out}/6-card.png` });
  console.log('calls left on board:', calls);
  console.log(errors.length ? errors.join('\n') : 'no errors');
  await b.close();
})();
