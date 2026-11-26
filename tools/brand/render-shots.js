// node tools/brand/render-shots.js → docs/design/brand/screens/<lang>/<n>-<key>.png (1080x1920, Play phone screenshots)
const { chromium } = require('playwright'); const path = require('path'), fs = require('fs');
const root = path.resolve(__dirname, '../..'), here = 'file://' + __dirname + '/';
const keys = ['chain', 'upgrade', 'show', 'detail', 'shade', 'attend'];  // dark, light, dark, light … (capture-shots.js)
(async () => { const b = await chromium.launch();
  for (const lang of ['en', 'ko']) { const dir = path.join(root, 'docs/design/brand/screens', lang); fs.mkdirSync(dir, { recursive: true });
    for (const [i, k] of keys.entries()) { const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
      await p.goto(here + 'screenshot.html?lang=' + lang + '&k=' + k); await p.evaluate(() => document.fonts.ready); await p.waitForFunction(() => window.__ready); await p.waitForTimeout(150);
      await p.screenshot({ path: path.join(dir, `${i + 1}-${k}.png`) }); await p.close(); } }
  await b.close(); })();
