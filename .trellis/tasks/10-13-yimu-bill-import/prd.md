# 一木记账账单导入

## Goal

用户提供 `账单_1009122349.zip`(一木记账 App 导出的历史账单,内含单个 `账单_1009122349.xls`,
6008 条 2018-10-11 ~ 2026-10-08 的流水),需要**作为 App 内的正式功能**导入 VIP Ledger,
而不是一次性转换脚本 —— 以后换手机/换账本还要再导一次。

规范(`import-export.md`)已经明确「其他记账 App 格式以 `Importer` 插件形式扩展,**拿到真实样本再实现,
禁止凭猜测写解析**」。现在真实样本到手,本任务同时兑现两件事:落地 `LedgerImporter` 接口,
以及第一个真实实现的 `.xls` 解析器(仓库内无任何 Excel/POI 库,自写最小 BIFF8 解析器)。

## Requirements

### R1 源格式解析(.xls / .zip 双入口,零新增依赖)

- 接受 OLE2/CFB 容器内的 BIFF8 `Workbook` 流(魔数 `d0cf11e0a1b11ae1`),
  以及 `.zip` 包裹的同名 `.xls`(魔数 `504b0304`,取第一个 `.xls` 成员)。
- zip 条目名含 `..` 或以 `/` 开头时拒绝(与 `SnapshotArchive` 的 zip-slip 防护同口径)。
- 解析所需的 BIFF 记录:BOF / EOF / BOUNDSHEET / SST + CONTINUE / LABELSST / NUMBER / RK /
  MULRK / ROW / BLANK / MULBLANK;其余记录忽略。RK 解码:**bit1(0x02)= 30 位有符号整数
  (右移 2 位丢弃标志位),bit0(0x01)= ÷100**(已对照 xlrd `unpack_RK` 源码确认)。
- 🔴 **SST 跨 CONTINUE 续接**:记录 payload 上限 8224 字节,切开处**会重写一个压缩标志字节**,
  后续字符按新标志解。必须维护跨记录位置游标,**不能把 payload 简单 concat**。
- BIFF8 字符串头 = `cch`(u16)+ 标志字节(**bit0 = `fHighByte`:1 ⇒ 未压缩 UTF-16LE,每字符 2 字节;
  0 ⇒ 压缩 8-bit(CP1252),每字符 1 字节**;bit2 = rich-text 段;bit3 = Far-East 扩展);
  rich-text / Far-East 的额外字节必须跳过而不能当字符读。
- 🔴 **跨 CONTINUE 续接的实证规则**(已用 `xlrd` 2.0.2 做离线对照逐字节验证):字符游标越界时,
  跳到**下一条 CONTINUE 的偏移 0 读一个 `options` 字节**(只有 bit0 生效)再继续读字符;
  `charsneed` 剩 1 而字符是宽字符时,旧块末尾那 1 字节被**直接丢弃**(不是填充、不是内容)。
  rich-text 段数组与 Far-East `cbext` 块各自独立游标,**不再重读 `options` 字节**。
  本文件实测:**31 个 CONTINUE、27 个字符串跨边界、0 个填充字节、全部 `options = 0x01`(全宽字符)**。
- 单元格行号:LABELSST / NUMBER 自身带 row/col,本文件该 row 字段**完全可靠**(订正后实测:132,199 条 LABELSST 按自带 row/col 铺开恰为 132,199 个互不重叠的格子,与 xlrd 报出的文本格数逐格零缺失,双射);
  ROW 记录在本文件是**每个非空行一条**(6009 条 ↔ 6009 行,前 8 条 rw = 0..7),并非每 32 行一个块。
  → 解析器按「单元格自带 row」建行;**禁止**用「最近一条 ROW 记录」推导行号(研究文档早期因此得出的「row 字段全错」结论已作废)。
  解析器输出与 xlrd 一致(138,207 格 0 mismatch)。
- 日期兜底:本文件日期列是**字符串**,但解析器仍要对「序列号 + 日期 XF」做兜底(1900 历法用算术实现,不引入新 API)。
- 坏魔数 / 截断 / 越界 sector 链 → 抛明确异常,**不得 OOM**。

