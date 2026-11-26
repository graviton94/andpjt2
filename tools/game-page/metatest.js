// Achievements/titles sheet and one daily challenge run: node tools/game-page/metatest.js <outDir>  (after /api/new?seed=5, /api/cheat?level=30)
const { chromium } = require('playwright');
const out = process.argv[2]; require('fs').mkdirSync(out, { recursive: true });
(async () => {
  const b = await chromium.launch(); const res = {};
  for (const scheme of ['dark', 'light']) {
    const p = await b.newPage({ viewport: { width: 390, height: 844 }, colorScheme: scheme });
    const errors = []; p.on('pageerror', e => errors.push(e.message));
    await p.goto('http://127.0.0.1:8765/index.html?auto=0&lang=ko&noftue'); await p.waitForTimeout(2600);
    await p.evaluate(() => document.querySelector('#scrim').click()); await p.waitForTimeout(500);
    await p.evaluate(() => window.__dbg.shade()); await p.waitForTimeout(900); await p.screenshot({ path: `${out}/${scheme}-shade.png` });
    await p.click('#shade .tile[data-k="ach"]'); await p.waitForTimeout(900); await p.screenshot({ path: `${out}/${scheme}-ach.png` });
    await p.click('#sheet-x'); await p.waitForTimeout(500); await p.click('#shade .tile[data-k="chal"]'); await p.waitForTimeout(900); await p.screenshot({ path: `${out}/${scheme}-chal.png` });
    if (scheme === 'dark') {
      await p.click('#chal-go'); await p.waitForTimeout(1500);
      for (let i = 0; i < 6; i++) { const y = await p.evaluate(async () => { const r = await fetch('/api/hint').then(r => r.json()); return window.__dbg.rowY(r.id); }); const yy = y > 0 ? y + 30 : 330;
        await p.mouse.move(100, yy); await p.mouse.down(); for (let x = 110; x < 330; x += 30) { await p.mouse.move(x, yy); await p.waitForTimeout(8); } await p.mouse.up(); await p.waitForTimeout(900); }
      await p.screenshot({ path: `${out}/run.png` });
      await p.click('#chud-x'); await p.waitForTimeout(2200); await p.screenshot({ path: `${out}/result.png` });
      res.level = await p.evaluate(() => fetch('/api/snapshot').then(r => r.json()).then(s => s.level));
      res.meta = await p.evaluate(() => fetch('/api/meta').then(r => r.json()).then(m => ({ keys: m.keys, pieces: m.pieces, best: m.bestShort })));
    }
    res[scheme] = errors.join(';') || 'no errors'; await p.close();
  }
  console.log(JSON.stringify(res)); await b.close();
})();
