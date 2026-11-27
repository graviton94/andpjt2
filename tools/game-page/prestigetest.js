// Prestige payoff: factory reset then cloud sync from the sheet, frames during the show, state after.
// Run after: /api/new?seed=5 and /api/cheat?level=80 (sync opens at Lv75 since balance v1.1). node tools/game-page/prestigetest.js <outDir>
const { chromium } = require('playwright');
const out = process.argv[2]; require('fs').mkdirSync(out, { recursive: true });
const API = 'http://127.0.0.1:8765/api/';
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 390, height: 844 } });
  const errors = []; p.on('pageerror', e => errors.push(e.message));
  await p.goto('http://127.0.0.1:8765/index.html?notitle&auto=0&lang=ko&noftue'); await p.waitForTimeout(3500);
  await p.evaluate(() => window.__dbg.open('reset')); await p.waitForTimeout(800);
  await p.screenshot({ path: `${out}/0-sheet.png` });
  // cloud sync first (Lv62 qualifies), then later a reset on the new run is not possible (Lv1): sync shows the GB payoff
  await p.click('#sheet-b [data-act="sync"]'); await p.waitForTimeout(200); await p.click('#sheet-b [data-act="sync"]');
  for (const [ms, n] of [[300, 'a'], [700, 'b'], [1200, 'c'], [2300, 'd'], [3800, 'e']]) { await p.waitForTimeout(ms - (n === 'a' ? 0 : 0)); await p.screenshot({ path: `${out}/1-${n}.png` }); }
  const st = await (await p.request.get(API + 'panel')).json();
  console.log(JSON.stringify({ level: st.state.level, gb: st.gb, syncs: st.syncs, files: st.files, errors }));
  await b.close();
})();
