# Research: 离线规则层中文记账解析器（M6 tier 1）

- **Query**: 设计中文自然语言记账的离线规则层，从一句话抽出金额 / 日期 / 类型 / 账户 / 分类候选，纯 Kotlin、无模型、可纯逻辑单测。
- **Scope**: mixed（仓库内部约束 + 外部工具/库行为）
- **Date**: 2026-09-28
- **Author**: trellis-research 子代理

## 0. 仓库约束（先钉死口径）

- 目标输入示例：`昨天午饭 35 块 微信` / `上周三打车 28.5` / `3月5日 房租 3500` / `收到工资 12000` / `楼下超市买苹果 12块5` / `退款 88 元`。
- `docs/ARCHITECTURE.md` §6 tier 1：正则抽金额（`12` / `12.5` / `12块5`）、日期（今天/昨天/上周三/3月5日）、账户关键词；`merchant_rule` 命中直接定分类。三层管线，任一级失败降级；无模型。
- 纯逻辑、无 Android 依赖。`core:model` / `core:domain` 是纯 Kotlin（`docs/ARCHITECTURE.md` §3），单测放被测模块 `src/test`（`.trellis/spec/android/testing.md`）。
- 金额一律 `Money`（Long 分），禁止 `Double`/`Float` 参与金额运算（ARCHITECTURE §4）。已有 `Money.parse(text)`（`core/model/src/main/kotlin/com/han1997/vipledger/core/model/money/Money.kt:107`）能处理 `¥/￥/$`、千分位逗号、`±`、超两位小数 HALF_UP，**但不认中文数字，也不认 `块/角/分/毛/元`**；规则层要把中文金额归一成阿拉伯串后再交给它。
- 目标字段类型已存在：`TransactionType { EXPENSE, INCOME, TRANSFER, DEBT_REPAY, ADJUST }`、`DraftStatus`、`TxnSource { MANUAL, RECURRING, AI, IMPORT, RULE }`（`core/model/.../LedgerEnums.kt`）；`merchant_rule` 的匹配方式 `CONTAINS/REGEX/EQUALS`、字段 `TITLE/REMARK/ANY`、`priority` 越小越优先（`core/model/.../RuleEnums.kt`、`docs/DATA-MODEL.md` 3.8）。
- 结果落 `draft_status = PENDING`，用户确认或修正后入库；每次修正写一条 `ml_training_sample`（ARCHITECTURE §6）。

> 本文的「外部工具行为」结论均来自 2026-09-28 直接抓取官方 README / 包元数据（见第 7 节 URL）。本轮**没有可用的通用 web 搜索工具**，因此只对抓取过的一手文档下断言；无法一手核实的（如随手记等闭源 App 的具体语法）一律标注为「未核实」，不写成事实。

---

## 1. 金额抽取（Amount grammar）

### 1.1 字符类

| 类 | 成员 | 处理 |
|---|---|---|
| 阿拉伯数字 | `0-9`、全角 `０-９` | 全角先归一为半角 |
| 小数点 | `.`、全角 `．`、中文「点」 | 「点」在金额语境按小数点；在钟点语境是分隔符（见 §1.3） |
| 货币单位前缀 | `¥ ￥ $` | 归一，交给 `Money.parse` |
| 人民币单位 | `元 圆 员 块 角 毛 分 厘` | 语义单位，参与「块角分」换算 |
| 中文数字 | `零 一 二 三 四 五 六 七 八 九 十 百 千 万 亿 两 俩` | 中文数词 → 阿拉伯串 |
| 全角标点 | `，。；：！？（）` | 归一为半角/空白 |
| 折扣/比例词 | `折 折扣 百分之 成` | **不产生金额**，见 §1.3 |
| 时间词 | `点 时 点半 分 刻` | 与金额争用同一段文本，见 §1.3 / §4 优先级 |

### 1.2 规则表（worked rules）

| # | 输入形态 | 语义 | 归一后 | 备注 |
|---|---|---|---|---|
| A1 | `35`、`28.5`、`12.50` | 元 | `35` / `28.5` / `12.50` | 直接 `Money.parse` |
| A2 | `¥35`、`１２．５` | 元 | `35` / `12.5` | 全角归一 + 去符号 |
| A3 | `35 块`、`35元`、`35块5` | 35.50 / 35.00 | `35.5` / `35` | 「块/元」后跟单个数字：数字是**角**（`12块5`=12.50） |
| A4 | `12块5毛` | 12.50 | `12.5` | 后跟数字 + `角/毛` 明确 |
| A5 | `12块5分` | 12.05 | `12.05` | 后跟数字 + `分`：数字是**分** |
| A6 | `12元5角3分` | 12.53 | `12.53` | 元角分完整式 |
| A7 | `12块零5` | 12.05 | `12.05` | `零` 显式「跳档」到分 |
| A8 | `十二块五` | 12.50 | `12.5` | 中文数词先转阿拉伯，再套 A3 |
| A9 | `一百二` | 120 | `120` | 口语省位：百后裸数字 = 十位 |
| A10 | `两百五` / `俩百五` | 250 | `250` | `两/俩`=2 |
| A11 | `三千五` | 3500 | `3500` | 千后裸数字 = 百位 |
| A12 | `一万五` | 15000 | `15000` | 万后裸数字 = 千位 |
| A13 | `一万零五` | 10005 | `10005` | `零` 隔断，**不省位** |
| A14 | `十块五` | 10.50 | `10.5` | 块后裸数字 = 角 |
| A15 | `一块五` | 1.50 | `1.5` | 同上 |
| A16 | `两块钱` | 2.00 | `2` | 量词 `钱` 可吞 |
| A17 | `一千零五十` | 1050 | `1050` | 标准中文数词 |
| A18 | `1百23`（混写） | 123 | `123` | cn2an `smart` 模式支持混写 |
| A19 | `3000万`、`1.5亿` | 大数 | `30000000` / `150000000` | `万/亿` 做乘数；上界约 `10^16`（千万亿） |

