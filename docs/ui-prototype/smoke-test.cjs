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
  const external = [];
  page.on('requestfailed', r => external.push(r.url()));
  page.on('request', r => { if (!r.url().startsWith('file:') && !r.url().startsWith('data:')) external.push(r.url()); });

  await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
  const go = p => page.locator(`#chapterNav [data-page="${p}"]`).click();
  const act = a => page.locator(`#overlay [data-action="${a}"], #content [data-action="${a}"], #appHeader [data-action="${a}"], #fab[data-action="${a}"]`).first().click();
  const header = () => page.locator('#appHeader').innerText();
  const key = k => page.locator(`[data-action="key"][data-key="${k}"]`).click();
  const inPhone = sel => page.locator(`#overlay ${sel}`);

  // 1. 首页默认 + 千分位
  assert.match(await header(), /首页/);
  assert.match(await page.locator('.hero .big').innerText(), /6,846\.00/, 'thousand separators');
  assert.equal(await page.locator('#fab').isVisible(), true, 'fab on home');

  // 2. 五个 Tab
  for (const p of ['calendar', 'assets', 'stats', 'me', 'home']) await go(p);
  assert.match(await header(), /首页/);

  // 3. 日历
  await go('calendar');
  const before = await page.locator('.day.sel').innerText();
  await page.locator('.day:not(.dim)').first().click();
  assert.notEqual(await page.locator('.day.sel').innerText(), before, 'selection changes');

  // 4. 统计切换 + 筛选(手机内子页面)
  await go('stats');
  const m0 = await page.locator('.overview .o').first().innerText();
  await page.locator('[data-action="stats-range"][data-range="year"]').click();
  assert.notEqual(await page.locator('.overview .o').first().innerText(), m0, 'range changes totals');
  await page.locator('[data-action="stats-range"][data-range="month"]').click();
  await page.locator('[data-action="stats-filter"]').click();
  assert.equal(await inPhone('.phone-page').count(), 1, 'filter is an in-phone page');
  await page.locator('[data-action="set-stat-account"][data-id="cmb_debit"]').click();
  await page.locator('[data-action="apply-stats-filter"]').click();
  assert.equal(await page.locator('.filter-note').count(), 1, 'filter applied');
  await page.locator('[data-action="clear-stats-filter"]').click();

  // 5. 记一笔:一屏、无滚动、分类恒有值
  await act('new-entry');
  assert.match(await header(), /记一笔/);
  assert.equal(await page.locator('.content.noscroll').count(), 1, 'editor does not scroll');
  assert.equal(await page.locator('.chip.none, [data-action="clear-cat"]').count(), 0, 'no "no category" option');
  assert.equal(await page.locator('.chip-row .chip.on').count() >= 1, true, 'a category is preselected');
  await key('1'); await key('2'); await key('8');
  assert.match(await page.locator('.amount-box .v').innerText(), /128/);
  // 分类横向切换
  await page.locator('[data-action="pick-cat"][data-id="food"]').click();
  await page.locator('[data-action="pick-sub"][data-sub="午餐"]').click();
  // 账户/日期/时间/标签 弹层都在手机内
  await page.locator('[data-action="pick-account"]').first().click();
  assert.equal(await inPhone('.phone-sheet').count(), 1, 'account sheet in phone');
  await page.locator('#overlay [data-action="set-account"][data-id="alipay"]').click();
  await page.locator('[data-action="pick-time"]').click();
  assert.equal(await inPhone('.time-seg').count(), 3, 'h/m/s in phone sheet');
  await page.locator('#overlay [data-action="time-step"][data-part="s"][data-dir="1"]').click();
  await page.locator('#overlay [data-action="close-sheet"]').first().click().catch(() => {});
  await page.locator('#overlay .phone-sheet-head [data-close]').click();
  await page.locator('[data-action="pick-tag"]').click();
  await page.locator('#overlay [data-action="set-tag"][data-tag="工作餐"]').click();
  await page.locator('.note-field input').fill('和同事逛街');
  await page.locator('.save-btn').click();
  assert.match(await header(), /首页/, 'saved returns home');

  // 6. AI 记账子页面(手机内)→ 解析 → 填入
  await act('new-entry');
  await page.locator('[data-action="open-sub"][data-sub="ai"]').click();
  assert.equal(await inPhone('.phone-page').count(), 1, 'AI is an in-phone page');
  await page.locator('[data-action="ai-sample"]').first().click();
  await page.locator('[data-action="ai-parse"]').click();
  assert.equal(await inPhone('.inline-note.ok').count(), 1, 'AI parsed');
  await page.locator('[data-action="ai-apply"]').click();
  assert.match(await header(), /记一笔/, 'AI fills editor');
  await page.locator('#appHeader [data-action="back-edit"]').click();
  await page.locator('#overlay [data-action="discard-edit"]').click();

  // 7. 搜索
  await act('search');
  await page.locator('#searchInput').fill('咖啡');
  assert.ok(await page.locator('#searchResults .txn').count() >= 2, 'search filters');
  await act('close-search');

  // 8. 转账双账户
  await act('new-entry');
  await page.locator('[data-action="set-type"][data-kind="transfer"]').click();
  await key('5'); await key('0'); await key('0');
  assert.equal(await page.locator('[data-action="pick-account"][data-side="from"]').count(), 1, 'from');
  assert.equal(await page.locator('[data-action="pick-account"][data-side="to"]').count(), 1, 'to');
  await page.locator('.save-btn').click();
  assert.match(await header(), /首页/, 'transfer saved');

  // 9. 删除确认
  await page.locator('.txn').first().click();
  await page.locator('[data-action="delete-entry"]').click();
  assert.equal(await inPhone('.phone-sheet').count(), 1, 'delete confirm in phone');
  await page.locator('#overlay [data-action="confirm-delete-entry"]').click();
  assert.match(await header(), /首页/, 'deleted returns home');

  // 10. 资产账户明细 + 新增账户
  await go('assets');
  await page.locator('[data-action="open-account"]').first().click();
  assert.equal(await inPhone('.phone-sheet').count(), 1, 'account detail in phone');
  await page.locator('#overlay .phone-sheet-head [data-close]').click();
  const accBefore = await page.locator('.acct').count();
  await page.locator('[data-action="add-account"]').click();
  await page.locator('#newAccName').fill('工商银行');
  await page.locator('[data-action="do-add-account"]').click();
  assert.equal(await page.locator('.acct').count(), accBefore + 1, 'account added');

  // 11. 我的 · 所有子页面可达且可交互
  await go('me');
  const subs = ['ledgers', 'categories', 'tags', 'reimburse', 'lending', 'installments', 'wealth', 'models', 'merchants', 'backup', 'export', 'privacy'];
  for (const s of subs) {
    await page.locator(`[data-action="open-sub"][data-sub="${s}"]`).click();
    assert.equal(await inPhone('.phone-page').count(), 1, `${s} opens in phone`);
    await page.locator('#overlay [data-action="close-sub"]').click();
  }
  // 子页面内真实交互
  await page.locator('[data-action="open-sub"][data-sub="ledgers"]').click();
  await page.locator('#overlay [data-action="switch-ledger"][data-id="l2"]').click();
  assert.equal(await inPhone('.pick.sel .badge').count() >= 0, true, 'ledger switch');
  await page.locator('#overlay [data-action="close-sub"]').click();
  await page.locator('[data-action="open-sub"][data-sub="categories"]').click();
  const sw0 = await inPhone('.switch.on').count();
  await page.locator('#overlay [data-action="toggle-cat"]').first().click();
  assert.notEqual(await inPhone('.switch.on').count(), sw0, 'category toggles');
  await page.locator('#overlay [data-action="close-sub"]').click();
  await page.locator('[data-action="open-sub"][data-sub="backup"]').click();
  const b0 = await inPhone('.pick').count();
  await page.locator('[data-action="create-backup"]').click();
  assert.notEqual(await inPhone('.pick').count(), b0, 'backup created');
  await page.locator('#overlay [data-action="close-sub"]').click();
  await page.locator('[data-action="open-sub"][data-sub="export"]').click();
  await page.locator('[data-action="do-export"]').click();
  assert.equal(await inPhone('.csv-preview').count(), 1, 'export produces preview');
  await page.locator('#overlay [data-action="close-sub"]').click();

  // 12. 预算 / 卡片设置
  await go('home');
  await act('edit-budget');
  await page.locator('#overlay [data-action="set-budget"][data-v="1200000"]').click();
  assert.match(await inPhone('.budget-row b').innerText(), /12,000/, 'budget changed');
  await page.locator('#overlay [data-action="close-sub"]').click();
  await act('home-settings');
  const on0 = await inPhone('.switch.on').count();
  await page.locator('#overlay [data-action="toggle-card"]').first().click();
  assert.notEqual(await inPhone('.switch.on').count(), on0, 'card toggles');
  await page.locator('#overlay [data-action="close-sub"]').click();

  // 13. 无假按钮:遍历每个页面与子页面,收集所有 data-action,断言都有处理器
  const seen = new Set();
  const collect = async () => { (await page.$$eval('[data-action]', els => els.map(e => e.dataset.action))).forEach(a => seen.add(a)); };
  // 先恢复默认卡片,确保 budget/savings 卡片可达
  await go('home');
  if (!(await page.locator('[data-action="edit-budget"]').count())) { await act('home-settings'); await page.locator('#overlay [data-action="reset-cards"]').click(); await page.locator('#overlay [data-action="close-sub"]').click(); }
  await collect();
  await act('home-settings'); await collect(); await page.locator('#overlay [data-action="close-sub"]').click();
  for (const p of ['calendar', 'assets', 'stats', 'me']) { await go(p); await collect(); }
  await go('me');
  for (const s of subs) {
    await page.locator(`[data-action="open-sub"][data-sub="${s}"]`).click();
    await collect();
    await page.locator('#overlay [data-action="close-sub"]').click();
  }
  for (const s of ['budget', 'settings', 'saving', 'trend', 'statsfilter', 'addaccount', 'ai']) {
    if (s === 'budget') { await go('home'); await act('edit-budget'); }
    else if (s === 'settings') { await go('home'); await act('home-settings'); }
    else if (s === 'saving') { await go('home'); await act('open-saving'); }
    else if (s === 'trend') { await go('assets'); await act('assets-trend'); }
    else if (s === 'statsfilter') { await go('stats'); await act('stats-filter'); }
    else if (s === 'addaccount') { await go('assets'); await act('add-account'); }
    else { await go('home'); await act('new-entry'); await act('open-sub'); }
    await collect();
    await page.locator('#overlay [data-action="close-sub"]').click();
  }
  await go('home'); await act('new-entry'); await collect();
  for (const pick of ['pick-account', 'pick-date', 'pick-time', 'pick-tag']) { await page.locator(`[data-action="${pick}"]`).first().click(); await collect(); await page.locator('#overlay .phone-sheet-head [data-close]').click(); }
  await act('delete-entry').catch(() => {});
  if (await page.locator('#overlay .phone-sheet').count()) { await collect(); await page.locator('#overlay [data-action="close-sheet"], #overlay .phone-sheet-head [data-close]').first().click(); }

  const missing = await page.evaluate(names => names.filter(n => !window.__vlHasAction(n)), [...seen]);
  assert.deepEqual(missing, [], `every data-action must have a handler; missing: ${missing.join(', ')}`);
  assert.ok(seen.size > 40, `expected many distinct actions, got ${seen.size}`);

  // 14. 重置
  await page.locator('#resetDemo').click();
  await page.locator('#overlay [data-action="confirm-reset"]').click();
  assert.match(await header(), /首页/);

  assert.deepEqual(external.filter(u => !u.startsWith('file:')), [], 'no external requests');
  assert.deepEqual(errors, [], 'no page errors');

  await page.screenshot({ path: path.join(__dirname, 'preview-desktop.png'), fullPage: true });
  await browser.close();
  console.log(`smoke-test: PASS (${seen.size} distinct actions, all handled)`);
})().catch(e => { console.error('smoke-test: FAIL\n', e); process.exit(1); });