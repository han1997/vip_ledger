'use strict';
/* VipLedger UI 重设计原型 · 交互逻辑
   纯内存演示:不联网、不读写设备、不调用后端。刷新即重置。
   原则:所有页面与选择器都在手机外壳内呈现,不跳出原型;不存在假按钮。 */

const $ = s => document.querySelector(s);
const icon = n => `<svg class="icon" aria-hidden="true"><use href="#i-${n}"/></svg>`;
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = c => (c < 0 ? '-' : '') + '¥' + (Math.abs(c) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const moneyShort = c => '¥' + Math.round(Math.abs(c) / 100).toLocaleString('zh-CN');
// 输入态金额:整数千分位,小数按输入保留;无小数点时不补 .00。
const amountInput = s => {
  if (s === '' || s == null) return '0';
  const [i, d] = String(s).split('.');
  const ip = Number(i || '0').toLocaleString('zh-CN');
  return d === undefined ? ip : `${ip}.${d}`;
};
const hhmm = t => (t || '').slice(0, 5);

// ── 静态字典 ─────────────────────────────────────────────
const CATS = [
  { id: 'food', name: '餐饮', subs: [['早餐', '早'], ['午餐', '午'], ['晚餐', '晚'], ['咖啡', '咖'], ['外卖', '外'], ['零食', '零'], ['聚餐', '聚'], ['其他', '其']] },
  { id: 'shop', name: '购物', subs: [['服饰', '衣'], ['数码', '数'], ['美妆', '妆'], ['家居', '家'], ['其他', '其']] },
  { id: 'trans', name: '交通', subs: [['地铁', '地'], ['打车', '打'], ['公交', '公'], ['加油', '油'], ['停车', '停']] },
  { id: 'home', name: '居住', subs: [['房租', '租'], ['水电', '水'], ['物业', '物'], ['网费', '网']] },
  { id: 'daily', name: '日用', subs: [['食品', '食'], ['清洁', '洁'], ['纸品', '纸'], ['其他', '其']] },
  { id: 'fun', name: '娱乐', subs: [['电影', '影'], ['游戏', '游'], ['旅行', '旅'], ['运动', '运']] },
  { id: 'med', name: '医疗', subs: [['药品', '药'], ['门诊', '门'], ['体检', '检']] },
  { id: 'study', name: '学习', subs: [['书籍', '书'], ['课程', '课'], ['文具', '文']] },
  { id: 'social', name: '人情', subs: [['红包', '红'], ['礼物', '礼'], ['请客', '请']] },
  { id: 'other', name: '其他', subs: [['其他', '其']] }
];
const INCOME_CATS = [
  { id: 'salary', name: '工资', subs: [['月薪', '薪'], ['奖金', '奖'], ['补贴', '补']] },
  { id: 'bonus', name: '奖金', subs: [['年终奖', '终'], ['提成', '提']] },
  { id: 'invest', name: '投资', subs: [['利息', '息'], ['分红', '红'], ['收益', '益']] },
  { id: 'other_in', name: '其他', subs: [['其他', '其']] }
];
const CAT_BY_ID = id => CATS.concat(INCOME_CATS).find(c => c.id === id);
const TONE_BG = { green: 'background:var(--tint);color:var(--accent)', blue: 'background:#e6edf3;color:var(--transfer)', gold: 'background:var(--gold-bg);color:var(--gold-ink)', gray: 'background:var(--soft);color:var(--text)', red: 'background:#f7e7e3;color:var(--expense)' };
const CHART_COLORS = ['#1a3c34', '#b08d57', '#b23a2e', '#3b5b7a', '#2e7d5b', '#a9a499', '#c9a96e', '#8a6b34'];
const TODAY = { y: 2025, m: 10, d: 11 };
const iso = (y, m, d) => `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

const freshImportState = () => ({ phase: 'pick', file: null, target: 'new', catsOpen: false, accsOpen: false, processed: 0, timer: null, report: null });

// ── 初始数据 ─────────────────────────────────────────────
function initial() {
  let id = 0;
  const t = (date, time, title, cat, sub, acc, kind, amount, tag) => ({ id: ++id, date, time, title, catId: cat, sub, accountId: acc, kind, amount: Math.round(amount * 100), tag: tag || '', note: '' });
  return {
    page: 'home', sub: null, sheet: null,
    importBill: freshImportState(),
    selectedDate: iso(2025, 10, 11), calYear: 2025, calMonth: 10, statsRange: 'month',
    query: '', searchOpen: false, aiText: '', inputDraft: '',
    ledgers: [{ id: 'l1', name: '日常账本', current: true }, { id: 'l2', name: '家庭账本', current: false }, { id: 'l3', name: '旅行记账', current: false }],
    tags: ['工作餐', '出差', '家庭', '朋友聚会', '通勤', '订阅', '报销', '礼物'],
    disabledCats: [],
    reimburse: [{ id: 'r1', name: '差旅报销 · 上海', amount: 1260, status: '进行中' }, { id: 'r2', name: '客户招待', amount: 860, status: '进行中' }],
    lending: [{ id: 'd1', name: '借给张伟', amount: 3000, status: '待收' }, { id: 'd2', name: '借入 · 家人', amount: 1580, status: '待还' }],
    installments: [{ id: 'i1', name: '住房贷款', periods: 168, amount: 5360 }, { id: 'i2', name: 'iPhone 分期', periods: 12, amount: 458 }],
    wealth: [{ id: 'w1', name: '稳健理财 A', invested: 50000, realized: 1240 }, { id: 'w2', name: '指数基金', invested: 30000, realized: -860 }, { id: 'w3', name: '货币基金', invested: 10000, realized: 96 }],
    models: [{ id: 'm1', name: '端侧分类器', enabled: true }, { id: 'm2', name: '云端阶梯解析', enabled: true }, { id: 'm3', name: 'OCR 商户识别', enabled: false }],
    merchantRules: [{ id: 'mr1', match: '瑞幸|星巴克', cat: 'food' }, { id: 'mr2', match: '滴滴|地铁', cat: 'trans' }, { id: 'mr3', match: '京东|淘宝', cat: 'shop' }],
    backups: [{ id: 'b1', at: '今天 09:12', size: '2.4 MB' }, { id: 'b2', at: '10-09 21:03', size: '2.3 MB' }],
    webdav: { enabled: true, url: 'dav.example.com/vipledger' },
    permissions: { notify: true, screenshot: true, background: false },
    cards: [{ id: 'budget', name: '本月预算', on: true }, { id: 'today', name: '今日收支', on: true }, { id: 'savings', name: '储蓄目标', on: true }, { id: 'week', name: '七日趋势', on: true }],
    budgetTotal: 800000,
    statFilter: { accountId: null, catId: null },
    statsCustom: { start: iso(2025, 10, 1), end: iso(2025, 10, 11), editing: 'start' },
    accounts: [
      { id: 'cmb_debit', name: '招商银行储蓄卡', group: 'asset', type: '储蓄', mark: '储', tone: 'green', base: 8642000, sub: '储蓄 · 尾号 6021', note: '工资卡', createdAt: '2024-01-05', archived: false, cardNo: '6225 8801 2345 6021' },
      { id: 'alipay', name: '支付宝余额', group: 'asset', type: '电子钱包', mark: '余', tone: 'blue', base: 386000, sub: '电子钱包', note: '', createdAt: '2024-01-05', archived: false, cardNo: '' },
      { id: 'wechat', name: '微信零钱', group: 'asset', type: '电子钱包', mark: '零', tone: 'green', base: 124000, sub: '电子钱包', note: '', createdAt: '2024-01-05', archived: false, cardNo: '' },
      { id: 'cash', name: '现金', group: 'asset', type: '现金', mark: '现', tone: 'gray', base: 60000, sub: '现金', note: '', createdAt: '2024-01-05', archived: false, cardNo: '' },
      { id: 'invest', name: '投资理财', group: 'asset', type: '投资', mark: '投', tone: 'gold', base: 9000000, sub: '理财台账 · 3 只', note: '', createdAt: '2024-03-01', archived: false, cardNo: '' },
      { id: 'cmb_credit', name: '招商银行信用卡', group: 'debt', type: '信用', mark: '信', tone: 'red', base: -324000, sub: '信用 · 尾号 3390', note: '每月 20 日还款', createdAt: '2024-01-05', archived: false, cardNo: '6225 8801 9876 3390' },
      { id: 'huabei', name: '花呗', group: 'debt', type: '信用', mark: '花', tone: 'red', base: -150000, sub: '信用 · 免息期内', note: '', createdAt: '2024-02-10', archived: false, cardNo: '' },
      { id: 'loan', name: '住房贷款', group: 'debt', type: '分期', mark: '贷', tone: 'red', base: -5350000, sub: '分期 · 剩余 168 期', note: '等额本息', createdAt: '2024-01-20', archived: false, cardNo: '' },
      { id: 'old_card', name: '已注销的储蓄卡', group: 'asset', type: '储蓄', mark: '旧', tone: 'gray', base: 0, sub: '储蓄 · 已归档', note: '', createdAt: '2023-06-01', archived: true, cardNo: '' }
    ],
    newAcc: { name: '', group: 'asset', type: '储蓄', base: '0', note: '', mark: '', cardNo: '' },
    editingAccount: null,
    savings: { name: '旅行基金', saved: 1840000, target: 3000000 },
    reviews: [
      { id: 'r1', title: '拼多多 · 待确认消费', meta: '10-09 20:14 · 微信支付 · 自动采集', amount: 13400 },
      { id: 'r2', title: '京东 · 待确认消费', meta: '10-08 11:42 · 白条 · 需补充时间', amount: 89900 }
    ],
    txns: [
      t('2025-07-05', '10:00:00', '房租', 'home', '房租', 'cmb_debit', 'expense', 4500),
      t('2025-07-18', '20:10:00', '聚餐', 'food', '聚餐', 'wechat', 'expense', 320),
      t('2025-08-01', '10:00:00', '房租', 'home', '房租', 'cmb_debit', 'expense', 4500),
      t('2025-08-12', '15:40:00', '手机数码', 'shop', '数码', 'cmb_credit', 'expense', 2999),
      t('2025-08-24', '09:20:00', '高铁票', 'trans', '打车', 'alipay', 'expense', 553),
      t('2025-09-01', '10:00:00', '房租', 'home', '房租', 'cmb_debit', 'expense', 4500),
      t('2025-09-09', '19:30:00', '健身房', 'fun', '运动', 'cmb_debit', 'expense', 299),
      t('2025-09-20', '12:15:00', '体检', 'med', '体检', 'alipay', 'expense', 680),
      t('2025-09-10', '09:00:00', '工资', 'salary', '月薪', 'cmb_debit', 'income', 12300),
      t('2025-10-01', '10:00:00', '房租', 'home', '房租', 'cmb_debit', 'expense', 4500),
      t('2025-10-01', '10:05:00', '水电费', 'home', '水电', 'cmb_debit', 'expense', 216),
      t('2025-10-05', '09:12:00', '星巴克', 'food', '咖啡', 'cmb_credit', 'expense', 41.5),
      t('2025-10-06', '19:40:00', '电影票', 'fun', '电影', 'wechat', 'expense', 120),
      t('2025-10-08', '15:20:00', '药店', 'med', '药品', 'alipay', 'expense', 88),
      t('2025-10-09', '11:42:00', '京东', 'shop', '数码', 'cmb_credit', 'expense', 899),
      t('2025-10-09', '18:05:00', '山姆会员店', 'shop', '食品', 'cmb_debit', 'expense', 341),
      t('2025-10-10', '08:15:00', '美团外卖', 'food', '外卖', 'cmb_debit', 'expense', 45.5),
      t('2025-10-10', '14:30:00', '优衣库', 'shop', '服饰', 'cmb_credit', 'expense', 299),
      t('2025-10-10', '18:26:00', '城市地铁', 'trans', '地铁', 'alipay', 'expense', 6),
      t('2025-10-10', '21:05:00', '便利蜂', 'daily', '零食', 'wechat', 'expense', 22),
      t('2025-10-10', '09:00:00', '工资', 'salary', '月薪', 'cmb_debit', 'income', 12300),
      t('2025-10-11', '08:32:07', '瑞幸咖啡', 'food', '咖啡', 'cmb_debit', 'expense', 32, '通勤'),
      t('2025-10-11', '11:05:22', '盒马鲜生', 'daily', '食品', 'alipay', 'expense', 156),
      t('2025-10-11', '14:10:41', '滴滴出行', 'trans', '打车', 'wechat', 'expense', 28, '通勤'),
      t('2025-10-11', '16:20:53', '星巴克', 'food', '咖啡', 'cmb_credit', 'expense', 52, '工作餐')
    ]
  };
}
let state = initial();
const ACCOUNTS = () => state.accounts;
const ACC = id => state.accounts.find(a => a.id === id);
const ACCOUNTS_ACTIVE = () => state.accounts.filter(a => !a.archived);
const CATS_ACTIVE = () => CATS.filter(c => !state.disabledCats.includes(c.id));

// 账户类型:银行卡类可记录卡号
const ACCOUNT_TYPES = [
  { id: '储蓄', icon: 'card', card: true },
  { id: '信用', icon: 'card', card: true },
  { id: '预付', icon: 'card', card: true },
  { id: '应收应付', icon: 'users', card: false },
  { id: '借贷', icon: 'plan', card: false },
  { id: '投资', icon: 'trend', card: false },
  { id: '电子钱包', icon: 'wallet', card: false },
  { id: '现金', icon: 'wallet', card: false }
];
const typeNeedsCard = t => (ACCOUNT_TYPES.find(x => x.id === t) || {}).card === true;
const fmtCardNo = s => (s || '').replace(/\s/g, '').replace(/(.{4})/g, '$1 ').trim();
const cardTail = s => { const d = (s || '').replace(/\s/g, ''); return d ? '尾号 ' + d.slice(-4) : ''; };

// ── 派生计算 ─────────────────────────────────────────────
const inMonth = (txn, y, m) => txn.date.startsWith(`${y}-${String(m).padStart(2, '0')}`);
const sumBy = (list, kind) => list.filter(t => t.kind === kind).reduce((s, t) => s + t.amount, 0);
const monthTxns = () => state.txns.filter(t => inMonth(t, TODAY.y, TODAY.m));
const dayTxns = date => state.txns.filter(t => t.date === date).sort((a, b) => b.time.localeCompare(a.time));
const fmtDateCN = date => { const [, m, d] = date.split('-'); return `${Number(m)}月${Number(d)}日`; };
const weekdayCN = date => '日一二三四五六'[new Date(date + 'T00:00:00').getDay()];
function dateLabel(date) {
  const t = iso(TODAY.y, TODAY.m, TODAY.d);
  const y = iso(TODAY.y, TODAY.m, TODAY.d - 1);
  if (date === t) return '今天';
  if (date === y) return '昨天';
  return '周' + weekdayCN(date);
}

// ── 页面元信息(章节导航 / 说明面板) ────────────────────
const PAGES = [['home', '首页'], ['calendar', '日历'], ['assets', '资产'], ['stats', '统计'], ['me', '我的'], ['edit', '记一笔']];
const NOTES = {
  home: ['01', '少一点数字,<br>多一点判断。', '首页顶部只回答一件事:这个月花了多少、还剩多少预算。卡片可自定义,流水按日归拢,金额右对齐等宽。', ['点草稿「确认」入账', '点任意流水直接编辑', '右上角放大镜搜索', '右上角滑杆调卡片']],
  calendar: ['02', '每一天,<br>都查得到。', '月历用金点标出有账的日子;选中某天,下方只显示那一天;「记一笔」会把选中日期带进编辑器。', ['点任意日期', '左右箭头翻月', '点「记一笔」带日期']],
  assets: ['03', '资产与负债,<br>一眼分明。', '净资产在最上方,资产用墨绿、负债用赭红分栏。点账户看流水,点趋势看走势,点「+」新增账户。', ['点账户查看流水', '点「资产趋势」', '点右上角「+」新增账户']],
  stats: ['04', '花在哪,<br>一目了然。', '统计默认与首页同口径的「本月」。环形图看结构、排行看大小、柱状图看趋势,共用同一份数据。', ['切换时间段', '点筛选限定账户/分类', '点排行进入明细']],
  me: ['05', '设置,<br>都能点开。', '账本、计划、AI 配置与数据四组入口,每一项都打开真实子页面,可增删改。', ['点账本管理切换账本', '点报销/借贷增删条目', '点备份创建新备份']],
  edit: ['06', '一屏之内,<br>记完一笔。', '金额、分类、账户、日期、时间、标签、备注全部在一屏内完成,无需滚动。分类默认已选中,不存在「未分类」。', ['点键盘输入金额', '横向滑动选分类', '点账户/日期/时间/标签弹层', '点「保存」写入']]
};
const CAPTION = { home: '首页', calendar: '日历', assets: '资产', stats: '统计', me: '我的', edit: '记一笔 / 编辑账目' };

// ── 头部 / 渲染 ─────────────────────────────────────────
function header(title, sub, actions = '', back = false, backAction = 'back') {
  $('#appHeader').innerHTML =
    `${back ? `<button class="icon-button" data-action="${backAction}" aria-label="返回">${icon('arrow-left')}</button>` : ''}
     <div class="header-title">${sub ? `<div class="eyeline">${sub}</div>` : ''}<h2>${title}</h2></div>
     <div class="header-actions">${actions}</div>`;
}
const ib = (action, label, ico, extra = '') => `<button class="icon-button" data-action="${action}" aria-label="${label}" title="${label}" ${extra}>${icon(ico)}</button>`;

function render() {
  const navPage = state.page === 'edit' ? null : state.page;
  const nav = $('#bottomNav');
  nav.classList.toggle('visible', !!navPage);
  if (navPage) {
    nav.innerHTML = [['home', '首页', 'home'], ['calendar', '日历', 'calendar'], ['assets', '资产', 'wallet'], ['stats', '统计', 'chart'], ['me', '我的', 'user']]
      .map(([p, n, i]) => `<button data-page="${p}" class="${navPage === p ? 'active' : ''}" ${navPage === p ? 'aria-current="page"' : ''}><span class="nav-icon">${icon(i)}</span>${n}</button>`).join('');
  }
  $('#fab').classList.toggle('visible', !state.sub && (state.page === 'home' || state.page === 'calendar' || state.page === 'stats'));

  $('#chapterNav').innerHTML = PAGES.map(([p, n], i) =>
    `<button data-page="${p}" class="${state.page === p ? 'active' : ''}"><span>0${i + 1}</span>${n}</button>`).join('');

  const note = NOTES[state.page] || NOTES.home;
  $('#noteNumber').textContent = note[0];
  $('#noteTitle').innerHTML = note[1];
  $('#noteBody').textContent = note[2];
  $('#noteTry').innerHTML = note[3].map(x => `<li>${esc(x)}</li>`).join('');
  $('#pageCaption').textContent = state.sub ? state.sub.title : (CAPTION[state.page] || '首页');

  const dock = $('#editorDock');
  const showDock = state.page === 'edit' && !state.sub;
  dock.classList.toggle('hidden', !showDock);
  if (showDock) dock.innerHTML = editorDock();

  const views = { home: homeView, calendar: calendarView, assets: assetsView, stats: statsView, me: meView, edit: editView };
  $('#content').innerHTML = views[state.page]();
  $('#content').classList.toggle('noscroll', state.page === 'edit');
  $('#content').classList.toggle('edit-mode', state.page === 'edit');

  // 手机内层:子页面 > 底部弹层(弹层可叠加在子页面之上)
  const ov = $('#overlay');
  let inner = '';
  if (state.sub) inner += subpageHtml(state.sub);
  if (state.sheet) inner += sheetHtml(state.sheet);
  ov.innerHTML = inner;
  ov.classList.toggle('open', !!(state.sub || state.sheet));
}

function go(page) { if (state.importBill.timer) { clearInterval(state.importBill.timer); state.importBill.timer = null; if (state.importBill.phase === 'progress') state.importBill = freshImportState(); } state.page = page; state.sub = null; state.sheet = null; closeAllSheets(); render(); $('#content').scrollTop = 0; }
function openSub(sub) { state.sub = sub; state.sheet = null; render(); const c = $('#overlay .sub-body'); if (c) c.scrollTop = 0; }
function closeSub() { state.sub = null; state.sheet = null; render(); }
function openSheet(sheet) { state.sheet = sheet; render(); }
function closeSheet() { state.sheet = null; render(); }
function closeAllSheets() { state.sheet = null; }

// 手机内文本输入弹层(替代浏览器 prompt)
function inputSheet(title, { label = '', value = '', placeholder = '', action, confirm = '确定' }) {
  state.inputDraft = value;
  openSheet({ title, body: `<label class="input-field"><span>${esc(label)}</span><input id="sheetInput" placeholder="${esc(placeholder)}" value="${esc(value)}"></label>
    <div class="sub-actions two"><button class="action-button secondary" data-close>取消</button><button class="action-button" data-action="${action}" data-confirm="1">${confirm}</button></div>` });
  const box = $('#sheetInput'); if (box) { box.focus(); box.setSelectionRange(box.value.length, box.value.length); }
}

// ── 首页 ─────────────────────────────────────────────────
function homeView() {
  header('首页', '日常账本', ib('search', '搜索', 'search') + ib('home-settings', '卡片设置', 'tune'));
  if (state.searchOpen) return searchView();
  const mExp = sumBy(monthTxns(), 'expense');
  const mInc = sumBy(monthTxns(), 'income');
  const remain = Math.max(0, state.budgetTotal - mExp);
  const used = Math.min(100, Math.round(mExp / state.budgetTotal * 1000) / 10);
  const today = dayTxns(iso(TODAY.y, TODAY.m, TODAY.d));
  const tExp = sumBy(today, 'expense');
  const reviews = state.reviews.map(r => `
    <div class="review">
      <div class="ic">${icon('camera')}</div>
      <div class="m"><div class="t">${esc(r.title)}</div><div class="s">${esc(r.meta)}</div>
        <div class="acts">
          <button class="pill ok" data-action="confirm-review" data-id="${r.id}">确认</button>
          <button class="pill" data-action="dismiss-review" data-id="${r.id}">忽略</button>
        </div>
      </div>
      <div class="v amt">${money(r.amount)}</div>
    </div>`).join('');
  const card = state.cards.filter(c => c.on);
  const mini = c => {
    if (c.id === 'budget') return `<button class="mini" data-action="edit-budget"><div class="top"><span class="dot"></span><span>本月预算</span></div><div class="num amt"><small>¥</small>${(remain / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div><div class="sub">剩余 · 已用 ${used}%</div><div class="bar"><i style="width:${used}%"></i></div></button>`;
    if (c.id === 'today') return `<div class="mini"><div class="top"><span class="dot" style="background:var(--expense)"></span><span>今日支出</span></div><div class="num amt exp"><small>¥</small>${(tExp / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div><div class="sub">${today.filter(t => t.kind === 'expense').length} 笔 · 收入 ${money(sumBy(today, 'income'))}</div></div>`;
    if (c.id === 'savings') return `<button class="mini" data-action="open-saving"><div class="top"><span class="dot" style="background:var(--gold)"></span><span>储蓄目标</span></div><div class="num amt"><small>¥</small>${(state.savings.saved / 100).toLocaleString('zh-CN')}</div><div class="sub">${esc(state.savings.name)} · ${Math.round(state.savings.saved / state.savings.target * 100)}%</div><div class="bar"><i style="width:${Math.round(state.savings.saved / state.savings.target * 100)}%"></i></div></button>`;
    return `<button class="mini" data-action="stats-week"><div class="top"><span class="dot" style="background:var(--transfer)"></span><span>七日趋势</span></div><div class="spark">${sparkBars()}</div></button>`;
  };
  const recent = [iso(2025, 10, 11), iso(2025, 10, 10), iso(2025, 10, 9)].map(date => dayGroup(date)).join('');
  return `
    <div class="hero">
      <div class="lead"><i>2025</i> 年 10 月 · 本月支出</div>
      <div class="big amt"><span class="cur">¥</span>${(mExp / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
      <div class="delta">较上月 <b>↓ 8.4%</b> · 预算已用 ${used}%</div>
      <div class="rule"></div>
      <div class="split">
        <div><div class="l">本月收入</div><div class="v amt"><small>¥</small>${(mInc / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
        <div><div class="l">本月结余</div><div class="v amt"><small>¥</small>${((mInc - mExp) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
      </div>
    </div>
    <div class="section-head"><h3>今日概览</h3><button data-action="home-settings">卡片可自定义 ${icon('chevron-right')}</button></div>
    <div class="grid2">${card.map(mini).join('')}</div>
    ${state.reviews.length ? `<div class="section-head"><h3>待确认</h3><small>${state.reviews.length} 条草稿</small></div>${reviews}` : ''}
    <div class="section-head"><h3>最近流水</h3><button data-page="stats">查看统计 ${icon('chevron-right')}</button></div>
    ${recent}`;
}
function sparkBars() {
  const days = [5, 6, 7, 8, 9, 10, 11].map(d => sumBy(dayTxns(iso(TODAY.y, TODAY.m, d)), 'expense'));
  const max = Math.max(...days, 1);
  return days.map((v, i) => `<i class="${i === 6 ? 'on' : i === 5 ? 'gold' : ''}" style="height:${Math.max(12, Math.round(v / max * 100))}%"></i>`).join('');
}
function dayGroup(date) {
  const list = dayTxns(date);
  if (!list.length) return '';
  return `<div class="daygroup"><div class="dh"><div class="d"><b>${fmtDateCN(date)}</b>${dateLabel(date)}</div><div class="s">支出 ${money(sumBy(list, 'expense'))}</div></div>${list.map(txnRow).join('')}</div>`;
}
function txnRow(t) {
  const cat = CAT_BY_ID(t.catId);
  const cls = t.kind === 'income' ? 'ico' : (cat && cat.id === 'shop' ? 'ico g' : (cat && cat.id === 'trans' ? 'ico b' : 'ico r'));
  const sign = t.kind === 'income' ? '+' : t.kind === 'transfer' ? '' : '-';
  const clsAmt = t.kind === 'income' ? 'a inc amt' : t.kind === 'transfer' ? 'a trf amt' : 'a exp amt';
  const sub = t.kind === 'transfer' ? `转账 · ${ACC(t.accountId)?.name || ''} → ${ACC(t.toId)?.name || ''}`
    : `${cat ? cat.name : ''}${t.sub ? ' / ' + t.sub : ''} · ${ACC(t.accountId)?.name || ''}${t.tag ? ' · ' + esc(t.tag) : ''}`;
  return `<button class="txn" data-action="open-edit" data-id="${t.id}">
      <div class="${cls}">${t.kind === 'transfer' ? icon('swap') : (cat ? cat.name[0] : '转')}</div>
      <div class="m"><div class="t">${esc(t.title)}</div><div class="s">${sub}</div></div>
      <div class="a-wrap"><div class="s time">${hhmm(t.time)}</div><div class="${clsAmt}">${sign}<span class="cur">¥</span>${(t.amount / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
    </button>`;
}

// ── 首页搜索 ─────────────────────────────────────────────
function matchesQuery(t, q) {
  const needle = q.toLowerCase();
  const cat = CAT_BY_ID(t.catId);
  const hay = [t.title, t.note, t.tag, cat ? cat.name : '', t.sub, String(t.amount / 100), ACC(t.accountId)?.name].join(' ').toLowerCase();
  return hay.includes(needle);
}
function searchView() {
  header('搜索', '日常账本', `<button class="text-button" data-action="close-search">取消</button>`);
  const q = state.query.trim();
  const results = q ? state.txns.filter(t => matchesQuery(t, q)).sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time)) : [];
  return `<div class="searchbox">${icon('search')}<input id="searchInput" placeholder="搜索商户 / 备注 / 标签 / 金额" value="${esc(state.query)}"></div>
    <div id="searchResults">${searchResultBody(q, results)}</div>`;
}
function searchResultBody(q, results) {
  if (!q) return `<p class="form-intro" style="padding:6px 4px">输入关键词开始搜索。支持商户、备注、标签、账户与金额。</p>`;
  if (!results.length) return `<div class="empty">${icon('search')}<h3>没有匹配的账目</h3><p>换个关键词试试。</p></div>`;
  return `<div class="section-head"><h3>${results.length} 条结果</h3><small>支出 ${money(sumBy(results.filter(t => t.kind === 'expense'), 'expense'))}</small></div>
    <div class="card tight">${results.map(txnRow).join('')}</div>`;
}

// ── 日历 ─────────────────────────────────────────────────
function calendarView() {
  header('日历', '日常账本', ib('search', '搜索', 'search'));
  const y = state.calYear, m = state.calMonth;
  let cells = calCells(y, m, state.selectedDate, true);
  const list = dayTxns(state.selectedDate);
  return `
    <div class="card" style="padding:16px 12px 14px">
      <div class="cal-head"><button class="icon-button" data-action="cal-prev" aria-label="上个月">${icon('chevron-left')}</button>
        <div class="mo">${y} 年 ${m} 月</div>
        <button class="icon-button" data-action="cal-next" aria-label="下个月">${icon('chevron-right')}</button></div>
      <div class="cal-grid"><div class="wd">一</div><div class="wd">二</div><div class="wd">三</div><div class="wd">四</div><div class="wd">五</div><div class="wd">六</div><div class="wd">日</div>${cells}</div>
    </div>
    <div class="cal-sum">
      <div class="b"><div class="l">当日支出</div><div class="v amt exp">${money(sumBy(list, 'expense'))}</div></div>
      <div class="b"><div class="l">当日收入</div><div class="v amt inc">${money(sumBy(list, 'income'))}</div></div>
    </div>
    <div class="section-head"><h3>${fmtDateCN(state.selectedDate)} · ${list.length} 笔</h3><button data-action="new-entry-day">记一笔 ${icon('chevron-right')}</button></div>
    <div class="card tight">${list.length ? list.map(txnRow).join('') : `<div class="empty">${icon('note')}<h3>这天还没有记录</h3><p>点右下角「+」记一笔。</p></div>`}</div>`;
}
function calCells(y, m, selDate, markDots, action = 'cal-pick') {
  const first = new Date(y, m - 1, 1);
  const startIdx = (first.getDay() + 6) % 7;
  const days = new Date(y, m, 0).getDate();
  const prevDays = new Date(y, m - 1, 0).getDate();
  let cells = '';
  for (let i = 0; i < startIdx; i++) cells += `<div class="day dim">${prevDays - startIdx + i + 1}</div>`;
  for (let d = 1; d <= days; d++) {
    const date = iso(y, m, d);
    const has = markDots && dayTxns(date).length > 0;
    const sel = date === selDate;
    cells += `<button class="day ${sel ? 'sel' : ''}" data-action="${action}" data-date="${date}">${d}${has ? '<i class="dot"></i>' : ''}</button>`;
  }
  const total = startIdx + days, trail = (7 - total % 7) % 7;
  for (let i = 1; i <= trail; i++) cells += `<div class="day dim">${i}</div>`;
  return cells;
}

// ── 资产 ─────────────────────────────────────────────────
function assetsView() {
  const assets = state.accounts.filter(a => a.group === 'asset' && !a.archived);
  const debts = state.accounts.filter(a => a.group === 'debt' && !a.archived);
  const archivedCount = state.accounts.filter(a => a.archived).length;
  const totalA = assets.reduce((s, a) => s + a.base, 0);
  const totalD = debts.reduce((s, a) => s + a.base, 0);
  header('资产', `${assets.length + debts.length} 个账户`, ib('assets-trend', '资产趋势', 'trend') + ib('add-account', '新增账户', 'plus'));
  const row = a => `<button class="acct" data-action="open-account" data-id="${a.id}"><div class="ico" style="${TONE_BG[a.tone]}">${a.mark}</div>
      <div class="m"><div class="t">${a.name}</div><div class="s">${a.type}${a.note ? ' · ' + esc(a.note) : ''}</div></div>
      <div class="a amt ${a.base < 0 ? 'exp' : ''}">${money(a.base)}</div></button>`;
  const savedPct = Math.round(state.savings.saved / state.savings.target * 1000) / 10;
  return `
    <div class="hero" style="margin-top:4px">
      <div class="lead"><i>净</i> 净资产</div>
      <div class="big amt"><span class="cur">¥</span>${((totalA + totalD) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
      <div class="rule"></div>
      <div class="split">
        <div><div class="l">总资产</div><div class="v amt" style="color:#8fd9b4"><small>¥</small>${(totalA / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
        <div><div class="l">总负债</div><div class="v amt" style="color:#f0a99b"><small>¥</small>${(Math.abs(totalD) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
      </div>
    </div>
    <div class="section-head"><h3>资产账户</h3><small>${assets.length} 个</small></div>
    <div class="card tight">${assets.map(row).join('')}<div class="group-total"><span>资产小计</span><b class="amt">${money(totalA)}</b></div></div>
    <div class="section-head"><h3>负债账户</h3><small>${debts.length} 个</small></div>
    <div class="card tight">${debts.map(row).join('')}<div class="group-total"><span>负债小计</span><b class="amt exp">${money(totalD)}</b></div></div>
    ${archivedCount ? `<button class="entry-row" style="margin-top:14px" data-action="show-archived"><div class="l"><div class="ico">${icon('database')}</div>已归档账户</div><div class="r">${archivedCount} 个 ${icon('chevron-right')}</div></button>` : ''}
    <div class="section-head"><h3>储蓄目标</h3><small>1 个</small></div>
    <button class="card goal" data-action="open-saving" style="width:100%;text-align:left">
      <div class="gt"><div class="n">${esc(state.savings.name)}</div><div class="p">${savedPct}%</div></div>
      <div class="bar"><i style="width:${savedPct}%"></i></div>
      <div class="gl"><span>已存 ${money(state.savings.saved)}</span><span>目标 ${money(state.savings.target)}</span></div>
    </button>
    <div style="margin-top:12px">
      <button class="entry-row" data-action="assets-trend"><div class="l"><div class="ico">${icon('trend')}</div>资产趋势</div><div class="r">近 12 个月 ${icon('chevron-right')}</div></button>
    </div>`;
}

// ── 统计 ─────────────────────────────────────────────────
function statsList() {
  let list;
  if (state.statsRange === 'year') list = state.txns.filter(t => t.date.startsWith(String(TODAY.y)));
  else if (state.statsRange === 'week') list = state.txns.filter(t => t.date >= iso(2025, 10, 5) && t.date <= iso(2025, 10, 11));
  else if (state.statsRange === 'custom') list = state.txns.filter(t => t.date >= state.statsCustom.start && t.date <= state.statsCustom.end);
  else list = monthTxns();
  if (state.statFilter.accountId) list = list.filter(t => t.accountId === state.statFilter.accountId || t.toId === state.statFilter.accountId);
  if (state.statFilter.catId) list = list.filter(t => t.catId === state.statFilter.catId);
  return list;
}
const rangeLabel = () => ({ week: '本周', month: '本月', year: '本年', custom: `${state.statsCustom.start.slice(5)} ~ ${state.statsCustom.end.slice(5)}` })[state.statsRange] || '本月';
function statsView() {
  const filterOn = state.statFilter.accountId || state.statFilter.catId;
  header('统计', '日常账本', ib('stats-filter', '筛选', 'filter') + (filterOn ? `<button class="text-button" data-action="clear-stats-filter">清除</button>` : ''));
  const range = state.statsRange;
  const list = statsList();
  const exp = sumBy(list, 'expense'), inc = sumBy(list, 'income');
  const byCat = {};
  list.filter(t => t.kind === 'expense').forEach(t => { byCat[t.catId] = (byCat[t.catId] || 0) + t.amount; });
  const rows = Object.entries(byCat).map(([id, v]) => ({ cat: CAT_BY_ID(id), v })).filter(r => r.cat).sort((a, b) => b.v - a.v);
  const total = rows.reduce((s, r) => s + r.v, 0) || 1;
  const C = 339.29;
  let acc = 0;
  const arcs = rows.map((r, i) => { const len = r.v / total * C; const el = `<circle cx="75" cy="75" r="54" stroke="${CHART_COLORS[i % CHART_COLORS.length]}" stroke-dasharray="${len.toFixed(2)} ${(C - len).toFixed(2)}" stroke-dashoffset="${(-acc).toFixed(2)}"/>`; acc += len; return el; }).join('');
  const legend = rows.map((r, i) => `<div class="lg"><i style="background:${CHART_COLORS[i % CHART_COLORS.length]}"></i>${r.cat.name}<b>${moneyShort(r.v)}</b></div>`).join('') || '<p class="form-intro">暂无支出</p>';
  const rank = rows.map((r, i) => `<button class="r rank-row" data-action="stats-cat" data-id="${r.cat.id}">
      <div class="rt"><span class="n">${r.cat.name}</span><span class="pc">${(r.v / total * 100).toFixed(1)}%</span><span class="amtv">${money(r.v)}</span></div>
      <div class="rb"><i style="width:${(r.v / rows[0].v * 100).toFixed(1)}%;background:${CHART_COLORS[i % CHART_COLORS.length]}"></i></div></button>`).join('');
  const bars = [['5月', 70], ['6月', 82], ['7月', 74], ['8月', 97], ['9月', 81], ['10月', Math.max(30, Math.min(100, Math.round(exp / state.budgetTotal * 100)))]]; 
  const filterChip = filterOn ? `<div class="filter-note">${icon('filter')} 已筛选:${state.statFilter.accountId ? ACC(state.statFilter.accountId).name : ''}${state.statFilter.catId ? CAT_BY_ID(state.statFilter.catId).name : ''}</div>` : '';
  const customBar = range === 'custom' ? `<button class="range-bar" data-action="edit-custom-range">${icon('calendar')} <span>${rangeLabel()}</span><span class="muted">修改</span></button>` : '';
  return `
    <div class="seg">${[['week', '本周'], ['month', '本月'], ['year', '本年'], ['custom', '自定义']].map(([k, n]) => `<button class="${range === k ? 'on' : ''}" data-action="stats-range" data-range="${k}">${n}</button>`).join('')}</div>
    ${customBar}
    ${filterChip}
    <div class="overview">
      <div class="o"><div class="l">支出</div><div class="v amt exp"><small>¥</small>${(exp / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
      <div class="o"><div class="l">收入</div><div class="v amt inc"><small>¥</small>${(inc / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
      <div class="o"><div class="l">结余</div><div class="v amt"><small>¥</small>${((inc - exp) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
    </div>
    <div class="section-head"><h3>分类占比</h3><small>按支出</small></div>
    <div class="card" style="padding:0"><div class="donut-wrap">
      <div class="donut"><svg width="142" height="142" viewBox="0 0 150 150"><g transform="rotate(-90 75 75)" fill="none" stroke-width="20">${arcs}</g></svg>
        <div class="ctr"><div class="l">${rangeLabel()}支出</div><div class="v amt">${moneyShort(exp)}</div></div></div>
      <div class="legend">${legend}</div>
    </div></div>
    <div class="section-head"><h3>分类排行</h3><small>共 ${rows.length} 类</small></div>
    <div class="card rank">${rank || '<p class="form-intro" style="padding:8px 0">暂无数据</p>'}</div>
    <div class="section-head"><h3>月度趋势</h3><small>近 6 个月</small></div>
    <div class="card" style="padding:0 0 14px"><div class="barchart">${bars.map(([l, h]) => `<div class="bar ${l === '10月' ? 'cur' : ''}"><div class="col" style="height:${h}%"></div><div class="lb">${l}</div></div>`).join('')}</div></div>`;
}

// ── 我的 ─────────────────────────────────────────────────
function meView() {
  header('我的', '设置', ib('open-notifications', '通知', 'bell'));
  const item = (ico, tone, title, sub, right, subKey) => `
    <button class="setitem" data-action="open-sub" data-sub="${subKey}">
      <div class="ico ${tone}">${icon(ico)}</div>
      <div class="t">${title}${sub ? `<div class="s">${sub}</div>` : ''}</div>
      <div class="r">${right || ''}${icon('chevron-right')}</div>
    </button>`;
  const current = state.ledgers.find(l => l.current);
  return `
    <div class="profile"><div class="av">翰</div>
      <div class="m"><div class="n">han1997</div>
        <div class="s">${icon('book')}当前账本 · ${current ? current.name : '—'} · 已记 ${state.txns.length} 笔</div></div></div>
    <div class="setgroup"><div class="lab">账本</div><div class="setlist">
      ${item('book', '', '账本管理', `多账本隔离 · 当前「${current ? current.name : ''}」`, '', 'ledgers')}
      ${item('tag', '', '分类管理', `${CATS.length - state.disabledCats.length} / ${CATS.length} 启用`, '', 'categories')}
      ${item('tag', '', '标签管理', `${state.tags.length} 个标签`, '', 'tags')}
    </div></div>
    <div class="setgroup"><div class="lab">计划</div><div class="setlist">
      ${item('plan', 'g', '报销', `${state.reimburse.length} 个项目进行中`, `<span class="badge">${state.reimburse.length}</span>`, 'reimburse')}
      ${item('plan', 'g', '借贷', `${state.lending.length} 笔在途`, '', 'lending')}
      ${item('plan', 'g', '分期', `${state.installments.length} 个计划`, '<span class="badge r">待还</span>', 'installments')}
      ${item('trend', 'g', '理财', `${state.wealth.length} 只 · 累计投入 ${moneyShort(state.wealth.reduce((s, w) => s + w.invested, 0))}`, '', 'wealth')}
    </div></div>
    <div class="setgroup"><div class="lab">AI 配置</div><div class="setlist">
      ${item('ai', 'b', '模型管理', `${state.models.filter(m => m.enabled).length} / ${state.models.length} 已启用`, '', 'models')}
      ${item('tag', 'b', '商户规则', `${state.merchantRules.length} 条自动归类规则`, '', 'merchants')}
    </div></div>
    <div class="setgroup"><div class="lab">数据</div><div class="setlist">
      ${item('database', '', '备份与恢复', state.webdav.enabled ? `WebDAV · 格式 6 · ${state.backups.length} 份` : `仅本地 · ${state.backups.length} 份`, `<span class="badge ${state.backups.length ? 'g' : ''}">${state.backups.length ? '已备份' : '未备份'}</span>`, 'backup')}
      ${item('upload', '', '导入账单', '一木记账 .xls / .zip', '', 'importBill')}
      ${item('download', '', '导出 CSV', '', '', 'export')}
      ${item('shield', '', '隐私与权限', '通知 / 截图采集', '', 'privacy')}
    </div></div>`;
}

// ── 记一笔(一屏完成,分类恒有值) ────────────────────────
function currentCats(kind) {
  if (kind === 'income') return INCOME_CATS;
  if (kind === 'transfer') return [];
  return CATS_ACTIVE();
}
function defaultCatId(kind) {
  const cats = currentCats(kind);
  return cats.length ? cats[0].id : null;
}
function editView() {
  const e = state.edit;
  header(e.id ? '编辑账目' : '记一笔', '日常账本', e.id ? `<button class="text-button" data-action="delete-entry">删除</button>` : '', true, 'back-edit');
  const cats = currentCats(e.kind);
  const parent = cats.find(c => c.id === e.catId) || cats[0];
  if (parent && e.catId !== parent.id) e.catId = parent.id;
  const digits = e.amountStr || '';

  const typebar = `<div class="typebar">${[['expense', '支出'], ['income', '收入'], ['transfer', '转账']].map(([k, n]) => `<button class="${e.kind === k ? 'on' : ''}" data-action="set-type" data-kind="${k}">${n}</button>`).join('')}</div>`;

  // 分类:一级图标方块(5 列);选中一级的二级区直接插在「它所在那一行下方」
  let catArea = '';
  if (e.kind !== 'transfer' && parent) {
    const perRow = 5;
    const rows = [];
    for (let i = 0; i < cats.length; i += perRow) rows.push(cats.slice(i, i + perRow));
    const rowsHtml = rows.map(rowCats => {
      const tiles = rowCats.map(c => `<button class="cat-tile ${c.id === e.catId ? 'on' : ''}" data-action="pick-cat" data-id="${c.id}"><span class="ci">${c.name[0]}</span><span class="cl">${c.name}</span></button>`).join('');
      const active = rowCats.some(c => c.id === e.catId);
      const subs = active && parent.subs.length
        ? `<div class="sub-panel">${parent.subs.map(([n]) => `<button class="sub-tile ${e.sub === n ? 'on' : ''}" data-action="pick-sub" data-sub="${n}">${n}</button>`).join('')}</div>`
        : '';
      return `<div class="cat-row">${tiles}</div>${subs}`;
    }).join('');
    catArea = `<div class="cat-grid">${rowsHtml}</div>`;
  } else if (e.kind === 'transfer') {
    catArea = `<div class="cat-grid transfer">
      <button class="wide-row" data-action="pick-account" data-side="from"><span class="k">转出</span><span class="v">${ACC(e.fromId)?.name || '选择账户'}</span>${icon('chevron-right')}</button>
      <button class="wide-row" data-action="pick-account" data-side="to"><span class="k">转入</span><span class="v">${ACC(e.toId)?.name || '选择账户'}</span>${icon('chevron-right')}</button>
    </div>`;
  }

  // 备注:真实输入框,点按直接弹系统输入法(不弹窗);金额:大点击区,点按收起输入法回到数字键盘
  const meta = `<div class="meta-row">
      <span class="note-ico">${icon('note')}</span>
      <input class="note-input ${e.note ? 'filled' : ''}" type="text" data-field="note" placeholder="添加备注…" value="${esc(e.note || '')}" maxlength="80">
      <button class="amt-zone" data-action="focus-amount" aria-label="输入金额"><span class="meta-amt amt ${e.kind} ${digits === '' ? 'zero' : ''}"><span class="cur">¥</span>${amountInput(digits)}<span class="caret"></span></span></button>
    </div>`;

  // 底部一行胶囊:账户 / 时间 / 标签 / AI
  const chip = (action, ico, label, extra = '') => `<button class="chip-btn" data-action="${action}" ${extra}>${icon(ico)}<span>${esc(label)}</span></button>`;
  const accLabel = e.kind === 'transfer' ? '账户' : (ACC(e.accountId)?.name || '账户');
  const chips = `<div class="chip-strip">
      ${e.kind === 'transfer' ? '' : chip('pick-account', 'card', accLabel)}
      ${chip('pick-datetime', 'calendar', `${relDate(e.date)} ${hhmm(e.time)}`)}
      ${chip('pick-tag', 'tag', e.tag || '标签')}
      <button class="chip-btn ai" data-action="open-sub" data-sub="ai">${icon('ai')}<span>AI 记账</span></button>
    </div>`;

  return `${typebar}${catArea}${meta}${chips}`;
}

// 相对日期:今天 / 昨天 / M月D日
function relDate(date) {
  const today = iso(TODAY.y, TODAY.m, TODAY.d);
  if (date === today) return '今天';
  const d = new Date(Date.UTC(TODAY.y, TODAY.m - 1, TODAY.d)); d.setUTCDate(d.getUTCDate() - 1);
  if (date === iso(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate())) return '昨天';
  return fmtDateCN(date);
}

// 选分类后:若二级面板被裁切,自动滚动分类区把它露出来(避免手动滑动找二级)
function revealSubPanel() {
  const g = document.querySelector('.cat-grid');
  const p = document.querySelector('.sub-panel');
  if (!g || !p) return;
  const gr = g.getBoundingClientRect();
  const pr = p.getBoundingClientRect();
  const cut = pr.bottom - gr.bottom;
  if (cut > 0) g.scrollTop += Math.ceil(cut) + 10;
}

// 底部停靠:数字键盘常驻(与参考应用一致),右侧竖条保存
function editorDock() {
  const digits = state.edit.amountStr || '';
  const save = `<button class="save-btn" data-action="save-entry" ${digits === '' ? 'disabled' : ''}>保 存</button>`;
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', '⌫']
    .map(k => `<button class="key ${k === '⌫' ? 'del' : ''}" data-action="key" data-key="${k}">${k === '⌫' ? icon('backspace') : k}</button>`).join('');
  return `<div class="pad-row"><div class="keypad">${keys}</div><div class="pad-side">${save}</div></div>`;
}
function openEdit(idOrNull, presetDate) {
  const existing = idOrNull ? state.txns.find(t => t.id === idOrNull) : null;
  const baseKind = existing ? existing.kind : 'expense';
  const cats = currentCats(baseKind);
  const catId = existing ? existing.catId : (cats[0] ? cats[0].id : null);
  state.edit = existing
    ? { id: existing.id, kind: baseKind, amountStr: (existing.amount / 100).toString(), catId, sub: existing.sub || '', accountId: existing.accountId, fromId: existing.accountId, toId: existing.toId || 'alipay', date: existing.date, time: existing.time.length === 5 ? existing.time + ':00' : existing.time, tag: existing.tag || '', note: existing.note || '' }
    : { id: null, kind: 'expense', amountStr: '', catId, sub: '', accountId: 'cmb_debit', fromId: 'cmb_debit', toId: 'alipay', date: presetDate || state.selectedDate || iso(TODAY.y, TODAY.m, TODAY.d), time: '12:38:00', tag: '', note: '' };
  go('edit');
}

// ── 手机内底部弹层 ───────────────────────────────────────
function sheetHtml(s) {
  return `<div class="phone-scrim" data-close></div>
    <div class="phone-sheet ${s.tall ? 'tall' : ''}">
      <div class="phone-sheet-head"><h3>${esc(s.title)}</h3><button class="icon-button plain" data-close aria-label="关闭">${icon('x')}</button></div>
      <div class="phone-sheet-body">${s.body}</div>
    </div>`;
}
function pickSheet(title, items, action) {
  return { title, body: `<div class="pick-list">${items.map(it => `<button class="pick ${it.sel ? 'sel' : ''}" data-action="${action}" ${it.data}>${it.icon ? icon(it.icon) : ''}<span class="grow">${it.label}</span>${it.right ? `<span class="muted small">${it.right}</span>` : ''}${it.sel ? icon('check') : ''}</button>`).join('')}</div>` };
}
function timeSeg(part, val, label) {
  return `<div class="time-seg"><button class="icon-button" data-action="time-step" data-part="${part}" data-dir="1">${icon('chevron-up')}</button><div class="val amt">${val}</div><button class="icon-button" data-action="time-step" data-part="${part}" data-dir="-1">${icon('chevron-down')}</button><div class="lab">${label}</div></div>`;
}

// ── 导入账单(一木记账 .xls / .zip)─────────────────────
// 演示数据:行数/区间/合计与真实样本一致;清单为代表性示例(数量按设计稿 34 / 18)。
const YIMU_PREVIEW = {
  name: '账单_1009122349.zip',
  rows: 6008,
  range: '2018-10-11 ~ 2026-10-08',
  expense: -54557364,
  income: 54145022,
  net: -412342,
  newCats: 34,
  newAccs: 18
};
const YIMU_NEW_CATS = ['饮料酒水', '休闲零食', '生鲜食品', '买菜', '奶茶', '粮油调味', '请客吃饭', '虚拟充值', '宠物用品', '装修装饰', '配饰腕表', '生活电器', '母婴玩具', '办公用品', '高速费', '汽车充电', '保养修车', '理发', '家政清洁', '车位费', '滋补保健', '棋牌桌游', '孝敬长辈', '借出', '理财支出', '补贴', '礼金人情', '奖金', '中奖', '兼职外快', '公积金', '收红包', '借入', '理财盈利'];
const YIMU_NEW_ACCS = ['现金', '支付宝', '微信零钱', '安徽农金信用卡', '花呗', '京东白条', '美团月付', '抖音月付', '公积金', '招商银行储蓄卡', '工商银行储蓄卡', '建设银行储蓄卡', '余额宝', '零钱通', '云闪付', '校园卡', '交通卡', '投资理财'];

function importBillBody() {
  const st = state.importBill;
  if (st.phase === 'pick') {
    return `<div class="import-empty"><div class="empty">${icon('upload')}<h3>还没有选择账单文件</h3><p>支持一木记账导出的 .xls 与 .zip;<br>解析只在本机进行,不上传。</p></div></div>
      <div class="sub-actions"><button class="action-button full" data-action="pick-import-file">${icon('upload')} 选择文件</button></div>
      <div class="inline-note">${icon('shield')} 原型不读写设备;点「选择文件」用演示数据模拟一次导入。</div>`;
  }
  if (st.phase === 'preview') {
    const current = state.ledgers.find(l => l.current);
    const fold = (label, list, key, open) => `
      <div class="section-head" style="margin-top:16px"><h3>${label}</h3><button data-action="toggle-import-list" data-v="${key}">${list.length} 个 ${icon(open ? 'chevron-down' : 'chevron-right')}</button></div>
      ${open ? `<div class="tag-list">${list.map(n => `<span class="tagchip">${esc(n)}</span>`).join('')}</div>` : ''}`;
    return `<div class="import-preview">
      <div class="setlist">
        <div class="pick static"><span class="mark">账</span><span class="grow"><b>一木记账账单</b><span class="small muted" style="display:block">${YIMU_PREVIEW.name}</span></span><span class="badge g">已识别</span></div>
        <div class="pick static"><span class="grow">数据行数<span class="small muted" style="display:block">${YIMU_PREVIEW.range}</span></span><span class="amt">${YIMU_PREVIEW.rows} 行</span></div>
        <div class="pick static"><span class="grow">支出合计</span><span class="amt exp">${money(YIMU_PREVIEW.expense)}</span></div>
        <div class="pick static"><span class="grow">收入合计</span><span class="amt inc">${money(YIMU_PREVIEW.income)}</span></div>
        <div class="pick static"><span class="grow">净额</span><span class="amt exp">${money(YIMU_PREVIEW.net)}</span></div>
      </div>
      <div class="section-head" style="margin-top:16px"><h3>导入到</h3></div>
      <div class="seg">
        <button class="${st.target === 'new' ? 'on' : ''}" data-action="toggle-import-target" data-v="new">新建账本「一木记账」</button>
        <button class="${st.target === 'current' ? 'on' : ''}" data-action="toggle-import-target" data-v="current">导入当前账本:${esc(current ? current.name : '—')}</button>
      </div>
      ${fold('将新建的分类', YIMU_NEW_CATS, 'cats', st.catsOpen)}
      ${fold('将新建的账户', YIMU_NEW_ACCS, 'accs', st.accsOpen)}
      <div class="sub-actions"><button class="action-button full" data-action="do-import">${icon('download')} 开始导入</button></div>
    </div>`;
  }
  if (st.phase === 'progress') {
    const pct = Math.round(st.processed / YIMU_PREVIEW.rows * 1000) / 10;
    return `<div class="import-progress"><p class="form-intro">正在导入,请不要离开本页…</p>
      <div class="goal"><div class="bar"><i style="width:${pct}%"></i></div>
        <div class="gl"><span>已处理 ${st.processed} / ${YIMU_PREVIEW.rows} 行</span><span>${pct}%</span></div></div></div>`;
  }
  const r = st.report || { inserted: 0, duplicates: 0, failed: 0, skipped: 0, categories: 0, accounts: 0 };
  return `<div class="import-report">
      <div class="inline-note ok">${icon('check-circle')} 导入完成:新增 ${r.inserted} · 重复 ${r.duplicates}</div>
      <div class="setlist" style="margin-top:14px">
        <div class="pick static"><span class="grow">新增</span><span class="amt inc">${r.inserted}</span></div>
        <div class="pick static"><span class="grow">重复</span><span class="amt">${r.duplicates}</span></div>
        <div class="pick static"><span class="grow">失败</span><span class="amt">${r.failed}</span></div>
        <div class="pick static"><span class="grow">跳过</span><span class="amt">${r.skipped}</span></div>
        <div class="pick static"><span class="grow">新建分类</span><span class="amt">${r.categories}</span></div>
        <div class="pick static"><span class="grow">新建账户</span><span class="amt">${r.accounts}</span></div>
      </div>
      <div class="inline-note">${icon('refresh')} 再次导入同一文件则全部计为重复,不会写双份。</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="import-again">重新选择文件</button></div>
    </div>`;
}

// ── 子页面 ───────────────────────────────────────────────
const SUBPAGES = {
  ledgers: () => ({
    title: '账本管理', actions: ib('add-ledger', '新增账本', 'plus'),
    body: `<p class="form-intro">多账本相互隔离,流水、预算与计划互不影响。</p>
      <div class="setlist">${state.ledgers.map(l => `<button class="pick ${l.current ? 'sel' : ''}" data-action="switch-ledger" data-id="${l.id}">
        ${icon('book')}<span class="grow">${esc(l.name)}</span>${l.current ? '<span class="badge g">当前</span>' : ''}</button>`).join('')}</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="add-ledger">${icon('plus')} 新建账本</button></div>`
  }),
  categories: () => ({
    title: '分类管理', actions: '',
    body: `<p class="form-intro">关闭的分类不会出现在记一笔中。</p>
      <div class="setlist">${CATS.map(c => `<div class="pick static"><span class="mark">${c.name[0]}</span><span class="grow">${c.name}<span class="small muted"> · ${c.subs.length} 个二级</span></span>
        <button class="switch ${state.disabledCats.includes(c.id) ? '' : 'on'}" data-action="toggle-cat" data-id="${c.id}" aria-label="启用 ${c.name}"><i></i></button></div>`).join('')}</div>`
  }),
  tags: () => ({
    title: '标签管理', actions: ib('add-tag', '新增标签', 'plus'),
    body: `<p class="form-intro">共 ${state.tags.length} 个标签。点标签可删除。</p>
      <div class="tag-list">${state.tags.map(t => `<button class="tag-item" data-action="delete-tag" data-tag="${esc(t)}"><span class="tagchip">${esc(t)}</span>${icon('trash')}</button>`).join('') || '<p class="form-intro">还没有标签。</p>'}</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="add-tag">${icon('plus')} 新建标签</button></div>`
  }),
  reimburse: () => ({
    title: '报销', actions: ib('add-reimburse', '新增报销', 'plus'),
    body: `<p class="form-intro">报销项目与到账记录。</p><div class="setlist">${state.reimburse.map(r => `<div class="pick static"><span class="grow"><b>${esc(r.name)}</b><span class="small muted" style="display:block">${r.status}</span></span><span class="amt">${money(r.amount)}</span><button class="mini-x" data-action="delete-reimburse" data-id="${r.id}">${icon('x')}</button></div>`).join('') || '<p class="form-intro">暂无报销项目。</p>'}</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="add-reimburse">${icon('plus')} 新增报销项目</button></div>`
  }),
  lending: () => ({
    title: '借贷', actions: ib('add-lending', '新增借贷', 'plus'),
    body: `<div class="setlist">${state.lending.map(r => `<div class="pick static"><span class="grow"><b>${esc(r.name)}</b><span class="small muted" style="display:block">${r.status}</span></span><span class="amt">${money(r.amount)}</span><button class="mini-x" data-action="delete-lending" data-id="${r.id}">${icon('x')}</button></div>`).join('') || '<p class="form-intro">暂无借贷。</p>'}</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="add-lending">${icon('plus')} 新增借贷</button></div>`
  }),
  installments: () => ({
    title: '分期', actions: ib('add-installment', '新增分期', 'plus'),
    body: `<p class="form-intro">每期本金转出、利息与费用计为支出。</p><div class="setlist">${state.installments.map(r => `<div class="pick static"><span class="grow"><b>${esc(r.name)}</b><span class="small muted" style="display:block">剩余 ${r.periods} 期 · 每期 ${money(r.amount)}</span></span><button class="mini-x" data-action="delete-installment" data-id="${r.id}">${icon('x')}</button></div>`).join('')}</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="add-installment">${icon('plus')} 新增分期计划</button></div>`
  }),
  wealth: () => ({
    title: '理财', actions: ib('add-wealth', '新增理财', 'plus'),
    body: `<p class="form-intro">金额台账:投入与赎回是转账,已实现盈亏计入收支。</p><div class="setlist">${state.wealth.map(w => `<div class="pick static"><span class="grow"><b>${esc(w.name)}</b><span class="small muted" style="display:block">投入 ${money(w.invested)}</span></span><span class="amt ${w.realized >= 0 ? 'inc' : 'exp'}">${w.realized >= 0 ? '+' : ''}${money(w.realized)}</span><button class="mini-x" data-action="delete-wealth" data-id="${w.id}">${icon('x')}</button></div>`).join('')}</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="add-wealth">${icon('plus')} 新增理财产品</button></div>`
  }),
  models: () => ({
    title: '模型管理', actions: '',
    body: `<p class="form-intro">云端阶梯解析与端侧分类器。原型不调用真实模型。</p><div class="setlist">${state.models.map(m => `<div class="pick static"><span class="grow">${esc(m.name)}<span class="small muted" style="display:block">${m.enabled ? '已启用' : '已停用'}</span></span><button class="switch ${m.enabled ? 'on' : ''}" data-action="toggle-model" data-id="${m.id}" aria-label="${m.name}"><i></i></button></div>`).join('')}</div>`
  }),
  merchants: () => ({
    title: '商户规则', actions: ib('add-merchant', '新增规则', 'plus'),
    body: `<p class="form-intro">命中商户关键词时自动归类。</p><div class="setlist">${state.merchantRules.map(r => `<div class="pick static"><span class="grow"><b>${esc(r.match)}</b><span class="small muted" style="display:block">→ ${CAT_BY_ID(r.cat)?.name || ''}</span></span><button class="mini-x" data-action="delete-merchant" data-id="${r.id}">${icon('x')}</button></div>`).join('')}</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="add-merchant">${icon('plus')} 新增商户规则</button></div>`
  }),
  backup: () => ({
    title: '备份与恢复', actions: '',
    body: `<div class="setlist">
        <div class="pick static"><span class="grow">WebDAV 备份<span class="small muted" style="display:block">${state.webdav.enabled ? state.webdav.url : '已关闭'}</span></span><button class="switch ${state.webdav.enabled ? 'on' : ''}" data-action="toggle-webdav" aria-label="WebDAV"><i></i></button></div>
      </div>
      <div class="section-head" style="margin-top:16px"><h3>本地备份</h3><small>${state.backups.length} 份</small></div>
      <div class="setlist">${state.backups.map(b => `<button class="pick" data-action="restore-backup" data-id="${b.id}">${icon('database')}<span class="grow">${esc(b.at)}<span class="small muted" style="display:block">格式 6 · ${b.size}</span></span>${icon('refresh')}</button>`).join('') || '<p class="form-intro">暂无备份。</p>'}</div>
      <div class="sub-actions"><button class="action-button full" data-action="create-backup">${icon('cloud')} 立即备份</button></div>`
  }),
  export: () => ({
    title: '导出 CSV', actions: '',
    body: `<p class="form-intro">按当前账本导出全部流水。原型不写文件,列出预览。</p>
      ${state.exported ? `<div class="inline-note ok">${icon('check-circle')} 已生成预览:${state.exported.rows} 行 · ${state.exported.size}</div>` : ''}
      <div class="setlist">
        <div class="pick static"><span class="grow">导出范围<span class="small muted" style="display:block">当前账本 · 全部时间</span></span></div>
        <div class="pick static"><span class="grow">包含字段<span class="small muted" style="display:block">日期、类型、分类、账户、金额、备注</span></span></div>
      </div>
      <div class="sub-actions"><button class="action-button full" data-action="do-export">${icon('download')} 生成 CSV</button></div>
      ${state.exported ? `<div class="csv-preview">${state.exported.preview.map(l => `<div class="csv-line">${esc(l)}</div>`).join('')}</div>` : ''}`
  }),
  importBill: () => ({
    title: '导入账单', actions: ib('import-help', '说明', 'cloud'),
    body: importBillBody()
  }),
  privacy: () => ({
    title: '隐私与权限', actions: '',
    body: `<p class="form-intro">原型不能修改系统权限,这里的开关只改变演示状态。</p>
      <div class="setlist">
        ${[['notify', '通知权限', '预算与草稿提醒'], ['screenshot', '截图采集', '付款截图自动识别'], ['background', '后台运行', '避免采集被系统中断']].map(([k, n, d]) => `<div class="pick static"><span class="grow">${n}<span class="small muted" style="display:block">${d}</span></span><button class="switch ${state.permissions[k] ? 'on' : ''}" data-action="toggle-permission" data-key="${k}" aria-label="${n}"><i></i></button></div>`).join('')}
      </div>
      <div class="inline-note">${icon('shield')} 数据仅保存在本机;原型不联网、不读写设备。</div>`
  }),
  budget: () => ({
    title: '本月预算', actions: '',
    body: `<p class="form-intro">预算用完不结转;退款会减少当月支出。</p>
      <div class="budget-row"><span>月度总额</span><b class="amt">${money(state.budgetTotal)}</b></div>
      <div class="seg budget-seg">${[500000, 800000, 1200000, 2000000].map(v => `<button class="${state.budgetTotal === v ? 'on' : ''}" data-action="set-budget" data-v="${v}">${moneyShort(v)}</button>`).join('')}</div>
      <div class="section-head" style="margin-top:16px"><h3>已用</h3><small>${Math.round(sumBy(monthTxns(), 'expense') / state.budgetTotal * 1000) / 10}%</small></div>
      <div class="goal"><div class="bar"><i style="width:${Math.min(100, Math.round(sumBy(monthTxns(), 'expense') / state.budgetTotal * 1000) / 10)}%"></i></div>
        <div class="gl"><span>已花 ${money(sumBy(monthTxns(), 'expense'))}</span><span>预算 ${money(state.budgetTotal)}</span></div></div>`
  }),
  settings: () => ({
    title: '首页卡片', actions: '',
    body: `<p class="form-intro">控制首页卡片的显示与顺序。</p>
      <div class="setlist">${state.cards.map((c, i) => `<div class="pick static">
        <button class="ord" data-action="card-up" data-id="${c.id}" ${i === 0 ? 'disabled' : ''}>${icon('chevron-up')}</button>
        <button class="ord" data-action="card-down" data-id="${c.id}" ${i === state.cards.length - 1 ? 'disabled' : ''}>${icon('chevron-down')}</button>
        <span class="grow">${c.name}</span>
        <button class="switch ${c.on ? 'on' : ''}" data-action="toggle-card" data-id="${c.id}" aria-label="${c.name}"><i></i></button></div>`).join('')}</div>
      <div class="sub-actions"><button class="action-button secondary full" data-action="reset-cards">恢复默认</button></div>`
  }),
  saving: () => ({
    title: '储蓄目标', actions: '',
    body: `<div class="saving-hero"><div class="n">${esc(state.savings.name)}</div>
        <div class="pct">${Math.round(state.savings.saved / state.savings.target * 1000) / 10}%</div>
        <div class="bar"><i style="width:${Math.round(state.savings.saved / state.savings.target * 100)}%"></i></div>
        <div class="gl"><span>已存 ${money(state.savings.saved)}</span><span>目标 ${money(state.savings.target)}</span></div></div>
      <p class="form-intro">存入或取出会生成一笔真实转账,不会重复改变余额。</p>
      <div class="sub-actions two"><button class="action-button" data-action="saving-deposit">存入</button><button class="action-button secondary" data-action="saving-withdraw">取出</button></div>`
  }),
  trend: () => ({
    title: '资产趋势', actions: '',
    body: `<p class="form-intro">近 12 个月净资产走势(由记账数据推导)。</p>
      <div class="trend-chart">${trendSvg()}</div>
      <div class="trend-legend"><span>最低 ${moneyShort(trendMin())}</span><span>最高 ${moneyShort(trendMax())}</span></div>
      <div class="info-row"><span>当前净资产</span><span class="amt">${money(state.accounts.reduce((s, a) => s + a.base, 0))}</span></div>`
  }),
  statsfilter: () => ({
    title: '统计筛选', actions: '',
    body: `<div class="chip-label">账户</div><div class="chip-row">${state.accounts.map(a => `<button class="chip ${state.statFilter.accountId === a.id ? 'on' : ''}" data-action="set-stat-account" data-id="${a.id}">${a.name}</button>`).join('')}</div>
      <div class="chip-label" style="margin-top:14px">分类</div><div class="chip-row">${CATS.map(c => `<button class="chip ${state.statFilter.catId === c.id ? 'on' : ''}" data-action="set-stat-cat" data-id="${c.id}">${c.name}</button>`).join('')}</div>
      <div class="sub-actions two"><button class="action-button full" data-action="apply-stats-filter">应用筛选</button><button class="action-button secondary" data-action="clear-stats-filter">清除</button></div>`
  }),
  addaccount: () => ({
    title: '新增账户', actions: '',
    body: `<p class="form-intro">支持储蓄、信用、预付、应收应付、借贷与投资账户。</p>
      <label class="input-field"><span>账户名称</span><input id="newAccName" placeholder="例如:工商银行储蓄卡" value="${esc(state.newAcc.name)}"></label>
      <div class="chip-label">分组</div><div class="chip-row">${[['asset', '资产'], ['debt', '负债']].map(([k, n]) => `<button class="chip ${state.newAcc.group === k ? 'on' : ''}" data-action="new-acc-group" data-v="${k}">${n}</button>`).join('')}</div>
      <div class="chip-label" style="margin-top:14px">类型</div><div class="chip-row">${ACCOUNT_TYPES.map(t => `<button class="chip ${state.newAcc.type === t.id ? 'on' : ''}" data-action="new-acc-type" data-v="${t.id}">${t.id}</button>`).join('')}</div>
      ${typeNeedsCard(state.newAcc.type) ? `<label class="input-field"><span>卡号(可选)</span><input id="newAccCard" inputmode="numeric" placeholder="6225 8801 2345 6789" value="${esc(state.newAcc.cardNo || '')}"></label>` : ''}
      <label class="input-field"><span>初始余额</span><input id="newAccBase" type="number" placeholder="0.00" value="${state.newAcc.base}"></label>
      <label class="input-field"><span>备注(可选)</span><input id="newAccNote" placeholder="例如:工资卡" value="${esc(state.newAcc.note)}"></label>
      <div class="sub-actions"><button class="action-button full" data-action="do-add-account">保存账户</button></div>`
  }),
  account: () => {
    const a = ACC(state.editingAccount) || state.accounts[0];
    const rows = state.txns.filter(t => t.accountId === a.id || t.toId === a.id).sort((x, y) => (y.date + y.time).localeCompare(x.date + x.time)).slice(0, 10);
    return {
      title: '账户详情', actions: ib('acc-archive', '归档', 'database'),
      body: `<div class="acct-hero"><div class="ico" style="${TONE_BG[a.tone]};width:52px;height:52px;border-radius:16px;font-size:22px;margin:0 auto 10px;display:grid;place-items:center;font-family:var(--serif);font-weight:600">${a.mark}</div>
          <div class="n">${esc(a.name)}</div>
          <div class="amt ${a.base < 0 ? 'exp' : ''}" style="font-size:26px;margin-top:6px">${money(a.base)}</div>
          <div class="small muted">${a.group === 'asset' ? '资产' : '负债'} · ${a.type}${a.cardNo ? ' · ' + cardTail(a.cardNo) : ''}</div></div>
        <div class="sub-actions two" style="margin-top:14px"><button class="action-button secondary" data-action="acc-rename">${icon('pencil')} 重命名</button><button class="action-button secondary" data-action="acc-calibrate">${icon('refresh')} 余额校准</button></div>
        <div class="setlist" style="margin-top:14px">
          <button class="pick" data-action="acc-edit-type"><span class="grow">类型<span class="small muted" style="display:block">${a.type}</span></span><span class="muted small">修改</span>${icon('chevron-right')}</button>
          ${typeNeedsCard(a.type) || a.cardNo ? `<button class="pick" data-action="acc-edit-card"><span class="grow">卡号<span class="small muted" style="display:block">${a.cardNo ? esc(a.cardNo) : '未填写'}</span></span><span class="muted small">修改</span>${icon('chevron-right')}</button>` : ''}
          <div class="pick static"><span class="grow">备注<span class="small muted" style="display:block">${esc(a.note || '未填写')}</span></span><button class="mini-x" data-action="acc-edit-note">${icon('pencil')}</button></div>
          <div class="pick static"><span class="grow">创建时间<span class="small muted" style="display:block">${a.createdAt}</span></span></div>
        </div>
        <div class="section-head" style="margin-top:16px"><h3>近期流水</h3><small>${rows.length} 笔</small></div>
        <div class="card tight">${rows.length ? rows.map(txnRow).join('') : '<p class="form-intro" style="padding:10px 4px">该账户暂无流水。</p>'}</div>
        <div class="sub-actions" style="margin-top:16px"><button class="action-button danger full" data-action="acc-delete">${icon('trash')} 删除账户</button></div>`
    };
  },
  archived: () => ({
    title: '已归档账户', actions: '',
    body: `<p class="form-intro">归档账户不计入资产合计,但仍保留流水。可恢复。</p>
      <div class="setlist">${state.accounts.filter(a => a.archived).map(a => `<div class="pick static"><div class="mark">${a.mark}</div><span class="grow">${esc(a.name)}<span class="small muted" style="display:block">${a.type} · ${money(a.base)}</span></span><button class="action-button secondary" style="min-height:36px;padding:6px 14px;font-size:12px" data-action="acc-restore" data-id="${a.id}">恢复</button></div>`).join('') || '<p class="form-intro">没有已归档的账户。</p>'}</div>`
  }),
  ai: () => ({
    title: 'AI 记账', actions: '',
    body: `<p class="form-intro">说一句话或贴一段文字,自动解析成账目。</p>
      <label class="input-field"><span>文字</span><input id="aiText" placeholder="例如:昨天打车花了 28 元" value="${esc(state.aiText)}"></label>
      <div class="ai-samples">${['昨天打车花了 28 元', '午餐 35', '星巴克 41.5'].map(s => `<button class="chip" data-action="ai-sample" data-v="${esc(s)}">${esc(s)}</button>`).join('')}</div>
      <div class="sub-actions"><button class="action-button full" data-action="ai-parse">${icon('ai')} 解析</button></div>
      ${state.aiResult ? `<div class="inline-note ok">${icon('check-circle')} 已识别:${esc(state.aiResult)}</div>
        <div class="sub-actions"><button class="action-button full" data-action="ai-apply">填入记一笔</button></div>` : ''}`
  })
};

function subpageHtml(sub) {  const def = SUBPAGES[sub.key];
  if (!def) return '';
  const page = def();
  return `<div class="phone-page">
      <div class="phone-page-head">
        <button class="icon-button plain" data-action="close-sub" aria-label="返回">${icon('arrow-left')}</button>
        <h2>${esc(page.title)}</h2>
        <div class="head-actions">${page.actions || ''}</div>
      </div>
      <div class="sub-body">${page.body}</div>
    </div>`;
}
function trendSeries() {
  const keys = ['May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct'];
  const vals = keys.map((_, i) => {
    const list = state.txns.filter(t => { const mm = t.date.slice(5, 7); return +mm <= 10 && +mm > 10 - (6 - i); });
    return 12000000 + sumBy(list, 'income') - sumBy(list, 'expense');
  });
  return vals;
}
const trendMin = () => Math.min(...trendSeries());
const trendMax = () => Math.max(...trendSeries());
function trendSvg() {
  const vals = trendSeries();
  const min = Math.min(...vals), max = Math.max(...vals);
  const W = 300, H = 120, pad = 8;
  const pts = vals.map((v, i) => {
    const x = pad + i * (W - pad * 2) / (vals.length - 1);
    const y = H - pad - (v - min) / (max - min || 1) * (H - pad * 2);
    return [x, y];
  });
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ');
  const area = `${line} L${pts[pts.length - 1][0].toFixed(1)} ${H} L${pts[0][0].toFixed(1)} ${H} Z`;
  const dots = pts.map(p => `<circle cx="${p[0].toFixed(1)}" cy="${p[1].toFixed(1)}" r="2.6" fill="#1a3c34"/>`).join('');
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none"><defs><linearGradient id="tg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1a3c34" stop-opacity=".18"/><stop offset="1" stop-color="#1a3c34" stop-opacity="0"/></linearGradient></defs>
    <path d="${area}" fill="url(#tg)"/><path d="${line}" fill="none" stroke="#1a3c34" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>${dots}</svg>`;
}

// ── toast ────────────────────────────────────────────────
let toastTimer;
function toast(s) {
  clearTimeout(toastTimer);
  const el = $('#toast');
  el.textContent = s;
  el.classList.add('visible');
  toastTimer = setTimeout(() => el.classList.remove('visible'), 2200);
}
let seq = 1000;
const nextId = () => ++seq;

// ── 动作分发 ─────────────────────────────────────────────
const actions = {
  // 导航
  'back': () => go('home'),
  'open-sub': el => openSub({ key: el.dataset.sub, title: '' }),
  'close-sub': () => closeSub(),
  'close-sheet': () => closeSheet(),
  'new-entry': () => openEdit(null),
  'new-entry-day': () => openEdit(null, state.selectedDate),
  'open-edit': el => openEdit(+el.dataset.id),

  // 编辑器
  'set-type': el => { const e = state.edit; e.kind = el.dataset.kind; const cats = currentCats(e.kind); e.catId = cats[0] ? cats[0].id : null; e.sub = ''; render(); revealSubPanel(); },
  'key': el => {
    const k = el.dataset.key, e = state.edit;
    let s = e.amountStr;
    if (k === '⌫') s = s.slice(0, -1);
    else if (k === '.') { if (!s.includes('.')) s = (s || '0') + '.'; }
    else { const dot = s.indexOf('.'); if (dot >= 0 && s.length - dot > 2) return; if (s.replace('.', '').length >= 9) return; s = (s === '0' ? '' : s) + k; }
    e.amountStr = s; render();
  },
  'pick-note': () => { const i = document.querySelector('.note-input'); if (i) { i.focus(); i.select?.(); } },
  'focus-amount': () => { const a = document.activeElement; if (a && a.blur) a.blur(); },
  'pick-cat': el => { const e = state.edit; e.catId = el.dataset.id; e.sub = ''; render(); revealSubPanel(); },
  'pick-sub': el => { state.edit.sub = el.dataset.sub; render(); },
  'pick-account': el => {
    const side = el.dataset.side;
    const cur = side === 'to' ? state.edit.toId : side === 'from' ? state.edit.fromId : state.edit.accountId;
    openSheet(pickSheet('选择账户', state.accounts.map(a => ({ label: a.name, right: money(a.base), sel: cur === a.id, data: `data-id="${a.id}" data-side="${side || ''}"` })), 'set-account'));
  },
  'set-account': el => { const id = el.dataset.id, side = el.dataset.side; if (side === 'to') state.edit.toId = id; else if (side === 'from') state.edit.fromId = id; else state.edit.accountId = id; closeSheet(); },
  // 合并的日期时间选择器:上方月历 + 下方时分秒步进
  'pick-datetime': () => openSheet({ title: '选择日期与时间', tall: true, body: `<div class="cal-head"><button class="icon-button" data-action="sheet-cal-nav" data-dir="-1">${icon('chevron-left')}</button><div class="mo">${state.calYear} 年 ${state.calMonth} 月</div><button class="icon-button" data-action="sheet-cal-nav" data-dir="1">${icon('chevron-right')}</button></div>
    <div class="cal-grid">${['一', '二', '三', '四', '五', '六', '日'].map(w => `<div class="wd">${w}</div>`).join('')}${calCells(state.calYear, state.calMonth, state.edit.date, true)}</div>
    <div class="datetime-time">${state.edit.time.split(':').map((val, i) => timeSeg(['h', 'm', 's'][i], val, ['时', '分', '秒'][i])).join('<span class="colon">:</span>')}</div>
    <div class="sub-actions two"><button class="action-button secondary" data-action="datetime-now">此刻</button><button class="action-button" data-close>完成</button></div>` }),
  'sheet-cal-nav': el => { const d = +el.dataset.dir; let m = state.calMonth + d, y = state.calYear; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } state.calMonth = m; state.calYear = y; actions['pick-datetime'](); },
  'cal-pick': el => { const date = el.dataset.date; if (state.page === 'edit' && state.edit) { state.edit.date = date; } state.selectedDate = date; if (state.sheet && state.sheet.title.includes('日期与时间')) actions['pick-datetime'](); else closeSheet(); },
  'time-step': el => { let [h, mi, se] = state.edit.time.split(':').map(Number); const d = +el.dataset.dir; if (el.dataset.part === 'h') h = (h + d + 24) % 24; else if (el.dataset.part === 'm') mi = (mi + d + 60) % 60; else se = (se + d + 60) % 60; state.edit.time = [h, mi, se].map(n => String(n).padStart(2, '0')).join(':'); actions['pick-datetime'](); },
  'datetime-now': () => { const n = new Date(); state.edit.date = iso(n.getFullYear(), n.getMonth() + 1, n.getDate()); state.edit.time = [n.getHours(), n.getMinutes(), n.getSeconds()].map(x => String(x).padStart(2, '0')).join(':'); actions['pick-datetime'](); toast('已设为当前时刻'); },
  'time-now': () => { const n = new Date(); state.edit.time = [n.getHours(), n.getMinutes(), n.getSeconds()].map(x => String(x).padStart(2, '0')).join(':'); closeSheet(); toast('已设为当前时刻'); },
  'pick-tag': () => {
    openSheet({ title: '选择标签', body: `<div class="tag-list">${state.tags.map(t => `<button class="pick ${state.edit.tag === t ? 'sel' : ''}" data-action="set-tag" data-tag="${esc(t)}">${icon('tag')}<span class="grow">${esc(t)}</span>${state.edit.tag === t ? icon('check') : ''}</button>`).join('')}</div>
      <div class="sub-actions two"><button class="action-button" data-action="new-tag-inline">${icon('plus')} 新建</button><button class="action-button secondary" data-action="set-tag" data-tag="">清除</button></div>` });
  },
  'set-tag': el => { state.edit.tag = el.dataset.tag; closeSheet(); },
  'new-tag-inline': () => inputSheet('新建标签', { label: '标签名称', action: 'do-new-tag-inline' }),
  'do-new-tag-inline': () => { const t = (state.inputDraft || '').trim(); if (!t) { toast('请输入标签名称'); return; } if (!state.tags.includes(t)) state.tags.push(t); state.edit.tag = t; closeSheet(); toast('标签已新增'); },
  'save-entry': () => {
    const e = state.edit;
    const amount = Math.round(parseFloat(e.amountStr || '0') * 100);
    if (!amount) { toast('请输入金额'); return; }
    if (e.kind !== 'transfer' && !e.catId) { toast('请选择分类'); return; }
    if (e.kind === 'transfer' && e.fromId === e.toId) { toast('转出与转入账户不能相同'); return; }
    const cat = CAT_BY_ID(e.catId);
    const title = e.kind === 'transfer' ? `${ACC(e.fromId)?.name} → ${ACC(e.toId)?.name}` : (e.sub || (cat ? cat.name : '未分类'));
    const rec = { id: e.id || nextId(), date: e.date, time: e.time, title, catId: e.kind === 'transfer' ? null : e.catId, sub: e.sub, accountId: e.kind === 'transfer' ? e.fromId : e.accountId, toId: e.kind === 'transfer' ? e.toId : null, kind: e.kind, amount, tag: e.tag, note: e.note };
    if (e.id) { const i = state.txns.findIndex(t => t.id === e.id); state.txns[i] = rec; } else state.txns.push(rec);
    state.selectedDate = e.date;
    toast('已保存(演示,刷新即重置)');
    go('home');
  },
  'delete-entry': () => confirmPhone('删除账目', '删除后不可恢复(演示仅从内存移除)。', 'confirm-delete-entry'),
  'confirm-delete-entry': () => { state.txns = state.txns.filter(t => t.id !== state.edit.id); closeSheet(); toast('账目已删除'); go('home'); },
  'back-edit': () => {
    const e = state.edit;
    if (e && (e.amountStr || e.note || e.tag) && !e.saved) confirmPhone('未保存的内容', '当前填写的内容将会丢失。', 'discard-edit', '放弃');
    else go('home');
  },
  'discard-edit': () => { closeSheet(); go('home'); },

  // 首页
  'search': () => { state.searchOpen = true; state.query = ''; render(); },
  'close-search': () => { state.searchOpen = false; state.query = ''; render(); },
  'confirm-review': el => { const r = state.reviews.find(x => x.id === el.dataset.id); if (r) { state.txns.push({ id: nextId(), date: iso(TODAY.y, TODAY.m, TODAY.d), time: '12:38:00', title: r.title.split(' · ')[0] + '消费', catId: 'other', sub: '', accountId: 'wechat', kind: 'expense', amount: r.amount, tag: '', note: '' }); state.reviews = state.reviews.filter(x => x.id !== r.id); } render(); toast('草稿已确认入账'); },
  'dismiss-review': el => { state.reviews = state.reviews.filter(x => x.id !== el.dataset.id); render(); toast('草稿已忽略'); },
  'home-settings': () => openSub({ key: 'settings' }),
  'edit-budget': () => openSub({ key: 'budget' }),
  'open-saving': () => openSub({ key: 'saving' }),
  'stats-week': () => { state.statsRange = 'week'; go('stats'); },
  'open-notifications': () => openSub({ key: 'privacy' }),

  // 日历
  'cal-day': el => { state.selectedDate = el.dataset.date; render(); },
  'cal-prev': () => { state.calMonth--; if (state.calMonth < 1) { state.calMonth = 12; state.calYear--; } render(); },
  'cal-next': () => { state.calMonth++; if (state.calMonth > 12) { state.calMonth = 1; state.calYear++; } render(); },

  // 资产
  'assets-trend': () => openSub({ key: 'trend' }),
  'add-account': () => { state.newAcc = { name: '', group: 'asset', type: '储蓄', base: '0', note: '', mark: '' }; openSub({ key: 'addaccount' }); },
  'new-acc-group': el => { state.newAcc.group = el.dataset.v; render(); },
  'new-acc-type': el => { state.newAcc.type = el.dataset.v; render(); },
  'do-add-account': () => {
    const name = ($('#newAccName') || {}).value;
    const base = $('#newAccBase') ? $('#newAccBase').value : state.newAcc.base;
    const note = $('#newAccNote') ? $('#newAccNote').value : state.newAcc.note;
    const cardEl = $('#newAccCard');
    const cardNo = cardEl ? fmtCardNo((cardEl.value || '').replace(/\D/g, '').slice(0, 19)) : '';
    const n = (name || '').trim();
    if (!n) { toast('请输入账户名称'); return; }
    const v = Math.round(parseFloat(base || '0') * 100) * (state.newAcc.group === 'debt' ? -1 : 1);
    state.accounts.push({ id: 'acc' + nextId(), name: n, group: state.newAcc.group, type: state.newAcc.type, mark: n[0], tone: state.newAcc.group === 'asset' ? 'green' : 'red', base: v, sub: `${state.newAcc.type} · 新建`, note: (note || '').trim(), cardNo, createdAt: iso(TODAY.y, TODAY.m, TODAY.d), archived: false });
    closeSub(); toast('账户已新增'); render();
  },

  // 账户详情 / 编辑
  'open-account': el => { state.editingAccount = el.dataset.id; openSub({ key: 'account' }); },
  'acc-edit-type': () => openSheet({ title: '账户类型', body: `<div class="setlist">${ACCOUNT_TYPES.map(t => `<button class="pick ${ACC(state.editingAccount).type === t.id ? 'sel' : ''}" data-action="acc-set-type" data-v="${t.id}">${icon(t.icon)}<span class="grow">${t.id}</span>${ACC(state.editingAccount).type === t.id ? icon('check') : ''}</button>`).join('')}</div>` }),
  'acc-set-type': el => { ACC(state.editingAccount).type = el.dataset.v; closeSheet(); render(); toast('类型已更新'); },
  'acc-edit-card': () => inputSheet('卡号', { label: '卡号(4 位分组)', value: ACC(state.editingAccount).cardNo || '', placeholder: '6225 8801 2345 6789', action: 'acc-save-card' }),
  'acc-save-card': () => { const a = ACC(state.editingAccount); const raw = (state.inputDraft || '').replace(/\D/g, '').slice(0, 19); a.cardNo = raw ? fmtCardNo(raw) : ''; closeSheet(); render(); toast('卡号已更新'); },
  'acc-rename': () => inputSheet('账户名称', { label: '名称', value: ACC(state.editingAccount).name, action: 'acc-save-name' }),
  'acc-save-name': el => { const v = (state.inputDraft || '').trim(); if (v) { ACC(state.editingAccount).name = v; } closeSheet(); render(); toast('名称已更新'); },
  'acc-edit-note': () => inputSheet('账户备注', { label: '备注', value: ACC(state.editingAccount).note || '', placeholder: '例如:工资卡', action: 'acc-save-note' }),
  'acc-save-note': () => { ACC(state.editingAccount).note = state.inputDraft || ''; closeSheet(); render(); toast('备注已更新'); },
  'acc-calibrate': () => inputSheet('余额校准', { label: '实际余额', value: (ACC(state.editingAccount).base / 100).toFixed(2), placeholder: '0.00', action: 'acc-save-calibrate', confirm: '校准' }),
  'acc-save-calibrate': () => {
    const a = ACC(state.editingAccount);
    const v = Math.round(parseFloat(state.inputDraft || '0') * 100);
    const diff = v - a.base;
    if (diff === 0) { closeSheet(); toast('余额未变,不生成调整记录'); return; }
    const acc = a;
    state.txns.push({ id: nextId(), date: iso(TODAY.y, TODAY.m, TODAY.d), time: '23:59:00', title: '余额校准', catId: null, sub: '', accountId: acc.id, kind: diff > 0 ? 'income' : 'expense', amount: Math.abs(diff), tag: '', note: '余额校准(不计入收支)', calibrate: true });
    acc.base = v;
    closeSheet(); render(); toast(`已校准,生成调整记录 ${money(Math.abs(diff))}`);
  },
  'acc-archive': () => { const a = ACC(state.editingAccount); a.archived = true; closeSub(); toast('账户已归档'); render(); },
  'acc-restore': el => { ACC(el.dataset.id).archived = false; toast('账户已恢复'); render(); },
  'acc-delete': () => confirmPhone('删除账户', '删除后不可恢复(演示仅从内存移除)。', 'acc-confirm-delete', '删除'),
  'acc-confirm-delete': () => { state.accounts = state.accounts.filter(x => x.id !== state.editingAccount); closeSheet(); closeSub(); toast('账户已删除'); render(); },
  'show-archived': () => openSub({ key: 'archived' }),

  // 统计
  'stats-range': el => { state.statsRange = el.dataset.range; render(); },
  'edit-custom-range': () => openSheet({ title: '自定义区间', tall: true, body: `<div class="range-tabs two"><button class="${state.statsCustom.editing === 'start' ? 'on' : ''}" data-action="range-tab" data-v="start">开始 ${state.statsCustom.start.slice(5)}</button><button class="${state.statsCustom.editing === 'end' ? 'on' : ''}" data-action="range-tab" data-v="end">结束 ${state.statsCustom.end.slice(5)}</button></div>
    <div class="cal-grid">${['一', '二', '三', '四', '五', '六', '日'].map(w => `<div class="wd">${w}</div>`).join('')}${calCells(state.calYear, state.calMonth, state.statsCustom[state.statsCustom.editing], true, 'cal-pick-range')}</div>
    <div class="sub-actions two"><button class="action-button secondary" data-close>取消</button><button class="action-button" data-action="apply-custom-range">应用</button></div>` }),
  'range-tab': el => { state.statsCustom.editing = el.dataset.v; actions['edit-custom-range'](); },
  'cal-pick-range': el => { state.statsCustom[state.statsCustom.editing] = el.dataset.date; actions['edit-custom-range'](); },
  'apply-custom-range': () => { if (state.statsCustom.start > state.statsCustom.end) { toast('开始日期晚于结束日期'); return; } closeSheet(); render(); toast('已应用自定义区间'); },
  'stats-cat': el => { const cat = CAT_BY_ID(el.dataset.id); const rows = statsList().filter(t => t.kind === 'expense' && t.catId === el.dataset.id).sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time)); openSheet({ title: cat.name, body: `<p class="form-intro">${rows.length} 笔 · 合计 <b class="amt">${money(rows.reduce((s, t) => s + t.amount, 0))}</b></p>${rows.map(txnRow).join('')}`, tall: true }); },
  'stats-filter': () => openSub({ key: 'statsfilter' }),
  'set-stat-account': el => { state.statFilter.accountId = state.statFilter.accountId === el.dataset.id ? null : el.dataset.id; render(); },
  'set-stat-cat': el => { state.statFilter.catId = state.statFilter.catId === el.dataset.id ? null : el.dataset.id; render(); },
  'apply-stats-filter': () => { closeSub(); toast('已应用筛选'); },
  'clear-stats-filter': () => { state.statFilter = { accountId: null, catId: null }; closeSub(); toast('已清除筛选'); render(); },

  // 我的 · 子页面数据操作
  'add-ledger': () => inputSheet('新建账本', { label: '账本名称', placeholder: '例如:家庭账本', action: 'do-add-ledger' }),
  'do-add-ledger': () => { const n = (state.inputDraft || '').trim(); if (!n) { toast('请输入账本名称'); return; } state.ledgers.push({ id: 'l' + nextId(), name: n, current: false }); closeSheet(); render(); toast('账本已新增'); },
  'switch-ledger': el => { state.ledgers.forEach(l => l.current = l.id === el.dataset.id); render(); toast('已切换账本'); },
  'toggle-cat': el => { const id = el.dataset.id; if (state.disabledCats.includes(id)) state.disabledCats = state.disabledCats.filter(x => x !== id); else state.disabledCats.push(id); render(); },
  'add-tag': () => inputSheet('新建标签', { label: '标签名称', placeholder: '例如:差旅', action: 'do-add-tag' }),
  'do-add-tag': () => { const t = (state.inputDraft || '').trim(); if (!t) { toast('请输入标签名称'); return; } if (!state.tags.includes(t)) state.tags.push(t); closeSheet(); render(); toast('标签已新增'); },
  'delete-tag': el => { state.tags = state.tags.filter(t => t !== el.dataset.tag); render(); toast('标签已删除'); },
  'add-reimburse': () => inputSheet('新建报销项目', { label: '项目名称', placeholder: '例如:差旅报销 · 上海', action: 'do-add-reimburse' }),
  'do-add-reimburse': () => { const n = (state.inputDraft || '').trim(); if (!n) { toast('请输入名称'); return; } state.reimburse.push({ id: 'r' + nextId(), name: n, amount: 0, status: '进行中' }); closeSheet(); render(); toast('已新增报销项目'); },
  'delete-reimburse': el => { state.reimburse = state.reimburse.filter(r => r.id !== el.dataset.id); render(); toast('已删除'); },
  'add-lending': () => inputSheet('新增借贷', { label: '名称', placeholder: '例如:借给张伟', action: 'do-add-lending' }),
  'do-add-lending': () => { const n = (state.inputDraft || '').trim(); if (!n) { toast('请输入名称'); return; } state.lending.push({ id: 'd' + nextId(), name: n, amount: 0, status: '待收' }); closeSheet(); render(); toast('已新增借贷'); },
  'delete-lending': el => { state.lending = state.lending.filter(r => r.id !== el.dataset.id); render(); toast('已删除'); },
  'add-installment': () => inputSheet('新增分期', { label: '计划名称', placeholder: '例如:车贷', action: 'do-add-installment' }),
  'do-add-installment': () => { const n = (state.inputDraft || '').trim(); if (!n) { toast('请输入名称'); return; } state.installments.push({ id: 'i' + nextId(), name: n, periods: 12, amount: 0 }); closeSheet(); render(); toast('已新增分期'); },
  'delete-installment': el => { state.installments = state.installments.filter(r => r.id !== el.dataset.id); render(); toast('已删除'); },
  'add-wealth': () => inputSheet('新增理财', { label: '产品名称', placeholder: '例如:稳健理财 B', action: 'do-add-wealth' }),
  'do-add-wealth': () => { const n = (state.inputDraft || '').trim(); if (!n) { toast('请输入名称'); return; } state.wealth.push({ id: 'w' + nextId(), name: n, invested: 0, realized: 0 }); closeSheet(); render(); toast('已新增理财产品'); },
  'delete-wealth': el => { state.wealth = state.wealth.filter(r => r.id !== el.dataset.id); render(); toast('已删除'); },
  'toggle-model': el => { const m = state.models.find(x => x.id === el.dataset.id); m.enabled = !m.enabled; render(); },
  'add-merchant': () => inputSheet('新增商户规则', { label: '商户关键词(可用 | 分隔)', placeholder: '例如:肯德基|麦当劳', action: 'do-add-merchant' }),
  'do-add-merchant': () => { const n = (state.inputDraft || '').trim(); if (!n) { toast('请输入关键词'); return; } state.merchantRules.push({ id: 'mr' + nextId(), match: n, cat: 'other' }); closeSheet(); render(); toast('已新增规则'); },
  'delete-merchant': el => { state.merchantRules = state.merchantRules.filter(r => r.id !== el.dataset.id); render(); toast('已删除'); },
  'toggle-webdav': () => { state.webdav.enabled = !state.webdav.enabled; render(); },
  'create-backup': () => { const now = new Date(); state.backups.unshift({ id: 'b' + nextId(), at: `今天 ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`, size: '2.4 MB' }); render(); toast('备份已创建(演示)'); },
  'restore-backup': el => { const b = state.backups.find(x => x.id === el.dataset.id); toast(`已恢复 ${b.at} 的备份(演示)`); },
  'do-export': () => { state.exported = { rows: state.txns.length, size: (state.txns.length * 0.06).toFixed(1) + ' KB', preview: ['日期,类型,分类,账户,金额,备注', ...state.txns.slice(0, 5).map(t => `${t.date},${t.kind},${CAT_BY_ID(t.catId)?.name || '转账'},${ACC(t.accountId)?.name || ''},${(t.amount / 100).toFixed(2)},${t.note || ''}`)] }; render(); toast('CSV 已生成(演示)'); },
  // 导入账单(四态:选文件 / 预览 / 导入中 / 报告)
  'import-help': () => openSheet({ title: '关于导入账单', body: `<p class="form-intro">支持一木记账导出的账单文件(.xls,以及 .zip 包裹的同名 .xls)。</p>
    <div class="inline-note">${icon('shield')} 文件只在本机解析;导入在单个事务内完成,失败则整体回滚,不留半份数据。</div>
    <p class="form-intro" style="margin-top:14px">重复行按「日期 + 收支类型 + 金额 + 分类 + 账户 + 备注」识别;再次导入同一文件会全部计为重复,不会写双份。</p>` }),
  'pick-import-file': () => { state.importBill.phase = 'preview'; render(); toast('已选择账单文件(演示)'); },
  'toggle-import-target': el => {
    const v = el.dataset.v;
    if (v === 'current' && !state.txns.length) { toast('当前账本还没有流水'); return; }
    if (v === 'current') { confirmPhone('导入当前账本', '当前账本已有流水;导入后新增与重复都会按实际结果统计。确认继续?', 'confirm-import-current', '继续'); return; }
    state.importBill.target = 'new'; render();
  },
  'confirm-import-current': () => { state.importBill.target = 'current'; closeSheet(); render(); toast('已选择导入当前账本'); },
  'toggle-import-list': el => { const st = state.importBill; if (el.dataset.v === 'cats') st.catsOpen = !st.catsOpen; else st.accsOpen = !st.accsOpen; render(); },
  'do-import': () => {
    const st = state.importBill;
    st.phase = 'progress'; st.processed = 0; render();
    clearInterval(st.timer);
    st.timer = setInterval(() => {
      st.processed = Math.min(YIMU_PREVIEW.rows, st.processed + Math.ceil(YIMU_PREVIEW.rows / 24));
      if (st.processed >= YIMU_PREVIEW.rows) {
        clearInterval(st.timer); st.timer = null;
        st.phase = 'report';
        st.report = { inserted: YIMU_PREVIEW.rows, duplicates: 0, failed: 0, skipped: 0, categories: YIMU_PREVIEW.newCats, accounts: YIMU_PREVIEW.newAccs };
        render(); toast('导入完成(演示)'); return;
      }
      render();
    }, 90);
  },
  'import-again': () => { clearInterval(state.importBill.timer); state.importBill = freshImportState(); render(); },
  'toggle-permission': el => { const k = el.dataset.key; state.permissions[k] = !state.permissions[k]; render(); },
  'set-budget': el => { state.budgetTotal = +el.dataset.v; render(); toast('预算已更新'); },
  'toggle-card': el => { const c = state.cards.find(x => x.id === el.dataset.id); c.on = !c.on; render(); },
  'card-up': el => { const i = state.cards.findIndex(c => c.id === el.dataset.id); if (i > 0) { [state.cards[i - 1], state.cards[i]] = [state.cards[i], state.cards[i - 1]]; render(); } },
  'card-down': el => { const i = state.cards.findIndex(c => c.id === el.dataset.id); if (i < state.cards.length - 1) { [state.cards[i + 1], state.cards[i]] = [state.cards[i], state.cards[i + 1]]; render(); } },
  'reset-cards': () => { state.cards = [{ id: 'budget', name: '本月预算', on: true }, { id: 'today', name: '今日收支', on: true }, { id: 'savings', name: '储蓄目标', on: true }, { id: 'week', name: '七日趋势', on: true }]; render(); toast('卡片已恢复默认'); },
  'saving-deposit': () => { state.savings.saved = Math.min(state.savings.target, state.savings.saved + 100000); render(); toast('已存入 ¥1,000(演示)'); },
  'saving-withdraw': () => { state.savings.saved = Math.max(0, state.savings.saved - 100000); render(); toast('已取出 ¥1,000(演示)'); },
  'ai-sample': el => { state.aiText = el.dataset.v; render(); },
  'ai-parse': () => { const t = ($('#aiText') || {}).value || state.aiText; state.aiText = t; const nums = (t.match(/\d+(\.\d+)?/) || ['0'])[0]; state.aiResult = `${t.includes('打车') ? '交通 / 打车' : t.includes('餐') ? '餐饮 / 午餐' : t.includes('星巴克') ? '餐饮 / 咖啡' : '其他'} · ¥${nums}`; render(); toast('已解析(演示)'); },
  'ai-apply': () => { const nums = (state.aiText.match(/\d+(\.\d+)?/) || ['0'])[0]; state.edit = { id: null, kind: 'expense', amountStr: nums, catId: state.aiText.includes('打车') ? 'trans' : 'food', sub: state.aiText.includes('打车') ? '打车' : '午餐', accountId: 'cmb_debit', fromId: 'cmb_debit', toId: 'alipay', date: state.selectedDate, time: '12:38:00', tag: '', note: state.aiText }; state.sub = null; state.page = 'edit'; render(); toast('已填入,请确认后保存'); },
  'add-ledger-form': () => actions['add-ledger'](),
  'reset': () => confirmPhone('重置演示数据', '本轮新增的账目与改动将清除,恢复初始演示状态。', 'confirm-reset', '重置'),
  'confirm-reset': () => { state = initial(); closeSheet(); go('home'); toast('演示数据已重置'); }
};

function confirmPhone(title, message, action, label = '确认') {
  openSheet({ title, body: `<p class="form-intro">${esc(message)}</p><div class="sub-actions two"><button class="action-button secondary" data-close>取消</button><button class="action-button danger" data-action="${action}">${label}</button></div>` });
}

// ── 设计系统说明(手机外) ────────────────────────────────
function dsBody() {
  const sw = (n, h, c) => `<div style="border:1px solid var(--line);border-radius:12px;overflow:hidden;background:#fff"><div style="height:46px;background:${c}"></div><div style="padding:7px 9px"><div style="font-size:11.5px;font-weight:600">${n}</div><div style="font-size:10px;color:var(--muted);font-family:var(--serif)">${h}</div></div></div>`;
  const map = (a, b, c) => `<tr><td style="padding:7px 10px 7px 0;border-bottom:1px solid var(--line);font-weight:600;white-space:nowrap">${a}</td><td style="padding:7px 10px 7px 0;border-bottom:1px solid var(--line);color:var(--muted)">${b}</td><td style="padding:7px 0;border-bottom:1px solid var(--line);color:var(--muted)">${c}</td></tr>`;
  return `
    <h3 style="font-family:var(--serif);font-size:15px;color:var(--accent);margin:4px 0 10px">配色 Token</h3>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">
      ${sw('墨绿主色', '#1A3C34', '#1a3c34')}${sw('深墨绿', '#0F2A24', '#0f2a24')}${sw('黄铜金', '#B08D57', '#b08d57')}
      ${sw('支出', '#B23A2E', '#b23a2e')}${sw('收入', '#2E7D5B', '#2e7d5b')}${sw('转账', '#3B5B7A', '#3b5b7a')}
      ${sw('纸感底', '#F7F5F0', '#f7f5f0')}${sw('主文字', '#16211D', '#16211d')}${sw('发丝线', '#E6E1D6', '#e6e1d6')}
    </div>
    <h3 style="font-family:var(--serif);font-size:15px;color:var(--accent);margin:22px 0 10px">字体层级</h3>
    <div style="font-family:var(--serif);font-size:30px;font-weight:600">¥6,842.50 <span style="font-family:var(--sans);font-size:10px;color:var(--muted)">Display 44 serif</span></div>
    <div style="font-family:var(--serif);font-size:21px;font-weight:600;margin-top:8px">首页 <span style="font-family:var(--sans);font-size:10px;color:var(--muted)">Title 23 serif</span></div>
    <div style="font-size:14px;margin-top:8px">瑞幸咖啡 · 招商银行储蓄卡 <span style="font-size:10px;color:var(--muted)">Body 14 sans</span></div>
    <h3 style="font-family:var(--serif);font-size:15px;color:var(--accent);margin:22px 0 10px">间距 / 圆角 / 触控</h3>
    <div style="font-size:12px;color:var(--muted);line-height:2">Space1–8 = 4 / 8 / 12 / 16 / 20 / 24 / 32 dp · Radius 8 / 12 / 16 / 20 · 触控 ≥44px</div>
    <h3 style="font-family:var(--serif);font-size:15px;color:var(--accent);margin:22px 0 10px">Compose 迁移映射</h3>
    <table style="width:100%;border-collapse:collapse;font-size:12px">
      <tr><th style="text-align:left;font-size:10px;letter-spacing:.1em;color:var(--muted);padding:0 10px 8px 0;border-bottom:1px solid var(--line)">原型</th><th style="text-align:left;font-size:10px;letter-spacing:.1em;color:var(--muted);padding:0 10px 8px 0;border-bottom:1px solid var(--line)">现状 Compose</th><th style="text-align:left;font-size:10px;letter-spacing:.1em;color:var(--muted);padding:0 0 8px;border-bottom:1px solid var(--line)">迁移动作</th></tr>
      ${map('墨绿主色 #1A3C34', 'primary(品牌绿 #1B6B4A)', '品牌绿升级为墨绿,同步 HeroCard 渐变端点')}
      ${map('纸感底 #F7F5F0', 'PageLight #F4F7F5', '浅色页面底改暖调纸感')}
      ${map('支出 / 收入 / 转账', 'ExpenseRed / IncomeGreen / TransferBlue', '保留语义色并复核对比度')}
      ${map('黄铜金 #B08D57', 'vipLedgerAccentColors() 琥珀系', '新增 accent')}
      ${map('4…32 间距', 'VipLedgerTokens.Space1..Space8', '完全对应')}
      ${map('分类恒有值(原型)', 'TxnEditScreen 首格「不选分类」', '⚠ 有意偏离:真机需单独确认是否同步')}
      ${map('一屏完成编辑', 'TxnEditScreen 可滚动', '⚠ 待确认:真机是否改为一屏')}
      ${map('衬线金额数字', 'system 字体', '引入字体资源 + tabular-nums')}
    </table>
    <div style="margin-top:16px;font-size:11.5px;color:var(--muted);background:var(--gold-bg);border:1px solid #e7dac0;border-radius:12px;padding:11px 13px"><b style="color:var(--gold-ink)">范围外:</b>深色模式与 Compose 迁移不在本轮。</div>`;
}

// ── 事件委托 ─────────────────────────────────────────────
document.addEventListener('click', e => {
  const close = e.target.closest('[data-close]');
  if (close) { closeSheet(); return; }
  const page = e.target.closest('[data-page]');
  if (page) { go(page.dataset.page); return; }
  const el = e.target.closest('[data-action]');
  if (el) { if (el.tagName === 'BUTTON') e.preventDefault?.(); actions[el.dataset.action]?.(el); }
});
document.addEventListener('input', e => {
  const el = e.target;
  if (el.id === 'searchInput') { state.query = el.value; const box = $('#searchResults'); if (box) box.innerHTML = searchResultBody(state.query.trim(), state.query.trim() ? state.txns.filter(t => matchesQuery(t, state.query.trim())).sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time)) : []); return; }
  if (el.dataset && el.dataset.field === 'note') { state.edit.note = el.value; return; }
  if (el.id === 'newAccName') { state.newAcc.name = el.value; return; }
  if (el.id === 'newAccBase') { state.newAcc.base = el.value; return; }
  if (el.id === 'newAccNote') { state.newAcc.note = el.value; return; }
  if (el.id === 'newAccCard') { state.newAcc.cardNo = el.value; return; }
  if (el.id === 'sheetInput') { state.inputDraft = el.value; return; }
  if (el.id === 'aiText') { state.aiText = el.value; }
});
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') { if (state.sheet) closeSheet(); else if (state.sub) closeSub(); else if (state.searchOpen) actions['close-search'](); return; }
  if (state.sheet || state.sub) return;
  if (e.target && e.target.tagName === 'INPUT') return;
  if (state.page === 'edit' && state.edit) {
    if (/^[0-9.]$/.test(e.key)) actions['key']({ dataset: { key: e.key } });
    if (e.key === 'Backspace') { e.preventDefault(); actions['key']({ dataset: { key: '⌫' } }); }
  }
});

$('#openDs').onclick = () => { $('#dsBody').innerHTML = dsBody(); $('#ds').showModal(); };
$('#resetDemo').onclick = actions.reset;
$('#ds').addEventListener('click', e => { if (e.target === $('#ds')) $('#ds').close(); });

// 测试钩子:供 smoke-test 验证「无假按钮」(每个 data-action 都有处理器)。
window.__vlHasAction = name => typeof actions[name] === 'function';

render();