口语省位规则（A9–A15）统一表述：

- `百+裸数字` → 裸数字进「十位」：`一百二` = 120。
- `千+裸数字` → 进「百位」：`三千五` = 3500。
- `万+裸数字` → 进「千位」：`一万五` = 15000。
- `块/元+裸数字` → 进「角位」：`十二块五` = 12.5。
- 任何位置出现 `零` 或完整单位链，即关闭省位：`一万零五` = 10005。

> 该省位规则与 `cn2an` 的 `strict`/`normal`/`smart` 语义**不完全相同**：cn2an 面向「数词 → 数」，`一百二` 在 strict 下不合法（缺单位），需要项目侧自己实现「量词后裸数字补位」这一层。cn2an 可直接复用的是：`一二三 → 123`（normal）、`1百23 → 123`（smart）、小数 `一点二三 → 1.23`、逐位 `一二点三零 → 12.30`（direct）、上界 `10^16`（一手依据：PyPI `cn2an` README，见 §7）。

### 1.3 歧义与消解（这是规则层最贵的部分）

| 歧义 | 例子 | 消解规则 | 归属 |
|---|---|---|---|
| `点`=小数点 vs 钟点 | `三点` / `三点五` / `三点零五` / `三点十五` | 见下方 L1–L4 | 时间优先于金额 |
| `三点` 是 3:00 还是「3 点（积分/百分点）」 | `三点` | 有 `点` 且无货币单位 → 时间；记账文本里几乎不出现「3 点积分」 | 时间 |
| `十分` | `三点十分` vs `十分` | 有前置 `N点` → 分钟；单独 `十分` 无货币单位 → 作副词/时间，忽略 | 时间/忽略 |
| `五折` / `打八折` | `打八折` | `折/成/百分之` → 比例，**不产金额**；若句中有原价再计算折后价（tier1 可记为 known-miss） | 比例 |
| `一万五` = 15000 ≠ 10005 | `一万五` / `一万零五` | 有 `零` 才走「不省位」 | 金额 |
| 裸数字 vs 日期数字 | `3月5日 房租 3500` / `上周三打车 28.5` | 先跑日期抽取并**占位**，金额抽取跳过已占位区间（§4） | 日期优先 |
| 裸整数无任何单位 | `楼下超市 12` | 仅当「非日期占位、非时间占位」的整数；若多个，取带货币单位者优先；剩余最大整数作金额并标低置信 | 金额（低置信） |
| `分`（金额 0.1 元）vs `分`（分钟） | `5分` | 前置为钟点 → 分钟；前置为元/块/数字且语境为价 → 0.05 元 | 依上下文 |
| 大额裸数字 vs 手机号/卡号 | `138...` | 连续 ≥ 7 位数字不认成金额；`merchant_rule` 与账户匹配先行 | 忽略 |
| `半` | `点半` / `一个半` | `点半` → :30（时间）；`一个半` 无货币单位 → 低置信 1.5 | 时间优先 |

钟点-金额消解细则（`点` 之后是什么）：

- **L1** `点` + `半` / `一刻` / `三刻` / `整` / `分` → 时间（`三点半`=3:30）。
- **L2** `点` + `零` + 数字 → 时间，`零` 是分钟前导零（`三点零五`=3:05，不是 3.05）。
- **L3** `点` + 两位数字且 ≤59 → 时间（`三点十五`=3:15）。
- **L4** `点` + 单个数字、后面**无** `分`/`半`/`刻`，且句中已有货币单位 → 该段是数字小数（`三点五`=3.5），或整体判为时间 3:05（项目需二选一并写进测试）。**建议**：`点` 后单数字一律按时间（3:05）处理，因为记账句里「工资 3.5」几乎不会写成「三点五」；若后面紧跟 `元/块` 才回退为金额。
- **L5** 有明确货币单位（`元/块/¥/角/分/毛`）紧邻 → 一律金额，覆盖以上所有。

> 关键：**「点」段落默认归时间，只有当同一段带货币单位时才是金额。** 这条优先级同时解决「3月5日」里的数字与「上周三」里的中文数字被误读为金额的问题。

### 1.4 外部工具在这块的真实表现（可直接引用）

- **Duckling 的中文维度里没有 `AmountOfMoney`。** `Duckling/Dimensions/ZH.hs` 只注册了 `Distance / Duration / Numeral / Ordinal / Quantity / Temperature / Time`；`AmountOfMoney` 只出现在 `Duckling/Dimensions/Common.hs`（通用语言集）。也就是说，一个成熟的开源规则引擎**没有为中文金额提供现成维度**——中文金额语言（块/角/分/口语省位）确实是需要自建的空白区（一手依据：Duckling 仓库 `Duckling/Dimensions/ZH.hs`、`Duckling/Dimensions/Common.hs`，见 §7）。
- **cn2an** 提供了可复用的「中文数词 ↔ 阿拉伯数字」内核与多种模式，但它是数词工具而非金额解析器：不认 `块/角/分`，也不做「块后裸数字补角位」（一手依据：PyPI `cn2an` README，见 §7）。
- **jieba** 只是分词工具（PyPI summary：*Chinese Words Segmentation Utilities*），不做金额或日期语义归一。
- 结论：金额段建议**自研量词层 + 复用 cn2an 式数词算法思路**，最后交给仓库已有的 `Money.parse`。

