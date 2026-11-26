const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const out = process.argv[2];
if (!out) throw new Error('Usage: node launch-v11-pass.js <output-dir> [port]');
fs.mkdirSync(out, { recursive: true });
const root = 'http://127.0.0.1:' + (process.argv[3] || '8765');
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({viewport:{width:390,height:844}, locale:'ko-KR'});
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.request.get(root+'/api/new?seed=11');
    await page.goto(root+'/index.html?auto=0&lang=ko&noftue&noattend');
    await page.waitForFunction(() => window.__dbg);
    const bm = await (await page.request.get(root+'/api/bm')).json();
    const registered = JSON.parse(fs.readFileSync(path.join(__dirname, '../play/products.json')));
    const apiProducts = [...bm.packs.map(p=>[p.id,p.won]), [bm.starterProductId,bm.starterWon],
        [bm.rookieProductId,bm.rookieWon],
        ...Object.entries(bm.passProductIds).map(([k,id])=>[id,bm.passValues[k].wonPrice])];
    assert.deepEqual(Object.fromEntries(apiProducts), Object.fromEntries(
      [...registered.inapp,...registered.subs].map(p=>[p.id,p.krw])));
    assert.equal(apiProducts.length,11);
    await page.evaluate(() => window.__dbg.open('pass'));
    const cards = page.locator('.passc');
    await cards.last().waitFor();
    assert.equal(await cards.count(),3);
    const shown = await cards.allTextContents();
    assert.match(shown[0], /최대\s*×8\.4/);
    assert.match(shown[0], /2,100/);
    assert.match(shown[0], /46,200/);
    assert.match(shown[0], /90시간/);
    assert.doesNotMatch(shown[0], /×22\.8/);
    assert.match(shown[1], /×1\.4/);
    assert.match(shown[2], /×1\.8/);
    await page.screenshot({path:out+'/pass-cards.png'});
    await page.evaluate(() => window.__dbg.open('data'));
    const dataText = await page.locator('#sheet-b').textContent();
    assert.match(dataText,/10\.2/);
    assert.match(dataText,/7\.1/);
    assert.deepEqual(errors,[]);
    const result = {
      passValues:bm.passValues, starterValueX:bm.starterValueX, rookieValueX:bm.rookieValueX,
      renderedCaptions:['최대 ×8.4 가치','최대 ×1.4 가치','최대 ×1.8 가치'],
      renderedMonth:['2,100 데이터','₩46,200','90시간'],
      renderedStarterAndRookie:true, verifiedPlayIdsAndPrices:Object.fromEntries(apiProducts), pageErrors:errors
    };
    fs.writeFileSync(out+'/pass-values.json',JSON.stringify(result,null,2)+'\n');
    console.log('Three pass cards, two introductory offers and 11 Play IDs/amounts verified');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1});
