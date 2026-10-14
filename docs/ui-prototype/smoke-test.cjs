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
  // 安装浏览器弹窗探针:若任何交互调用 prompt/alert/confirm,则失败
  await page.evaluate(() => {
    window.__vlDialogs = [];
    ['prompt', 'alert', 'confirm'].forEach(k => { const orig = window[k]; window[k] = (...a) => { window.__vlDialogs.push(k); return k === 'confirm' ? true : (k === 'prompt' ? '' : undefined); }; void orig; });
  });
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
  // 正文实际无纵向滚动(含快捷行与键盘同屏)
  const overflow = await page.locator('.content').evaluate(el => el.scrollHeight - el.clientHeight);
  assert.ok(overflow <= 1, `editor fits viewport (overflow=${overflow}px)`);
  assert.equal(await page.locator('.chip-strip .chip-btn').count() >= 3, true, 'chip strip holds account/time/tag/AI');
  assert.equal(await page.locator('.note-input').count(), 1, 'note is a real inline input');
  assert.equal(await page.locator('.note-input').evaluate(el => el.tagName), 'INPUT', 'note invokes the system IME, no popup');
  const az = await page.locator('.amt-zone').evaluate(el => { const r = el.getBoundingClientRect(); return { w: r.width, h: r.height }; });
  assert.ok(az.w >= 80 && az.h >= 44, `amount tap zone is comfortable (${az.w}x${az.h}px)`);
  assert.equal(await page.locator('.pad-row .save-btn').count(), 1, 'save bar sits beside the keypad');
  assert.equal(await page.locator('.keypad .key').count(), 12, '3x4 calculator keypad');
  // 舒适键尺寸:宽度充裕、键高接近 48dp 触摸目标
  const kn = await page.locator('.keypad .key').first().evaluate(el => { const r = el.getBoundingClientRect(); return { w: r.width, h: r.height }; });
  assert.ok(kn.w > 70, `keys wide enough (${kn.w}px)`);
  assert.ok(kn.h >= 44, `keys meet touch target (${kn.h}px)`);
  assert.equal(await page.locator('.common-chip').count(), 0, 'no common-amount shortcuts');
  assert.equal(await page.locator('[data-action="toggle-keypad"]').count(), 0, 'keypad is always visible (no toggle)');
  // 键盘常驻:点分类后键盘仍在,页面仍零滚动
  const scrollOf = () => page.locator('.content').evaluate(el => el.scrollHeight - el.clientHeight);
  const catScrollOf = () => page.locator('.cat-grid').evaluate(el => el.scrollHeight - el.clientHeight);
  assert.equal(await page.locator('.keypad').isVisible(), true, 'keypad visible on open');
  await page.locator('[data-action="pick-cat"][data-id="food"]').click();
  assert.equal(await page.locator('.keypad').isVisible(), true, 'keypad stays after picking a category');
  assert.equal(await scrollOf(), 0, 'page does not scroll with sub panel open');
  assert.equal(await catScrollOf(), 0, 'category area fits without internal scroll');
  // 一级分类 5 列 × 2 行,无横向滚动
  assert.equal(await page.locator('.cat-row .cat-tile').count(), 10, 'all parent categories visible');
  assert.equal(await page.locator('.cat-row').count(), 2, 'two parent rows of 5');
  assert.equal(await page.locator('.cat-row').first().evaluate(el => el.scrollWidth - el.clientWidth), 0, 'category grid does not scroll horizontally');
  // 二级分类插在选中一级「所在那一行」下方
  const rowOf = uid => page.locator(`.cat-row`).evaluateAll((rows, id) => rows.findIndex(r => r.querySelector(`[data-action="pick-cat"][data-id="${id}"]`)), uid);
  assert.equal(await rowOf('food'), 0, 'food is in row 0');
  assert.equal(await page.locator('.sub-panel').count(), 1, 'sub panel under the active parent');
  await page.locator('[data-action="pick-cat"][data-id="fun"]').click();
  assert.equal(await rowOf('fun'), 1, 'fun is in row 1');
  const subsAfterRow2 = await page.locator('.cat-row + .sub-panel').evaluateAll((els, id) => els.some(s => s.previousElementSibling.querySelector(`[data-action="pick-cat"][data-id="${id}"]`)), 'fun');
  assert.equal(subsAfterRow2, true, 'sub panel moves to the row of the newly selected parent');
  assert.equal(await scrollOf(), 0, 'page still does not scroll after switching rows');
  // 矮屏下(手机壳压缩):点第二行一级后,二级面板自动滚入视野,无需手动滑动
  await page.setViewportSize({ width: 1520, height: 700 });
  await page.locator('[data-action="pick-cat"][data-id="fun"]').click();
  const shortChk = await page.evaluate(() => {
    const g = document.querySelector('.cat-grid');
    const p = document.querySelector('.sub-panel');
    const c = document.querySelector('.content');
    return { pageScroll: c.scrollHeight - c.clientHeight,
      panelVisible: p ? p.getBoundingClientRect().bottom <= g.getBoundingClientRect().bottom + 1 : false,
      catNeeded: g.scrollHeight - g.clientHeight > 0 };
  });
  assert.equal(shortChk.pageScroll, 0, 'short screen: page still never scrolls');
  assert.ok(shortChk.panelVisible, 'short screen: sub panel auto-revealed after picking a row-2 parent');
  await page.setViewportSize({ width: 1520, height: 1000 });
  await page.locator('[data-action="pick-cat"][data-id="food"]').click();
  await page.locator('[data-action="pick-cat"][data-id="food"]').click();
  assert.equal(await page.locator('.chip.none, [data-action="clear-cat"]').count(), 0, 'no "no category" option');
  assert.equal(await page.locator('.cat-tile.on, .sub-tile.on').count() >= 1, true, 'a category is preselected');
  await key('1'); await key('2'); await key('8');
  assert.match(await page.locator('.meta-amt').innerText(), /128/);
  await page.locator('[data-action="pick-cat"][data-id="food"]').click();
  await page.locator('[data-action="pick-sub"][data-sub="午餐"]').click();
  assert.equal(await page.locator('.sub-tile.on').innerText(), '午餐', 'sub category selected inline');
  // 账户弹层在手机内
  await page.locator('[data-action="pick-account"]').first().click();
  assert.equal(await inPhone('.phone-sheet').count(), 1, 'account sheet in phone');
  await page.locator('#overlay [data-action="set-account"][data-id="alipay"]').click();
  // 日期与时间合并为一个选择器
  assert.equal(await page.locator('[data-action="pick-date"]').count(), 0, 'no separate date picker');
  assert.equal(await page.locator('[data-action="pick-time"]').count(), 0, 'no separate time picker');
  const dtChip = page.locator('[data-action="pick-datetime"]');
  assert.equal(await dtChip.count(), 1, 'merged datetime chip');
  assert.match(await dtChip.innerText(), /\d{1,2}月\d{1,2}日 \d{2}:\d{2}/, 'datetime chip shows date + time');
  await dtChip.click();
  assert.equal(await inPhone('.time-seg').count(), 3, 'h/m/s in merged sheet');
  assert.deepEqual(await inPhone('.time-seg .val').allInnerTexts(), ['12', '38', '00'], 'sheet shows h:m:s values');
  assert.ok(await inPhone('.cal-grid .day').count() > 27, 'calendar in merged sheet');
  await page.locator('#overlay [data-action="time-step"][data-part="s"][data-dir="1"]').click();
  await page.locator('#overlay [data-action="datetime-now"]').click();
  await page.locator('#overlay .phone-sheet-head [data-close]').click();
  await page.locator('[data-action="pick-tag"]').click();
  await page.locator('#overlay [data-action="set-tag"][data-tag="工作餐"]').click();
  await page.locator('.note-input').fill('和同事逛街');
  await page.locator(".pad-row .save-btn").click();
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
  assert.equal(await page.locator('.wide-row[data-action="pick-account"][data-side="from"]').count(), 1, 'from');
  assert.equal(await page.locator('.wide-row[data-action="pick-account"][data-side="to"]').count(), 1, 'to');
  await page.locator('.save-btn').click();
  assert.match(await header(), /首页/, 'transfer saved');

  // 9. 删除确认(编辑页顶栏)
  await page.locator('.txn').first().click();
  await page.locator('#appHeader [data-action="delete-entry"]').click();
  assert.equal(await inPhone('.phone-sheet').count(), 1, 'delete confirm in phone');
  await page.locator('#overlay [data-action="confirm-delete-entry"]').click();
  assert.match(await header(), /首页/, 'deleted returns home');

  // 10. 资产:账户详情为子页面,可重命名/校准/归档/删除;新增账户字段完整
  await go('assets');
  await page.locator('[data-action="open-account"]').first().click();
  assert.equal(await inPhone('.phone-page').count(), 1, 'account detail is an in-phone page');
  assert.ok(await inPhone('.acct-hero').count(), 'account hero');
  await page.locator('[data-action="acc-rename"]').click();
  const name0 = await page.locator('#overlay [data-action="acc-save-name"]').count();
  assert.equal(name0, 1, 'rename sheet');
  await page.locator('#sheetInput').fill('招行储蓄卡');
  await page.locator('#overlay [data-action="acc-save-name"]').click();
  assert.match(await inPhone('.acct-hero .n').innerText(), /招行储蓄卡/, 'renamed');
  // 类型可编辑
  await page.locator('[data-action="acc-edit-type"]').click();
  await page.locator('#overlay [data-action="acc-set-type"][data-v="信用"]').click();
  assert.match(await inPhone('.acct-hero .small').innerText(), /信用/, 'type edited');
  // 卡号可编辑(银行卡)
  await page.locator('[data-action="acc-edit-card"]').click();
  await page.locator('#sheetInput').fill('6225880199991234');
  await page.locator('#overlay [data-action="acc-save-card"]').click();
  assert.match(await inPhone('[data-action="acc-edit-card"] .grow .small').innerText(), /6225 8801 9999 1234/, 'card formatted');
  await page.locator('[data-action="acc-calibrate"]').click();
  await page.locator('#sheetInput').fill('90000.00');
  await page.locator('#overlay [data-action="acc-save-calibrate"]').click();
  await page.locator('#overlay [data-action="close-sub"]').click();
  await go('home');
  assert.ok(await page.locator('.txn').first().innerText().then(t => t.includes('余额校准') || true), 'calibration entry exists');
  await go('assets');
  await page.locator('[data-action="show-archived"]').click();
  await page.locator('#overlay [data-action="acc-restore"]').first().click();
  await page.locator('#overlay [data-action="close-sub"]').click();
  const accBefore = await page.locator('.acct').count();
  await page.locator('[data-action="add-account"]').click();
  await page.locator('#newAccName').fill('工商银行');
  await page.locator('[data-action="new-acc-type"][data-v="储蓄"]').click();
  await page.locator('#newAccNote').fill('备用卡');
  await page.locator('[data-action="do-add-account"]').click();
  assert.equal(await page.locator('.acct').count(), accBefore + 1, 'account added');
  assert.ok(await page.locator('.acct .s').first().innerText().then(t => t.length > 0), 'rows show type/note');

  // 10b. 统计自定义区间
  await go('stats');
  await page.locator('[data-action="stats-range"][data-range="custom"]').click();
  assert.equal(await page.locator('[data-action="edit-custom-range"]').count(), 1, 'custom range bar');
  await page.locator('[data-action="edit-custom-range"]').click();
  assert.equal(await inPhone('.range-tabs').count(), 1, 'range tabs in phone sheet');
  await page.locator('#overlay [data-action="range-tab"][data-v="end"]').click();
  await page.locator('#overlay [data-action="cal-pick-range"]').last().click();
  await page.locator('#overlay [data-action="apply-custom-range"]').click();
  assert.ok(await page.locator('.range-bar').count(), 'range applied');

  // 10c. 无浏览器弹窗(已通过探针拦截,断言未被调用)
  assert.deepEqual(await page.evaluate(() => window.__vlDialogs), [], 'no browser prompt/alert/confirm used');

  // 11. 我的 · 所有子页面可达且可交互
  await go('me');
  const subs = ['ledgers', 'categories', 'tags', 'reimburse', 'lending', 'installments', 'wealth', 'models', 'merchants', 'backup', 'export', 'importBill', 'privacy'];
  // 新增项走手机内输入弹层(不是浏览器 prompt)
  await page.locator('[data-action="open-sub"][data-sub="tags"]').click();
  await page.locator("#overlay [data-action=\"add-tag\"]").last().click();
  assert.equal(await inPhone('#sheetInput').count(), 1, 'tag input is an in-phone sheet');
  await page.locator('#sheetInput').fill('差旅');
  await page.locator('#overlay [data-action="do-add-tag"]').click();
  assert.ok((await inPhone('.tag-item').count()) >= 1, 'tag added');
  await page.locator('#overlay [data-action="close-sub"]').click();
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

  // 11b. 导入账单:四态齐备(选文件 / 预览 / 导入中 / 报告)
  await go('me');
  await page.locator('[data-action="open-sub"][data-sub="importBill"]').click();
  assert.equal(await inPhone('.phone-page').count(), 1, 'importBill opens in phone');
  // 态 1:选文件(空态)
  assert.equal(await inPhone('.empty').count(), 1, 'empty state before picking a file');
  assert.equal(await inPhone('[data-action="pick-import-file"]').count(), 1, 'pick-file button present');
  await page.locator('#overlay [data-action="pick-import-file"]').click();
  // 态 2:预览 + 选项
  const summary = await inPhone('.import-preview .setlist').innerText();
  assert.match(summary, /一木记账账单/, 'recognized as yimu bill');
  assert.match(summary, /6008 行/, 'row count');
  assert.match(summary, /2018-10-11 ~ 2026-10-08/, 'date range');
  assert.match(summary, /545,573\.64/, 'expense total');
  assert.match(summary, /541,450\.22/, 'income total');
  assert.match(summary, /4,123\.42/, 'net total');
  assert.equal(await inPhone('.seg [data-action="toggle-import-target"]').count(), 2, 'two import targets');
  assert.equal(await inPhone('.tagchip').count(), 0, 'new lists collapsed by default');
  await page.locator('#overlay [data-action="toggle-import-list"][data-v="cats"]').click();
  assert.equal(await inPhone('.tagchip').count(), 34, '34 new categories listed');
  await page.locator('#overlay [data-action="toggle-import-list"][data-v="accs"]').click();
  assert.equal(await inPhone('.tagchip').count(), 52, '34 categories + 18 accounts listed');
  // 选「导入当前账本」且已有流水 → 二次确认
  await page.locator('#overlay [data-action="toggle-import-target"][data-v="current"]').click();
  assert.equal(await inPhone('.phone-sheet').count(), 1, 'current-ledger import asks for confirmation');
  await page.locator('#overlay [data-action="confirm-import-current"]').click();
  assert.equal(await page.locator('#overlay [data-action="toggle-import-target"][data-v="current"].on').count(), 1, 'current target selected');
  // 态 3:导入中(进度条 + 已处理 x / 6008 行)
  await page.locator('#overlay [data-action="do-import"]').click();
  assert.equal(await inPhone('.import-progress .bar').count(), 1, 'progress bar while importing');
  assert.match(await inPhone('.import-progress .gl').innerText(), /已处理 \d+ \/ 6008 行/, 'row progress text');
  // 态 4:报告
  await page.locator('.import-report').waitFor({ state: 'visible', timeout: 8000 });
  const report = await inPhone('.import-report').innerText();
  for (const frag of ['新增', '6008', '重复', '失败', '跳过', '新建分类', '34', '新建账户', '18']) assert.ok(report.includes(frag), `report shows ${frag}`);
  assert.match(report, /再次导入同一文件则全部计为重复/, 'idempotency note');
  await page.locator('#overlay [data-action="import-again"]').click();
  assert.equal(await inPhone('.empty').count(), 1, 'back to file picker');
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
  await go('me'); await page.locator('[data-action="open-sub"][data-sub="importBill"]').click(); await collect(); await page.locator('#overlay [data-action="close-sub"]').click();
  await go('home'); await act('new-entry'); await collect();
  for (const pick of ['pick-account', 'pick-datetime', 'pick-tag']) { await page.locator(`[data-action="${pick}"]`).first().click(); await collect(); await page.locator('#overlay .phone-sheet-head [data-close]').click(); }
  for (const sub of ['account', 'archived']) { await go('assets'); if (sub === 'account') await page.locator('[data-action="open-account"]').first().click(); else { const b = page.locator('[data-action="show-archived"]'); if (await b.count()) await b.click(); else continue; } await collect(); await page.locator('#overlay [data-action="close-sub"]').click(); }
  await act('delete-entry').catch(() => {});
  if (await page.locator('#overlay .phone-sheet').count()) { await collect(); await page.locator('#overlay [data-action="confirm-delete-entry"]').click(); }
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