// First-run guide end to end on a fresh game: node tools/game-page/ftuetest.js <outDir>  (after /api/new?seed=5)
const { chromium } = require('playwright');
const out = process.argv[2]; require('fs').mkdirSync(out, { recursive: true });
(async () => {
  const b = await chromium.launch(); const log = [];
  for (const scheme of ['dark', 'light']) {
    await fetch('http://127.0.0.1:8765/api/new?seed=5');
    const p = await b.newPage({ viewport: { width: 390, height: 844 }, colorScheme: scheme });
    const errors = []; p.on('pageerror', e => errors.push(e.message));
    await p.goto('http://127.0.0.1:8765/index.html?auto=0'); await p.waitForTimeout(3200);
    const step = () => p.evaluate(() => +localStorage.getItem('ftue'));
    const bubble = () => p.evaluate(() => document.querySelector('#coach.on b') ? document.querySelector('#coach.on b').textContent : '');
    await p.screenshot({ path: `${out}/${scheme}-1.png` }); log.push(scheme + ' boot: ' + (await step()) + ' ' + await bubble());
    // swipe the hinted rows until the shop step shows up
    let shots = 1;
    for (let i = 0; i < 60 && (await step()) < 4; i++) {
      const h = await p.evaluate(async () => { const r = await fetch('/api/hint').then(r => r.json()); return window.__dbg.rowY(r.id); });
      const y = h > 0 ? h + 30 : 330;
      await p.mouse.move(100, y); await p.mouse.down(); for (let x = 110; x < 330; x += 30) { await p.mouse.move(x, y); await p.waitForTimeout(8); } await p.mouse.up();
      await p.waitForTimeout(1500);
      const s = await step(); if (s === 2 && shots === 1) { shots++; await p.waitForTimeout(600); await p.screenshot({ path: `${out}/${scheme}-2.png` }); log.push('burst: ' + await bubble()); }
    }
    await p.waitForTimeout(1500); await p.screenshot({ path: `${out}/${scheme}-3.png` }); log.push('pull: ' + (await step()) + ' ' + await bubble());
    await p.evaluate(() => window.__dbg.shade()); await p.waitForTimeout(1200); await p.screenshot({ path: `${out}/${scheme}-4.png` }); log.push('shade: ' + await bubble());
    await p.click('#shade .tile[data-k="shop"]'); await p.waitForTimeout(1800); await p.screenshot({ path: `${out}/${scheme}-5.png` }); log.push('shop: ' + (await step()) + ' ' + await bubble());
    await p.click('#sheet-b [data-act="buy"]:not([disabled])'); await p.waitForTimeout(300); if (await p.$('#cf.on')) await p.click('#cf-ok'); await p.waitForTimeout(1000);
    log.push('bought: ' + (await step()) + ' coach on=' + !!(await p.$('#coach.on')));
    await p.screenshot({ path: `${out}/${scheme}-6.png` });
    log.push(scheme + ' ' + (errors.join(';') || 'no errors')); await p.close();
  }
  console.log(log.join('\n')); await b.close();
})();
