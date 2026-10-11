'use strict';
/* VipLedger UI 重设计原型 · 交互逻辑
   纯内存演示:不联网、不读写设备、不调用后端。刷新即重置。
   参考结构:NasTools/prototypes/nastools-ui(app.js)。 */

const $ = s => document.querySelector(s);
const icon = n => `<svg class="icon" aria-hidden="true"><use href="#i-${n}"/></svg>`;
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = c => (c < 0 ? '-' : '') + '¥' + (Math.abs(c) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const moneyShort = c => '¥' + Math.round(Math.abs(c) / 100).toLocaleString('zh-CN');
const yuan = f => Math.round(f * 100);

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
const ACCOUNTS = [
  { id: 'cmb_debit', name: '招商银行储蓄卡', group: 'asset', mark: '储', tone: 'green', base: 8642000, sub: '储蓄 · 尾号 6021' },
  { id: 'alipay', name: '支付宝余额', group: 'asset', mark: '余', tone: 'blue', base: 386000, sub: '电子钱包' },
  { id: 'wechat', name: '微信零钱', group: 'asset', mark: '零', tone: 'green', base: 124000, sub: '电子钱包' },
  { id: 'cash', name: '现金', group: 'asset', mark: '现', tone: 'gray', base: 60000, sub: '现金' },
  { id: 'invest', name: '投资理财', group: 'asset', mark: '投', tone: 'gold', base: 9000000, sub: '理财台账 · 3 只' },
  { id: 'cmb_credit', name: '招商银行信用卡', group: 'debt', mark: '信', tone: 'red', base: -324000, sub: '信用 · 尾号 3390' },
  { id: 'huabei', name: '花呗', group: 'debt', mark: '花', tone: 'red', base: -150000, sub: '信用 · 免息期内' },
  { id: 'loan', name: '住房贷款', group: 'debt', mark: '贷', tone: 'red', base: -5350000, sub: '分期 · 剩余 168 期' }
];
const ACC = id => ACCOUNTS.find(a => a.id === id);
const TONE_BG = { green: 'background:var(--tint);color:var(--accent)', blue: 'background:#e6edf3;color:var(--transfer)', gold: 'background:var(--gold-bg);color:var(--gold-ink)', gray: 'background:var(--soft);color:var(--text)', red: 'background:#f7e7e3;color:var(--expense)' };
const CHART_COLORS = ['#1a3c34', '#b08d57', '#b23a2e', '#3b5b7a', '#2e7d5b', '#a9a499', '#c9a96e', '#8a6b34'];

const TODAY = { y: 2025, m: 10, d: 11 };
const iso = (y, m, d) => `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

// ── 初始数据 ─────────────────────────────────────────────
function initial() {
  let id = 0;
  const t = (date, time, title, cat, sub, acc, kind, amount) => ({
    id: ++id, date, time, title,
    catId: cat, sub, accountId: acc, kind, amount: yuan(amount)
  });
  return {
    page: 'home',
    selectedDate: iso(2025, 10, 11),
    calYear: 2025,
    calMonth: 10,
    statsRange: 'month',
    editingId: null,
    edit: null,
    txns: [
      t('2025-07-05', '10:00', '房租', 'home', '房租', 'cmb_debit', 'expense', 4500),
      t('2025-07-18', '20:10', '聚餐', 'food', '聚餐', 'wechat', 'expense', 320),
      t('2025-08-01', '10:00', '房租', 'home', '房租', 'cmb_debit', 'expense', 4500),
      t('2025-08-12', '15:40', '手机数码', 'shop', '数码', 'cmb_credit', 'expense', 2999),
      t('2025-08-24', '09:20', '高铁票', 'trans', '打车', 'alipay', 'expense', 553),
      t('2025-09-01', '10:00', '房租', 'home', '房租', 'cmb_debit', 'expense', 4500),
      t('2025-09-09', '19:30', '健身房', 'fun', '运动', 'cmb_debit', 'expense', 299),
      t('2025-09-20', '12:15', '体检', 'med', '体检', 'alipay', 'expense', 680),
      t('2025-09-10', '09:00', '工资', 'salary', '月薪', 'cmb_debit', 'income', 12300),
      t('2025-10-01', '10:00', '房租', 'home', '房租', 'cmb_debit', 'expense', 4500),
      t('2025-10-01', '10:05', '水电费', 'home', '水电', 'cmb_debit', 'expense', 216),
      t('2025-10-05', '09:12', '星巴克', 'food', '咖啡', 'cmb_credit', 'expense', 41.5),
      t('2025-10-06', '19:40', '电影票', 'fun', '电影', 'wechat', 'expense', 120),
      t('2025-10-08', '15:20', '药店', 'med', '药品', 'alipay', 'expense', 88),
      t('2025-10-09', '11:42', '京东', 'shop', '数码', 'cmb_credit', 'expense', 899),
      t('2025-10-09', '18:05', '山姆会员店', 'shop', '食品', 'cmb_debit', 'expense', 341),
      t('2025-10-10', '08:15', '美团外卖', 'food', '外卖', 'cmb_debit', 'expense', 45.5),
      t('2025-10-10', '14:30', '优衣库', 'shop', '服饰', 'cmb_credit', 'expense', 299),
      t('2025-10-10', '18:26', '城市地铁', 'trans', '地铁', 'alipay', 'expense', 6),
      t('2025-10-10', '21:05', '便利蜂', 'daily', '零食', 'wechat', 'expense', 22),
      t('2025-10-10', '09:00', '工资', 'salary', '月薪', 'cmb_debit', 'income', 12300),
      t('2025-10-11', '08:32', '瑞幸咖啡', 'food', '咖啡', 'cmb_debit', 'expense', 32),
      t('2025-10-11', '11:05', '盒马鲜生', 'daily', '食品', 'alipay', 'expense', 156),
      t('2025-10-11', '14:10', '滴滴出行', 'trans', '打车', 'wechat', 'expense', 28),
      t('2025-10-11', '16:20', '星巴克', 'food', '咖啡', 'cmb_credit', 'expense', 52)
    ],
    reviews: [
      { id: 'r1', title: '拼多多 · 待确认消费', meta: '10-09 20:14 · 微信支付 · 自动采集', amount: yuan(134) },
      { id: 'r2', title: '京东 · 待确认消费', meta: '10-08 11:42 · 白条 · 需补充时间', amount: yuan(899) }
    ],
    budgetTotal: yuan(8000),
    savings: { name: '旅行基金', saved: yuan(18400), target: yuan(30000) }
  };
}
let state = initial();

// ── 派生计算 ─────────────────────────────────────────────
const inMonth = (txn, y, m) => txn.date.startsWith(`${y}-${String(m).padStart(2, '0')}`);
const sumBy = (list, kind) => list.filter(t => t.kind === kind).reduce((s, t) => s + t.amount, 0);
const monthTxns = () => state.txns.filter(t => inMonth(t, TODAY.y, TODAY.m));
const dayTxns = date => state.txns.filter(t => t.date === date).sort((a, b) => b.time.localeCompare(a.time));
const fmtDateCN = date => {
  const [, m, d] = date.split('-');
  return `${Number(m)}月${Number(d)}日`;
};
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
  home: ['01', '少一点数字,<br>多一点判断。', '首页顶部只回答一件事:这个月花了多少、还剩多少预算。卡片按你自己的习惯排序,流水按日归拢,金额一律右对齐等宽。', ['点「待确认」草稿:确认或忽略', '点任意一笔流水:直接进入编辑', '右下角「+」记一笔', '点右上角滑杆:卡片个性化']],
  calendar: ['02', '每一天,<br>都查得到。', '月历上用金点标出有账的日子。选中某天,下方只显示那一天;「记一笔」会把选中日期直接带进编辑器。', ['点月历里的任意日期', '左右箭头切换月份', '点「记一笔」带上当前选中日']],
  assets: ['03', '资产与负债,<br>一眼分明。', '净资产放在最上面,资产用墨绿、负债用赭红分栏。分组账户给出小计,储蓄目标用进度条表示累积。', ['点账户查看明细', '点「资产趋势」查看历史', '储蓄目标展示真实转账进度']],
  stats: ['04', '花在哪,<br>一目了然。', '统计默认与首页同口径的「本月」。环形图看结构,排行看大小,柱状图看趋势,三者共用同一份数据。', ['切换本周 / 本月 / 本年', '点分类进入明细', '环形图与排行数字一致']],
  me: ['05', '设置,<br>不打扰。', '账本、计划、AI 配置与数据四组入口。计划里的报销、借贷、分期、理财都以独立入口进入,不挤在首页。', ['点账本管理切换账本', '计划分组查看四个入口', '数据分组查看备份状态']],
  edit: ['06', '记一笔,<br>三步之内。', '金额、分类、账户,三步完成。分类一级网格塞得下常用项,二级在原行下方就地展开,不跳页。', ['点数字键盘输入金额', '点「餐饮」展开二级分类', '点账户 / 日期 / 时间修改', '点「保存」写入演示数据']]
};
const CAPTION = { home: '首页', calendar: '日历', assets: '资产', stats: '统计', me: '我的', edit: '记一笔 / 编辑账目' };

// ── 头部 / 渲染 ─────────────────────────────────────────
function header(title, sub, actions = '', back = false) {
  $('#appHeader').innerHTML =
    `${back ? `<button class="icon-button" data-action="close-edit" aria-label="返回">${icon('arrow-left')}</button>` : ''}
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
  $('#fab').classList.toggle('visible', state.page === 'home' || state.page === 'calendar' || state.page === 'stats');

  $('#chapterNav').innerHTML = PAGES.map(([p, n], i) =>
    `<button data-page="${p}" class="${state.page === p ? 'active' : ''}"><span>0${i + 1}</span>${n}</button>`).join('');

  const note = NOTES[state.page] || NOTES.home;
  $('#noteNumber').textContent = note[0];
  $('#noteTitle').innerHTML = note[1];
  $('#noteBody').textContent = note[2];
  $('#noteTry').innerHTML = note[3].map(x => `<li>${esc(x)}</li>`).join('');
  $('#pageCaption').textContent = CAPTION[state.page] || '首页';

  // 编辑页底部停靠键盘 + 保存
  const dock = $('#editorDock');
  dock.classList.toggle('hidden', state.page !== 'edit');
  if (state.page === 'edit') dock.innerHTML = editorDock();

  const views = { home: homeView, calendar: calendarView, assets: assetsView, stats: statsView, me: meView, edit: editView };
  $('#content').innerHTML = views[state.page]();
}

