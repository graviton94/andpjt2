/* Exercise the actual return handler with two receipts emitted by CadenceContractTest. */
const { chromium } = require("playwright");
const { createReadStream, readFileSync, writeFileSync, mkdirSync } = require("node:fs");
const { resolve, dirname, sep, join } = require("node:path");
const { createServer } = require("node:http");
const assert = require("node:assert/strict");

async function main() {
  const [oldPage, candidatePage, receipts, output] = process.argv.slice(2);
  mkdirSync(output, { recursive: true });
  const roots = { current: dirname(resolve(oldPage)), candidate: dirname(resolve(candidatePage)) };
  const server = createServer((req, response) => {
    const pathname = new URL(req.url, "http://localhost").pathname;
    const match = /^\/(current|candidate)\/(.*)$/.exec(pathname);
    const root = match && roots[match[1]];
    const file = root && resolve(root, match[2]);
    if (!file || !file.startsWith(root + sep)) return response.writeHead(404).end();
    if (file.endsWith(".html")) response.setHeader("Content-Type", "text/html; charset=utf-8");
    if (file.endsWith(".svg")) response.setHeader("Content-Type", "image/svg+xml");
    createReadStream(file).on("error", () => response.writeHead(404).end()).pipe(response);
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const browser = await chromium.launch({
    executablePath: process.env.CHROMIUM || "/usr/bin/chromium",
    args: ["--no-sandbox", "--allow-file-access-from-files"],
  });
  const results = [];
  try {
    for (const version of ["current", "candidate"]) {
      for (const hours of [6, 15]) {
        const json = JSON.parse(readFileSync(join(receipts, "return-" + hours + "h.json"), "utf8"));
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
        }, json.state);
        await page.goto(`http://127.0.0.1:${server.address().port}/${version}/index.html?auto=0`, { waitUntil: "load" });
        await page.waitForFunction(() => typeof window.onOffline === "function");
        await page.evaluate(receipt => window.onOffline(receipt), json);
        await page.waitForTimeout(350);
        const panel = page.locator("#offline-ex-summary");
        const visible = await panel.isVisible();
        const actual = visible ? (await panel.innerText()).split("\n") : [];
        const cap = "EX 정산 상한 +147분";
        const credited = "이번 추가 인정 " + (hours === 6 ? "0" : "147") + "분 · +" + json.exExtraPShort + " P";
        assert.deepEqual(errors, []);
        assert.equal(visible, version === "candidate");
        assert.equal(actual.includes(cap), version === "candidate");
        assert.equal(actual.includes(credited), version === "candidate");
        if (version === "candidate") {
          await page.screenshot({ path: join(output, "return-" + hours + "h.png") });
        }
        results.push({ version, hours, actual, expectedExtraP: json.exExtraPShort });
        await page.close();
      }
    }
    writeFileSync(join(output, "rendered.json"), JSON.stringify(results, null, 2) + "\n");
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch(error => {
  process.stderr.write(String(error) + "\n");
  process.exitCode = 1;
});