---

## 2. 日期 / 时间抽取（Date grammar）

### 2.1 参考「今天」契约（Reference date contract）

**所有相对日期必须相对一个显式传入的参考日解析，禁止在纯函数里读系统时钟。**

- 形如 `fun parseDate(text: String, reference: LocalDate): DateResolution`。
- 参考日由调用方在 UI/用例层用设备时区取「本地今天」后传入。
- 好处：纯逻辑单测可以像 Duckling 语料那样固定参考时间。Duckling 的 ZH Time 语料就在文件头声明参考时间 *"Tuesday Feb 12, 2013 at 4:30am"*，所有 `今天/昨天/上周二` 的期望值都相对它写死（一手依据：Duckling README + `Duckling/Time/ZH/Corpus.hs`）。本仓库应照搬这个做法。
- 本文件测试语料统一取 **REF = 2026-09-28（星期一，ISO weekday 1）**。

### 2.2 相对「天」

| 表达 | 结果（相对 REF=R） |
|---|---|
| 今天 / 今日 / 本日 / 今儿 | R |
| 昨天 / 昨日 / 昨儿 | R − 1 |
| 前天 / 前日 | R − 2 |
| 大前天 | R − 3 |
| 明天 / 明日 | R + 1 |
| 后天 | R + 2 |
| 大后天 | R + 3 |
| N 天前 / 前 N 天 | R − N |
| N 天后 / 过 N 天 | R + N |
| 昨晚 / 昨天晚上 | (R − 1) 晚间 |
| 今早 / 今天早上 | R 早上 |
| 明早 | (R + 1) 早上 |

### 2.3 星期与「周」

设 R 的 ISO weekday = w（周一=1 … 周日=7）。

| 表达 | 消解规则 | 相对 REF=周一(1) |
|---|---|---|
| 周X / 星期X / 礼拜X（裸） | **严格晚于今天**的下一个 X；若今天就是 X，跳到 +7 | 周三 → 09-30；周二 → 09-29 |
| 这周X / 本周X / 这个星期X | 本周（周一为首）内 **≥ 今天** 的 X；若 X 的星期序 < w，则 +7 | 这周三 → 09-30；这周一 → 10-05（因 09-28 已过…见注） |
| 上周X / 上个星期X | 上一个自然周（周一为首）里的 X = (本周一 − 7) + (X−1) | 上周三 → 09-23 |
| 下周X | **需要在项目里明确二选一**，见下 | — |
| 这周 / 本周 | 本周（周一 00:00 起）| 09-28 ~ 10-04 |
| 上周 / 上星期 | 上一周 | 09-21 ~ 09-27 |
| 下周 / 下星期 | 下一周 | 10-05 ~ 10-11 |
| 周末 | 周六 + 周日 | 10-03 ~ 10-04 |

注（`这周X` 边界）：Duckling ZH 在 REF=周二 2013-02-12 下，`这周二` → 02-12（今天），`这周一` → 02-18（+7，因为本周一已过去）。即 Duckling 把 `这周X` 定义为「本周内不早于今天的最近 X」，跨周顺延。这与「这一自然周内的 X（哪怕已过去）」不同，项目必须显式选定。**记账场景建议**取 Duckling 式「不早于今天」（因为记的是已发生的事，用户说「这周三」通常指刚过去的那个或即将来的那个，二者需靠 `上周/下周` 区分；保持与裸 `周X` 一致更不易错）。

`下周X` 的两种读法（**必须写进测试钉死**）：

1. **自然周读法**：下一自然周的 X，`下周二` = R+7~R+13 里的周二。
2. **Duckling 读法**：等价于裸 `周X`（严格晚于今天的下一个 X）。Duckling ZH 实测：REF=周二 02-12 时 `下周三` → **02-13**（就是明天），`下周二` → 02-19。即「下」被理解成「下一个到的」。

Duckling 的实测行为对「下周三 = 明天」是反直觉的，但它有据可依（`Duckling/Time/ZH/Corpus.hs`）。项目建议：**采用自然周读法**（`下周X` = 下一自然周的 X），并在 known-miss 里注明「与 Duckling 的 ZH 语料不同；此差异是有意的」。

### 2.4 绝对日期

| 表达 | 结果 |
|---|---|
| `3月5日` / `3月5号` / `3月5` | 当年 3 月 5 日；若已过则仍是当年（记账偏过去），见下「年份推断」 |
| `3/5` / `3-5` / `3.5` | 中文语序为 **月/日**（`2/15` → 2 月 15 日，Duckling ZH 实测），故 `3/5` = 3 月 5 日 |
| `2015年3月3号` | 显式年 |
| `二零一五年三月三号` | 中文数字年份，先转阿拉伯 |
| `15年3月` | 两位年 → 20xx（`15`→2015），需与 `15日` 区分 |
| `3月` | 月粒度（该月 1 日 / 整月，看落库字段） |
| `10月31` | 当年 10 月 31 |
| `去年 / 今年 / 明年` | R.year −1 / R.year / +1 |
| `上个月 / 这个月 / 下个月` | R 的月 ±1 |
| 传统节日（春节/元旦等） | tier1 可只覆盖少数固定公历节日或不支持（known-miss） |