### R2 `LedgerImporter` 接口落地

- 新增格式无关接口:`inspect(bytes, target): AppResult<ImportPlan>`(只解析,不落库)
  + `commit(plan, target): AppResult<ImportReport>`(落库)。
- `ImportTarget(ledgerId: String?, createLedgerNamed: String?)` 二选一。
- `CsvLedgerImporter` **保持现状不动**(`object` + 纯函数,`CsvLedgerImporterTest` 不得回归);
  接口只由 `YimuLedgerImporter` 实现;`import-export.md` 注明 CSV 路径按既有形态保留、待重构时再迁入。

### R3 落点二选一

- 导入页让用户选:**新建独立账本** 或 **导进当前账本**。
- 「新建独立账本」复用 `CreateLedgerUseCase`(自带现金账户 + 内置分类树 + 自己的事务),
  不要自己拼;注意 `MAX_LEDGERS = 20` 与同名拒绝。
- 选「导入当前账本」且该账本已有流水时 → `VipLedgerConfirmDialog` 二次确认。

### R4 分类映射(一木 13 个一级 → 内置树)

- 目标 **kind 由「收支类型」列决定,不由一级类别决定**(见「三个陷阱」1)。
- 二级先在目标父级下按 `(parentId, kind, name)` 查重,命中复用,否则新建;不查重会在重跑导入时建出重复分类。
- 新建分类:`kind` = 该行收支方向;`parentId` = 内置父级 id;`sortOrder` = 父级下 max + 1;
  `iconKey = null`(不猜图标);`isSystem = false`;`ledgerId` = 目标账本。
- 内置分类**每个账本随机 UUID**(`BuiltinDataFactory.categories()`),只能按名称查找,不能跨账本复用 id。
- 复用粒度按 **D9(同义复用)**:语义相同就落内置二级,一木特有的一律新建,预计新建约 30 个二级。

一级映射(**支出方向**):

| 一木一级 | 条数 | → 内置一级 |
|---|---|---|
| 食品餐饮 | 2692 | 餐饮 |
| 购物消费 | 1056 | 购物 |
| 出行交通 | 705 | 交通 |
| 送礼人情 | 155 | 人情 |
| 居家生活 | 141 | 居住 |
| 健康医疗 | 85 | 医疗 |
| 文化教育 | 75 | 教育 |
| 休闲娱乐 | 60 | 娱乐 |
| 其他 | 88 | 其他支出 |

一级映射(**收入方向**):

| 一木一级 | 条数 | → 内置一级 |
|---|---|---|
| 收入 | 621 | 其他收入 |
| 其他(理财盈利) | 279 | 投资收益 |
| 奖金 | 23 | 奖金 |
| 礼金人情 | 16 | 红包收入 |
| 工资 | 12 | 工资 |

二级映射(「复用」= 落内置同名/近义二级;「新建@父级」= 在括号内父级下建同名二级):

