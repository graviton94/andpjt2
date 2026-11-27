/* Replay real core receipts against the return card from the screen team's pinned build. */
const assert = require("node:assert/strict");
const { createReadStream, mkdirSync, readFileSync, writeFileSync } = require("node:fs");
const { createServer } = require("node:http");
const { join, resolve, sep } = require("node:path");
const { chromium } = require("playwright");

async function main() {
  const [assets, receipts, output] = process.argv.slice(2);
  const root = resolve(assets);
  mkdirSync(output, { recursive: true });
  const server = createServer((request, response) => {
    const name = new URL(request.url, "http://localhost").pathname;
    const file = resolve(root, "." + name);
    if (!file.startsWith(root + sep)) return response.writeHead(404).end();
    if (file.endsWith(".html")) response.setHeader("Content-Type", "text/html; charset=utf-8");
    if (file.endsWith(".svg")) response.setHeader("Content-Type", "image/svg+xml");
    createReadStream(file).on("error", () => response.writeHead(404).end()).pipe(response);
  });
  await new Promise(ready => server.listen(0, "127.0.0.1", ready));
  let browser;
  try {
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM || "/usr/bin/chromium", args: ["--no-sandbox"] });
    const results = [];
    for (const hours of [6, 15]) {
      const receipt = JSON.parse(readFileSync(join(receipts, `return-${hours}h.json`), "utf8"));
      assert.equal(receipt.awayMs, hours * 3_600_000);
      const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
      const errors = [];
      page.on("pageerror", error => errors.push(error.message));
      await page.addInitScript(state => {
        window.Game = {
          call(method) {
            if (method === "snapshot") return JSON.stringify(state);
            if (method === "bm") return JSON.stringify({ claimedToday: true });
            return JSON.stringify({ ok: false, state });
          },
          buzz() {},
        };
      }, receipt.state);
      await page.goto(`http://127.0.0.1:${server.address().port}/index.html?auto=0`);
      await page.waitForFunction(() => typeof window.onOffline === "function");
      await page.evaluate(payload => window.onOffline(payload), receipt);
      const card = page.locator("#ret.on");
      await card.waitFor({ state: "visible" });
      await page.waitForFunction(amount => document.querySelector("#ret-v")?.textContent === amount, "+" + receipt.paidShort);
      const actual = await card.innerText();
      assert.match(actual, new RegExp(`${hours}시간`));
      assert.match(actual, /상한 14시간 27분/);
      if (hours === 15) {
        assert.match(actual, /EX 추가 인정/);
        assert.match(actual, /2시간 27분/);
        assert.ok(actual.includes("+" + receipt.exExtraPShort));
        assert.match(actual, /상한을 넘긴 33분/);
      } else {
        assert.doesNotMatch(actual, /상한을 넘긴/);
      }
      assert.deepEqual(errors, []);
      await page.screenshot({ path: join(output, `return-${hours}h.png`) });
      results.push({ hours, paidShort: receipt.paidShort, exExtraPShort: receipt.exExtraPShort, actual, errors });
      await page.close();
    }
    writeFileSync(join(output, "rendered.json"), JSON.stringify(results, null, 2) + "\n");
  } finally {
    if (browser) await browser.close();
    server.close();
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