**年份推断（写进契约）**：只给月日时取参考年；不对「未来则回退一年」做默认。理由：记账几乎总是记过去，若 `3月5日` 在 9 月被提到，理解为今年 3 月 5 日最合理。Duckling ZH 实测 `2月15号` 在 REF=2013-02-12 下 → 2013-02-15（不跨年），与「取参考年」一致。dateparser 则用 `PREFER_DATES_FROM`（past/future/current_period）显式配置这个偏好——若将来要可配，照此命名（一手依据：PyPI `dateparser` README 的 `PREFER_DATES_FROM`、`RELATIVE_BASE` 设置，见 §7）。

**裸「N 号」**：取参考月的第 N 日（`5号` 在 REF=09-28 → 09-05）；若 N > 当月天数则该月无此日，判为解析失败或顺延（建议失败，提示用户）。`5号` 落在未来（如 REF 是 09-01 时的 `5号`）时，记账场景按「当月未来」处理还是「上月」处理需选定；建议与 `PREFER_DATES_FROM=past` 一致，取最近的不晚于 R 的该日号。

### 2.5 时段与钟点

Duckling ZH 语料实测的时段区间（`grain=Hour`）：

| 词 | 区间 | 依据 |
|---|---|---|
| 凌晨 | 00:00 – 06:00（建议） | 项目补，Duckling ZH 未单列 |
| 早上 / 早晨 | 04:00 – 12:00 | Duckling ZH：`周一早上` → 04:00–12:00 |
| 上午 | 08:00 – 12:00（建议） | 项目补 |
| 中午 | 11:00 – 13:00（建议） | 项目补 |
| 下午 | 12:00 – 18:00（建议） | 项目补，Duckling 用显式小时 |
| 晚上 / 晚间 | 18:00 – 24:00 | Duckling ZH：`情人节晚上` → 18:00–次日 00:00 |
| 周末（时段） | 周五 18:00 – 周一 00:00 | Duckling ZH：`这周末` |

钟点：

- `三点` / `下午三点` → Duckling ZH 均给 **15:00**（裸小时按 PM）。
- `下午三点十五` / `下午3:15` / `15:15` → 15:15；`三点半` → 15:30；`三点零五` → 15:05。
- `今晚8点` → 20:00。

**建议**：项目显式定义「无时段提示的裸小时 1–11」→ +12（下午/晚上），与 Duckling ZH 一致；带 `早/凌晨` → 不 +12；带 `下午/晚/中午` → +12。此规则标为低置信，反正结果先落待确认草稿。这是一个**项目决策**，Duckling 只提供「裸 `三点`=15:00」这一条实测。

### 2.6 只给日期时的时间默认值

Duckling ZH 对裸日期（今天/昨天/3月5日）一律给 **00:00，grain=Day**（例如 `今天` → 2013-02-12 00:00:00）。dateparser 亦以午夜为无时刻时的默认。

**建议**：

- 解析结果是「日期」时落成本地 **00:00**（`occurredAt` = 该本地日零点对应的 epoch millis）；
- 若解析出的日期 == 参考日（今天）**且**句中无钟点，可用「当前时刻」而不是零点，以保留用户记账的当下时间；
- 两者都写进契约与测试，避免实现随意。

### 2.7 「上周三」到底怎么解（本题重点）

**结论：`上周三` = 上一个自然周（周一为首）里的星期三，不是「最近过去的星期三」。**

- 一手证据：Duckling ZH，REF=周二 2013-02-12 时，`上周二` → 2013-02-05、`上周日` → 2013-02-10。周二 02-12 所在周为 02-11（一）~02-17（日），上一周为 02-04~02-10，其周二=02-05、周日=02-10，与实测吻合。
- 对照陷阱：若按「最近过去的星期三」且今天周二，则应为 02-06；Duckling 不给 02-06，说明实现取的是自然周切片。
- 追加陷阱：`上周日` 在周一为首的自然周里是**上一周的星期日**（= 上一周最后一天），不是「上周的第 7 天算到本周日前」。用「本周一 − 7 + 偏移」公式最稳：`(R 所在周一 − 7) + (X − 1)`。

### 2.8 日期外部工具（可引用）

- **Duckling**：中文 Time 维度成熟，覆盖相对日、裸/带周修饰的星期、月日、时段、假日；语料固定参考时间，是「参考日显式传入」的最佳先例（§7）。
- **dateparser**：支持中文，且把相对日期基准做成参数 `RELATIVE_BASE`、过去/未来倾向 `PREFER_DATES_FROM`、是否返回时间段 `RETURN_AS_TIME_PERIOD`；changelog 多次「Improved … Chinese」（#1148、#910、#359 等）（§7）。
- **JioNLP**：`parse_time`（时间语义解析为时间戳/时长）、`extract_time`（抽时间实体），是中文时间归一的可参考实现（§7）。
- **HanLP / LTP**：面向分词、词性、NER 的通用平台，不提供开箱即用的相对日期归一（§7）。
- **jieba**：仅分词。

---