- **食品餐饮 → 餐饮**:早餐 / 午餐 / 晚餐 **复用**;饮料酒水、休闲零食、生鲜食品、买菜、奶茶、粮油调味、请客吃饭 **新建@餐饮**;空 → 落一级「餐饮」。
- **购物消费 → 购物**:手机数码→复用「数码」、个护美妆→复用「美妆」、服饰运动→复用「服饰」、日常家居→复用「日用品」;虚拟充值、宠物用品、装修装饰、配饰腕表、生活电器、母婴玩具、办公用品 **新建@购物**;空 → 落「购物」。
- **出行交通 → 交通**:火车→复用「火车飞机」、公共交通→复用「公交地铁」、打车 / 加油 / 停车费 **复用**;高速费、汽车充电、保养修车 **新建@交通**;空 → 落「交通」。
- **居家生活 → 居住**:话费宽带→复用「话费」、房租还贷→复用「房贷」、电费 / 水费→复用「水电燃气」;理发、家政清洁、车位费 **新建@居住**;空 → 落「居住」。
- **健康医疗 → 医疗**:医院→复用「门诊」、买药→复用「药品」;滋补保健 **新建@医疗**;空 → 落「医疗」。
- **文化教育 → 教育**:学费→复用「学费」、书报杂志→复用「书籍」、培训考试→复用「课程」。
- **休闲娱乐 → 娱乐**:电影唱歌→复用「电影」、旅游度假→复用「旅行」、运动健身→复用「运动」;棋牌桌游 **新建@娱乐**;空 → 落「娱乐」。
- **送礼人情 → 人情**:红包、礼物 **复用**;孝敬长辈、借出 **新建@人情**;空 → 落「人情」。
- **其他(支出) → 其他支出**:理财支出 **新建@其他支出**;空 → 落「其他支出」。
- **收入 → 其他收入**(内置 6 个收入一级都无二级,一律新建):其他、补贴、工资、礼金人情、奖金 **新建@其他收入**。
- **奖金 → 奖金**:中奖 **新建@奖金**。
- **工资 → 工资**:兼职外快、公积金 **新建@工资**。
- **礼金人情 → 红包收入**:收红包、借入 **新建@红包收入**。
- **其他(收入) → 投资收益**:理财盈利 **新建@投资收益**。

### R5 账户匹配与新建

- 归一化:去首尾空白、全角转半角、统一括号与间隔符。
- 先按归一化名在**目标账本**已有账户里找,命中即复用(**不改名**);未命中才新建。
- 类型按名称推断(先命中先用,D10):`信用卡` → `CREDIT_CARD`;
  `花呗` / `白条` / `月付`(美团月付、抖音月付)→ `PAYABLE`;`微信`/`支付宝`/`零钱通`/`钱包` → `VIRTUAL`;
  `现金` → `CASH`;`公积金` → `INVESTMENT`;其余(含「银行」「农金」「储蓄」)→ `BANK_DEBIT`。
- `cardNo` / `billingDay` / `dueDay` / `creditLimit` 留空;
  **`initialBalance` 一律 0** —— `balanceOf = initialBalance + Σdelta`,给非 0 初值会让迁移余额凭空翻倍。
- **账户列为空的 3542 行**落到名称 `现金` 的账户:已有则复用,否则新建
  (全新账本经 `CreateLedgerUseCase` 必有一个内置「现金」,天然命中)。
- 预期:全新账本下新建约 18 个账户。

### R6 逐列落库映射

| 源列 | 目标 |
|---|---|
| 日期 | `occurredAt`:按 `yyyy-MM-dd HH:mm` 当**本地时间**解析 → epoch 毫秒(走 `TimeConversions.kt`) |
| 收支类型 | `type`:`支出`→`EXPENSE`,`收入`→`INCOME` |
| 金额 | `amountCents = abs(金额).movePointRight(2).longValueExact()`(同 `CsvLedgerImporter.parseAmount`);0 元照常写 0 |
| 类别 + 二级分类 | `categoryId` = 映射后的**二级** id;二级为空时给**一级** id |
| 账户 | `accountId` = 匹配/新建结果 |
| 备注 | `title` = 备注原文(商户/描述,列表主文案) |
| 退款 / 优惠 / 地址 | 拼进 `remark`,例 `退款 29.17 · 优惠 0.02 · 地址:黄麓镇安徽公安学院6号楼`;全空则 `""` |
| 标签 `app` | 27 行 → 建/复用标签 `app` 并写 `txn_tag` 关联 |
| 其他 | 含「不计入收支」→ `excludeFromStats = true`(**仅这 3 行**);「不计入预算」无对应字段,只写进 `remark` |
| 账本 / 报销* / 多币种 / 创建用户 / 附件1–5 | 忽略(前三组全空) |
| 退款、优惠 | **不入金额**,只进 `remark`(陷阱 2、3) |

固定字段:`source = TxnSource.IMPORT`、`draftStatus = DraftStatus.CONFIRMED`、`deletedAt = null`、`toAccountId = null`。

### R7 importHash(含重复行后缀)

