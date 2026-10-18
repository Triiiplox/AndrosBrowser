// The YouTube flow of Fermata in Chromium (the engine of the Android WebView), at the car's and
// the phone's size: home, search, open a video, Back. Videos do not play in a test browser.
//   FERMATA_CSS="$(cat css)" node tools/screens/youtube-flow.js build/screenshots
// PLAYWRIGHT: path of the playwright module; CHROMIUM: a Chromium to use instead of Playwright's.
const { chromium } = require(process.env.PLAYWRIGHT || 'playwright');
const out = process.argv[2];
const UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) ' +
  'Chrome/154.0.8037.57 Mobile Safari/537.36';
const sizes = {
  car: { viewport: { width: 800, height: 336 }, deviceScaleFactor: 1 },
  phone: { viewport: { width: 393, height: 716 }, deviceScaleFactor: 3 },
};
const log = [];
function step(name, ok, detail) { log.push(`${ok ? 'OK  ' : 'FAIL'} ${name}${detail ? ' - ' + detail : ''}`); }

(async () => {
  const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
  for (const [size, cfg] of Object.entries(sizes)) {
    const ctx = await browser.newContext({ ...cfg, userAgent: UA, isMobile: true, hasTouch: true,
      locale: 'pt-BR', colorScheme: 'dark', timezoneId: 'America/Sao_Paulo' });
    const page = await ctx.newPage();
    const css = process.env.FERMATA_CSS;
    if (css) page.on('load', () => page.addStyleTag({ content: css }).catch(() => {}));
    const shot = async (n) => page.screenshot({ path: `${out}/${size}-yt-${n}.png` });
    try {
      await page.goto('https://m.youtube.com/?hl=pt&gl=BR', { waitUntil: 'domcontentloaded', timeout: 60000 });
      await page.waitForTimeout(6000);
      if (css) await page.addStyleTag({ content: css }).catch(() => {});
      await shot('home');
      step(`${size}: início`, true, await page.title());

      // Search, as the user would: the search button, the query, enter
      // The search entry in the middle of a new account's home, else the magnifier of the top bar
      const entry = page.locator('.search-bar-entry-point-primary-button, button.topbar-button-search-button');
      await entry.first().click({ timeout: 15000 });
      await page.locator('input[name="search_query"]').first().waitFor({ state: 'visible', timeout: 15000 });
      await page.keyboard.type('Iris Goo Goo Dolls');
      await page.keyboard.press('Enter');
      await page.waitForTimeout(6000);
      if (css) await page.addStyleTag({ content: css }).catch(() => {});
      await shot('search');
      step(`${size}: busca`, page.url().includes('results'), page.url());

      // The first video of the results
      // A tap in the middle of the first result's picture, as a finger would
      const item = page.locator('ytm-media-item, ytm-video-with-context-renderer').first();
      const box = await item.boundingBox({ timeout: 15000 });
      await page.touchscreen.tap(box.x + box.width / 2, box.y + Math.min(box.height / 3, 120));
      await page.waitForTimeout(8000);
      await shot('watch');
      step(`${size}: abrir vídeo`, page.url().includes('/watch'), page.url());

      // Back, the way the phone's Back does in a WebView: the page history
      await page.goBack({ timeout: 30000 });
      await page.waitForTimeout(4000);
      await shot('back');
      step(`${size}: voltar`, page.url().includes('results'), page.url());
    } catch (e) {
      step(`${size}: erro`, false, String(e).split('\n').slice(0, 12).join(' | '));
      await shot('error').catch(() => {});
    }
    await ctx.close();
  }
  await browser.close();
  console.log(log.join('\n'));
})();
