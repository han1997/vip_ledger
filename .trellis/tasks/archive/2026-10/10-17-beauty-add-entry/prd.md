# 美化原型「记一笔」页面

## Goal

提升 `docs/ui-prototype` 中「记一笔 / 编辑账目」页(`edit` 页)的视觉品质,并借此把**全站共用的
「首字汉字」分类标识**统一到一套质感口径。本页是原型里交互最复杂、用户每天用最多次的页面,
当前在**间距节奏、分类标识质感、键盘质感、空态、布局死白**五处存在明显「未打磨感」,
与首页/资产/统计的「墨绿金 · 静奢」水准不齐。

## Requirements

### R1 修复转账态死白

`.cat-grid{flex:1 1 auto}` 配 `::before/::after{margin:auto}` 的「安全居中」在只有 2 行内容时
上下各撑出大片空白,页面被切成三段。改为:内容不足时**顶部对齐**,溢出的自由高度不再被
cat-grid 吸收(或吸附给下方,使备注/胶囊紧贴其下方),溢出自滚动行为不变。

**约束(不可破)**:`ui.md` v2.0 定死「唯一可内部滚动的是中间分类区(`flex:1 + overflow-y:auto`)」,
故 `.cat-grid` 的 `flex:1 1 auto; overflow-y:auto` **必须保留**,只能改**内部对齐**(去掉
`::before/::after{margin:auto}` 的自动居中),不得改 flex 骨架。冒烟测试对全部状态断言
`页面与分类区 scrollHeight == clientHeight`。

### R2 分类标识三处统一质感(核心)

实测现状 —— 记一笔是三者中**唯一使用纯灰**的:

| 位置 | 选择器 | 形状 | 现状底/字色 |
|---|---|---|---|
| 记一笔 | `.cat-tile .ci` | 46px 圆 | `--soft` 纯灰 / `--muted` 灰字 + 1px `--line` 边 |
| 流水行 | `.txn .ico` | 40px 方 r13 | `--tint` / `--accent`(另有 `.g` 金 `.r` 赭红 `.b` 靛蓝语义变体) |
| 分类管理 | `.pick .mark` | 30px 方 r10 | `--tint` / `--accent` |

- 记一笔圆块**继承既有墨绿底语言**:底 `--tint`、字 `--accent`、边框收细为 inset 环或取消;
  字号 19→20,`font-weight:600` 保持衬线。
- 选中态**继承品牌标 `.brand-mark` 语言**:`linear-gradient(150deg,--accent-2,--accent-deep)`
  底 + `--accent-text` 字 + 黄铜金 inset 环 `inset 0 0 0 1px rgba(201,169,110,.35)`。
- 二级胶囊与选中态同步精修(现为 `--tint` + `inset 0 0 0 1px --accent`,与新一级选中态拉开层级)。
- 流水行 / 分类管理:语义色体系**不变**,仅统一字体、字重、内边距口径,使三处成一套。

### R3 修复胶囊条裁切

`.chip-strip{overflow-x:auto}` 在 390px 宽下第 4 个「AI 记账」被硬切,留下墨绿残影(观感像
渲染 bug)。需让四个胶囊在 390px 下**完整可见**(缩短文案/收紧 padding/让 AI 退化为图标按钮,
三选一或组合),不留半截元素。

### R4 数字键盘合盘

`.keypad{gap:6px}` + 每键 1px 边框 = 12 个独立白方块,读起来碎。改为**整体面盘**:外层一个
圆角面板 + 内部细分隔(或去边框、统一 `--soft` 底靠间距分区),单键 46px 保持并保留
`tabular-nums` 衬线数字与按下反馈。

**约束(不可破)**:`ui.md` v1.7「新增件不得把停靠区推回 200px 以上」→ 合盘**不得增加高度**,
反而应减少(见 R7)。

### R5 保存键重做

- 空金额 `disabled` 态过弱(浅灰字几乎不可见)→ 提高可读性,同时明确「不可用」语义。
- 有金额时右侧 76px 宽 × 204px 高竖条视觉重量过大且孤立 → 重做宽度/圆角/色彩权重,
  与键盘面盘成为一体,不喧宾夺主。
- `ui.md` v1.5 要求「保存与键盘**并排**而不单独占行」→ 并排结构保留。

### R6 间距节奏

`typebar{margin:2px 0}` 与分类区过挤;键盘上沿存在硬切分感。统一并分级:分段器上下、
分类区、备注行、胶囊条之间建立一致的节奏,键盘区上沿给出明确但柔和的分隔。

### R7 纵向空间回收(为 R6 供源)

页面**总高不增**。留白靠重新分配,**回收来源限定为两处**:

- 键盘 `.keypad{gap}` 6 → 4(键高 **保持 46px 不变**),
- 二级胶囊 `.sub-tile` 内边距收紧。