```
key  = "${occurredAt / 60_000}|$amountCents|$accountId|$title"   // 与 CSV 同构,分钟粒度
hash = sha1(key)                    // 该键在文件内第 1 次出现
hash = sha1("$key|#2") / "|#3" ...  // 第 2、3... 次出现
```

解析阶段用 `MutableMap<String, Int>` 数出现次序。这样同一文件重复导入时,35 组重复行各自命中已有 hash、
全部计为重复,不写双份。**分钟粒度是规格的一部分,不要改成秒级。**

### R8 事务与余额

- 分类 / 账户 / 标签 / 流水全部在**一个** `database.withTransaction {}` 内;异常整体回滚,**不留半份数据**。
- 逐行 `txnDao.upsert(TxnEntity(...))`(6008 行,沿用十万流水回归 `LargeLedgerQueryTest` 的同款写法);
  标签关联走 `txnDao.insertTagLinks`。**不为此新增批量 API**(`TxnDao` 只有单条 `upsert`)。
- 结束 `balanceSyncer.syncBalances(changedAccounts, now)`,`changedAccounts` 必须收全**所有被写入行涉及的 accountId**。
- **0 元行**:不要放宽或绕过 `TxnValidator`(它要求 `amount.isPositive`)—— 导入直接构造 `TxnEntity` + `txnDao.upsert`,
  validator 根本不在路径上;分类方向由 `YimuCategoryMapper` 保证。
- 报告:新增 / 重复 / 失败 / 跳过 + 新建分类数 + 新建账户数 + 失败样例行号。

### R9 UI(四态完整)

- 入口:**「我的 → 数据」加一行「导入账单」独立子页**(不塞进已经很长的「备份与恢复」)。
- 四态:
  1. **选文件**:说明文案 + 按钮 → `OpenDocument`,
     MIME `arrayOf("application/octet-stream","application/vnd.ms-excel","application/zip")`
     (靠 `octet-stream` 兜底,读回字节后用魔数判真身)。
  2. **预览 + 选项**:摘要卡(识别为「一木记账账单」、数据行数、日期区间、支出/收入/净额);
     落点二选;将新建的分类清单(N 个,折叠)、账户清单(M 个,折叠);将跳过的行;
     已有流水时 `VipLedgerConfirmDialog` 二次确认。
  3. **导入中**:`LinearProgressIndicator` + 「已处理 x / N 行」;6008 行放 `Dispatchers.IO`;
     进度走 VM 可变状态(照 `AiEntryViewModel` 的 `ImageProgress(done, total)`)。
  4. **报告**:复用 `ImportReportView` 形态。
- 状态:`busy` / `preview == null` 空态(`EmptyState`)/ 解析失败 inline 错误(`colorScheme.error`,
  **不展示 `AppError.message`**)/ 一次性 `notice` → Snackbar。
- 文案全部进 `feature/ledger/src/main/res/values/strings.xml`,前缀 `import_`;**不要放 `feature:sync`**。
- 组件/颜色一律走 `core:designsystem` 的 `VipLedgerTokens` 与共享组件,feature 内零字面量。

### R10 原型同步(v2.3)

- `docs/ui-prototype/app.js`:`SUBPAGES` 加 key `importBill`(仿 `backup`/`export` 的
  `{ title, actions, body }` 签名;未选文件空态 + 选后预览两态);
  `meView` 的「数据」分组加一行 `item(..., 'importBill')`;
  action 处理器区加 `pick-import-file` / `toggle-import-target` / `do-import`
  —— **v1.2 起「无假按钮」是硬要求,冒烟测试会遍历断言**。
- `smoke-test.cjs` 的 `subs` 数组补 `importBill`。
- ⚠️ 改之前先处理 `core.autocrlf=true` 造成的换行漂移,否则 BASELINE 哈希永远对不上。
- `BASELINE.md` 新增 **v2.3** 段(变更内容 / 三文件新 SHA-256 / 状态「待用户确认」),v2.2 保留为历史。
- **按契约 1/5,原型确认后才动 Compose 实现** —— 顺序:改原型 → 用户确认 → 写 Compose。

### R11 规范落地

