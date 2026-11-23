// node tools/brand/render.js → docs/design/brand/*.png and app launcher mipmaps
const { chromium } = require('playwright');
const path = require('path'), fs = require('fs');
const root = path.resolve(__dirname, '../..'), here = 'file://' + __dirname + '/';
(async () => {
  const b = await chromium.launch();
  const shot = async (url, w, h, out, scale = 1) => {
    const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: scale });
    await p.goto(here + url); await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(200);
    await p.screenshot({ path: out }); await p.close();
  };
  const brand = path.join(root, 'docs/design/brand');
  await shot('icon.html', 512, 512, path.join(brand, 'icon-512.png'));
  for (const [d, px] of [['mdpi', 48], ['hdpi', 72], ['xhdpi', 96], ['xxhdpi', 144], ['xxxhdpi', 192]]) {
    const dir = path.join(root, 'app/src/main/res/mipmap-' + d); fs.mkdirSync(dir, { recursive: true });
    await shot('icon.html', 512, 512, path.join(dir, 'ic_launcher.png'), px / 512);
  }
  for (const l of ['ko', 'en']) await shot('feature.html?lang=' + l, 1024, 500, path.join(brand, 'feature-' + l + '.png'));
  await b.close();
})();
