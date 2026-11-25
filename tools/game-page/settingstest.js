// Settings: gear → sheet, switches persist across reload, game data reset (two taps + dialog) wipes the save.
// node tools/game-page/settingstest.js <outDir>
const { chromium } = require('playwright');
const out = process.argv[2]; require('fs').mkdirSync(out, { recursive: true });
const API = 'http://127.0.0.1:8765/api/';
(async () => {
  const b = await chromium.launch();
  const res = {};
  for (const sc of ['dark', 'light']) {
    const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, colorScheme: sc });
    const p = await ctx.newPage(); const errors = []; p.on('pageerror', e => errors.push(e.message));
    await p.goto('http://127.0.0.1:8765/index.html?auto=0'); await p.waitForTimeout(3500);
    await p.evaluate(() => document.querySelector('#scrim').click()); await p.waitForTimeout(400);
    await p.evaluate(() => window.__dbg.shade()); await p.waitForTimeout(700);
    await p.click('#gear'); await p.waitForTimeout(800);
    await p.screenshot({ path: `${out}/${sc}-settings.png` });
    if (sc === 'dark') {
      await p.click('[data-sw="reduce"]'); await p.waitForTimeout(200); await p.click('[data-sw="vib"]'); await p.waitForTimeout(200);
      await p.reload(); await p.waitForTimeout(2500);
      res.prefs = await p.evaluate(() => localStorage.getItem('prefs'));
      await p.evaluate(() => window.__dbg.open('settings')); await p.waitForTimeout(700);
      res.reduceOn = await p.$eval('[data-sw="reduce"]', e => e.classList.contains('on'));
      await p.click('#wipe'); await p.waitForTimeout(300); await p.screenshot({ path: `${out}/wipe-armed.png` });
      await p.click('#wipe'); await p.waitForTimeout(400); await p.screenshot({ path: `${out}/wipe-dialog.png` });
      await p.click('#cf-ok'); await p.waitForTimeout(1200); await p.screenshot({ path: `${out}/wiped.png` });
      const st = await (await p.request.get(API + 'snapshot')).json();
      res.after = { wallet: st.wallet, level: st.level, swipes: st.swipes, prefs: await p.evaluate(() => localStorage.getItem('prefs')) };
    }
    res[sc] = errors.join(';') || 'ok'; await ctx.close();
  }
  console.log(JSON.stringify(res)); await b.close();
})();
