/**
 * Check the real README's rendered HTML and pictures at phone widths.
 *
 * gh api markdown -f mode=gfm -F text=@README.md > build/readme-preview/body.html
 * node docs/review-evidence/readme-phone-preview.mjs --map-root=../osrs-stars-map --section=farm-runs-in-runelite
 *
 * The website supplies the existing dependency-free Chromium helper. All images are served
 * from this checkout on loopback. This preview uses GitHub-like typography; it is not a Hub
 * page or a game visit. No package installation and no replacement product UI.
 */
import { createServer } from "node:http";
import { readFileSync, mkdirSync, writeFileSync, existsSync } from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import assert from "node:assert/strict";

const arg = (name, fallback) => process.argv.find((a) => a.startsWith(`--${name}=`))?.slice(name.length + 3) ?? fallback;
const root = process.cwd();
const tools = path.resolve(arg("map-root", "../osrs-stars-map"));
const { launchChromium } = await import(pathToFileURL(path.join(tools, "scripts/browser-cdp.mjs")).href);
const output = path.resolve(arg("out", "build/readme-preview"));
const section = arg("section", "farm-runs-in-runelite");
const offset = Number(arg("offset", "-10"));
assert(Number.isFinite(offset));
const expectedImages = (readFileSync("README.md", "utf8").match(/<img /g) ?? []).length;
const body = readFileSync(arg("html", "build/readme-preview/body.html"), "utf8");
const css = `html{color-scheme:light}body{margin:0;font:16px/1.5 -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;background:#fff;color:#1f2328}main{max-width:980px;margin:auto;padding:16px}img{max-width:100%;height:auto}h1,h2{line-height:1.25;border-bottom:1px solid #d1d9e0;padding-bottom:.3em;margin-top:24px}h1{font-size:2em}h2{font-size:1.5em}a{color:#0969da}details{padding:10px;border:1px solid #d1d9e0;border-radius:6px}li{margin:5px 0}code{background:#8882;padding:2px 4px;border-radius:4px}@media(prefers-color-scheme:dark){html{color-scheme:dark}body{background:#0d1117;color:#e6edf3}a{color:#4493f8}h1,h2,details{border-color:#3d444d}}`;
const html = `<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><title>README phone preview</title><style>${css}</style><main>${body}</main><script>for(const h of document.querySelectorAll('h1,h2,h3'))h.id=h.textContent.toLowerCase().replace(/[^a-z0-9 -]/g,'').trim().replace(/ +/g,'-');</script>`;
const server = createServer((req, res) => {
  if (req.url === "/") { res.setHeader("Content-Type", "text/html; charset=utf-8"); res.end(html); return; }
  const filename = path.resolve(root, "." + new URL(req.url, "http://localhost").pathname);
  if (!filename.startsWith(root + path.sep) || !existsSync(filename)) { res.writeHead(404); res.end(); return; }
  res.setHeader("Content-Type", filename.endsWith(".png") ? "image/png" : "image/jpeg");
  res.end(readFileSync(filename));
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
mkdirSync(output, { recursive: true });
const base = "http://127.0.0.1:" + server.address().port;
const { client, close } = await launchChromium({ label: "readme-phones" });
try {
  await client.send("Page.enable");
  await client.send("Runtime.enable");
  for (const width of [320, 390, 736]) for (const theme of ["light", "dark"]) {
    await client.send("Emulation.setDeviceMetricsOverride", { width, height: 900, deviceScaleFactor: 1, mobile: true });
    await client.send("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: theme }] });
    const loaded = client.once("Page.loadEventFired", 20_000);
    await client.send("Page.navigate", { url: base });
    await loaded;
    const checks = await client.send("Runtime.evaluate", {
      expression: `(async()=>{await Promise.all([...document.images].map(i=>i.decode()));document.querySelector('details').open=true;const links=[...document.querySelectorAll('a[href^="#"]')];const focus=document.getElementById(${JSON.stringify(section)});if(!focus)throw Error('Missing section');return {images:document.images.length,overflow:document.documentElement.scrollWidth>innerWidth,missingAnchors:links.filter(a=>!document.getElementById(a.hash.slice(1))).map(a=>a.hash),top:focus.getBoundingClientRect().top+scrollY};})()`,
      awaitPromise: true, returnByValue: true,
    });
    if (checks.exceptionDetails) throw new Error(JSON.stringify(checks.exceptionDetails));
    const got = checks.result.value;
    assert.equal(got.images, expectedImages);
    assert.equal(got.overflow, false);
    assert.deepEqual(got.missingAnchors, []);
    await client.send("Runtime.evaluate", { expression: `scrollTo(0,${got.top + offset})` });
    const shot = await client.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: false });
    writeFileSync(path.join(output, `section-${width}-${theme}.png`), Buffer.from(shot.data, "base64"));
    console.log(JSON.stringify({ width, theme, ...got }));
  }
} finally {
  await close();
  await new Promise((resolve) => server.close(resolve));
}
