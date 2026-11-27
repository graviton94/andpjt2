// M4 walkthrough: daily check-in, data packs, data items, a (fake) ad, month pass, premium star. node tools/game-page/bmtest.js <outDir>
const { chromium } = require('playwright');
const out = process.argv[2]; require('fs').mkdirSync(out, { recursive: true });
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 390, height: 844 } });
  const errors = []; p.on('pageerror', e => errors.push(e.message)); p.on('console', m => m.type() === 'error' && errors.push(m.text()));
  await p.goto('http://127.0.0.1:8765/index.html?notitle&auto=0&lang=ko&noftue');
  await p.waitForTimeout(2800); await p.screenshot({ path: `${out}/1-attend.png` });
  // every payment asks first: press 결제 when the confirm pops up
  const pay = async sel => { await p.click(sel); await p.waitForTimeout(250); if (await p.$('#cf.on')) await p.click('#cf-ok'); };
  const shot = async n => { await p.waitForTimeout(500); await p.screenshot({ path: `${out}/${n}.png` }); };
  const open = async label => {
    await p.click('#sheet-x'); await p.waitForTimeout(350);
    if (!(await p.$('#shade.open'))) await p.evaluate(() => document.querySelector('#scrim').click());
    await p.mouse.move(200, 165); await p.mouse.down(); for (let y = 170; y < 330; y += 20) { await p.mouse.move(200, y); await p.waitForTimeout(10); } await p.mouse.up();
    await p.waitForTimeout(450); await p.click(`#shade .tile:has(b:text-is("${label}"))`); await p.waitForTimeout(400);
  };
  await p.waitForTimeout(1500);
  await open('데이터 충전'); await shot('2-data');
  await pay('[data-pack="data_540"]'); await shot('3-data-bought');
  await pay('[data-act="buyItem"][data-item="Boost"]'); await p.waitForTimeout(300);
  await p.click('[data-act="watchAd"][data-slot="CreditBox"]'); await p.waitForTimeout(700); await p.screenshot({ path: `${out}/4-ad.png` });
  await p.waitForTimeout(3200); await shot('5-after-ad');
  await open('더 빠르게'); await shot('6-pass');
  await pay('[data-act="buyPass"][data-kind="month"]'); await shot('7-pass-bought');
  await open('프리미엄'); await shot('8-premium');
  const star = await p.$('#sheet-b [data-act="premium"]:not([disabled])'); if (star) { await star.click(); await p.waitForTimeout(250); if (await p.$('#cf.on')) await p.click('#cf-ok'); await shot('9-premium-star'); }
  await open('매일 들르기'); await shot('10-attend-again');
  const m = await (await p.request.get('http://127.0.0.1:8765/api/bm')).json();
  console.log('data', m.data, 'pass', m.passActive, 'streak', m.streak, 'boostMin', m.boostLeftMin, 'stars', m.premium.map(a => a.id + a.stars).join(' '));
  console.log(errors.length ? errors.join('\n') : 'no errors');
  await b.close();
})();