## 3. 字段分类与消解（类型 / 账户 / 分类）

### 3.1 类型线索表（对应 `TransactionType`）

| 类型 | 线索词 | 备注 |
|---|---|---|
| EXPENSE（默认） | 无明确收入/转账线索时的兜底；`买/吃/付/花/交了/打车/打车/房租/话费` | 金额恒正，方向由类型定 |
| INCOME | `收到`、`收入`、`到账`、`发了工资`、`发工资`、`工资`、`退款`、`报销到账`、`退回`、`利息`、`分红`、`红包收`、`卖了`、`中奖`、`收了` | `收到工资 12000` → INCOME |
| TRANSFER | `转账`、`转给`、`转到`、`从X转到Y`、`转入`、`转出`、`提现`、`充值` | 需 `toAccountId`，`requiresTargetAccount=true` |
| DEBT_REPAY | `还款`、`还信用卡`、`还花呗`、`还房贷`、`还车贷`、`还借呗`、`还清了`、`还了` | 目标账户为负债类（`AccountType.isDebt`） |
| ADJUST | `余额调整`、`对账`、`更正` | 罕见，tier1 可仅识别关键词 |

### 3.2 陷阱（必须在测试里钉）

| 句子 | 常见误判 | 正确 | 判据 |
|---|---|---|---|
| `还信用卡 2000` | EXPENSE | **DEBT_REPAY**（或 TRANSFER 到信用卡账户） | `还` + 负债账户；`还/还款` 线索优先于「默认支出」 |
| `还款给张三` | EXPENSE | TRANSFER / DEBT_REPAY | `还` + 非负债目标 → 转给他人，不是消费 |
| `退款 88 元` | EXPENSE | **INCOME**（或冲减支出，需产品定） | `退款` 线索；DATA-MODEL 统计口径需确认是否计收入 |
| `报销 1200`（未说收到） | INCOME | 视上下文：`到账/收到` → INCOME，否则低置信（可能是待报销的垫付支出） | 缺 `收到` 时降级为「待确认」，不硬判 |
| `收到报销 1200` | — | INCOME | `收到` 强线索 |
| `微信支付 35` / `微信 35` | 分类=微信 | `微信` 是**账户/渠道**候选，不是分类 | 账户名匹配先于分类匹配 |
| `转账 500`（只出现一个账户） | TRANSFER 但缺 toAccount | TRANSFER + `toAccountId=null`，标「待补收款账户」 | `requiresTargetAccount` 为真时缺目标 → 部分结果 |
| `还了 500` | 歧义：hái（还是）vs huán（归还） | 需后接负债/人名才判归还；否则低置信 | 单字 `还` 歧义，见 3.4 known-miss |
| `借出 2000 给李四` | INCOME | 应收（TRANSFER 到应收类账户） | `借出` = 债权，不是收入 |
| `借入 2000` | INCOME | 负债（借入） | `借入` = 债务 |

### 3.3 无模型下的打分与置信度

**不要伪造概率。** 建议用「离散证据桶 + 排序」而不是 float 置信：

- 每个抽取器给候选打**证据分**（整数），证据例如：
  - `+5` 有货币单位 / `Currency` 紧邻；
  - `+4` `merchant_rule` 命中（ARCHITECTURE §6：命中直接定分类，最高优先）；
  - `+3` 账户名精确（EQUALS）匹配；
  - `+2` 账户名包含（CONTAINS）匹配；
  - `+2` 强类型线索词（收到/还款…）；
  - `+1` 弱线索；
  - `−3` 该候选文本落在另一个抽取器已占位区间（冲突）；
  - `−2` 依赖歧义 token（单字 `还`、裸整数无单位）。
- 排序取 top1；若 `top1 − top2 < margin`（如 margin=2）→ 标 `ambiguous`，把 top2 一并交给用户。
- 对外只暴露三档枚举：`HIGH`（有货币单位 + 有类型线索 / merchant_rule 命中）、`MEDIUM`（有货币单位无类型线索 / 有线索无单位）、`LOW`（裸整数、歧义 token）。存进草稿以便 UI 提示。
- 需要连续置信度的是 M7 的端侧分类器；tier1 用枚举即可，避免以后拿不可比的数混用。

### 3.4 规则层判不了的分类（known-miss，见 §6）

- 无任何关键词、纯商户名的类型（`星巴克 35` 是支出，`星巴克退款 35` 是收入——靠词，不靠规则）。
- 账户/分类候选名称与标题文本重叠时，无法知道哪个是哪个（`在工商银行交电费`：工商银行是账户，电费是分类）。
- 多笔混合类型的一句话。

---

## 4. 规则层架构建议（纯 Kotlin）

### 4.1 核心思想：先占位、后归并，而不是「顺序吃文本」

朴素实现最容易出的 bug 就是「日期里的 `3`/`5` 被金额正则吃掉」「`上周三` 的 `三` 被中文数词吃掉」。**不要用顺序 `replace` 抽干文本**；改为：

1. 对原文算出若干**候选 span**：`Span(start, end, kind)`，`kind ∈ {DATE, TIME, AMOUNT, RATIO, ACCOUNT, MERCHANT, CUE}`。
2. 按**优先级**把 span 落到一张 `SpanTable`（区间不可重叠）。高优先级先落，低优先级遇到重叠只能退让（缩小或放弃）。
3. 优先级（高 → 低）：`DATE/TIME` ＞ `RATIO(折/百分之)` ＞ `AMOUNT` ＞ `ACCOUNT/MERCHANT` ＞ `CUE(类型词)` ＞ 剩余文本（标题/备注）。
4. 每个抽取器可再在**未被占用的文本**上跑（用 `SpanTable.mask()` 得到带洞文本），从而天然避免互吃。

