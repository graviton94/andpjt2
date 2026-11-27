// Every screen in dark and light: node tools/game-page/themetest.js <outDir>
const { chromium } = require('playwright');
const out = process.argv[2]; require('fs').mkdirSync(out, { recursive: true });
(async () => {
  const b = await chromium.launch();
  for (const scheme of ['dark', 'light']) {
    const p = await b.newPage({ viewport: { width: 390, height: 844 }, colorScheme: scheme });
    const errors = []; p.on('pageerror', e => errors.push(e.message));
    await p.goto('http://127.0.0.1:8765/index.html?notitle&auto=0&lang=ko'); await p.waitForTimeout(2200);
    await p.evaluate(() => document.querySelector('#scrim').click()); await p.waitForTimeout(400);
    await p.screenshot({ path: `${out}/${scheme}-0-board.png` });
    await p.evaluate(() => window.__dbg.shade()); await p.waitForTimeout(700); await p.screenshot({ path: `${out}/${scheme}-1-shade.png` });
    let i = 2;
    for (const k of ['shop', 'up', 'deck', 'reset', 'data', 'pass', 'premium', 'attend']) {
      await p.evaluate(k => window.__dbg.open(k), k); await p.waitForTimeout(700);
      await p.screenshot({ path: `${out}/${scheme}-${i++}-${k}.png` });
    }
    console.log(scheme, errors.join(';') || 'no errors');
    await p.close();
  }
  await b.close();
})();