**硬性不砍项**:`.keypad .key` 高度保持 46px —— 原型自身规则要求触控目标 ≥44px,而
`ui-contract.md` #6 要求 Android 侧无障碍触控 ≥48dp,把键高往下砍会同时向两条线恶化。

**附加目标**:把停靠区 `.pad-row` 从实测的 **204px 压到 ≤200px**,顺手修掉 `ui.md` v1.7
「停靠区不得推回 200px 以上」这条**既存漂移**(当前实现已越界,非本任务引入)。

### R8 交付登记

原型版本递增为 **v2.4**,在 `BASELINE.md` 登记四份文件(`index.html`/`style.css`/`app.js`/
`smoke-test.cjs`)的 LF 归一化 SHA-256,状态按 `ui-contract.md` #5 与 `ui.md` 记为
「**待用户确认**」,用户确认后再转「已确认」;`README.md` 的「记一笔」小节同步更新。
**不动应用版本**(纯原型任务,无 `version.py bump`、无 CHANGELOG 变更)。

## Acceptance Criteria

- [ ] 四态截图(支出默认 / 支出+二级面板 / 已输金额 / 转账)均无死白、无元素裁切
- [ ] 转账态上下无大片空白,备注行与胶囊条紧随账户行下方
- [ ] 三处分类标识的底/字色取自同一组 token(`--tint`/`--accent`,选中态加黄铜金 inset 环)
- [ ] `.chip-strip` 四个胶囊在 390px 宽下全部完整显示,无半截元素
- [ ] `smoke-test.cjs` 全绿,`86 distinct actions, all handled` 不变
- [ ] 编辑页仍零滚动(`overflow <= 1px`),分类区仍为 `flex:1 + overflow-y:auto` 且
      `scrollHeight == clientHeight`
- [ ] 键盘单键 46px 未缩、金额点击区 ≥80×44、`.pad-row` ≤200px(既有断言不变且新增上限达标)
- [ ] 无新增运行时依赖,仍可双击 `index.html` 直接打开
- [ ] `BASELINE.md` v2.4 段四份哈希自洽(可用 `tr -d '\r' | sha256sum` 复核),状态为「待用户确认」
- [ ] `README.md` 已同步

## Definition of Done

- 冒烟测试通过:本机 `PLAYWRIGHT_MODULE=C:/Users/hanhu/Code/Collection_of_wedding_photos/node_modules/playwright`
- 四态截图人工核对(截图存 `/tmp`,不入库)
- `BASELINE.md` + `README.md` 更新完成
- 无 `prompt/alert/confirm` 引入;无新增无处理器的 `data-action`(原型两条硬性原则)

## Technical Approach

纯 CSS 为主:`style.css` 的编辑态段(`.content.edit-mode` / `.typebar` / `.cat-grid` /
`.cat-tile` / `.sub-panel` / `.meta-row` / `.amt-zone` / `.chip-strip` / `.pad-row` / `.keypad` /
`.save-btn`)与三处分类标识段(`.cat-tile .ci` / `.txn .ico` / `.pick .mark`)重做;
`app.js` 仅在 R3 需要调整胶囊结构或文案时改动 `editView()` 的 `chips` 片段。

设计 token 全部取自 `style.css` 顶部既有定义,**不新增色相**。

## Decision (ADR-lite)

### D1:改动幅度 —— 打磨现有骨架(已确认)

**Context**:结构(分段器 → 分类区 → 备注/金额行 → 胶囊条 → 键盘+保存)已由 10-11/10-12 系列
任务定型并被冒烟测试断言锁定;「金额升为主角」的重排需重写断言、改变既有习惯。

**Decision**:结构不动,只修具体视觉问题。

**Consequences**:冒烟测试零风险,交互习惯不变;金额仍居备注行右侧(靠字号/权重/色彩提升存在感)。
若日后仍觉金额不够突出,再单开「金额升为主角」任务。

### D2:分类图标语言 —— 保留衬线汉字,提升质感(已确认)

**Context**:「首字汉字」是全站共用分类标识(记一笔 / 流水行 / 分类管理三处)。仅改一处会造成
同一分类在不同页面长得不一样;三处齐换矢量图标则超出范围,且现有 51 个矢量图标中无任何分类图标,
需新绘 10 个一级 + 若干二级。

**Decision**:保留衬线汉字语言,重做质感。**方向是继承而非发明** —— 记一笔的圆块向全站已确立的
`--tint`/`--accent` 墨绿底语言看齐,选中态继承 `.brand-mark` 的墨绿渐变 + 黄铜金 inset 环。

**Consequences**:三处语言一致,改动面小、风险低。分类图标「矢量化」留给未来单独任务。

### D3:纵向预算 —— 重新分配,不长高(直接判定)

**Context**:实测支出默认态 `.content` 高 501px、`overflow = 0`,**刚好填满**(typebar 46 +
cat-grid 295 + meta 66 + chips 38 + 间距/内边距);键盘 dock 204px 在 content 之外。两个硬约束
已在仓库定死:README「触控目标 ≥44px」与 `smoke-test.cjs` 的零滚动断言。