这样「`3月5日 房租 3500`」：日期先占 `3月5日`，金额只看到 `房租 3500`，取 `3500`。

### 4.2 有序 pass

```
P0  normalize       全角→半角（数字/．，：/¥）、去零宽/不可见字符、统一 週→周 禮拜→礼拜 圓/园→元、trim
P1  splitTriggers   剥掉「记一笔/帮我记/记账」前缀与句末「。！？」；不做语义切分
P2  dateTime        DATE/TIME 抽取并占位（标点/单位词也要一起占，避免「月/日/点」被金额吞）
P3  ratio           「折/百分之/成」抽取并占位；产生 ratio 修饰而非金额
P4  amount          在 mask 后的文本上抽金额；中文数词归一 → Money.parse；产 Money? 候选
P5  accounts        用剩余文本匹配用户 DB 账户名（EQUALS → CONTAINS → REGEX）
P6  merchantRules   用「标题+备注」文本跑 merchant_rule（priority 升序），命中即定分类
P7  typeCues        类型线索词 → TransactionType 候选（含 §3.2 陷阱规则）
P8  categories      其余分类名匹配（仅当 P6 未命中）
P9  assemble        组装 ParsedEntry + 候选列表 + 置信度 + 已消费 span（调试用）
P10 result          Result<...>，任何一步异常都降级，不抛到 UI
```

### 4.3 Kotlin 草案

```kotlin
package com.han1997.vipledger.core.domain.nlp   // 纯 Kotlin，无 Android 依赖

enum class SpanKind { DATE, TIME, AMOUNT, RATIO, ACCOUNT, MERCHANT, CUE }

data class Span(val start: Int, val end: Int, val kind: SpanKind)

/** 区间表：高优先级先落，重叠者退让；可导出「带洞文本」给下一 pass。 */
class SpanTable(private val text: String) {
    private val taken = ArrayList<Span>()
    fun tryClaim(span: Span): Boolean {
        if (span.end <= span.start) return false
        if (taken.any { it.start < span.end && span.start < it.end }) return false
        taken += span
        return true
    }
    /** 未占用的字符替换为空格，保持下标不变，便于后续抽取器继续用全局偏移。 */
    fun masked(): String {
        val a = text.toCharArray()
        for (s in taken) for (i in s.start until s.end) a[i] = ' '
        return String(a)
    }
}

enum class Confidence { HIGH, MEDIUM, LOW }

data class AmountCandidate(val value: Money, val span: Span, val confidence: Confidence)

data class DateCandidate(val date: LocalDate, val time: LocalTime?, val span: Span)

data class ParsedEntry(
    val amount: Money?,
    val type: TransactionType,
    val date: LocalDate,
    val time: LocalTime?,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val title: String,
    val remark: String,
    val confidence: Confidence,
    val matchedRuleId: String?,       // merchant_rule 命中
    val alternatives: List<ParsedEntry> = emptyList(),
    val consumed: List<Span> = emptyList(),
    val warnings: List<String> = emptyList(),   // 缺 toAccount、单字「还」歧义等
)

sealed interface ParseResult {
    data class Ok(val entry: ParsedEntry) : ParseResult
    data class Partial(val entry: ParsedEntry, val missing: List<String>) : ParseResult
    data class Failed(val reason: String) : ParseResult
}

/** 纯函数：参考日与账户/分类/规则都是入参，保证可单测、不读时钟/DB。 */
class RuleEntryParser(
    private val accounts: List<Account>,          // 用户 DB 候选
    private val categories: List<Category>,       // 用户 DB 候选
    private val merchantRules: List<MerchantRule>,// 按 priority 升序
) {
    fun parse(text: String, today: LocalDate): ParseResult = runCatching {
        // P0..P9：每个 stage 是独立纯函数，返回 (result, spans)；任一步失败走降级
        val norm = NlNormalizer.normalize(text)
        val spans = SpanTable(norm)
        val date = DateExtractor.extract(norm, spans, today)     // 先占位
        val ratio = RatioExtractor.extract(norm, spans)
        val amount = AmountExtractor.extract(norm, spans)        // 看 mask 后的文本
        val acct = AccountMatcher.match(norm, spans, accounts)
        val rule = MerchantRuleMatcher.firstHit(norm, merchantRules)  // §6 直接定分类
        val type = TypeClassifier.classify(norm, acct, rule)     // 含 §3.2 陷阱
        assemble(amount, date, acct, rule, type, ratio, norm)
    }.getOrElse { e -> ParseResult.Failed(e.message ?: "parse error") }
}
```

### 4.4 复用与落点

- 金额：中文数词归一后交给 `Money.parse`（`Money.kt:107`）；`块/角/分` 换算用手写规则产阿拉伯串。
- 账户/分类：**只产出候选 id**，匹配真实名字（ARCHITECTURE 要求），不自由生成。
- `merchant_rule`：`RuleMatchType.CONTAINS/REGEX/EQUALS` + `RuleTargetField` + `priority` 已定义，直接实现匹配循环即可。
- 放置建议：解析器放 `core:domain`（纯 Kotlin，已有 `calculator/RecurrenceCalculator` 用 `kotlinx.datetime.LocalDate` 的先例），测试放 `core/domain/src/test/.../nlp/`；`feature:ai` 作为 tier1 的调用方与 UI。**不要**放进 `core:model`（那里只有值对象）。
- 时间：用 `kotlinx.datetime.LocalDate/LocalTime`，落库再转 epoch millis（§4 时间约定）。

