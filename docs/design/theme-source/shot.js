const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1480, height: 1200 }, deviceScaleFactor: +process.argv[2] || 1 });
  for (const id of ['amber', 'mint', 'day']) {
    await p.goto('file://' + process.cwd() + `/theme-${id}.html`);
    await p.waitForTimeout(300);
    await p.screenshot({ path: `theme-${id}.png`, fullPage: true });
  }
  await b.close();
})();