**Decision**:不增加页面总高,留白靠重新分配(见 R7)。

**Consequences**:两条硬线均不破;键盘手感变化极小。

### D4:改动边界 —— 三处分类标识一起统一(已确认)

**Context**:除记一笔外,首页/日历/搜索的流水行(`app.js:294`)与我的→分类管理列表
(`app.js:682`)使用同一汉字标识语言。

**Decision**:本任务边界含这三处标识的质感统一;不含这四页的其他视觉。

**Consequences**:全站分类标识成一套;需回归首页/日历/搜索/我的四页(靠冒烟测试覆盖)。

## Out of Scope (explicit)

- Android Compose 端 `VipLedgerApp` 编辑页的任何改动(另开任务)
- 分类图标的矢量化(新绘图标集)
- 深色模式
- 首页/日历/资产/统计/我的四页中**除分类标识外**的视觉
- 编辑页新增功能(分账单、多币种、附件等)
- 应用版本号 / CHANGELOG(纯原型任务)

## 规格约束(from `.trellis/spec`,已逐条核对)

本任务改的是**原型**(`docs/ui-prototype/`),受 `android/ui.md`「原型优先」硬性规则与
`android/ui-contract.md` 契约约束。逐条核对结果:

| 规则 | 出处 | 对本任务的含义 |
|---|---|---|
| 编辑页永不上下滑动;分类区是唯一可内部滚动区(`flex:1 + overflow-y:auto`) | `ui.md` v2.0(硬性) | R1 只能改内部对齐,**不得改 flex 骨架** |
| 停靠区不得推回 200px 以上 | `ui.md` v1.7 | R4/R7 **不得增高**停靠区;实测当前 `.pad-row` 204px **已越界**,本任务顺手修正 |
| 保存与键盘并排,不单独占行;信息行单行可点 | `ui.md` v1.5 | R5 保留并排结构 |
| 金额点击区 ≥44px | `ui.md` v2.1 | 不缩 `.amt-zone`,既有断言 ≥80×44 |
| 一级 5 列圆形方块 + 二级浅底胶囊 | `ui.md` v1.9 | R2 保留形状骨架 |
| 备注为真实输入框,不用弹层;二级面板被裁切需自动滚入视野 | `ui.md` v2.1 | 不动 `note-input` 与 `revealSubPanel()` |
| 无假按钮;所有页面在原型内;禁 `prompt/alert/confirm` | `ui.md` v1.2 / v1.3 | 冒烟测试遍历断言,不得打破 |
| 原型变更后 `BASELINE.md` 摘要更新,差异部分回到「待确认」 | `ui.md`:19 | R8 新版本状态记「待用户确认」 |
| 原型变更需版本记录(版本/变更内容/SHA-256/确认状态) | `ui-contract.md` #5 | R8 |
| 无障碍触控目标 ≥48dp(平台要求优先) | `ui-contract.md` #6 | **不把键高 46px 往下砍** |
| 单一设计规则来源,禁止字面量颜色 | `ui-contract.md` #4 | token 全取 `style.css` 既有定义,不新增色相 |
| 状态完整覆盖(禁用/危险确认等) | `ui-contract.md` #3 | 编辑页已具备:删除账目走 `confirmPhone` 二次确认、返回未保存走丢弃确认;**禁用态**即 R5 |

**契约范围判定**:`ui-contract.md` 的适用对象是 Compose UI 开发(`ui-contract.md`:3)。
本任务是原型侧交付,故 R8 只做到「登记 v2.4 为待确认」;**原型确认后的 Compose 同步另开任务**
(见 Out of Scope)。

## Technical Notes

- 文件:`docs/ui-prototype/{index.html,style.css,app.js,smoke-test.cjs,BASELINE.md,README.md}`
- 设计 token(`style.css` 顶部):`--accent:#1a3c34` / `--accent-2:#24504a` /
  `--accent-deep:#0f2a24` / `--accent-text:#f0e7d6` / `--tint:#e8eee9` / `--soft:#f0ede4` /
  `--gold-2:#c9a96e` / `--gold-bg:#f0e7d6` / `--gold-ink:#8a6b34` / `--line:#e6e1d6` /
  `--muted:#7c7767` / `--faint:#a9a499`
- 参考同源语言:`.brand-mark`(style.css:71)墨绿渐变 + 黄铜金 inset 环
- 现状关键行:`app.js:514`(记一笔方块)、`app.js:294`(流水行)、`app.js:682`(分类管理)
- 冒烟测试关键断言:`.content.noscroll` 存在、`overflow <= 1`、`.chip-strip .chip-btn >= 3`、
  `.note-input` 为真实 INPUT、`.amt-zone >= 80x44`、`.keypad .key == 12`、遍历 `data-action`
- 截图脚本与产物在 `/tmp/vl-shot/`(临时)