### 4.5 测试语料形状（防回归）

- **表驱动 + 固定参考日**：`data class Case(input, today, expectedAmount, expectedDate, expectedType, expectedAccountId, expectedCategoryId, expectedConfidence, expectedWarnings)`，一条 `@Test` 跑全表。
- 参考日固定（如 `2026-09-28`），绝不 `LocalDate.now()`。
- 断言用 `Money`（分）比较，不比较字符串。
- 每类歧义至少一条边界：`一万五` vs `一万零五`、`三点半` vs `三点五`、`上周三` vs `这周三` vs `周三`、`还信用卡` vs `还了 500`、全角输入。
- 另配一组「负例」（Duckling 语料有 negative corpus 的先例）：`13800000000`、`3.14159`、纯文本无数。
- 由于 `adb shell input text` 注入不了中文（`testing.md`），这些必须是 **JVM 单测**，不能靠模拟器走查。

---

## 5. 建议的单测语料（≥20 条）

参考日 **REF = 2026-09-28（星期一）**，账户假设存在：`微信`、`支付宝`、`工商银行`、`信用卡`；分类存在：`餐饮`、`交通`、`房租`、`购物`、`工资`、`退款`。

| # | 输入 | 金额 | 日期 | 类型 | 账户 | 备注 |
|---|---|---|---|---|---|---|
| 1 | `昨天午饭 35 块 微信` | 35.00 | 09-27 | EXPENSE | 微信 | 标题「午饭」 |
| 2 | `上周三打车 28.5` | 28.50 | **09-23** | EXPENSE | — | 上周三=上一自然周周三 |
| 3 | `3月5日 房租 3500` | 3500.00 | 2026-03-05 | EXPENSE | — | 月/日序，当年 |
| 4 | `收到工资 12000` | 12000.00 | 09-28 | **INCOME** | — | 分类候选 工资 |
| 5 | `楼下超市买苹果 12块5` | **12.50** | 09-28 | EXPENSE | — | 块后裸数字=角 |
| 6 | `退款 88 元` | 88.00 | 09-28 | **INCOME** | — | 退款线索 |
| 7 | `今天早上买咖啡 18.5 支付宝` | 18.50 | 09-28 早上 | EXPENSE | 支付宝 | 时段+账户 |
| 8 | `今天午饭 30` | 30.00 | 09-28 | EXPENSE | — | 无单位，MEDIUM |
| 9 | `晚上打车回家 46` | 46.00 | 09-28 晚 | EXPENSE | — | 晚上 18–24 |
| 10 | `3/5 买书 42` | 42.00 | 2026-03-05 | EXPENSE | — | `3/5` = 3月5 |
| 11 | `前天超市 123.45` | 123.45 | 09-26 | EXPENSE | — | 小数 |
| 12 | `还信用卡 2000` | 2000.00 | 09-28 | **DEBT_REPAY** | 信用卡 | 陷阱 |
| 13 | `转账 500 到支付宝` | 500.00 | 09-28 | TRANSFER | toAccount=支付宝 | 缺转出账户则 warning |
| 14 | `收到报销 1200 微信` | 1200.00 | 09-28 | INCOME | 微信 | |
| 15 | `三斤苹果一共 一百二` | 120.00 | 09-28 | EXPENSE | — | 口语省位 |
| 16 | `一万五房租` | 15000.00 | 09-28 | EXPENSE | — | ≠10005 |
| 17 | `一万零五 房租` | 10005.00 | 09-28 | EXPENSE | — | 零隔断 |
| 18 | `上周三打车 28.5`（重复对照）| 28.50 | 09-23 | EXPENSE | — | 与 #2 同 |
| 19 | `这周三聚餐 200` | 200.00 | **09-30** | EXPENSE | — | 这周三=未来 |
| 20 | `周三聚餐 200` | 200.00 | **09-30** | EXPENSE | — | 裸周三=下一个 |
| 21 | `三点半买奶茶 十五块` | 15.00 | 09-28 15:30 | EXPENSE | — | `三点半`≠金额 |
| 22 | `１２．５ 打车` | 12.50 | 09-28 | EXPENSE | — | 全角 |
| 23 | `¥35 午饭` | 35.00 | 09-28 | EXPENSE | — | 符号 |
| 24 | `5号交话费 99` | 99.00 | 2026-09-05 | EXPENSE | — | 裸「N号」 |
| 25 | `借出 2000 给李四` | 2000.00 | 09-28 | TRANSFER/应收 | — | 不是收入 |
| 26 | `打八折 后 240` | 240.00 | 09-28 | EXPENSE | — | 折=修饰，不吞 240 |

负例（期望失败或低置信，不给金额）：

- `13800000000`（手机号，非金额）
- `3.14159`（超两位小数，`Money.parse` 已 HALF_UP；此处若句中无货币语境 → 低置信）
- `今天`（只有日期，金额缺失 → `Partial`）
- `帮我记一笔`（无内容 → `Failed`）

