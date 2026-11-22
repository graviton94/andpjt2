// node shot.js <scale> [names...]   (default: theme-amber theme-mint theme-day)
const { chromium } = require('playwright');
(async () => {
  const scale = +process.argv[2] || 1;
  const names = process.argv.slice(3).length ? process.argv.slice(3) : ['theme-amber', 'theme-mint', 'theme-day'];
  const b = await chromium.launch();
  for (const n of names) {
    const p = await b.newPage({ viewport: { width: 1400, height: 1000 }, deviceScaleFactor: scale });
    await p.goto('file://' + __dirname + `/${n}.html`);
    const w = await p.evaluate(() => document.body.scrollWidth);
    await p.setViewportSize({ width: w, height: 1000 });
    await p.waitForTimeout(300);
    await p.screenshot({ path: `${n}.png`, fullPage: true });
    await p.close();
  }
  await b.close();
})();
