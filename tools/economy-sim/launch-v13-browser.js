const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const output = process.argv[2];
if (!output) throw new Error('Usage: node launch-v13-browser.js <output-dir> [port]');
const url = `http://127.0.0.1:${process.argv[3] || 8765}`;

function assertStable(rows) {
  const meldable = row => row.kind === 'App' || row.kind === 'Wild';
  for (let i = 1; i < rows.length; i++) {
    const [a, b] = [rows[i - 1], rows[i]];
    if (meldable(a) && meldable(b)) {
      assert(a.kind !== 'Wild' && b.kind !== 'Wild' && a.app !== b.app,
        `Unresolved board contact ${a.id}:${a.app} / ${b.id}:${b.app}`);
    }
  }
}

(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_PATH || undefined,
    args: ['--no-sandbox', '--no-proxy-server']
  });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, locale: 'ko-KR', reducedMotion: 'reduce' });
    const errors = [];
    page.on('pageerror', e => { errors.push(e.message); console.error(e.message); });
    const api = async (method, query = {}) => {
      const response = await page.request.get(`${url}/api/${method}?${new URLSearchParams(query)}`);
      assert(response.ok(), `${method} returned ${response.status()}`);
      return response.json();
    };
    const seed = 11;
    let now = Date.now();
    await api('new', { seed, now });
    let board = await api('cheat', { level: 7, now });
    let warmup = 0;
    while (board.gauge < 90 && warmup < 120) {
      now += 6_000;
      const { id } = await api('hint', { now });
      const result = await api('swipe', { id, now });
      assert(result.ok);
      board = result.state;
      assertStable(board.rows);
      warmup++;
    }
    assert(board.gauge >= 90, 'Fixture did not charge the gauge');
    await page.goto(`${url}/index.html?auto=0&lang=ko&noattend`);
    await page.evaluate(() => localStorage.setItem('ftue', '9'));
    await page.reload();
    await page.locator('#boot-go').click();
    await page.locator('#boot').waitFor({ state: 'hidden' });
    await page.waitForFunction(() => window.__dbg && JSON.parse(window.__dbg.flags()).S);

    const swipes = [];
    let sawWave = false;
    for (let index = 0; index < 25; index++) {
      now += 6_000;
      const { id } = await api('hint', { now });
      const response = await api('swipe', { id, now });
      assert(response.ok);
      const reason = response.beats.find(beat => beat.t === 'chainEnd')?.reason ?? null;
      if (reason === 'wave') {
        sawWave = true;
        const lastWave = response.beats.findLastIndex(beat => beat.t === 'wave');
        assert(lastWave >= 0 && lastWave < response.beats.findIndex(beat => beat.t === 'chainEnd'));
      }
      await page.evaluate(result => play(I18N.localizeCore(result), true), response);
      try {
        await page.waitForFunction(() => !JSON.parse(window.__dbg.flags()).boardBusy);
      } catch (error) {
        const state = await page.evaluate(() => ({ frameNo, stop, slow, pending: timeline.map(e => e.at), flags: window.__dbg.flags() }));
        throw new Error(`Replay ${index + 1} (${reason}): ${error.message} ${JSON.stringify(state)}`);
      }
      const scene = await page.evaluate(() => ({ rows: window.__dbg.rows(), replay: window.__lastPlay }));
      const visual = scene.rows.map(row => [row.id, row.kind, row.app ?? null]);
      const core = response.state.rows.map(row => [row.id, row.kind, row.app ?? null]);
      assert.deepEqual(visual, core, `Swipe ${index + 1}`);
      assertStable(scene.rows);
      assert(scene.replay.t + 2.38 <= 6, `Replay exceeds six seconds on swipe ${index + 1}`);
      swipes.push({ swipe: index + 1, chain: response.chain, reason, replaySeconds: scene.replay.t, rowIds: core.map(row => row[0]) });
    }
    assert(sawWave, 'Fixture needs to include a wave');
    assert.deepEqual(errors, []);
    fs.mkdirSync(output, { recursive: true });
    fs.writeFileSync(path.join(output, 'board-replay.json'), JSON.stringify({ seed, warmup, swipes, errors }, null, 2) + '\n');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
