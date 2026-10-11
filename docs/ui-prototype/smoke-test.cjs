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
  const key = k => page.locator(`[data-action="key"][data-key="${k}"]`).click();

  // 1. 首页默认
  assert.match(await header(), /首页/);
  assert.equal(await page.locator('.hero .big').count(), 1, 'hero amount');
  assert.equal(await page.locator('.txn').count() > 5, true, 'txn rows');
  assert.equal(await page.locator('#fab').isVisible(), true, 'fab on home');
  // 金额千分位:(hero)本月支出应为 6,846.00
  assert.match(await page.locator('.hero .big').innerText(), /6,846\.00/, 'thousand separators');

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
  await page.locator('[data-action="stats-range"][data-range="month"]').click();

  // 4b. 统计分类下钻
  await page.locator('.rank-row').first().click();
  assert.equal(await page.locator('#sheet').evaluate(d => d.open), true, 'category drill sheet');
  await page.locator('#sheet .sheet-heading [data-close]').click();

  // 5. 记一笔:键盘 → 金额 → 键盘折叠 → 分类二级逻辑 → 保存
  await act('new-entry');
  assert.match(await header(), /记一笔/);
  await key('1'); await key('2'); await key('8');
  assert.match(await page.locator('.amount-box .v').innerText(), /128/);
  assert.equal(await page.locator('.save-btn').isDisabled(), false, 'save enabled with amount');

  // 折叠键盘
  await act('toggle-keypad');
  assert.equal(await page.locator('.keypad').count(), 0, 'keypad collapsed');
  await page.locator('[data-action="toggle-keypad"]').click();
  assert.equal(await page.locator('.keypad').count(), 1, 'keypad expanded again');

  // 分类:首格「不选分类」存在;点一级展开二级,一次仅一个
  assert.equal(await page.locator('.cat.none').count(), 1, 'clear-category cell');
  await page.locator('[data-action="pick-cat"][data-id="food"]').click();
  assert.equal(await page.locator('.subcats').count(), 1, 'subcats expand');
  await page.locator('[data-action="pick-cat"][data-id="shop"]').click();
  assert.equal(await page.locator('.subcats').count(), 1, 'only one parent expanded');
  // 二级区插在父级所在行下方:food 行在 shop 行之前
  const foodBox = await page.locator('[data-action="pick-cat"][data-id="food"]').boundingBox();
  const subBox = await page.locator('.subcats').boundingBox();
  const shopBox = await page.locator('[data-action="pick-cat"][data-id="shop"]').boundingBox();
  assert.ok(subBox.y > shopBox.y && subBox.y > foodBox.y, 'subcats after its parent row');
  await page.locator('[data-action="pick-sub"][data-sub="服饰"]').click();

  // 备注页内输入
  await page.locator('.note-field input').fill('和同事逛街');
  assert.equal(await page.locator('.note-field.filled').count(), 1, 'note filled');

  // 时间到秒
  await act('pick-time');
  assert.equal(await page.locator('.time-seg').count(), 3, 'hour/minute/second segments');
  const t0 = await page.locator('.time-seg .val').nth(2).innerText();
  await page.locator('[data-action="time-step"][data-part="s"][data-dir="1"]').click();
  assert.notEqual(await page.locator('.time-seg .val').nth(2).innerText(), t0, 'second steps');
  await page.locator('#sheet [data-action="time-now"]').click();
  assert.match(await page.locator('.field .fv.amt').first().innerText(), /^\d{2}:\d{2}:\d{2}$/, 'HH:MM:SS field');

  await page.locator('.save-btn').click();
  assert.match(await header(), /首页/, 'saved returns home');

  // 6. 首页搜索过滤
  await act('search');
  await page.locator('#searchInput').fill('咖啡');
  assert.ok(await page.locator('#searchResults .txn').count() >= 2, 'search filters by merchant');
  await page.locator('#searchInput').fill('zzz-no-match');
  assert.equal(await page.locator('#searchResults .empty').count(), 1, 'empty state');
  await act('close-search');

  // 7. 待确认草稿
  const r0 = await page.locator('.review').count();
  await page.locator('[data-action="confirm-review"]').first().click();
  assert.equal(await page.locator('.review').count(), r0 - 1, 'review resolved');

  // 8. 转账:转出/转入双账户 + 同账户校验
  await act('new-entry');
  await page.locator('[data-action="set-type"][data-kind="transfer"]').click();
  await key('5'); await key('0'); await key('0');
  assert.equal(await page.locator('[data-action="pick-account"][data-side="from"]').count(), 1, 'from account field');
  assert.equal(await page.locator('[data-action="pick-account"][data-side="to"]').count(), 1, 'to account field');
  await page.locator('.save-btn').click();
  assert.match(await header(), /首页/, 'transfer saved');

  // 9. 编辑已有账目:删除 + 二次确认
  await page.locator('.txn').first().click();
  assert.match(await header(), /编辑账目/);
  await act('delete-entry');
  assert.equal(await page.locator('#sheet').evaluate(d => d.open), true, 'delete confirm');
  await page.locator('#sheet [data-action="confirm-delete-entry"]').click();
  assert.match(await header(), /首页/, 'deleted returns home');

  // 10. 未保存离开确认
  await act('new-entry');
  await key('9');
  await act('close-edit');
  assert.equal(await page.locator('#sheet').evaluate(d => d.open), true, 'unsaved confirm');
  await page.locator('#sheet [data-action="discard-edit"]').click();

  // 11. 资产账户明细
  await go('assets');
  await page.locator('[data-action="open-account"]').first().click();
  assert.ok(await page.locator('#sheet .menu-item').count() > 0, 'account has entries');
  await page.locator('#sheet .sheet-heading [data-close]').click();

  // 12. 重置
  await page.locator('#resetDemo').click();
  await page.locator('#sheet [data-action="confirm-reset"]').click();
  assert.match(await header(), /首页/);

  // 13. 说明文字与手机分离
  assert.equal(await page.locator('.phone .notes').count(), 0, 'no notes inside phone');

  // 14. 无外部请求 / 无脚本错误
  assert.deepEqual(failedReq.filter(u => !u.startsWith('file:')), [], 'no external requests');
  assert.deepEqual(errors, [], 'no page errors');

  await page.screenshot({ path: path.join(__dirname, 'preview-desktop.png'), fullPage: true });
  await browser.close();
  console.log('smoke-test: PASS');
})().catch(e => { console.error('smoke-test: FAIL\n', e); process.exit(1); });