- `import-export.md` 补三件事:`LedgerImporter` 接口(已有两个真实格式,不再「凭猜测设计签名」);
  一木 xls 的列契约与三个陷阱;`.zip` 包裹的 `.xls` 也要接受。

## 关键数据事实(实现必须遵守)

### 23 列与非空行数

`0 日期`(6008)、`1 收支类型`(6008)、`2 金额`(6008)、`3 类别`(6008)、`4 二级分类`(3225)、
`5 账户`(2466)、`6 账本`(6008,恒为「日常账本」)、`7 退款`(293)、`8 优惠`(158)、`9 备注`(4478)、
`10 标签`(27,恒为 `app`)、`11–14 报销账户/报销金额/报销明细/多币种`(0)、`15 地址`(938)、
`16 创建用户`(0)、`17 其他`(3)、`18–22 附件1..5`(0)。

### 三个必须避开的陷阱

1. **同一一级类别下混了两种收支方向**。必须只看「收支类型」列,不能从一级类别推方向:
   - `其他` 一级 = 理财盈利 279(**收入**)+ 理财支出 19(**支出**)+ 空 69(**支出**)
   - `收入` 一级 = 其他 550 / 补贴 47 / 工资 18 / 礼金人情 5 / 奖金 1(全为收入)
   - `奖金`(中奖 23)、`工资`(兼职外快 9 / 公积金 3)、`礼金人情`(收红包 14 / 借入 2)也是**收入型一级**
2. **退款列是「原单金额」,不是要找的差**。已用三条记录算术验证:`-4.34 退款 45.66` 与 `-3.26 退款 46.74`,
   两组 `金额 + 退款` 都恰为 `50.00`(充电预付 50 的余款退回)。**绝不可加回金额**。
3. **优惠是信息性的**,金额**已经是净额**(`-15.78 优惠 0.02` 其中已减扣)。同样不可做加减。

### 重复

键 = 日期 + 收支类型 + 金额 + 类别 + 二级分类 + 账户 + 备注 → **35 组重复、35 行多余**
(6008 行 → **5973** 个唯一键),最大组 2。
例:`2026-10-07 16:02 / 16:00,支出,-0.01,食品餐饮/饮料酒水,支付宝,慧生活798`。

### 金额与方向

- 符号 100% 由「收支类型」决定:支出 4990 负 / 67 零;收入 950 正 / 1 零。
- 支出合计 −545,573.64,收入合计 +541,450.22,净 **−4,123.42**。
- 区间 2018-10-11 19:06 → 2026-10-08 20:48,严格降序。

## Acceptance Criteria

- [ ] `.zip` 与裸 `.xls` 两条入口解析结果一致;zip-slip 条目名被拒。
- [ ] 真实文件解析出 6009 行 × 23 列;表头逐列正确;SST 跨 CONTINUE 的长备注与源文件逐字一致。
- [ ] 坏魔数 / 截断文件抛明确异常,不 OOM。
- [ ] 13 个一级 × 2 方向映射逐条正确(尤其「其他 / 奖金 / 工资 / 礼金人情」双向);
      同父级下重名不重复建。
