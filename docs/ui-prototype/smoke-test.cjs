// 可选冒烟测试:PLAYWRIGHT_MODULE 指向本机已安装的 playwright 模块。
// 运行: node smoke-test.cjs。不向 Android 项目安装依赖。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { pathToFileURL } = require('node:url');
const path = require('node:path');
const assert = require('node:assert/strict');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1520, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  const failedReq = [];
  page.on('requestfailed', r => failedReq.push(r.url()));
  page.on('request', r => { if (!r.url().startsWith('file:') && !r.url().startsWith('data:')) failedReq.push(r.url()); });

  await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
  const go = p => page.locator(`#bottomNav [data-page="${p}"]`).click();
  const act = a => page.locator(`[data-action="${a}"]`).first().click();
  const header = () => page.locator('#appHeader').innerText();

  // 1. 首页默认
  assert.match(await header(), /首页/);
  assert.equal(await page.locator('.hero .big').count(), 1, 'hero amount');
  assert.equal(await page.locator('.txn').count() > 5, true, 'txn rows');
  assert.equal(await page.locator('#fab').isVisible(), true, 'fab on home');

  // 2. 五个 Tab 可达
  for (const p of ['calendar', 'assets', 'stats', 'me', 'home']) await go(p);
  assert.match(await header(), /首页/);

  // 3. 日历选日
  await go('calendar');
  const before = await page.locator('.day.sel').innerText();
  await page.locator('.day:not(.dim)').first().click();
  assert.notEqual(await page.locator('.day.sel').innerText(), before, 'selection changes');

  // 4. 统计时间段切换 → 数字变化
  await go('stats');
  const monthTxt = await page.locator('.overview .o').first().innerText();
  await page.locator('[data-action="stats-range"][data-range="year"]').click();
  assert.notEqual(await page.locator('.overview .o').first().innerText(), monthTxt, 'range changes totals');

  // 5. 记一笔:键盘 → 金额 → 分类 → 保存
  await act('new-entry');
  assert.match(await header(), /记一笔/);
  for (const k of ['1', '2', '8']) await page.locator(`[data-action="key"][data-key="${k}"]`).click();
  assert.match(await page.locator('.amount-box .v').innerText(), /128/);
  assert.equal(await page.locator('.save-btn').isDisabled(), false, 'save enabled with amount');
  await page.locator('[data-action="pick-cat"][data-id="food"]').click();
  assert.equal(await page.locator('.subcats').count(), 1, 'subcats expand');
  await page.locator('[data-action="pick-sub"][data-sub="午餐"]').click();
  await page.locator('.save-btn').click();
  assert.match(await header(), /首页/, 'saved returns home');
  assert.equal(await page.locator('.txn').first().innerText().then(t => t.includes('128') || t.includes('餐饮')), true, 'new txn present');

  // 6. 待确认草稿
  const r0 = await page.locator('.review').count();
  await page.locator('[data-action="confirm-review"]').first().click();
  assert.equal(await page.locator('.review').count(), r0 - 1, 'review resolved');

  // 7. 编辑已有账目 + 未保存离开确认
  await page.locator('.txn').first().click();
  assert.match(await header(), /编辑账目/);
  await act('close-edit');
  assert.equal(await page.locator('#sheet').evaluate(d => d.open), true, 'unsaved confirm');

  // 8. 重置
  await page.locator('#sheet .sheet-heading [data-close]').click();
  await act('close-edit'); await page.locator('#sheet [data-action="discard-edit"]').click();
  await page.locator('#resetDemo').click();
  await page.locator('#sheet [data-action="confirm-reset"]').click();
  assert.match(await header(), /首页/);

  // 9. 说明文字与手机分离
  assert.equal(await page.locator('.phone .notes').count(), 0, 'no notes inside phone');

  // 10. 无外部请求 / 无脚本错误
  assert.deepEqual(failedReq.filter(u => !u.startsWith('file:')), [], 'no external requests');
  assert.deepEqual(errors, [], 'no page errors');

  await page.screenshot({ path: path.join(__dirname, 'preview-desktop.png'), fullPage: true });
  await browser.close();
  console.log('smoke-test: PASS');
})().catch(e => { console.error('smoke-test: FAIL\n', e); process.exit(1); });
