// Renders every synth sound in docs/design/sounds.json from the live game page (real WebAudio graph, echo included)
// to docs/design/sounds/<id>.wav; build_board.py turns them into MP3s for the board. Needs the dev server on :8765.
const { chromium } = require('playwright'); const fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..', '..'), OUT = path.join(ROOT, 'docs/design/sounds'); fs.mkdirSync(OUT, { recursive: true });
const reg = JSON.parse(fs.readFileSync(path.join(ROOT, 'docs/design/sounds.json'), 'utf8'));
const wav = (pcm, sr, f) => { const n = pcm.length, b = Buffer.alloc(44 + n * 2); b.write('RIFF', 0); b.writeUInt32LE(36 + n * 2, 4); b.write('WAVEfmt ', 8); b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(sr, 24); b.writeUInt32LE(sr * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(n * 2, 40); for (let i = 0; i < n; i++) b.writeInt16LE(Math.max(-32767, Math.min(32767, Math.round(pcm[i] * 32767))), 44 + i * 2); fs.writeFileSync(f, b); };
(async () => {
  const b = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required'] }); const p = await b.newPage();
  await p.addInitScript(() => localStorage.setItem('prefs', JSON.stringify({ sound: true, music: false, vol: 1 })));
  await p.goto('http://127.0.0.1:8765/index.html?notitle&noftue&noattend&auto=0&lang=ko'); await p.waitForTimeout(1500);
  const synth = reg.groups.flatMap(g => g.items).filter(i => i.kind === 'synth');
  for (const it of synth) {
    const secs = /siren|reel|boil|steam|payoff|jackpot|wave|pad|unit|stamp|heart|powerDown|boot/.test(it.call) ? 2.4 : 1.1;
    const { pcm, sr } = await p.evaluate(async ([call, secs]) => {
      const { A, sfx } = __dbg.audio(); const c = A.c; const sp = c.createScriptProcessor(2048, 1, 1); const buf = [];
      sp.onaudioprocess = e => buf.push(Array.from(e.inputBuffer.getChannelData(0))); A.comp.connect(sp); sp.connect(c.destination);
      await new Promise(r => setTimeout(r, 60)); new Function('sfx', 'sfx.' + call)(sfx);
      await new Promise(r => setTimeout(r, secs * 1000)); A.comp.disconnect(sp); sp.disconnect();
      return { pcm: buf.flat(), sr: c.sampleRate };
    }, [it.call, secs]);
    wav(pcm, sr, path.join(OUT, it.id + '.wav')); console.log(it.id, (pcm.length / sr).toFixed(2) + 's');
  }
  await b.close();
})();
