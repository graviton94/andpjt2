const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.SMOKE_PLAYWRIGHT || 'playwright-core');

const [endpoint, phase, output, appPackage] = process.argv.slice(2);
const appUrl = 'https://appassets.androidplatform.net/assets/game/index.html';
const localUrl = 'http://127.0.0.1:8765/index.html?auto=0';
const adb = (...args) => execFileSync('adb', args, { timeout: 15_000 });

async function run() {
  const desktop = endpoint === 'desktop';
  const browser = desktop
    ? await chromium.launch({ executablePath: process.env.SMOKE_CHROMIUM || undefined, args: ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] })
    : await chromium.connectOverCDP(endpoint);
  let page;
  if (desktop) {
    page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    await page.goto(localUrl);
  } else {
    assert.equal(browser.contexts().length, 1);
    page = await browser.contexts()[0].waitForEvent('page', {
      predicate: candidate => candidate.url() === appUrl, timeout: 750,
    }).catch(() => browser.contexts()[0].pages().find(candidate => candidate.url() === appUrl));
    assert.ok(page, 'Could not find the game WebView');
  }

  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
  await page.waitForFunction(() => typeof S !== 'undefined' && S && S.rows.length > 0);
  if (!desktop) assert.equal(await page.evaluate(() => typeof window.Game.call), 'function');
  await page.evaluate(() => { autoOn = false; });
  const bm = () => page.evaluate(() => api('bm'));
  const panel = () => page.evaluate(() => api('panel'));
  const attr = (name, value) => page.locator(`[${name}="${value}"]`);
  const act = name => attr('data-act', name);
  const quiet = async () => {
    await page.waitForFunction(() => !boardBusy && !ev);
  };
  const open = async kind => {
    await page.evaluate(async kind => { closeAll(); await loadPanel(); await openSheet(kind); }, kind);
    await page.locator('#sheet.open').waitFor();
  };
  const pay = async (button, text, approve = true) => {
    await button.click();
    await page.locator('#cf.on').waitFor();
    const confirmation = (await page.locator('#cf-p').innerText()).replace(/[^0-9]/g, '');
    assert.equal(confirmation, String(text));
    await page.locator(approve ? '#cf-ok' : '#cf-no').click();
    await page.locator('#cf.on').waitFor({ state: 'hidden' });
  };
  const waitData = expected => page.waitForFunction(async expected => {
    const current = await api('bm');
    return current.data === expected && P?.bm?.data === expected;
  }, expected);
  const shot = async name => {
    if (desktop) {
      await page.screenshot({ path: path.join(output, name + '.png') });
      return;
    }
    fs.writeFileSync(path.join(output, name + '.png'), adb('exec-out', 'screencap', '-p'));
    const save = adb('exec-out', 'run-as', appPackage, 'cat', 'files/save.json');
    assert.equal(JSON.parse(save).save, 1);
    fs.writeFileSync(path.join(output, name + '.json'), save);
  };
  const account = async () => {
    const m = await bm();
    return {
      data: m.data, firstData: m.firstData, stars: m.premium.filter(a => a.stars).map(a => [a.id, a.stars]),
      streak: m.streak, vouchers: m.vouchers, starterBought: m.starterLeftMs === 0,
    };
  };
  const progress = async () => {
    const p = await panel();
    return {
      account: await account(), wallet: p.state.wallet, level: p.state.level,
      deck: p.apps.filter(a => a.active).map(a => a.id), files: p.files, resets: p.resets,
    };
  };
  const checkpoint = name => path.join(output, name + '-checkpoint.json');
  const read = name => JSON.parse(fs.readFileSync(checkpoint(name), 'utf-8'));
  const write = async name => fs.writeFileSync(checkpoint(name), JSON.stringify(await progress(), null, 2));
  const pack = attr('data-pack', 'data_1200');
  const premium = app => act('premium').and(attr('data-id', app));

  if (phase === 'purchase' || desktop) {
    await open('attend');
    if (!(await bm()).claimedToday) {
      await act('attend').click();
      await page.waitForFunction(() => P?.bm?.claimedToday);
    }
    assert.equal((await bm()).streak, 1);
    await shot('00-attendance');
    await open('data');
    const initial = (await bm()).data;
    await pay(pack, 12000, false);
    assert.equal((await bm()).data, initial);
    await pay(pack, 12000);
    await waitData(initial + 2400);
    assert.equal((await bm()).firstData, false);
    await shot('01-first-pack');
    await pay(pack, 12000);
    await waitData(initial + 3600);
    await shot('02-second-pack');
    const before = await bm();
    await pay(act('buyStarter'), 2500);
    await waitData(before.data + 500);
    const after = await bm();
    assert.equal(after.vouchers, before.vouchers + 1);
    assert.equal(after.items.find(item => item.id === 'Boost').tickets, before.items.find(item => item.id === 'Boost').tickets + 3);
    assert.equal(after.items.find(item => item.id === 'Overclock').tickets, before.items.find(item => item.id === 'Overclock').tickets + 3);
    await shot('03-starter');

    const warp = after.items.find(item => item.id === 'Warp');
    assert.equal(warp.tickets, 0);
    await pay(act('buyItem').and(attr('data-item', 'Warp')), warp.price);
    await waitData(after.data - warp.price);
    assert.equal((await bm()).items.find(item => item.id === 'Warp').left, warp.left - 1);
    await shot('04-data-item');

    await open('premium');
    await premium('alarm').click(); // Voucher payment.
    await page.waitForFunction(() => P?.bm?.premium.find(a => a.id === 'alarm').stars === 1);
    const preStar = await bm();
    const memo = preStar.premium.find(a => a.id === 'memo');
    await pay(premium('memo'), memo.price);
    await waitData(preStar.data - memo.price);
    assert.equal((await bm()).premium.find(a => a.id === 'memo').stars, 1);
    await shot('05-premium');

    await open('deck');
    await attr('data-in', 'recorder').click();
    await page.waitForFunction(() => P?.apps.some(app => app.id === 'recorder' && app.active));
    await shot('06-deck');

    await page.evaluate(() => closeAll());
    await quiet();
    const swipe = await page.evaluate(async () => {
      const s = await api('snapshot');
      const row = byId(s.rows.find(r => r.app === 'weather' &&
        s.rows[s.rows.indexOf(r) - 1]?.app === 'memo' &&
        s.rows[s.rows.indexOf(r) + 1]?.app === 'memo').id);
      const b = cv.getBoundingClientRect();
      return { before: s.swipes, x: b.left + b.width * .75, dx: b.width * .55, y: b.top + (row.y.x + CH / 2) / H * b.height };
    });
    await page.mouse.move(swipe.x, swipe.y);
    await page.mouse.down();
    await page.mouse.move(swipe.x - swipe.dx, swipe.y, { steps: 6 });
    await page.mouse.up();
    await page.waitForFunction(async before => (await api('snapshot')).swipes === before + 1, swipe.before);
    await quiet();
    assert.ok((await panel()).bestChain >= 1);
    await shot('07-chain');
    await write('before-restart');
  }

  if (phase === 'reset' || desktop) {
    assert.deepEqual(await progress(), read('before-restart'));
    await shot('08-relaunch');
    await open('reset');
    const pre = await progress();
    await act('reset').click();
    await page.getByRole('button', { name: /정말 초기화할까요/ }).waitFor();
    await act('reset').click();
    await page.waitForFunction(async count => (await api('panel')).resets === count + 1, pre.resets);
    const post = await progress();
    assert.ok(post.files > pre.files);
    assert.equal(post.level, 1);
    assert.equal(post.wallet, '0');
    assert.deepEqual(post.account, pre.account);
    await shot('09-factory-reset');
    await write('after-reset');
  }

  if (phase === 'verify' || desktop) {
    assert.deepEqual(await progress(), read('after-reset'));
    await shot('10-reset-relaunch');
  }

  assert.deepEqual(errors, []);
  console.log(`Verified balance flow: ${phase}`);
  if (desktop) await browser.close();
}

// Disconnect Playwright without issuing Browser.close to the WebView.
run().then(() => process.exit(0), error => { console.error(error); process.exit(1); });