function go(page) {
  state.page = page;
  render();
  $('#content').scrollTop = 0;
}

// ── 首页 ─────────────────────────────────────────────────
function homeView() {
  header('首页', '日常账本',
    ib('search', '搜索', 'search') + ib('home-settings', '卡片设置', 'tune'));
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

  const recent = [iso(2025, 10, 11), iso(2025, 10, 10), iso(2025, 10, 9)]
    .map(date => dayGroup(date)).join('');

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
    <div class="grid2">
      <button class="mini" data-action="edit-budget">
        <div class="top"><span class="dot"></span><span>本月预算</span></div>
        <div class="num amt"><small>¥</small>${(remain / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
        <div class="sub">剩余 · 已用 ${used}%</div>
        <div class="bar"><i style="width:${used}%"></i></div>
      </button>
      <div class="mini">
        <div class="top"><span class="dot" style="background:var(--expense)"></span><span>今日支出</span></div>
        <div class="num amt exp"><small>¥</small>${(tExp / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
        <div class="sub">${today.filter(t => t.kind === 'expense').length} 笔 · 收入 ${money(sumBy(today, 'income'))}</div>
      </div>
      <div class="mini">
        <div class="top"><span class="dot" style="background:var(--gold)"></span><span>储蓄目标</span></div>
        <div class="num amt"><small>¥</small>${(state.savings.saved / 100).toLocaleString('zh-CN')}</div>
        <div class="sub">${esc(state.savings.name)} · ${Math.round(state.savings.saved / state.savings.target * 100)}%</div>
        <div class="bar"><i style="width:${Math.round(state.savings.saved / state.savings.target * 100)}%"></i></div>
      </div>
      <div class="mini">
        <div class="top"><span class="dot" style="background:var(--transfer)"></span><span>七日趋势</span></div>
        <div class="spark">
          <i style="height:38%"></i><i style="height:62%"></i><i style="height:44%"></i>
          <i style="height:80%"></i><i style="height:52%"></i><i class="gold" style="height:70%"></i><i class="on" style="height:96%"></i>
        </div>
      </div>
    </div>

    ${state.reviews.length ? `<div class="section-head"><h3>待确认</h3><small>${state.reviews.length} 条草稿</small></div>${reviews}` : ''}

    <div class="section-head"><h3>最近流水</h3><button data-page="stats">查看统计 ${icon('chevron-right')}</button></div>
    ${recent}`;
}

function dayGroup(date) {
  const list = dayTxns(date);
  if (!list.length) return '';
  const exp = sumBy(list, 'expense');
  const rows = list.map(txnRow).join('');
  return `<div class="daygroup">
      <div class="dh"><div class="d"><b>${fmtDateCN(date)}</b>${dateLabel(date)}</div><div class="s">支出 ${money(exp)}</div></div>
      ${rows}
    </div>`;
}
function txnRow(t) {
  const cat = CAT_BY_ID(t.catId);
  const acc = ACC(t.accountId);
  const cls = t.kind === 'income' ? 'ico' : (cat && cat.id === 'shop' ? 'ico g' : (cat && cat.id === 'trans' ? 'ico b' : 'ico r'));
  const sign = t.kind === 'income' ? '+' : t.kind === 'transfer' ? '' : '-';
  const clsAmt = t.kind === 'income' ? 'a inc amt' : t.kind === 'transfer' ? 'a trf amt' : 'a exp amt';
  return `<button class="txn" data-action="open-edit" data-id="${t.id}">
      <div class="${cls}">${cat ? cat.name[0] : '转'}</div>
      <div class="m"><div class="t">${esc(t.title)}</div><div class="s">${cat ? cat.name : '转账'}${t.sub ? ' / ' + t.sub : ''} · ${acc ? acc.name : ''}</div></div>
      <div class="${clsAmt}">${sign}<span class="cur">¥</span>${(t.amount / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
    </button>`;
}

// ── 日历 ─────────────────────────────────────────────────
function calendarView() {
  header('日历', '日常账本', ib('search', '搜索', 'search'));
  const { y, m } = { y: state.calYear, m: state.calMonth };
  const first = new Date(y, m - 1, 1);
  const startIdx = (first.getDay() + 6) % 7; // 周一为第 0 列
  const days = new Date(y, m, 0).getDate();
  const prevDays = new Date(y, m - 1, 0).getDate();
  let cells = '';
  for (let i = 0; i < startIdx; i++) cells += `<div class="day dim">${prevDays - startIdx + i + 1}</div>`;
  for (let d = 1; d <= days; d++) {
    const date = iso(y, m, d);
    const has = dayTxns(date).length > 0;
    const sel = date === state.selectedDate;
    const today = date === iso(TODAY.y, TODAY.m, TODAY.d);
    cells += `<button class="day ${sel ? 'sel' : ''} ${today && !sel ? 'today' : ''}" data-action="cal-day" data-date="${date}">${d}${has ? '<i class="dot"></i>' : ''}</button>`;
  }
  const total = startIdx + days;
  const trail = (7 - total % 7) % 7;
  for (let i = 1; i <= trail; i++) cells += `<div class="day dim">${i}</div>`;

  const sel = state.selectedDate;
  const list = dayTxns(sel);
  const exp = sumBy(list, 'expense'), inc = sumBy(list, 'income');

  return `
    <div class="card" style="padding:16px 12px 14px">
      <div class="cal-head">
        <button class="icon-button" data-action="cal-prev" aria-label="上个月">${icon('chevron-left')}</button>
        <div class="mo">${y} 年 ${m} 月</div>
        <button class="icon-button" data-action="cal-next" aria-label="下个月">${icon('chevron-right')}</button>
      </div>
      <div class="cal-grid">
        <div class="wd">一</div><div class="wd">二</div><div class="wd">三</div><div class="wd">四</div><div class="wd">五</div><div class="wd">六</div><div class="wd">日</div>
        ${cells}
      </div>
    </div>
    <div class="cal-sum">
      <div class="b"><div class="l">当日支出</div><div class="v amt exp">${money(exp)}</div></div>
      <div class="b"><div class="l">当日收入</div><div class="v amt inc">${money(inc)}</div></div>
    </div>
    <div class="section-head"><h3>${fmtDateCN(sel)} · ${list.length} 笔</h3><button data-action="new-entry-day">记一笔 ${icon('chevron-right')}</button></div>
    <div class="card tight">${list.length ? list.map(txnRow).join('') : `<div class="empty">${icon('note')}<h3>这天还没有记录</h3><p>点右下角「+」记一笔。</p></div>`}</div>`;
}

// ── 资产 ─────────────────────────────────────────────────
function assetsView() {
  header('资产', '8 个账户', ib('assets-trend', '资产趋势', 'trend') + ib('add-account', '新增账户', 'plus'));
  const assets = ACCOUNTS.filter(a => a.group === 'asset');
  const debts = ACCOUNTS.filter(a => a.group === 'debt');
  const totalA = assets.reduce((s, a) => s + a.base, 0);
  const totalD = debts.reduce((s, a) => s + a.base, 0);
  const row = a => `<button class="acct" data-action="open-account" data-id="${a.id}">
      <div class="ico" style="${TONE_BG[a.tone]}">${a.mark}</div>
      <div class="m"><div class="t">${a.name}</div><div class="s">${a.sub}</div></div>
      <div class="a amt ${a.base < 0 ? 'exp' : ''}">${money(a.base)}</div>
    </button>`;
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
    <div class="card tight">${assets.map(row).join('')}
      <div class="group-total"><span>资产小计</span><b class="amt">${money(totalA)}</b></div>
    </div>
    <div class="section-head"><h3>负债账户</h3><small>${debts.length} 个</small></div>
    <div class="card tight">${debts.map(row).join('')}
      <div class="group-total"><span>负债小计</span><b class="amt exp">${money(totalD)}</b></div>
    </div>
    <div class="section-head"><h3>储蓄目标</h3><small>1 个</small></div>
    <div class="card goal">
      <div class="gt"><div class="n">${esc(state.savings.name)}</div><div class="p">${savedPct}%</div></div>
      <div class="bar"><i style="width:${savedPct}%"></i></div>
      <div class="gl"><span>已存 ${money(state.savings.saved)}</span><span>目标 ${money(state.savings.target)}</span></div>
    </div>
    <div style="margin-top:12px">
      <button class="entry-row" data-action="assets-trend">
        <div class="l"><div class="ico">${icon('trend')}</div>资产趋势</div>
        <div class="r">近 12 个月 ${icon('chevron-right')}</div>
      </button>
    </div>`;
}

// ── 统计 ─────────────────────────────────────────────────
function statsView() {
  header('统计', '日常账本', ib('stats-filter', '筛选', 'tune'));
  const range = state.statsRange;
  const list = range === 'year' ? state.txns.filter(t => t.date.startsWith(String(TODAY.y))) : monthTxns();
  const exp = sumBy(list, 'expense'), inc = sumBy(list, 'income');
  const byCat = {};
  list.filter(t => t.kind === 'expense').forEach(t => { byCat[t.catId] = (byCat[t.catId] || 0) + t.amount; });
  const rows = Object.entries(byCat).map(([id, v]) => ({ cat: CAT_BY_ID(id), v })).sort((a, b) => b.v - a.v);
  const total = rows.reduce((s, r) => s + r.v, 0) || 1;

  // 环形图
  const C = 339.29, R = 54;
  let acc = 0;
  const arcs = rows.map((r, i) => {
    const len = r.v / total * C;
    const el = `<circle cx="75" cy="75" r="${R}" stroke="${CHART_COLORS[i % CHART_COLORS.length]}" stroke-dasharray="${len.toFixed(2)} ${(C - len).toFixed(2)}" stroke-dashoffset="${(-acc).toFixed(2)}"/>`;
    acc += len;
    return el;
  }).join('');
  const legend = rows.map((r, i) => `<div class="lg"><i style="background:${CHART_COLORS[i % CHART_COLORS.length]}"></i>${r.cat.name}<b>${moneyShort(r.v)}</b></div>`).join('');
  const rank = rows.map((r, i) => `
    <div class="r">
      <div class="rt"><span class="n">${r.cat.name}</span><span class="pc">${(r.v / total * 100).toFixed(1)}%</span><span class="amtv">${money(r.v)}</span></div>
      <div class="rb"><i style="width:${(r.v / rows[0].v * 100).toFixed(1)}%;background:${CHART_COLORS[i % CHART_COLORS.length]}"></i></div>
    </div>`).join('');

  const bars = [['5月', 70], ['6月', 82], ['7月', 74], ['8月', 97], ['9月', 81], ['10月', Math.max(30, Math.min(100, Math.round(exp / state.budgetTotal * 100)))]];

  return `
    <div class="seg">
      ${[['week', '本周'], ['month', '本月'], ['year', '本年'], ['custom', '自定义']].map(([k, n]) => `<button class="${range === k ? 'on' : ''}" data-action="stats-range" data-range="${k}">${n}</button>`).join('')}
    </div>
    <div class="overview">
      <div class="o"><div class="l">支出</div><div class="v amt exp"><small>¥</small>${(exp / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
      <div class="o"><div class="l">收入</div><div class="v amt inc"><small>¥</small>${(inc / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
      <div class="o"><div class="l">结余</div><div class="v amt"><small>¥</small>${((inc - exp) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div>
    </div>
    <div class="section-head"><h3>分类占比</h3><small>按支出</small></div>
    <div class="card" style="padding:0">
      <div class="donut-wrap">
        <div class="donut">
          <svg width="142" height="142" viewBox="0 0 150 150">
            <g transform="rotate(-90 75 75)" fill="none" stroke-width="20">${arcs}</g>
          </svg>
          <div class="ctr"><div class="l">${range === 'year' ? '本年' : range === 'week' ? '本周' : '本月'}支出</div><div class="v amt">${moneyShort(exp)}</div></div>
        </div>
        <div class="legend">${legend}</div>
      </div>
    </div>
    <div class="section-head"><h3>分类排行</h3><small>共 ${rows.length} 类</small></div>
    <div class="card rank">${rank}</div>
    <div class="section-head"><h3>月度趋势</h3><small>近 6 个月</small></div>
    <div class="card" style="padding:0 0 14px">
      <div class="barchart">
        ${bars.map(([l, h]) => `<div class="bar ${l === '10月' ? 'cur' : ''}"><div class="col" style="height:${h}%"></div><div class="lb">${l}</div></div>`).join('')}
      </div>
    </div>`;
}

// ── 我的 ─────────────────────────────────────────────────
function meView() {
  header('我的', '设置', ib('notifications', '通知', 'bell'));
  const item = (ico, tone, title, sub, right, action) => `
    <button class="setitem" data-action="${action}">
      <div class="ico ${tone}">${icon(ico)}</div>
      <div class="t">${title}${sub ? `<div class="s">${sub}</div>` : ''}</div>
      <div class="r">${right || ''}${icon('chevron-right')}</div>
    </button>`;
  return `
    <div class="profile">
      <div class="av">翰</div>
      <div class="m"><div class="n">han1997</div>
        <div class="s">${icon('book')}当前账本 · 日常账本 · 已记 ${state.txns.length} 笔</div></div>
    </div>
    <div class="setgroup">
      <div class="lab">账本</div>
      <div class="setlist">
        ${item('book', '', '账本管理', '多账本隔离 · 当前「日常账本」', '', 'manage-ledger')}
        ${item('tag', '', '分类管理', '一级 / 二级分类', '', 'manage-cat')}
        ${item('tag', '', '标签管理', '12 个标签', '', 'manage-tag')}
      </div>
    </div>
    <div class="setgroup">
      <div class="lab">计划</div>
      <div class="setlist">
        ${item('plan', 'g', '报销', '2 个项目进行中', '<span class="badge">2</span>', 'plan-reimburse')}
        ${item('plan', 'g', '借贷', '应收账款 ¥4,580', '', 'plan-lending')}
        ${item('plan', 'g', '分期', '住房贷款 · 168 期', '<span class="badge r">待还</span>', 'plan-loan')}
        ${item('trend', 'g', '理财', '3 只 · 累计投入 ¥90,000', '', 'plan-wealth')}
      </div>
    </div>
    <div class="setgroup">
      <div class="lab">AI 配置</div>
      <div class="setlist">
        ${item('ai', 'b', '模型管理', '云端阶梯 · 端侧分类器', '<span class="badge g">已启用</span>', 'ai-model')}
        ${item('tag', 'b', '商户规则', '48 条自动归类规则', '', 'ai-merchant')}
      </div>
    </div>
    <div class="setgroup">
      <div class="lab">数据</div>
      <div class="setlist">
        ${item('database', '', '备份与恢复', 'WebDAV · 格式 6', '<span class="badge g">已备份</span>', 'data-backup')}
        ${item('note', '', '导出 CSV', '', '', 'data-export')}
        ${item('shield', '', '隐私与权限', '通知 / 截图采集', '', 'data-privacy')}
      </div>
    </div>`;
}

// ── 记一笔 / 编辑 ────────────────────────────────────────
function editView() {
  const e = state.edit;
  header(e.id ? '编辑账目' : '记一笔', '日常账本', ib('close-edit', '关闭', 'x'), true);
  const cats = e.kind === 'income' ? INCOME_CATS : CATS;
  const cat = cats.find(c => c.id === e.catId);
  const digits = e.amountStr || '';
  const display = digits === '' ? '0.00' : digits;

  const catCells = cats.map(c => {
    const on = c.id === e.catId;
    const cell = `<button class="cat ${on ? 'on' : ''}" data-action="pick-cat" data-id="${c.id}">
        <div class="ci">${c.name[0]}</div><div class="cl">${c.name}</div>
      </button>`;
    const subs = (on && e.showSubs) ? `<div class="subcats">${c.subs.map(([n, m]) =>
      `<button class="sc ${e.sub === n ? 'on' : ''}" data-action="pick-sub" data-sub="${n}"><div class="ci">${m}</div>${n}</button>`).join('')}</div>` : '';
    return cell + subs;
  }).join('');

  const accField = e.kind === 'transfer'
    ? `<button class="field" data-action="pick-account"><div class="fi">${icon('swap')}</div><div class="fl">从 / 到</div><div class="fv">${ACC(e.accountId)?.name || '选择'} ${icon('chevron-right')}</div></button>`
    : `<button class="field" data-action="pick-account"><div class="fi">${icon('card')}</div><div class="fl">账户</div><div class="fv">${ACC(e.accountId)?.name || '选择'} ${icon('chevron-right')}</div></button>`;

  return `
    <div class="typebar">
      ${[['expense', '支出'], ['income', '收入'], ['transfer', '转账']].map(([k, n]) => `<button class="${e.kind === k ? 'on' : ''}" data-action="set-type" data-kind="${k}">${n}</button>`).join('')}
    </div>
    <div class="amount-box">
      <div class="t">金额</div>
      <div class="v amt ${digits === '' ? 'zero' : ''}"><span class="cur">¥</span>${display}<span class="caret"></span></div>
    </div>
    ${e.kind !== 'transfer' ? `
    <div class="section-head" style="margin-top:14px"><h3>分类</h3><small>${cat ? cat.name + (e.sub ? ' / ' + e.sub : '') : '未选择'}</small></div>
    <div class="catgrid">${catCells}</div>` : ''}
    <div class="section-head" style="margin-top:18px"><h3>详情</h3></div>
    <div class="card" style="padding:2px 14px 6px">
      ${accField}
      <button class="field" data-action="pick-date"><div class="fi">${icon('calendar')}</div><div class="fl">日期</div><div class="fv">${dateLabel(e.date)} · ${fmtDateCN(e.date)} ${icon('chevron-right')}</div></button>
      <button class="field" data-action="pick-time"><div class="fi">${icon('clock')}</div><div class="fl">时间</div><div class="fv amt">${e.time} ${icon('chevron-right')}</div></button>
      <button class="field" data-action="pick-tag"><div class="fi">${icon('tag')}</div><div class="fl">标签</div><div class="fv">${e.tag ? `<span class="tagchip">${esc(e.tag)}</span>` : '<span class="muted">添加标签</span>'} ${icon('chevron-right')}</div></button>
    </div>
    <button class="note-input ${e.note ? 'filled' : ''}" data-action="pick-note">${e.note ? esc(e.note) : '备注…'}</button>
    <div style="height:4px"></div>`;
}

function editorDock() {
  const digits = state.edit.amountStr || '';
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', '⌫']
    .map(k => `<button class="key ${k === '⌫' ? 'fn' : ''}" data-action="key" data-key="${k}">${k === '⌫' ? icon('backspace') : k}</button>`).join('');
  return `<div class="keypad">${keys}</div>
    <button class="save-btn" data-action="save-entry" ${digits === '' ? 'disabled' : ''}>保 存</button>`;
}

function openEdit(idOrNull, presetDate) {
  const existing = idOrNull ? state.txns.find(t => t.id === idOrNull) : null;
  state.edit = existing
    ? { id: existing.id, title: existing.title, kind: existing.kind, amountStr: (existing.amount / 100).toString(), catId: existing.catId, sub: existing.sub, accountId: existing.accountId, date: existing.date, time: existing.time, tag: existing.tag || '', note: existing.note || '', showSubs: false }
    : { id: null, title: '记一笔', kind: 'expense', amountStr: '', catId: null, sub: '', accountId: 'cmb_debit', date: presetDate || state.selectedDate || iso(TODAY.y, TODAY.m, TODAY.d), time: '12:38', tag: '', note: '', showSubs: false };
  state.editingId = idOrNull || null;
  go('edit');
}

// ── toast / sheet ────────────────────────────────────────
let toastTimer, returnFocus;
function toast(s) {
  clearTimeout(toastTimer);
  $('#toast').textContent = s;
  $('#toast').classList.add('visible');
  toastTimer = setTimeout(() => $('#toast').classList.remove('visible'), 2600);
}
function sheet(title, body) {
  returnFocus = document.activeElement;
  $('#sheetTitle').textContent = title;
  $('#sheetBody').innerHTML = body;
  if (!$('#sheet').open) $('#sheet').showModal();
}
function closeSheet() { $('#sheet').close(); if (returnFocus && returnFocus.isConnected) returnFocus.focus(); }
function confirmSheet(title, message, action, id, label = '确认') {
  sheet(title, `<p class="form-intro">${esc(message)}</p><div class="form-actions">
    <button class="action-button secondary" data-close>取消</button>
    <button class="action-button danger" data-action="${action}" data-id="${id}">${label}</button></div>`);
}

// ── 动作分发 ─────────────────────────────────────────────
const actions = {
  'new-entry': () => openEdit(null),
  'new-entry-day': () => openEdit(null, state.selectedDate),
  'open-edit': el => openEdit(+el.dataset.id),
  'back': () => go('home'),
  'close-edit': () => {
    const e = state.edit;
    if (e && (e.amountStr || e.note || e.tag) && !e.saved) confirmSheet('未保存的内容', '当前填写的内容将会丢失。', 'discard-edit', 0, '放弃');
    else go('home');
  },
  'discard-edit': () => { closeSheet(); go('home'); },
  'set-type': el => { state.edit.kind = el.dataset.kind; state.edit.catId = null; state.edit.sub = ''; state.edit.showSubs = false; render(); },
  'key': el => {
    const k = el.dataset.key, e = state.edit;
    let s = e.amountStr;
    if (k === '⌫') s = s.slice(0, -1);
    else if (k === '.') { if (!s.includes('.')) s = (s || '0') + '.'; }
    else {
      const dot = s.indexOf('.');
      if (dot >= 0 && s.length - dot > 2) return;
      if (s.replace('.', '').length >= 9) return;
      s = (s === '0' ? '' : s) + k;
    }
    e.amountStr = s;
    render();
  },
  'pick-cat': el => {
    const e = state.edit;
    if (e.catId === el.dataset.id) { e.showSubs = !e.showSubs; }
    else { e.catId = el.dataset.id; e.sub = ''; e.showSubs = true; }
    render();
  },
  'pick-sub': el => { state.edit.sub = el.dataset.sub; render(); },
  'pick-account': () => {
    sheet('选择账户', ACCOUNTS.map(a => `<button class="menu-item ${state.edit.accountId === a.id ? 'sel' : ''}" data-action="set-account" data-id="${a.id}">
      <div class="ico" style="width:28px;height:28px;border-radius:9px;display:grid;place-items:center;${TONE_BG[a.tone]}">${a.mark}</div>
      <span class="grow">${a.name}</span><span class="muted amt">${money(a.base)}</span></button>`).join(''));
  },
  'set-account': el => { state.edit.accountId = el.dataset.id; closeSheet(); render(); },
  'pick-date': () => { openDateSheet(); },
  'cal-pick': el => {
    const date = el.dataset.date;
    if (el.dataset.nav) { state.calYear = +el.dataset.y; state.calMonth = +el.dataset.m; openDateSheet(); return; }
    state.edit.date = date; state.selectedDate = date; closeSheet(); render();
  },
  'pick-time': () => {
    const [h, mi] = state.edit.time.split(':');
    sheet('选择时间', `<div class="stepper">
      <button class="icon-button" data-action="time-step" data-part="h" data-dir="-1">${icon('minus')}</button>
      <div class="val amt">${h}:${mi}</div>
      <button class="icon-button" data-action="time-step" data-part="h" data-dir="1">${icon('plus')}</button>
    </div>
    <div class="stepper">
      <button class="icon-button" data-action="time-step" data-part="m" data-dir="-1">${icon('minus')}</button>
      <span class="muted">分钟</span>
      <button class="icon-button" data-action="time-step" data-part="m" data-dir="1">${icon('plus')}</button>
    </div>
    <div class="form-actions"><button class="action-button full" data-action="time-now">此刻</button>
      <button class="action-button secondary" data-close>完成</button></div>`);
  },
  'time-step': el => {
    let [h, mi] = state.edit.time.split(':').map(Number);
    if (el.dataset.part === 'h') h = (h + +el.dataset.dir + 24) % 24;
    else mi = (mi + +el.dataset.dir * 5 + 60) % 60;
    state.edit.time = `${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`;
    actions['pick-time']();
  },
  'time-now': () => { state.edit.time = '12:38'; closeSheet(); render(); },
  'pick-tag': () => {
    const tags = ['工作餐', '出差', '家庭', '朋友聚会', '通勤', '订阅'];
    sheet('选择标签', tags.map(t => `<button class="menu-item ${state.edit.tag === t ? 'sel' : ''}" data-action="set-tag" data-tag="${t}">${icon('tag')}${t}</button>`).join('') +
      `<button class="menu-item" data-action="set-tag" data-tag="">${icon('x')}不设置标签</button>`);
  },
  'set-tag': el => { state.edit.tag = el.dataset.tag; closeSheet(); render(); },
  'pick-note': () => {
    const v = window.prompt('备注', state.edit.note || '');
    if (v !== null) { state.edit.note = v; render(); }
  },
  'save-entry': () => {
    const e = state.edit;
    const amount = Math.round(parseFloat(e.amountStr || '0') * 100);
    if (!amount) { toast('请输入金额'); return; }
    if (e.kind !== 'transfer' && !e.catId) { toast('请选择分类'); return; }
    const cat = CAT_BY_ID(e.catId);
    const title = e.kind === 'transfer' ? '账户转账' : (cat ? cat.name + (e.sub ? '·' + e.sub : '') : '未分类');
    const rec = { id: e.id || Date.now(), date: e.date, time: e.time, title, catId: e.catId, sub: e.sub, accountId: e.accountId, kind: e.kind, amount, tag: e.tag, note: e.note };
    if (e.id) { const i = state.txns.findIndex(t => t.id === e.id); state.txns[i] = rec; }
    else state.txns.push(rec);
    state.selectedDate = e.date;
    e.saved = true;
    toast('已保存(演示,刷新即重置)');
    go('home');
  },
  'confirm-review': el => {
    const r = state.reviews.find(x => x.id === el.dataset.id);
    if (r) {
      state.txns.push({ id: Date.now(), date: iso(TODAY.y, TODAY.m, TODAY.d), time: '12:38', title: r.title.split(' · ')[0] + '消费', catId: 'other', sub: '', accountId: 'wechat', kind: 'expense', amount: r.amount });
      state.reviews = state.reviews.filter(x => x.id !== r.id);
    }
    render(); toast('草稿已确认入账');
  },
  'dismiss-review': el => { state.reviews = state.reviews.filter(x => x.id !== el.dataset.id); render(); toast('草稿已忽略'); },
  'cal-day': el => { state.selectedDate = el.dataset.date; render(); },
  'cal-prev': () => { state.calMonth--; if (state.calMonth < 1) { state.calMonth = 12; state.calYear--; } render(); },
  'cal-next': () => { state.calMonth++; if (state.calMonth > 12) { state.calMonth = 1; state.calYear++; } render(); },
  'stats-range': el => { state.statsRange = el.dataset.range; render(); },
  'edit-budget': () => sheet('本月预算', `<p class="form-intro">当前总额上限 ${money(state.budgetTotal)}。<br>演示原型不修改此项。</p><button class="action-button full" data-close>知道了</button>`),
  'home-settings': () => sheet('首页卡片', `<p class="form-intro">可拖动排序、可隐藏。默认:预算 / 今日 / 储蓄 / 七日趋势。</p>
    ${['预算', '今日', '储蓄', '七日趋势'].map(n => `<div class="menu-item">${icon('tune')}${n}<span class="grow"></span><span class="badge g">显示</span></div>`).join('')}
    <button class="action-button full" style="margin-top:12px" data-close>完成</button>`),
  'search': () => sheet('搜索', `<p class="form-intro">原型未实现搜索。正式应用支持按商户 / 备注 / 金额筛选。</p><button class="action-button full" data-close>关闭</button>`),
  'assets-trend': () => sheet('资产趋势', `<p class="form-intro">近 12 个月净资产走势。原型以静态示意呈现,不绘制真实曲线。</p><button class="action-button full" data-close>关闭</button>`),
  'add-account': () => sheet('新增账户', `<p class="form-intro">支持储蓄、信用、预付、应收应付、借贷与投资账户。原型不实现新增。</p><button class="action-button full" data-close>关闭</button>`),
  'open-account': el => { const a = ACC(el.dataset.id); sheet(a.name, `<p class="form-intro">当前余额 ${money(a.base)}。<br>正式应用可查看该账户全部流水、编辑、归档与余额校准。</p><button class="action-button full" data-close>关闭</button>`); },
  'stats-filter': () => sheet('统计筛选', `<p class="form-intro">可按账本、账户、分类与标签筛选。原型仅演示时间段切换。</p><button class="action-button full" data-close>关闭</button>`),
  'notifications': () => sheet('通知', `<p class="form-intro">预算提醒与待确认草稿通知。原型不请求系统权限。</p><button class="action-button full" data-close>关闭</button>`),
  'manage-ledger': () => sheet('账本管理', `<p class="form-intro">多账本相互隔离。当前「日常账本」。</p>
    ${['日常账本', '家庭账本', '旅行记账'].map((n, i) => `<button class="menu-item ${i === 0 ? 'sel' : ''}" data-close>${icon('book')}${n}${i === 0 ? '<span class="grow"></span><span class="badge g">当前</span>' : ''}</button>`).join('')}`),
  'plan-reimburse': () => sheet('报销', `<p class="form-intro">2 个项目进行中,待收 ¥1,260。</p><button class="action-button full" data-close>关闭</button>`),
  'plan-lending': () => sheet('借贷', `<p class="form-intro">应收账款 ¥4,580 · 2 笔。</p><button class="action-button full" data-close>关闭</button>`),
  'plan-loan': () => sheet('分期', `<p class="form-intro">住房贷款 · 剩余 168 期。</p><button class="action-button full" data-close>关闭</button>`),
  'plan-wealth': () => sheet('理财', `<p class="form-intro">3 只 · 累计投入 ¥90,000。</p><button class="action-button full" data-close>关闭</button>`),
  'ai-model': () => sheet('模型管理', `<p class="form-intro">云端阶梯解析 + 端侧分类器。原型不调用任何模型。</p><button class="action-button full" data-close>关闭</button>`),
  'ai-merchant': () => sheet('商户规则', `<p class="form-intro">48 条自动归类规则。</p><button class="action-button full" data-close>关闭</button>`),
  'data-backup': () => sheet('备份与恢复', `<p class="form-intro">WebDAV 备份 · 格式 6,兼容格式 1–5。</p><button class="action-button full" data-close>关闭</button>`),
  'data-export': () => sheet('导出 CSV', `<p class="form-intro">按账本导出 CSV 到本地目录。</p><button class="action-button full" data-close>关闭</button>`),
  'data-privacy': () => sheet('隐私与权限', `<p class="form-intro">通知与截图采集权限说明。原型不读写设备。</p><button class="action-button full" data-close>关闭</button>`),
  'reset': () => confirmSheet('重置演示数据', '本轮新增的账目与草稿将清除,恢复初始演示状态。', 'confirm-reset', 0, '重置'),
  'confirm-reset': () => { state = initial(); closeSheet(); go('home'); toast('演示数据已重置'); }
};

function openDateSheet() {
  const y = state.calYear, m = state.calMonth;
  const first = new Date(y, m - 1, 1);
  const startIdx = (first.getDay() + 6) % 7;
  const days = new Date(y, m, 0).getDate();
  const prevDays = new Date(y, m - 1, 0).getDate();
  let cells = '';
  for (let i = 0; i < startIdx; i++) cells += `<div class="day dim">${prevDays - startIdx + i + 1}</div>`;
  for (let d = 1; d <= days; d++) {
    const date = iso(y, m, d);
    const sel = date === state.edit.date;
    cells += `<button class="day ${sel ? 'sel' : ''}" data-action="cal-pick" data-date="${date}">${d}</button>`;
  }
  const total = startIdx + days, trail = (7 - total % 7) % 7;
  for (let i = 1; i <= trail; i++) cells += `<div class="day dim">${i}</div>`;
  const pm = m === 1 ? { y: y - 1, m: 12 } : { y, m: m - 1 };
  const nm = m === 12 ? { y: y + 1, m: 1 } : { y, m: m + 1 };
  sheet('选择日期', `<div class="cal-head">
      <button class="icon-button" data-action="cal-pick" data-nav="1" data-y="${pm.y}" data-m="${pm.m}">${icon('chevron-left')}</button>
      <div class="mo">${y} 年 ${m} 月</div>
      <button class="icon-button" data-action="cal-pick" data-nav="1" data-y="${nm.y}" data-m="${nm.m}">${icon('chevron-right')}</button>
    </div>
    <div class="cal-grid"><div class="wd">一</div><div class="wd">二</div><div class="wd">三</div><div class="wd">四</div><div class="wd">五</div><div class="wd">六</div><div class="wd">日</div>${cells}</div>`);
}

// ── 设计系统说明(与手机分离) ────────────────────────────
function dsBody() {
  const sw = (n, h, c) => `<div style="border:1px solid var(--line);border-radius:12px;overflow:hidden;background:#fff">
      <div style="height:46px;background:${c}"></div>
      <div style="padding:7px 9px"><div style="font-size:11.5px;font-weight:600">${n}</div><div style="font-size:10px;color:var(--muted);font-family:var(--serif)">${h}</div></div></div>`;
  const map = (a, b, c) => `<tr><td style="padding:7px 10px 7px 0;border-bottom:1px solid var(--line);font-weight:600;white-space:nowrap">${a}</td><td style="padding:7px 10px 7px 0;border-bottom:1px solid var(--line);color:var(--muted)">${b}</td><td style="padding:7px 0;border-bottom:1px solid var(--line);color:var(--muted)">${c}</td></tr>`;
  return `
    <h3 style="font-family:var(--serif);font-size:15px;color:var(--accent);margin:4px 0 10px">配色 Token</h3>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">
      ${sw('墨绿主色', '#1A3C34', '#1a3c34')}
      ${sw('深墨绿', '#0F2A24', '#0f2a24')}
      ${sw('黄铜金', '#B08D57', '#b08d57')}
      ${sw('支出', '#B23A2E', '#b23a2e')}
      ${sw('收入', '#2E7D5B', '#2e7d5b')}
      ${sw('转账', '#3B5B7A', '#3b5b7a')}
      ${sw('纸感底', '#F7F5F0', '#f7f5f0')}
      ${sw('主文字', '#16211D', '#16211d')}
      ${sw('发丝线', '#E6E1D6', '#e6e1d6')}
    </div>
    <h3 style="font-family:var(--serif);font-size:15px;color:var(--accent);margin:22px 0 10px">字体层级</h3>
    <div style="font-family:var(--serif);font-size:30px;font-weight:600">¥6,842.50 <span style="font-family:var(--sans);font-size:10px;color:var(--muted)">Display 44 serif</span></div>
    <div style="font-family:var(--serif);font-size:21px;font-weight:600;margin-top:8px">首页 <span style="font-family:var(--sans);font-size:10px;color:var(--muted)">Title 23 serif</span></div>
    <div style="font-size:14px;margin-top:8px">瑞幸咖啡 · 招商银行储蓄卡 <span style="font-size:10px;color:var(--muted)">Body 14 sans</span></div>
    <div style="font-size:11px;color:var(--muted);margin-top:8px">10-09 20:14 · 微信支付 · 自动采集 <span style="font-size:10px">Caption 11 sans</span></div>
    <h3 style="font-family:var(--serif);font-size:15px;color:var(--accent);margin:22px 0 10px">间距 / 圆角 / 触控</h3>
    <div style="font-size:12px;color:var(--muted);line-height:2">Space1–8 = 4 / 8 / 12 / 16 / 20 / 24 / 32 dp · Radius 8 / 12 / 16 / 20 · 触控 ≥44px · 卡片 1px 发丝边框 + 极浅阴影</div>
    <h3 style="font-family:var(--serif);font-size:15px;color:var(--accent);margin:22px 0 10px">Compose 迁移映射</h3>
    <table style="width:100%;border-collapse:collapse;font-size:12px">
      <tr><th style="text-align:left;font-size:10px;letter-spacing:.1em;color:var(--muted);padding:0 10px 8px 0;border-bottom:1px solid var(--line)">原型</th>
          <th style="text-align:left;font-size:10px;letter-spacing:.1em;color:var(--muted);padding:0 10px 8px 0;border-bottom:1px solid var(--line)">现状 Compose</th>
          <th style="text-align:left;font-size:10px;letter-spacing:.1em;color:var(--muted);padding:0 0 8px;border-bottom:1px solid var(--line)">迁移动作</th></tr>
      ${map('墨绿主色 #1A3C34', 'VipLedgerTheme primary(品牌绿 #1B6B4A)', '品牌绿升级为墨绿,同步 HeroCard 渐变端点')}
      ${map('纸感底 #F7F5F0', 'PageLight #F4F7F5', '浅色页面底改暖调纸感')}
      ${map('支出 / 收入 / 转账', 'ExpenseRed / IncomeGreen / TransferBlue', '保留语义色,微调至对应值并复核对比度')}
      ${map('黄铜金 #B08D57', 'vipLedgerAccentColors() 琥珀系', '新增 accent:Hero 点缀、进度条、储蓄目标')}
      ${map('4 / 8 / 12 / 16 / 20 / 24 / 32', 'VipLedgerTokens.Space1..Space8', '完全对应,无需改动')}
      ${map('圆角 8 / 12 / 16 / 20', 'RadiusSmall..RadiusLarge', '卡片由 12 提至 16,同步 GroupCard')}
      ${map('衬线金额数字', 'system 字体', '引入字体资源(Noto Serif SC)+ tabular-nums')}
      ${map('分组卡 / 行 / 徽章', 'GroupCard / ActionRow / SectionHeader / StatusBadge', '复用现有组件,仅换色与圆角')}
    </table>
    <div style="margin-top:16px;font-size:11.5px;color:var(--muted);background:var(--gold-bg);border:1px solid #e7dac0;border-radius:12px;padding:11px 13px">
      <b style="color:var(--gold-ink)">范围外:</b>深色模式、动效细节与二级管理页(报销 / 借贷 / 分期 / 理财明细)不在本原型,仅以「我的」页入口体现。
    </div>`;
}

// ── 事件委托 ─────────────────────────────────────────────
document.addEventListener('click', e => {
  const close = e.target.closest('[data-close]');
  if (close) { closeSheet(); return; }
  const page = e.target.closest('[data-page]');
  if (page) { go(page.dataset.page); return; }
  const el = e.target.closest('[data-action]');
  if (el) {
    if (el.tagName === 'BUTTON' && el.closest('form') && el.type !== 'submit') e.preventDefault();
    actions[el.dataset.action]?.(el);
  }
});
$('#sheet').addEventListener('click', e => { if (e.target === $('#sheet')) closeSheet(); });
$('#ds').addEventListener('click', e => { if (e.target === $('#ds')) $('#ds').close(); });
$('#openDs').onclick = () => { $('#dsBody').innerHTML = dsBody(); $('#ds').showModal(); };
$('#resetDemo').onclick = actions.reset;
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') { if ($('#sheet').open) closeSheet(); return; }
  if ($('#sheet').open || $('#ds').open) return;
  if (state.page === 'edit' && state.edit) {
    if (/^[0-9.]$/.test(e.key)) actions['key']({ dataset: { key: e.key } });
    if (e.key === 'Backspace') { e.preventDefault(); actions['key']({ dataset: { key: '⌫' } }); }
  }
});

render();