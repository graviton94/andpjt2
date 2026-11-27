const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.SMOKE_PLAYWRIGHT || 'playwright-core');

const [endpoint, phase, output, appPackage] = process.argv.slice(2);
const appUrl = 'https://appassets.androidplatform.net/assets/game/index.html?auto=0';
const localUrl = 'http://127.0.0.1:8765/index.html?auto=0';
const adb = (...args) => execFileSync('adb', args, { timeout: 15_000, maxBuffer: 64 * 1024 * 1024 });  // full-screen PNGs pass 1 MB
let stage = 'CDP connection';
const checkpointStage = name => {
  stage = name;
  console.log(`${phase}: ${name}`);
};

async function run() {
  const desktop = endpoint === 'desktop';
  const browser = desktop
    ? await chromium.launch({ executablePath: process.env.SMOKE_CHROMIUM || undefined, args: ['--no-sandbox', '--no-proxy-server', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] })
    : await chromium.connectOverCDP(endpoint, { noDefaults: true, timeout: 25_000 });
  checkpointStage("find WebView page");
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

  page.setDefaultTimeout(18_000);
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', msg => {
    if (msg.type() !== 'error') return;
    const source = msg.location().url;
    // The test page has no browser-tab icon. Check every other resource and script error.
    if (source.endsWith('/favicon.ico')) return;
    errors.push(`${msg.text()} (${source})`);
  });
  if (desktop) await page.goto(localUrl);
  await page.locator('#stage').waitFor();
  // The app opens on the title screen and runs the first-run guide before attendance: mark the guide done,
  // reload, and tap through the title so the balance checks start from the board.
  if (!desktop) {
    if (await page.evaluate(() => localStorage.getItem('ftue') !== '9')) {
      await page.evaluate(() => { localStorage.setItem('ftue', '9'); location.reload(); });
      await page.waitForLoadState('load').catch(() => {});
      await page.locator('#stage').waitFor();
    }
    const go = page.locator('#boot-go');
    // a page click: the remote WebView reports its buttons as off-viewport
    try { await go.waitFor({ state: 'visible', timeout: 15_000 }); await page.evaluate(() => document.getElementById('boot-go').click()); } catch (error) { if (error.name !== 'TimeoutError') throw error; }
  }
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
    checkpointStage('close panel');
    const scrim = page.locator('#scrim.open');
    if (await scrim.count()) await scrim.dispatchEvent('click');
    await page.locator('#scrim.open').waitFor({ state: 'detached' });
  };
  const drag = async (x, y, endX, endY) => {
    if (desktop) {
      await page.mouse.move(x, y);
      await page.mouse.down();
      await page.mouse.move(endX, endY, { steps: 6 });
      await page.mouse.up();
      return;
    }
    const size = [...adb('shell', 'wm', 'size').toString().matchAll(/(\d+)x(\d+)/g)].at(-1);
    assert.ok(size, 'Unable to read emulator display size');
    const viewport = await page.evaluate(() => ({ w: innerWidth, h: innerHeight }));
    const px = x => String(Math.round(x * Number(size[1]) / viewport.w));
    const py = y => String(Math.round(y * Number(size[2]) / viewport.h));
    adb('shell', 'input', 'swipe', px(x), py(y), px(endX), py(endY), '180');
  };
  const open = async kind => {
    checkpointStage(`open ${kind}`);
    if (await page.locator('#sheet.open').count()) await page.locator('#sheet-x').click();
    else if (!(await page.locator('#shade.open').count())) {
      const b = await page.locator('#stage').boundingBox();
      const x = b.x + b.width / 2;
      await drag(x, b.y + b.height * .145, x, b.y + b.height * .36);
    }
    await page.locator('#shade.open').locator(`[data-k="${kind}"]`).click();
    await page.locator('#sheet.open').waitFor();
  };
  const pay = async (button, text, approve = true) => {
    checkpointStage(`confirm ${text} (${approve ? 'accept' : 'cancel'})`);
    await button.click();
    await page.locator('#cf.on').waitFor();
    const confirmation = (await page.locator('#cf-p').innerText()).replace(/[^0-9]/g, '');
    assert.equal(confirmation, String(text));
    await page.locator(approve ? '#cf-ok' : '#cf-no').click();
    await page.locator('#cf.on').waitFor({ state: 'hidden' });
  };
  const waitData = expected => until(async () => (await bm()).data === expected, 'Unexpected data balance');
  const shot = async name => {
    checkpointStage(name);
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
    checkpointStage('claim attendance');
    await until(async () => (await bm()).claimedToday, 'The game did not claim attendance');
    const attendance = page.locator('#sheet.open #sheet-t').getByText('매일 들르기', { exact: true });
    try {
      // The core can record the claim before the page finishes opening the attendance sheet.
      await attendance.waitFor({ timeout: 2500 });
    } catch (error) {
      if (error.name !== 'TimeoutError') throw error;
      await open('attend');
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
    checkpointStage('swipe the seeded sandwich');
    const s = await api('snapshot');
    const row = s.rows.findIndex((r, i) => r.app === 'weather' &&
      s.rows[i - 1]?.app === 'memo' && s.rows[i + 1]?.app === 'memo');
    assert.ok(row >= 0, 'The fixed seed lost its sandwich');
    const b = await page.locator('#stage').boundingBox();
    const dimensions = await page.evaluate(() => ({ w: innerWidth, h: innerHeight }));
    const h = Math.max(740, Math.round(390 * dimensions.h / dimensions.w));
    const y = b.y + (206 + Math.round((h - 740) * .35) + 72 * row) / h * b.height;
    const x = b.x + b.width * .75;
    await page.waitForFunction(({ x, y }) => document.elementFromPoint(x, y)?.id === 'stage', { x, y }, { polling: 100 });
    await drag(x, y, b.x + b.width * .2, y);
    await until(async () => (await api('snapshot')).swipes === s.swipes + 1, 'Swipe was not applied');
    assert.ok((await panel()).bestChain >= 1);
    await shot('07-chain');
    await write('before-restart');
  }

  if (phase === 'reset' || desktop) {
    checkpointStage('restore purchase and progress');
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
    checkpointStage('restore after reset');
    if (desktop) await page.reload();
    assert.deepEqual(await progress(), read('after-reset'));
    await shot('10-reset-relaunch');

    checkpointStage('read offline rewards through the game API');
    const initial = (await bm()).offlineBonus;
    assert.equal(initial.multiplierPermille, 1000);
    assert.equal(initial.rewardActive, false);
    assert.equal(initial.rewardBonusPermille, 700);
    assert.equal(initial.ultraBonusPermille, 500);
    assert.equal((await api('watchAd', { slot: 'CreditBox' })).ok, true);
    const ad = (await bm()).offlineBonus;
    assert.equal(ad.adActive, true);
    assert.ok(ad.adLeftMs > 0 && ad.adLeftMs <= 48 * 3_600_000);
    assert.equal(ad.multiplierPermille, 1700);
    assert.equal((await api('buyPass', { kind: 'month' })).ok, true);
    const pass = (await api('snapshot')).offlineBonus;
    assert.equal(pass.passActive, true);
    assert.ok(pass.passLeftMs > 0);
    assert.equal(pass.multiplierPermille, 1700);
    assert.equal((await api('buyPass', { kind: 'Ultra' })).ok, true);
    const ultra = (await bm()).offlineBonus;
    assert.equal(ultra.ultraActive, true);
    assert.ok(ultra.ultraLeftMs > 0);
    assert.equal(ultra.multiplierPermille, 2550);
    assert.equal(ultra.multiplierPermille, initial.ultraWithPassMultiplierPermille);
    fs.writeFileSync(path.join(output, 'offline-bonus-api.json'),
      JSON.stringify({ initial, ad, pass, ultra }, null, 2));
  }

  if (errors.length) throw new Error(`WebView errors: ${errors.join(' | ')}`);
  console.log(`Verified balance flow: ${phase}`);
  if (desktop) await browser.close();
}

// Disconnect Playwright without issuing Browser.close to the WebView.
run().then(() => process.exit(0), error => { console.error(`${stage}: ${error.message}`); console.error(error); process.exit(1); });
