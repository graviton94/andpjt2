const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.SMOKE_PLAYWRIGHT || 'playwright-core');

const [endpoint, phase, output, appPackage] = process.argv.slice(2);
const appUrl = 'https://appassets.androidplatform.net/assets/game/index.html?auto=0';
const localUrl = 'http://127.0.0.1:8765/index.html?auto=0';
const adb = (...args) => execFileSync('adb', args, { timeout: 15_000 });

async function run() {
  const desktop = endpoint === 'desktop';
  const browser = desktop
    ? await chromium.launch({ executablePath: process.env.SMOKE_CHROMIUM || undefined, args: ['--no-sandbox', '--no-proxy-server', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] })
    : await chromium.connectOverCDP(endpoint, { noDefaults: true });
  let page;
  if (desktop) {
    page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  } else {
    const [context] = browser.contexts();
    assert.ok(context, 'Could not open the default WebView context');
    page = context.pages().find(candidate => candidate.url() === appUrl);
    if (!page) page = await context.waitForEvent('page', {
      predicate: candidate => candidate.url() === appUrl, timeout: 5_000,
    });
  }

  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', msg => {
    if (msg.type() !== 'error') return;
    const source = msg.location().url;
    // Chromium asks for a browser-tab icon on desktop, which the app asset does not provide.
    if (desktop && source.endsWith('/favicon.ico')) return;
    errors.push(`${msg.text()} (${source})`);
  });
  if (desktop) await page.goto(localUrl);
  await page.locator('#stage').waitFor();
  if (!desktop) assert.equal(await page.evaluate(() => typeof window.Game.call), 'function');
  const api = (method, args = {}) => page.evaluate(async ({ method, args }) => {
    if (window.Game) return JSON.parse(window.Game.call(method, JSON.stringify(args)));
    const q = new URLSearchParams(args);
    return (await fetch('/api/' + method + '?' + q)).json();
  }, { method, args });
  const bm = () => api('bm');
  const panel = () => api('panel');
  const until = async (predicate, description) => {
    const end = Date.now() + 12_000;
    while (Date.now() < end) {
      if (await predicate()) return;
      await new Promise(resolve => setTimeout(resolve, 150));
    }
    assert.fail(description);
  };
  const attr = (name, value) => page.locator(`[${name}="${value}"]`);
  const act = name => attr('data-act', name);
  const close = async () => {
    if (await page.locator('#scrim.open').count()) await page.locator('#scrim').click({ position: { x: 3, y: 3 } });
    await page.locator('#scrim.open').waitFor({ state: 'detached' });
  };
  const open = async kind => {
    if (await page.locator('#sheet.open').count()) await page.locator('#sheet-x').click();
    else if (!(await page.locator('#shade.open').count())) {
      const b = await page.locator('#stage').boundingBox();
      const x = b.x + b.width / 2;
      await page.mouse.move(x, b.y + b.height * .145);
      await page.mouse.down();
      await page.mouse.move(x, b.y + b.height * .36, { steps: 5 });
      await page.mouse.up();
    }
    await page.locator('#shade.open').locator(`[data-k="${kind}"]`).click();
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
  const waitData = expected => until(async () => (await bm()).data === expected, 'Unexpected data balance');
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
    await until(async () => (await bm()).claimedToday, 'The game did not claim attendance');
    if (!(await page.locator('#sheet.open').count())) await open('attend');
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
    await until(async () => (await bm()).premium.find(a => a.id === 'alarm').stars === 1, 'Voucher did not grant a star');
    const preStar = await bm();
    const memo = preStar.premium.find(a => a.id === 'memo');
    await pay(premium('memo'), memo.price);
    await waitData(preStar.data - memo.price);
    assert.equal((await bm()).premium.find(a => a.id === 'memo').stars, 1);
    await shot('05-premium');

    await open('deck');
    await attr('data-in', 'recorder').click();
    await attr('data-out', 'recorder').waitFor();
    await shot('06-deck');

    await close();
    const s = await api('snapshot');
    const row = s.rows.findIndex((r, i) => r.app === 'weather' &&
      s.rows[i - 1]?.app === 'memo' && s.rows[i + 1]?.app === 'memo');
    assert.ok(row >= 0, 'The fixed seed lost its sandwich');
    const b = await page.locator('#stage').boundingBox();
    const dimensions = await page.evaluate(() => ({ w: innerWidth, h: innerHeight }));
    const h = Math.max(740, Math.round(390 * dimensions.h / dimensions.w));
    const y = b.y + (206 + Math.round((h - 740) * .35) + 72 * row) / h * b.height;
    const x = b.x + b.width * .75;
    await page.waitForFunction(({ x, y }) => document.elementFromPoint(x, y)?.id === 'stage', { x, y });
    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(b.x + b.width * .2, y, { steps: 6 });
    await page.mouse.up();
    await until(async () => (await api('snapshot')).swipes === s.swipes + 1, 'Swipe was not applied');
    assert.ok((await panel()).bestChain >= 1);
    await shot('07-chain');
    await write('before-restart');
  }

  if (phase === 'reset' || desktop) {
    if (desktop) await page.reload();
    assert.deepEqual(await progress(), read('before-restart'));
    await shot('08-relaunch');
    await open('reset');
    const pre = await progress();
    await act('reset').click();
    await page.getByRole('button', { name: /정말 초기화할까요/ }).waitFor();
    await act('reset').click();
    await until(async () => (await panel()).resets === pre.resets + 1, 'Factory reset did not pay');
    const post = await progress();
    assert.ok(post.files > pre.files);
    assert.equal(post.level, 1);
    assert.equal(post.wallet, '0');
    assert.deepEqual(post.account, pre.account);
    await shot('09-factory-reset');
    await write('after-reset');
  }

  if (phase === 'verify' || desktop) {
    if (desktop) await page.reload();
    assert.deepEqual(await progress(), read('after-reset'));
    await shot('10-reset-relaunch');
  }

  assert.deepEqual(errors, []);
  console.log(`Verified balance flow: ${phase}`);
  if (desktop) await browser.close();
}

// Disconnect Playwright without issuing Browser.close to the WebView.
run().then(() => process.exit(0), error => { console.error(error); process.exit(1); });