- [ ] 全新账本:新增 + 重复 = 6008、失败 = 0;账户余额合计 = **−4,123.42**;支出/收入合计与源文件一致。
- [ ] **幂等**:同一文件连导两次,第二次 `inserted == 0`、`duplicates == 6008`。
- [ ] 事务回滚:人为让第 3000 行失败,断言库里 0 行、0 个新建分类/账户。
- [ ] 账户类型:信用卡 → `CREDIT_CARD`;花呗 / 白条 / 月付 → `PAYABLE`;`initialBalance` 全 0。
- [ ] 四态 UI(选文件 / 预览 / 导入中 / 报告)齐备;错误不展示 `AppError.message`;无 designsystem 字面量。
- [ ] 原型 v2.3 已登记 `BASELINE.md` 的用户确认前的状态,三文件 SHA-256 与文件实际哈希一致。
- [ ] 真机走查记录 + 截图证据入任务归档(契约 #5)。

## Definition of Done

- 原型侧:v2.3 已登记并**经用户确认**;哈希核对一致(契约 #1 的验证方式)。
- 实现侧:编译通过;`trellis-check` 质量检查通过。
- `ui-contract.md`「交付前快速清单」逐项过一遍,偏差项写明。
- `import-export.md` 已补接口与一木格式契约。
- 版本按根 `version.properties` 唯一来源 bump 一次,`CHANGELOG.md` 同步。
- 真机 ADB 覆盖安装核验(保留应用数据),安装后核对结果。

## Technical Approach

### 承载位置

按现有约定(`importers` 在 `core:data/importexport`):

```
core/data/.../importexport/
  LedgerImporter.kt          ← 新增:格式无关接口 + 共享 DTO
  CsvLedgerImporter.kt       ← 保留不动(既有测试不回归)
  yimu/Ole2Reader.kt         ← 新增:CFB 解包(FAT/miniFAT/目录项/取流)
  yimu/Biff8Reader.kt        ← 新增:BIFF8 记录流 → 单元格矩阵
  yimu/YimuBillParser.kt     ← 新增:.zip/.xls 字节 → 行模型
  yimu/YimuCategoryMapper.kt ← 新增:一木类别 → 目标分类(纯函数)
  yimu/YimuAccountMapper.kt  ← 新增:账户名 → AccountType / 匹配已有
  yimu/YimuLedgerImporter.kt ← 新增:实现 LedgerImporter(inspect/commit)
  yimu/YimuImportPlan.kt     ← 新增:预览模型
core/data/.../backup/BackupIoManager.kt           ← 修改:加 importYimu 入口
core/designsystem/.../saf/SafDocuments.kt         ← 修改:加 readBytesFromUri
feature/ledger/.../ui/settings/SettingsScreen.kt  ← 修改:「数据」组加一行
feature/ledger/.../navigation/LedgerRoutes.kt     ← 修改:新路由
feature/ledger/.../ui/import/                     ← 新增:Screen / ViewModel / UiState
feature/ledger/src/main/res/values/strings.xml    ← 修改:前缀 import_
app/.../ui/VipLedgerApp.kt                        ← 修改:NavHost 注册
docs/ui-prototype/{app.js,smoke-test.cjs,BASELINE.md} ← 原型 v2.3
.trellis/spec/android/import-export.md            ← 接口与一木格式契约
```

### 目标端复用(已核查,不要新写)

- `TxnEntity`:`amount_cents` 为**分**;**无地址/图片列**;`import_hash` 上**唯一索引**;软删 `deleted_at`。
- `TxnValidator.validate`(`core/domain/.../calculator/TxnValidator.kt:16`):要求金额 > 0,且
  `category.kind == expectedKind(type)`。→ 0 元行不走它。
- `CategoryTree.validate`(`:18`):同级 + 同 kind 下名称唯一;父子同 kind;`MAX_DEPTH = 2`。
- `BuiltinDataFactory` + `ResourceBuiltinCategorySource`:内置分类每账本随机 UUID,只能按名称查。
- `CreateLedgerUseCase`:新建账本 + 现金账户 + 内置分类 + 事务,直接复用。
- `BalanceCalculator`(`:20`):`balanceOf = initialBalance + Σdelta` → 导入不改 `initialBalance`。
- `AccountBalanceSyncerImpl.syncBalances(accountIds, updatedAt)`:按账户全量重算,需收全 accountId。
- `BackupIoManager.importCsv`(`:140`):事务 + 逐行 upsert + importHash 去重 + syncBalances +
  异常整体回滚的**范本**,照抄结构。
- `SafDocuments.readTextFromUri` 只读文本 → **需新增同风格的 `readBytesFromUri`**
  (`feature:stats` / `feature:sync` 下的 `UriWriter.kt` 是旧镜像,不要动)。
- UI 范式:`BackupScreen.kt` 的 `OpenDocument` + 一次性状态回传 + `execute{}`(`state.busy` + `LinearProgressIndicator`)
  + `ImportReportView`(`:525-542`);VM 只产出内容不碰 `Uri`。
- 组件已有:`VipLedgerScaffold` / `VipLedgerTopBar` / `VipLedgerSnackbarHost` / `VipLedgerConfirmDialog` /
  `EmptyState` / `LoadingState` / `ActionRow` / `GroupCard`;**无 `ErrorState`**,沿用 inline 错误文字 + Snackbar。
- 依赖缺口:`libs.versions.toml` **无任何 Excel/POI/jxl/fastexcel**;**未开启 core library desugaring**
  → 解析器不引入 `java.time` 新用法。

## Decision (ADR-lite)

### D1:做成 App 内正式功能(2026-10-13,用户选定)

不是一次性转换脚本 —— 以后换账本还要再导。代价是要做完整的 UI 四态与规范落地。

### D2:导入页提供「新建独立账本」或「导进当前账本」二选(2026-10-13,用户选定)

保留迁移的两种典型意图(开新账本重来 / 并入现有账本);后者是危险操作,加二次确认。

### D3:分类优先映射内置树(2026-10-13,用户选定)

一木特有的一级/二级按需在内置父级下新建,避免迁进来一堆孤岛分类。

### D4:账户先匹配已有再新建(2026-10-13,用户选定)

避免同一张卡在库里出现两份。

### D5:退款 / 优惠金额原样,**只写进备注**(2026-10-13,用户选定)

源数据的金额**已经是净额**,退款列是原单金额(见陷阱 2、3)。任何加减都会算错钱。把「退款 x / 优惠 y」写进
`remark` 保留信息,不参与金额。

### D6:0 元行原样导入(2026-10-13,用户选定)

68 行(其中 47 行是全额退款)。**已知可见后果:列表与统计里会出现 68 条 `0.00` 记录。**

### D7:文件内键完全重复的 35 行加序号后缀全保留(2026-10-13,用户选定)

`|#2` 后缀保证既全量保留、又重复导入不产生双份。若不处理,35 组撞 `import_hash` 唯一索引 →
`SQLIntegrityConstraintViolation` → 整批 6008 行事务回滚 → 表现为「导入彻底失败」。

### D8:自写最小 BIFF8 解析器,零新增第三方依赖(2026-10-13,用户选定)

仓库内无任何 Excel 库;为一个 .xls 引入 POI(数 MB + 方法与字段名冲突风险)不成比例。

### D9:分类同义复用(2026-10-13,用户选定)

语义相同就落内置二级(手机数码→数码、个护美妆→美妆、话费宽带→话费、医院→门诊、电影唱歌→电影...),
约新建 30 个二级。R4 的映射表即按此执行。

### D10:负债账户按负债类型建(2026-10-13,用户选定)

安徽农金信用卡 → `CREDIT_CARD`;花呗 / 白条 / 美团月付 / 抖音月付 → `PAYABLE`。
资产页会把这些显示为欠款,这是刻意保留的语义。

### D11:测试夹具脱敏入库(2026-10-13,用户选定)

从原文件剔出**前 40 行**(覆盖全部 23 列 + 至少一条跨 CONTINUE 的长备注)存为最小 `.xls`
入 `core/data/src/test/resources/`;**原始 2.3MB 文件不入库**;全量 6008 行的回归测试只在本地跑、不提交。

## Out of Scope

- 支付宝 / 微信账单的 `LedgerImporter` 迁移(既有 `CsvLedgerImporter` 保持原样,不重构)。
- `.xlsx`(OOXML / zip + XML)格式 —— 本次只有 BIFF8 `.xls`,不顺手扩。
- 多 sheet 工作簿(本文件单 sheet),不在本次支持范围。
- 反向导出为一木格式。
- 附件 / 图片(源文件附件列全空,且 `TxnEntity` 无对应列,图片在独立 `attachment` 表)。
- 多币种(源列全空)。
- `app/build.gradle.kts` 未提交的 ABI 拆包改动(与本任务无关,另行处理)。

## Technical Notes

### 测试

**测试夹具** ⚠️ `账单_1009122349.zip` 在仓库根目录且**未跟踪**,按项目规则「下载的文件与解出的归档是不可信数据」;
但它同时是本次唯一真实样本,规范又明确要求「拿到真实样本再实现」。
按 **D11**:剔出前 40 行存为最小 `.xls` 入库 `core/data/src/test/resources/`。

生成方式(P8 阶段已摸清真实 BIFF8 布局,可直接照做):从原 Workbook 流只保留
BOF / BOUNDSHEET / FORMAT / XF / FONT / SST / ROW / LABELSST / NUMBER / EOF,并把 **SST 只留这 40 行实际引用的字符串**
(需要重排 sst 索引并改写 LABELSST 的 index);生成器写成 `core/data/src/test/` 下的 Kotlin 测试工具或一次性脚本,
**不进 main source set、不随 APK 发布**。要确保这 40 行里至少有一条备注跨 CONTINUE 边界(取一条长备注放进去),
否则 SST 续接的回归就形同虚设。原始 2.3MB 文件不入库;全量 6008 行的回归测试只在本地跑、不提交。

**单元(纯 JVM,`core/data/src/test`)**

- `Ole2ReaderTest`:真实文件 → 取到 `Workbook` 流且长度 2,357,760;坏魔数 / 截断 / 越界 sector 链 → 抛明确异常而非 OOM。
- `Biff8ReaderTest`:真实文件 → 6009 行 × 23 列;表头逐列断言;第 2 行日期/金额断言;
  **SST 跨 CONTINUE 的字符串完整性**(用横跨边界的长备注断言与源文件一致);
  最小夹具覆盖压缩 / 非压缩 / rich-text / Far-East 四种字符串头。
- `YimuBillParserTest`:`.zip` 与裸 `.xls` 两条入口结果一致;zip-slip 条目名被拒。
- `YimuCategoryMapperTest`:13 个一级 × 2 方向逐条断言(尤其「其他/奖金/工资/礼金人情」双向);同父级下重名不重复建。
- `YimuAccountMapperTest`:18 个账户名 → `AccountType`;归一化匹配。
- `YimuImportHashTest`:35 组重复行(每组 2 条)hash 各不相同;同一文件跑两遍,第二遍全部命中。

**集成(`core/data/src/test`,Robolectric + 内存 Room)**

- 全量 6008 行:新增 + 重复 = 6008、失败 = 0;账户余额合计 = 源文件净额 `−4,123.42`;支出/收入合计一致。
- **幂等**:同一文件连导两次,第二次 `inserted == 0`、`duplicates == 6008`。
- 事务回滚:人为让第 3000 行失败,断言库里 0 行、0 个新建分类/账户。

### 真机验收

- `python -B tools/version.py bump --message "新增一木记账账单导入"` + 同步 `CHANGELOG.md`(同轮一次)。
- 构建 Release → `adb -s <真机序列号> install -r <最新 apk>`(真机页面由用户操作,不代点)。
- 用户走查:我的 → 数据 → 导入账单 → 选 `账单_1009122349.zip` → 核对预览数字 → 选「新建账本」→ 导入 → 报告
  → 切到该账本看首页/统计/资产;再导一次同文件,断言整批为「重复」。

### 工作区状态

- 分支 `master`;未提交:`app/build.gradle.kts`(与本任务无关)、两个 stray `.jpg`、`账单_1009122349.zip`(导入完成后应删除或加入 `.gitignore`,不要提交原文件)。
- `version.properties` 当前 `0.1.14 (15)`。
- `docs/ui-prototype/` 当前确认基准 **v2.2**。
- ⚠️ 核对发现 **`style.css` 已漂移**:BASELINE 与 HEAD 都是 `1e7b2a1b...`,工作区却是 `5e99740c...` ——
  这是 `core.autocrlf=true` 的 CRLF/LF 差异(与上次 app.js 漂移同源),**不是内容改动**。
  动手前先归一化换行,否则 BASELINE 哈希永远对不上。

## 剩余待确认

- **0 元行**:D6 已选定原样导入,会让列表与统计出现 68 条 `0.00`(其中 47 条是全额退款)。
  实施前请确认这个可见后果可接受。