---

## 6. 「规则层做不到」的案例（写进 known-miss，别硬凑）

1. **省略式引用**：`跟昨天一样`、`老样子`、`再来一笔` —— 需要历史记忆，不是句内规则。
2. **一句话多笔**：`早饭10块午饭20打车30` —— 需要子句切分与多个 (金额,分类) 对齐；tier1 只能整句一笔记。
3. **算术/分摊**：`三个人AA 一共90`、`打八折后 240`（需算折后）、`满200减30` —— 需要计算与意图理解。
4. **范围与模糊量**：`三四十块`、`差不多一百`、`一百多` —— 落到单值需模型/交互；tier1 只能标 LOW 或给区间让用户选。
5. **错别字 / 拼音 / 谐音**：`夜肖`(夜宵)、`weixin`(微信)、`付了` 误写 —— 需模糊匹配或模型；规则层只做精确/包含匹配。
6. **无关键词的方向判定**：`星巴克 35` vs `星巴克退款 35`、单字 `还` 的 hái/huán 歧义 —— 词表面不足以判类型。
7. **跨域名词混淆**：`在工商银行交电费`（账户名 vs 商户名 vs 分类名同现）—— 需要真实词序/语义。

处理原则：这些进 `warnings`/`known-miss` 文档与测试的 `expected=Partial/LOW`，**不**写特判 hack。

---

## 7. 参考资料（URL，2026-09-28 抓取）

- cn2an（中文数字 ↔ 阿拉伯数字）：https://pypi.org/project/cn2an/ ；仓库 https://github.com/Ailln/cn2an
  - 依据：`strict/normal/smart/direct` 模式；`一二三→123`(normal)、`1百23→123`(smart)、`一点二三→1.23`、`一二点三零→12.30`(direct)；支持百分比；上界 `10^16`（千万亿）、下界 `10^-16`；`transform` 会做日期/分数上下文处理，README 自曝电话/日期误改风险。
- Duckling（Facebook，规则引擎）：https://github.com/facebook/duckling
  - 依据：`Duckling/Dimensions/ZH.hs` 仅注册 `Distance/Duration/Numeral/Ordinal/Quantity/Temperature/Time`（**无 AmountOfMoney**）；`AmountOfMoney` 只在 `Duckling/Dimensions/Common.hs`。
  - `Duckling/Time/ZH/Corpus.hs`（REF=2013-02-12 周二 04:30）：`上周二→02-05`、`上周日→02-10`、裸 `星期二→02-19`、`这周二→02-12`、`这周一→02-18`、`下周三→02-13`、`周一早上→04:00–12:00`、裸 `三点→15:00`、`今晚8点→20:00`、裸 `今天/昨天→00:00 Day`、`2月15号→2013-02-15`、`2/15` 为月/日。
  - README：*"The reference time for the corpus is Tuesday Feb 12, 2013 at 4:30am."*
- dateparser：https://pypi.org/project/dateparser/ ；文档 https://dateparser.readthedocs.io/
  - 依据：支持中文（changelog `Improved … Chinese` #1148/#910/#359 等）；设置 `RELATIVE_BASE`（相对基准）、`PREFER_DATES_FROM`（past/future/current_period）、`RETURN_AS_TIME_PERIOD`。
- JioNLP（中文 NLP 工具集）：https://pypi.org/project/jionlp/ ；`parse_time` / `extract_time`，wiki https://github.com/dongrixinyu/JioNLP/wiki/时间语义解析-说明文档
- HanLP：https://pypi.org/project/hanlp/
- LTP：https://pypi.org/project/ltp/
- jieba（仅分词）：https://pypi.org/project/jieba/
- 仓库内：`core/model/src/main/kotlin/com/han1997/vipledger/core/model/money/Money.kt:107`（`Money.parse` 能力与边界）、`core/model/.../LedgerEnums.kt`、`core/model/.../RuleEnums.kt`、`core/domain/.../calculator/RecurrenceCalculator.kt:127`（`daysUntilWeekday` 已有 ISO weekday 算法）、`docs/ARCHITECTURE.md` §6/§7、`docs/DATA-MODEL.md` 3.8/3.10、`.trellis/spec/android/testing.md`、`.trellis/spec/android/ai-ml.md`。

---

## 8. Caveats / 未核实

- 本轮**无通用 web 搜索工具**，只做了定向文档/包元数据抓取；任何「某库/某 App 的行为」若未列在第 7 节即视为未核实。闭源中文记账 App（随手记/网易有钱等）的快捷记账语法未取得一手文档，故本文不对其下结论。
- `这周X` / `下周X` 的语义在实现间不一致（Duckling ZH 的 `下周三` = 明天）。本文给出两种读法并要求项目**显式选定 + 写测试钉死**；第 5 节语料按「自然周读法 + 裸周X=下一个」编写。
- 时段区间（上午/中午/下午/凌晨）中标注「项目补」的是建议值，非抓取到的库行为；Duckling 仅一手证实「早上=04:00–12:00、晚上=18:00–00:00、周末=周五18:00–周一00:00」。
- `退款` 是否计 `INCOME` 还是冲减支出，取决于 `docs/DATA-MODEL.md` 第 5 节统计口径，本文按「映射 INCOME」建议，实现前需与统计口径对齐。
- 年份推断（只给月日取参考年、不做未来回退）是项目决策，Duckling 只提供「不跨年」的实测样例。
