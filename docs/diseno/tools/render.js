// Renderiza un HTML local a PNG con Chromium (fuentes web vía el proxy del entorno).
// uso: node render.js <archivo.html> <salida.png> [ancho=390] [alto=844] [full=0|1] [escala=2]
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const [html, out, w = '390', h = '844', full = '0', scale = '2'] = process.argv.slice(2);
  const proxy = process.env.HTTPS_PROXY || process.env.https_proxy;
  const browser = await chromium.launch(proxy ? { proxy: { server: proxy } } : {});
  const ctx = await browser.newContext({ viewport: { width: +w, height: +h }, deviceScaleFactor: +scale });
  const page = await ctx.newPage();
  const failed = [];
  page.on('requestfailed', r => failed.push(r.url().slice(0, 120) + ' :: ' + (r.failure() || {}).errorText));
  page.on('console', m => { if (m.type() === 'error') console.log('[console.error]', m.text().slice(0, 200)); });
  await page.goto('file://' + path.resolve(html), { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  await page.screenshot({ path: out, fullPage: full === '1' });
  const fonts = await page.evaluate(() => [...document.fonts].filter(f => f.status === 'loaded').map(f => f.family + ' ' + f.weight));
  console.log('ok', out, '| fuentes cargadas:', [...new Set(fonts)].join(', ') || 'NINGUNA');
  if (failed.length) console.log('fallaron:', failed.slice(0, 6).join(' | '));
  await browser.close();
})();
