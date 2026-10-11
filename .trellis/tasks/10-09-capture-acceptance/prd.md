# P6 自动记账与平台验收

Status: code-side slices delivered (entries + dedup sharing + platform records); on-device platform acceptance pending a connected device.

保留通知/无障碍/OCR，完善候选/已入账/忽略入口。截图与自动采集共享可靠凭据判重，疑似重复人工处理；固定账本、绑定、来源、账目编辑保持。微信/支付宝/京东/拼多多/淘宝分别记录真实通过条件。历史/冲突/缺绑定/退款关系不明继续待确认。用户操作真机页面与支付，合成样本不代表平台验收。

## 本轮交付（代码侧，用户确认范围）

### 入口完善
- 「识别记录」区新增状态筛选 chip：`候选 (n) / 已入账 (n) / 已忽略 (n)`（LINKED 计入已入账），默认候选。
- 已忽略候选可「重新确认」（`AutoBookRepository.reopen`：IGNORED → PENDING，凭据不变、重放仍不入账，可正常确认入账）。

### 判重共享（双向互查，均只转人工）
- capture 侧：`nearAiSourceEntry` 查同账本同日（±1 天容差）同额、方向一致的 `source='AI'` 账目（草稿 PENDING 或已确认、未删除）→ 候选标 `DUPLICATE` 待确认，`duplicateTxnId` 指向该账目，自动入账被挡。
- AI 草稿侧：落库前 `hasSimilarCandidate(ledgerId, 日界零点, 金额, 类型)` 查同日 PENDING/POSTED 候选（方向未知的候选也算同向）→ 草稿标「疑似与自动采集重复」提示，编辑金额/类型后重算；不阻塞确认。
- 自动入账账目 `source=AUTO`，与 AI 过滤互不污染；不自动合并、不自动丢弃。

### 平台验收记录
- 新增 `docs/AUTOBOOK-PLATFORMS.md`：五平台各自的识别路径、真实通过条件、已验证结论与待验证项（微信最终验证/京东精度/淘宝未开始等），README 链接；后续真机验收按此更新。

### 真机验收（待设备连接）
- 无 ADB 设备连接，本轮未执行真机走查；待连接后按 docs/AUTOBOOK-PLATFORMS.md 的待验证项补验收，不阻塞已交付代码。

See docs/FEATURE-ROADMAP.md for shared contracts, acceptance and delivery requirements.

## What I already know（现状研究）

### 已有能力（M5/M6 已交付）
- 采集链路：通知监听 / 无障碍页面 / 本机 OCR → `RoomAutoBookRepository.capture()` → 候选（`PENDING/POSTED/IGNORED/LINKED`）。
- 判重凭据（`auto_evidence`，账本隔离）：可靠单号键（应用+类型）、event 键、方向无关键、不完整补全键；同额同方向近邻 → `ReviewReason.DUPLICATE` 待确认；确认/忽略/关联后重放不入账。
- 自动记账页（`AutoBookScreen`）单页：权限/启用应用/绑定分组（默认收起）+「记录」列表：PENDING 内联展开确认表单（类型/金额/商户/账户/目标/退款原单 + 关联既有账目），POSTED/LINKED/IGNORED 显示状态与「编辑账目」入口。
- 平台验证现状（README 2026-10 设备测试）：支付宝/拼多多流程通过；京东 OCR 临时接受（精度受限）；微信待最终实field验证；淘宝未开始。真机页面由用户操作，合成样本不算验收。

### P6 目标 vs 差距
1. **完善候选/已入账/忽略入口**：现状全部候选混在一个列表；「完善入口」的具体形态待定（分组/筛选/跳转）。
2. **截图与自动采集共享可靠凭据判重**：P5 图片切片（AI 页 OCR 草稿）走 `NlEntryPipeline → PENDING 账目`，完全不经过 `auto_evidence` 判重——同一笔支付可能被「自动采集候选」和「截图草稿」双记。需要跨入口共享判重，疑似重复交人工处理。
3. **分平台真实通过条件记录**：结构化记录五平台各自的验收条件与结论（现在散落在 README）。
4. **真机验收**：用户操作真实支付页面；当前无 ADB 设备连接。

## Decisions

- **本轮只做代码侧**（用户确认）：完善候选/已入账/忽略入口 + 截图↔自动采集判重共享 + 五平台通过条件结构化记录；真机走查记录待办条件，设备连接后补验收轮。
- **判重共享采用双向互查**（用户确认）：① capture 侧 `nearby` 范围扩大到同账本近期 AI 来源草稿账目，命中即标 `DUPLICATE` 待确认（复用现有机制，自动入账被挡住）；② AI 草稿落库时查同账本近期自动采集候选，命中则在草稿上标「疑似与自动采集重复」提示，人工决定；不自动合并、不自动丢弃。
- **入口形态采用状态筛选 chip**（用户确认）：「记录」区顶部 `候选 (n) / 已入账 (n) / 已忽略 (n)` 三个 FilterChip，默认候选；已忽略视图提供「重新确认」（置回 PENDING，凭据不变、重放仍不入账）。

## Open Questions

- [ ] 本轮范围：无真机连接时，代码侧（入口完善+判重共享+条件记录）与真机验收如何分配？
- [ ] 判重共享的机制形态（候选侧/草稿侧/双向，交互如何呈现疑似重复）？
- [ ] 候选/已入账/忽略入口的形态（分组 vs 筛选 vs 现状微调）？
