// Real game captures for the store screenshots: tools/brand/shots/<lang>/<key>.png (1080x1920).
// Themes alternate (1 dark, 2 light, 3 dark …) so the store row shows both looks.
// Needs the dev server: gradle -p core run --args="serve 8765 ../app/src/main/assets/game"
// node tools/brand/capture-shots.js [lang…]   then   node tools/brand/render-shots.js
const { chromium } = require('playwright'); const path = require('path'), fs = require('fs');
const API = 'http://127.0.0.1:8765/api/', get = p => fetch(API + p);
const SHOTS = [  // key, theme, game state, how to reach the moment, when to take it (s, paused game clock)
  ['chain', 'dark', 'cheat?level=40&wallet=200&apps=520,320,120', p => p.evaluate(() => window.__dbg.pop(6, 'chat', 5)), .16],
  ['upgrade', 'light', 'cheat?level=40&wallet=200&apps=520,320,120', p => p.evaluate(() => window.__dbg.open('up')), 1.2],
  ['show', 'dark', 'cheat?level=40&wallet=200&apps=520,320,120', p => p.evaluate(() => { window.__dbg.swipeHint(); setTimeout(() => window.__dbg.fx('wave', 0), 500); setTimeout(() => window.__dbg.fx('wave', 1), 1000); }), 2.1],
  ['detail', 'light', 'cheat?level=30&apps=520,320,120', p => p.evaluate(() => window.__dbg.open('shop').then(() => setTimeout(() => appDetail('alarm'), 300))), 1.6],
  ['shade', 'dark', 'cheat?level=40&wallet=200&apps=520,320,120', p => p.evaluate(() => window.__dbg.shade()), 1.2],
  ['attend', 'light', 'cheat?level=12', p => p.evaluate(() => window.__dbg.open('attend')), 1.2],
];
(async () => {
  const langs = process.argv.slice(2).length ? process.argv.slice(2) : ['en', 'ko'];
  const b = await chromium.launch();
  for (const lang of langs) {
    const dir = path.join(__dirname, 'shots', lang); fs.mkdirSync(dir, { recursive: true });
    for (const [key, theme, state, go, at] of SHOTS) {
      await get('new?seed=5'); await get(state);
      const p = await b.newPage({ viewport: { width: 390, height: 693 }, deviceScaleFactor: 1080 / 390, colorScheme: theme });
      const errs = []; p.on('pageerror', e => errs.push(e.message));
      await p.clock.install(); await p.goto(`http://127.0.0.1:8765/index.html?notitle&auto=0&noftue&noattend&lang=${lang}`);
      for (let i = 0; i < 40; i++) { await p.clock.runFor(100); await p.waitForTimeout(10); }
      const now = await p.evaluate(() => Date.now()); await p.clock.pauseAt(now + 3000);
      await go(p);
      for (let t = 0; t < at; t += 1 / 60) { await p.clock.runFor(1000 / 60); await p.waitForTimeout(2); }
      await p.screenshot({ path: path.join(dir, key + '.png') });
      const miss = await p.evaluate(() => [...I18N.missing]);
      console.log(lang, key, theme, errs.length ? 'errors: ' + errs.join(';') : 'ok', miss.length ? 'missing: ' + miss.join(',') : '');
      await p.close();
    }
  }
  await b.close();
})();
