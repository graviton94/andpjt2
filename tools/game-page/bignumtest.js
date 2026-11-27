// Infinite-growth screen check: wallet 10^digits, swipe, shade, shop/upgrade sheets. No Infinity/NaN anywhere.
// node tools/game-page/bignumtest.js <outDir> [digits]
const { chromium } = require('playwright');
const out = process.argv[2], digits = process.argv[3] || '400'; require('fs').mkdirSync(out, { recursive: true });
const API = 'http://127.0.0.1:8765/api/';
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 390, height: 844 } });
  const errors = []; p.on('pageerror', e => errors.push(e.message)); p.on('console', m => m.type() === 'error' && errors.push(m.text()));
  await p.request.get(API + 'bigWallet?digits=' + digits);  // run cheat?level=30 first to unlock the shop
  await p.goto('http://127.0.0.1:8765/index.html?notitle&auto=0&lang=ko'); await p.waitForTimeout(3500);
  await p.evaluate(() => document.querySelector('#scrim').click()); await p.waitForTimeout(400);
  await p.screenshot({ path: `${out}/1-board.png` });
  const id = (await (await p.request.get(API + 'hint')).json()).id;
  const y = (await p.evaluate(i => window.__dbg.rowY(i), id)) + 30;
  await p.mouse.move(300, y); await p.mouse.down(); for (let s = 1; s <= 8; s++) { await p.mouse.move(300 - s * 28, y); await p.waitForTimeout(12); } await p.mouse.up();
  await p.waitForTimeout(700); await p.screenshot({ path: `${out}/2-swipe.png` });
  await p.waitForTimeout(2500); await p.screenshot({ path: `${out}/3-after.png` });
  await p.evaluate(() => window.__dbg.shade()); await p.waitForTimeout(700); await p.screenshot({ path: `${out}/4-shade.png` });
  const shadeText = await p.evaluate(() => document.querySelector('#shade').innerText);
  await p.evaluate(() => window.__dbg.open('shop')); await p.waitForTimeout(700); await p.screenshot({ path: `${out}/5-shop.png` });
  const buyable = await p.$$eval('#sheet-b [data-act="buy"]:not([disabled])', e => e.length);
  await p.evaluate(() => window.__dbg.open('up')); await p.waitForTimeout(700); await p.screenshot({ path: `${out}/6-up.png` });
  const sheetText = await p.evaluate(() => document.querySelector('#sheet').innerText);
  const bad = /Infinity|NaN|undefined/.test(shadeText + sheetText);
  console.log(JSON.stringify({ wallet: shadeText.split('\n').slice(0, 3).join(' | '), buyable, bad, errors }));
  await b.close();
})();
