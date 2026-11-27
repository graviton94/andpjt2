const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const output = process.argv[2];
if (!output) throw new Error('Usage: node launch-v12-browser.js <output-dir> [port]');
const root = 'http://127.0.0.1:' + (process.argv[3] || '8765');

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, locale: 'ko-KR' });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const request = async (method, query = {}) =>
      (await page.request.get(root + '/api/' + method + '?' + new URLSearchParams(query))).json();
    const continuesAfterFall = result => {
      const arrivals = new Set(result.beats.filter(beat => beat.t === 'spawn').map(beat => beat.row.id));
      return result.beats.some(beat => beat.t === 'burst' && arrivals.has(beat.row.id));
    };
    let fixtureSeed;
    for (const seed of [7, ...Array.from({ length: 32 }, (_, index) => index + 1).filter(seed => seed !== 7)]) {
      await request('new', { seed });
      if (continuesAfterFall(await request('swipe', { id: 4 }))) {
        fixtureSeed = seed;
        break;
      }
    }
    assert(fixtureSeed, 'Expected one fixed seed with a falling continuation');
    await request('new', { seed: fixtureSeed });
    await page.goto(root + '/index.html?notitle&auto=0&lang=ko&noftue&noattend');
    await page.waitForFunction(() => window.__dbg && JSON.parse(window.__dbg.flags()).S);
    const swipe = await request('swipe', { id: 4 });
    assert(swipe.ok);
    const falling = swipe.beats.filter(beat => beat.t === 'spawn');
    assert(falling.length >= 2);
    assert(continuesAfterFall(swipe));
    assert.equal(swipe.beats.filter(beat => beat.t === 'chainEnd').length, 1);
    await page.evaluate(result => play(result), swipe);
    await page.waitForFunction(() => !JSON.parse(window.__dbg.flags()).boardBusy);
    const boardIds = await page.evaluate(() => window.__dbg.rows().map(row => row.id));
    assert.deepEqual(boardIds, swipe.state.rows.map(row => row.id));

    await page.evaluate(async () => {
      await window.__dbg.open('pass');
      window.__dbg.auto(true);
    });
    let autoRequests = 0;
    page.on('request', request => {
      if (new URL(request.url()).pathname === '/api/auto') autoRequests++;
    });
    await page.waitForTimeout(5_000);
    assert.equal(autoRequests, 0);
    const startedAt = Date.now();
    const firstAuto = page.waitForRequest(request => new URL(request.url()).pathname === '/api/auto');
    await page.evaluate(() => {
      closeAll();
      idleT = 0;
    });
    await firstAuto;
    const elapsed = Date.now() - startedAt;
    assert(elapsed >= 3_950, 'Automatic swipe began before four seconds');
    assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(output, 'falling-swipe.json'), JSON.stringify(swipe, null, 2)+'\n');
    fs.writeFileSync(path.join(output, 'browser-verification.json'), JSON.stringify({
      seed: fixtureSeed, finalBoardMatches: true, pageErrors: errors, autoRequestsDuringOpenSheet: 0,
      measuredIdleBeforeAutoMs: elapsed, spawnCount: falling.length,
    }, null, 2)+'\n');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
