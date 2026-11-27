const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const output = process.argv[2];
if (!output) throw new Error('Usage: node launch-v1-return.js <output-dir> [port]');
fs.mkdirSync(output, { recursive: true });
const assert = require('node:assert/strict');
(async () => {
  const root = 'http://127.0.0.1:' + (process.argv[3] || '8765');
  const epoch = Date.now() - 24 * 3_600_000;
  const browser = await chromium.launch({ executablePath:process.env.CHROMIUM_PATH || undefined, args:['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport:{ width:390, height:844 }, locale:'ko-KR' });
    const failures = [];
    page.on('pageerror', e => failures.push(e.message));
    const request = async (path, params = {}) => {
      const q = new URLSearchParams(params);
      return (await page.request.get(root + '/api/' + path + '?' + q)).json();
    };
    await request('new', { seed:11, now:epoch });
    const first = await request('swipe', { id:4, now:epoch+3_000 });
    const burst = first.beats.find(beat => beat.t === 'burst');
    assert.equal(burst.row.region, 'stock');
    assert.equal(burst.tierMax, 1);
    await request('cheat', { level:35, now:epoch+3_000 });
    await request('buyPass', { kind:'month', now:epoch+3_000 });
    const awayMs = 15 * 3_600_000;
    const returned = await request('offline',{ ms:awayMs, now:epoch+3_000+awayMs });
    assert.equal(returned.awayMs, awayMs);
    assert.equal(returned.offlineCapMs, 12 * 3_600_000);
    assert.equal(returned.offlineBonus.multiplierPermille, 1700);
    assert(BigInt(returned.paid) > 0n);
    await page.goto(root+'/index.html?notitle&auto=0&lang=ko');
    await page.evaluate(response => showReturn(response), returned);
    await page.locator('#ret.on .warn').waitFor();
    await page.waitForFunction(amount => document.querySelector('#ret-v').textContent === '+' + amount, returned.paidShort);
    const body = await page.locator('#ret.on').innerText();
    assert.match(body,/15시간/);
    assert.match(body,/상한 12시간/);
    assert.match(body,/3시간/);
    assert.deepEqual(failures, []);
    await page.screenshot({ path:path.join(output, '15h-return.png') });
    const claim = await request('watchAd', { slot:'OfflineDouble', now:epoch+3_000+awayMs });
    const refused = await request('watchAd', { slot:'OfflineDouble', now:epoch+3_000+awayMs });
    assert.equal(claim.ok,true);
    assert.equal(refused.ok,false);
    const result = {awayMs, capMs:returned.offlineCapMs, message:body, paid:returned.paid, bonusPaid:returned.bonusPaid, qualifiedOfferGranted:claim.ok,
      replayGranted:refused.ok, burst, browserErrors:failures};
    fs.writeFileSync(path.join(output, 'verification.json'), JSON.stringify(result, null, 2)+'\n');
    process.stdout.write(JSON.stringify(result) + '\n');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exit(1); });
