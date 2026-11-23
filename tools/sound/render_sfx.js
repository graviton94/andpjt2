// Renders the demo's WebAudio synth (docs/bible/20-sound.md) to OGG files: the first-pass game SFX.
// Usage: node tools/sound/render_sfx.js <renderer.html> <out dir>
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');
const [page_, out] = process.argv.slice(2);
const jobs = [
  ['swipe', 'hiss(.12,.35,{f0:2000,f1:5000,q:1.2,a:.01})', .5],
  ['merge', "tone('square',1800,1800,.02,.05,{filt:6000});tone('sine',300,180,.12,.4)", .5],
  ...Array.from({ length: 16 }, (_, i) => [`pop.${i + 1}`, `sfx.pop(${i + 1})`, 1]),
  ...[1, 4, 8, 12].map(k => [`chip.${k}`, `sfx.chip(${k})`, .5]),
  ...[1, 4, 8, 12].map(k => [`mult.${k}`, `sfx.mult(${k})`, .6]),
  ['badge', 'sfx.badge()', .6], ['whoosh', 'sfx.whoosh()', .6],
  ...[1, 3, 5, 8].map(k => [`payoff.${k}`, `sfx.payoff(${k})`, 1.6]),
  ...[0, .5, 1].map(p => [`coin.${p}`, `sfx.coin(${p})`, .3]),
  ['land', 'sfx.land()', .6], ['unit', 'sfx.unit()', 1.8],
  ...[0, 1, 2].map(w => [`wave.${w + 1}`, `sfx.wave(${w})`, 1.2]),
  ['stamp', 'sfx.stamp()', 1.2], ['snap', 'sfx.snapback ? sfx.snapback() : sfx.snap()', .4],
  ['overheat.beep', 'sfx.beep(.5)', .3], ['overheat.boil', 'sfx.boil()', 1], ['overheat.steam', 'sfx.steam()', 1.8],
  ['siren', 'sfx.siren(1.7)', 2.2], ['drill.tap', 'sfx.drill(3)', .3], ['glitch', 'sfx.glitch()', .8],
  ['powerdown', 'sfx.powerDown()', .8], ['boot', 'sfx.boot()', 1.2], ['ring', 'sfx.ring()', .8],
  ['smash', 'sfx.smash()', 1], ['heart', 'sfx.heart()', .7], ['zap', 'sfx.zap()', 1.2],
  ['reel', 'sfx.reel()', .2], ['jackpot', 'sfx.jackpot()', 1.8], ['fail', 'sfx.fail()', 2.2],
  ['dnd.pad', 'sfx.pad()', 3.8], ['soft', 'sfx.soft()', .8],
];
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.goto('file://' + path.resolve(page_));
  fs.mkdirSync(out, { recursive: true });
  for (const [id, call, secs] of jobs) {
    const bytes = await p.evaluate(async ([c, s]) => window.render(() => eval(c), s), [call, secs]);
    const raw = path.join(out, id + '.raw');
    fs.writeFileSync(raw, Buffer.from(bytes));
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-f', 's16le', '-ar', '48000', '-ac', '1', '-i', raw, '-c:a', 'libvorbis', '-q:a', '4', path.join(out, id + '.ogg')]);
    fs.unlinkSync(raw);
  }
  console.log('rendered', jobs.length);
  await b.close();
})();
