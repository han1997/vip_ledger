# M6 AI 自然语言记账

> 需求走查记录：2026-09-28。依据 `docs/PLAN.md`、`docs/ARCHITECTURE.md`（§6 AI 管线、§7 端侧分类器）、`docs/DATA-MODEL.md`（3.8 `merchant_rule`、3.10 `ml_training_sample`）与现有代码结构交叉核对。用户要求按里程碑继续推进；本文把仓库已有决策作为范围依据，不将其表述为本轮逐项口头确认。

## Goal

让用户用一句自然语言（例如「昨天午饭 35 块 微信」）就能记账：先离线解析出结构化字段，落成待确认草稿，用户确认或修正后入库；每次修正沉淀为训练样本，为 M7 的端侧分类器供料。云端解析可选且默认关闭。

## What I already know

- M0–M5 已交付。`docs/ARCHITECTURE.md` §6 已规定三级回退管线：规则层 → 端侧分类器 → 云端 LLM（可选，默认关闭联网），任何一级失败降级到下一级。
- 数据层在 M1 已铺好，M6 只需接引擎与界面：
  - `merchant_rule` 表 + `MerchantRuleDao` + `MerchantRuleRepositoryImpl` + 领域模型 `MerchantRule`（其 KDoc 明确写「匹配引擎在 M6，本层只负责存取」）。
  - `ml_training_sample` 表 + `MlTrainingSampleDao` + `MlSampleRepositoryImpl`。
  - `DraftStatus { CONFIRMED, PENDING, DISCARDED }` 已是 `TxnEntity.draftStatus`（默认 CONFIRMED），`TxnDao.updateDraftStatus` 已存在。
  - `TxnSource { MANUAL, RECURRING, AI, IMPORT, RULE }` 已定义。
- 模块骨架已就位：`:feature:ai` 只有 `build.gradle.kts`，其依赖已声明 `project(":ml:classifier")`；`:ml:classifier` 只有 `build.gradle.kts`（无源码）。
- 既有可复用模式：
  - `:feature:ledger` 已有 `DraftListScreen/ViewModel`（M4 周期记账的待确认草稿），可参照其用例与 UI 模式。
  - 导航：各 feature 暴露 `*Routes` + graph，在 `app/.../ui/VipLedgerApp.kt` 用 `onOpenXxx` 回调装配。
  - M5 的 `EncryptedSettingsStore`（AndroidKeyStore + AES/GCM，`shared_prefs/backup_settings.xml` 存加密 payload）可直接复用来加密保存 LLM `apiKey`。
  - M5 的 `:core:data` 已引入 OkHttp（手写 WebDAV），云端调用可复用同一 HTTP 栈，不必新增依赖。

## Assumptions

- 云端接口按 OpenAI 兼容 `POST {baseUrl}/chat/completions` 实现，严格 JSON 出参 + 解析失败重试一次（§6 已定；wire 机制按 `research/llm-structured-output.md` 的降级阶梯落地）。
- 草稿复用 `txn` 表的 `draft_status = PENDING`，不新建表。
- 验收关注（`docs/PLAN.md` M6 行）：断网时走本地规则仍能记账；云端解析失败可回退。

## Decision (ADR-lite)

### D1：第 2 级端侧分类器在 M6 预留接缝

**Context**：ARCHITECTURE §6 的三级回退中，第 2 级（端侧分类器）按 `docs/PLAN.md` M7 行与 `docs/DATA-MODEL.md` 3.10 归属 M7；但 `:feature:ai` 的 `build.gradle.kts` 已经声明 `project(":ml:classifier")`，模块图已按「存在分类器」预设。若 M6 不占位，M7 需回头改动管线与草稿候选的数据结构。

**Decision**（2026-09-28 确认）：M6 预留接缝——在 `:core:domain` 定义 `CategoryClassifier` 接口与管线调用点，`:ml:classifier` 提供返回空候选的实现。阈值、top-3 候选等管线形状在 M6 定型并有单测覆盖；M7 只替换实现，不改管线。

**Consequences**：M6 多出接口 + 空实现 + 单测；换来 M7 无返工，且「分类器低于阈值不出建议」这条行为在 M6 就能被测到。空实现必须明确是占位（返回空列表），不得伪装成可用分类器而干扰规则层判定。

### D2：AI 入口是 `:feature:ai` 的独立页

**Context**：`.trellis/spec/android/architecture.md` 规定「`feature:*` 之间禁止互相依赖（需要共享时下沉到 `core:domain` 或 `core:designsystem`）」。在 `:feature:ledger` 的记一笔页内联自然语言输入，会迫使输入组件下沉 `core:designsystem`、解析用例下沉 `core:domain`，维护面更大。

**Decision**（2026-09-28 确认）：`feature:ai` 自持 `AiRoutes` + `aiGraph()`，首页通过 `onOpenAi` 回调进入（与 M5 的 `onOpenBackup` 同构）。解析、候选选择、草稿确认、商户规则管理都收敛在这一页内。

**Consequences**：与既有导航装配模式一致，零跨 feature 依赖。AI 生成的草稿同时会出现在既有「待确认」列表（M4 的 `DraftListScreen`），因此确认入口有两处——需要在实现时确保两处读同一份 `draft_status = PENDING` 数据，不出现两套口径。

### D3：隐私开关是全局总开关，默认关

**Context**：ARCHITECTURE §6 已定「云端 LLM（可选，默认关闭联网）」。剩下的自由度是开启后的外发是否逐次确认。

**Decision**（2026-09-28 确认）：一个全局「允许联网解析」开关，默认关。关着时云端那一级完全不调用（不发起请求、不走网络），只走规则层与端侧分类器接缝。打开后不再逐次询问；云端在规则层产出不足时补位，具体触发条件见 D7。

**Consequences**：记账路径最短；开关打开后并非每句都外发，而是**规则层产出不足时**才把该句发往用户配置的端点，因此开关附近的文案应写清「会在需要时把你输入的账目文字发送到该地址」，且 `apiKey` 与请求正文都不进日志。

### D4：云端客户端的落位

**Context**：`:feature:ai` 目前不依赖 `:core:data`（也不应依赖——architecture.md 要求 feature 只依赖 `core:*`，且 `core:*` 内部只允许向内依赖）。云端调用需要一个 HTTP 客户端。

**Decision**（走查结论，依据 `architecture.md` 现成规则，非用户偏好）：接口（`LlmParserClient` 之类）放 `:core:domain`，实现放 `:core:data`，复用 M5 已在用的 OkHttp 栈与错误收敛方式；`:feature:ai` 只依赖 `:core:domain` 的接口。与 `MerchantRuleRepository` / `MlSampleRepository` 的既有分层完全一致。

**Consequences**：不新增依赖、不引入 OpenAI SDK / Retrofit；云端实现可在 JVM 单测里用 MockWebServer 直接验，不依赖 Android 框架。

### D5：M6 纳入三项扩展（多笔 / 学习规则 / 未识别明示）

**Context**：走查扩张扫掠时识别出三项可从 M6 起做、且能减少后续返工的能力。

**Decision**（2026-09-28 确认）：
1. **一句话可拆多笔**——规则层的 Span 表架构天然支持多个 `AMOUNT` span，主要成本在云端提示词与草稿确认 UI 的多笔处理。
2. **修正时学习商户规则**——写入 `source = LEARNED` 的 `merchant_rule`，`priority` 低于用户手写规则，使重复商户下次直接命中规则层、不耗云端调用。
3. **未识别字段要明示**——云端返回的 id 对不上真实数据时在草稿上显式标为「未能识别」，不静默降级。

**Consequences**：MVP 变大，但三项都在既有数据结构内（`RuleSource.LEARNED`、`merchant_rule.priority`、草稿字段本就可空），无需改表。风险点是第 2 项可能积累出低质量规则，因此必须限制：同商户不重复写、且 LEARNED 规则可在管理页禁用/删除（用户手写规则优先级恒高于它）。

### D8：账户字段不计入云端触发判据（走查实测后追加）

**Context**：真机走查发现 `RuleEntryParser` 在**没匹配上账户时无条件**把 `ACCOUNT` 标为未识别（无论句子是否提到账户），而 D7 的 (b) 条件是「任一字段未识别即问云端」。用户账户名极少恰好出现在账目文本里，于是 **ACCOUNT 实际上总是未识别 → 几乎每句都调用云端**，每次烧 15–60 秒；AC「同商户二次输入不消耗云端调用」也永远不成立——实测在 LEARNED 规则已供上分类后重输同一句，仍出现了云端调用的进度条。单测看不见，因为假数据的账户总能匹配上。

**Decision**（2026-09-28 确认）：`needsCloud` 的「未识别即触发」判据**排除账户字段**（`ACCOUNT` 与 `TO_ACCOUNT`）。理由：云端变不出库里不存在的账户（实测 `gemma3:4b` 给的就是不存在的账户，被后校验挡成未识别），而账户本来就必须由用户在本页手选（确认以 `accountId != null` 为前置）。

**Consequences**：规则层抽全的句子不再触发云端，解析回到瞬时，AC 失而复得。**`ACCOUNT` 仍保留在 `ParsedEntry.unresolved` 中**——UI 照旧显示「未能识别：账户」并要求用户选择（需求 8 不变），改的只是云端触发。代价：句子若真提到账户而规则层漏了，云端不会被问；但实测它也答不对，收益本就接近零。

## Research References

* [`research/llm-structured-output.md`](research/llm-structured-output.md) — OpenAI 兼容端点的结构化输出：各服务对 `response_format` 的支持差异、容错抽取与「一次修复重试」模式、字段级坑位（元 vs 分、幻觉 categoryId、ISO vs epoch）、以及映射到本仓库既有依赖的实现建议。
* [`research/chinese-nl-rule-parser.md`](research/chinese-nl-rule-parser.md) — 规则层的金额/日期文法、收支类型线索与陷阱、抽取器架构与单测语料。

## Research Notes

### 关键结论（来自 `research/llm-structured-output.md`）

- **基线用 `response_format: {"type":"json_object"}`，而不是 strict `json_schema`**：不少 OpenAI 兼容服务对未知的 `response_format` 直接 400，硬失败会破坏「逐级回退」这条验收项。strict 模式作为**可选升级档**放进降级阶梯。
- 抽取容错（去 ```json 围栏 → 取第一个配平的 `{...}` → 容忍尾逗号）用已有的 `org.json` 手写即可，无需新依赖。
- 解析失败只做**一次**提示式修复重试，整个云端级预算 ≤ 2 次网络调用，之后按 `AppResult.Failure` 回退规则层，异常不逃逸到 UI。
- 字段级坑位：模型可能给「元」而非「分」、可能编造 `categoryId`/`accountId`（必须用调用方传入的真实列表后校验）、`occurredAt` 可能是 ISO 字符串而非 epoch、`confidence` 需夹到 0..1；纯中文提示里需显式出现「JSON」字样。

### 关键结论（来自 `research/chinese-nl-rule-parser.md`）

- **抽取架构：Span 表 + 优先级认领，而不是顺序消费文本**。先把所有候选抽成互不重叠的 `Span(start, end, kind)`，再按 `DATE/TIME > RATIO > AMOUNT > ACCOUNT/MERCHANT > CUE` 统一认领——这样「3月5日」里的数字不可能被当成金额，「五折」也不会被当金额。
- 固定遍次：`P0 归一化 → P2 日期时间 → P3 比例 → P4 金额（中文数字归一后交给 Money.parse）→ P5 账户 → P6 merchant_rule → P7 收支线索 → P9 组装`。
- 纯 Kotlin 放 `core:domain/nlp`，**入参显式传 `today: LocalDate` 与 DB 候选**，不读时钟、不读库 → 可在纯 JVM 单测里锁死相对日期（「上周三」）的解析结果。
- 只产出**候选**（账户/分类 id 必须与真实名称匹配），按离散证据分档给 HIGH/MEDIUM/LOW 置信度，返回 `Ok/Partial/Failed`，不抛异常。

### 与既有代码的接缝（已核实）

- `Money.parse(text): Money?`（`core/model/.../money/Money.kt`）已存在，但**只处理 ¥/￥/$、千分位、前导 ±**，不认中文数字、不认「块/毛/角」。因此 NLP 层需先把中文数字与「块/毛/角」归一成十进制字符串，再交给 `Money.parse`；这条边界要在单测里覆盖。
- `MlSampleSource { USER_CORRECTION, IMPORT }`（`core/model/.../AiEnums.kt`）已存在，修正样本写 `USER_CORRECTION`。
- `core:domain` 现有包：`calculator/ model/ repository/ support/ usecase/`；新增规则层放 `nlp/`，保持纯 JVM、无 Android 依赖。

### 待确认的 spec 措辞

`docs/ARCHITECTURE.md` §6 现写「用 JSON Schema 约束 + 解析失败重试一次」。研究结论是把 JSON Schema 实现为**降级阶梯里的一档**（基线 json_object，strict 为升级档）。实现时按阶梯走，并在 M6 收尾的 spec 更新里把 §6 的这句措辞对齐（属 Phase 3.3）。

## 云端端点配置（验收用）

验收用两个端点，都按 OpenAI 兼容 `POST {baseUrl}/chat/completions` 接。客户端 baseUrl 可配置，两者都能用同一份实现。

**端点 A —— 本地 Ollama（默认推荐：离线、免费、无外发）**

- `baseUrl`：`http://192.168.10.4:11434/v1`（Ollama 的 OpenAI 兼容面；`GET /v1/models` 返回 200 与标准 OpenAI 列表）。
- 模型：默认 `gemma3:4b`（基准性价比最优，见下节；用户最初指定的 `deepseek-r1:7b` 被它全面支配）。该机另装有 `qwen3-14b-q4km:latest`、`qwen3:30b-a3b`、`deepseek-r1:1.5b`。
- **明文 http，但 debug 明文白名单在 M5 已含 `192.168.10.4`，无需再改配置**（`app/src/debug/res/xml/network_security_config.xml`）。
- `apiKey`：Ollama 不需要，留空。

**端点 B —— 在线 `MiniMax-M2`（2026-09-28 用户提供）**

- `baseUrl`：`https://api.kunyou.asia/v1`；模型 `MiniMax-M2`（该网关共 31 个模型，同族还有 `MiniMax-M2.1`/`M2.5`/`M2.7`/`M3` 及各 `-highspeed` 变体）。
- `apiKey`：**只允许经 `EncryptedAiSettingsStore` 加密存储或在 App 设置页手工填入；绝不写入仓库任何文件、spec、日志或提交信息。**
- https，**不需要**改明文白名单。

### 端点 B 的实测与一个必须处理的障碍

- ✅ `GET /v1/models` 返回 200，key 有效，`MiniMax-M2` 在列。
- ❌ **无 UA 的请求被 Cloudflare 拦下**：`HTTP 403`，body 为 `{"title":"Error 1010: Access denied", ...}`（Cloudflare 1xxx 错误，含义是「按浏览器签名封禁」）。
- ✅ **带上浏览器风格 `User-Agent` 后 HTTP 200**，28.8s 返回正常补全。

→ **结论：`LlmParserClient` 必须发送 `User-Agent` 头**，否则该网关下必然 403。这不是可选项，是这条链路的硬前提。默认 OkHttp 的 `okhttp/4.12.0` 与 Python 的 UA 一样属于被拦的非浏览器签名，因此要显式设置。**替代方案（更干净）是让用户把自己的网关配置改成放行 API 客户端**——两种都可以，但实现里必须先带上 UA，否则验收做不了。

- ⚠️ 主机侧工具注意：本机 Windows curl 走 schannel，对该域名会 `SSL/TLS connection timeout`（`CRYPT_E_REVOCATION_OFFLINE`，吊销服务器在 VPN 路由下不可达）。这是**主机工具问题，与产品无关**——Android 的 OkHttp 不使 schannel。用 Python/OpenSSL 或 `curl --ssl-no-revoke` 可绕过。

### 对本地 Ollama 的实测（真实请求，HTTP 200）

输入「昨天午饭 35 块 微信」+ 八字段系统提示词，模型 `deepseek-r1:7b`：

- ✅ `response_format: {"type":"json_object"}` 被接受。
- ✅ **语义字段正确**：`amountCents=3500`（35 元 → 分，正确）、`type=expense`、`categoryId=c_food` 与 `accountId=a_wx`（**均取自提示词给定的列表**）、`title=午饭`、`remark=微信支付`，中文处理无问题。
- ❌ **输出被 ```json 围栏包裹，前面还有两个空行** → 直接 `json.loads()` 失败。
- ❌ **`occurredAt` 不可信**：返回 `1623456000`，既是 **epoch 秒**（10 位）而非提示词要求的毫秒，**日期本身也是错的**（解作 2021-06-12，而「昨天」应为 2026-09-27）。
- ⚠️ **耗时 65.5 s**（366 completion tokens）——单次解析超过一分钟。
- 本次返回内容里**没有** ` thinking` 块（换模型/换版本可能出现，抽取仍需容忍）。

### 由实测推出的三条硬约束（PR3 必须实现）

1. **容错抽取是必需品，不是可选项**：去 ```json 围栏（含前后空白）→ 取首个配平 `{...}` → 容忍尾逗号。两个不同服务商（云端与本地）**都**加围栏，已两次实证。
2. **`occurredAt` 必须由规则层负责，不能用模型给的**。基准实测 5 个模型、每个 5 条真实句子：**没有任何一个模型算对过相对日期**，即便系统提示词里明确写了「今天是 2026-09-28」。最好的一档也稳定给出 2023-10-27 这类训练期锚点日期；qwen3 给出 2025-11 与 2026-07 等散布结果。另有 10 位整数（epoch 秒）与 `null`/`0` 两种错法。→ **日期一律取规则层结果**（对「昨天/上周三」确定性可测），模型只负责语义（分类/标题/类型）。模型给的 `occurredAt` 仅在规则层未识别出日期时参考，且需做单位与区间校验（10 位按秒 ×1000、超出今天 ±1 年判为不可信），不可信则按需求 8 标记「未能识别」。
3. **读超时必须放宽**：7B 本地模型单次解析约 65 s，客户端 read timeout 至少 120 s，且期间要有「正在解析」的可见状态，不能让用户以为卡死。

### 先前云端服务商（已弃用，备查）

用户最初指定的 `https://ai.hybgzs.com/gemini` 实测只有 `/v1` 前缀可通：`POST https://ai.hybgzs.com/v1/chat/completions` 返回 200 与正确解析（`amountCents=3500`、id 取自列表），而 `/gemini/chat/completions` 与 `/gemini/v1/chat/completions` 均被 **Cloudflare** 边缘 403（`server: cloudflare` + `CF-RAY`）；该服务商亦曾以 `content_anti_probe_blocking` 拦截脚本化探活。**若日后回到该服务商，baseUrl 应为 `https://ai.hybgzs.com/v1`。**

### D6：先开工离线部分，云端联调后置

**Context**：云端端点先是遇到服务商风控与 Cloudflare 403（`content_anti_probe_blocking`、`/gemini` 前缀不可用），随后用户改为本地 Ollama（`http://192.168.10.4:11434/v1`，模型 `deepseek-r1:7b`）。本地端点已实测可用，但暴露出围栏包裹、`occurredAt` 不可信、单次耗时 65 s 三项实现约束。

**Decision**（2026-09-28 确认）：不被云端项阻塞。规则层（`core:domain/nlp`）与分类器接缝完全离线，先做；云端客户端按 `POST {baseUrl}/chat/completions` 的形状实现，并用 MockWebServer 覆盖各降级分支，baseUrl 保持可配置以便在云端服务商与本地 Ollama 之间切换。

**Consequences**：离线部分与云端部分的验收解耦；云端真机验收成为收尾时才可能暴露的风险项，PRD 的 AC 中「云端 400 降级」「云端 id 对不上」两条在联调前只能由 mock 覆盖。

### D7：云端触发条件——规则层产出不足时才问云端

**Context**：检查发现当前实现只在规则层产出**零条目**时才调用云端（这是 `ai-ml.md`「前一级失败降级到下一级」的字面读法），而 D3 的措辞是「打开后每次解析直接走云端」。两者实际差别很大：「昨天午饭 35 块 微信」这类句子规则层总能抽出金额，于是**开关打开也几乎不会真的调到 AI**——与用户对这个功能的预期不符。

**Decision**（2026-09-28 确认）：规则层先跑；**当且仅当**下列任一情况成立且联机开关已打开时，再调用云端补全语义——(a) 规则层产出零条目；(b) 有字段落入 `unresolved`（类型 / 账户 / 分类 / 标题）；(c) 整体置信度非 `HIGH`。

**Consequences**：LLM 只在真正需要时被调用——既满足「开了联机就该看到 AI 起作用」，也保住 AC 里「同商户二次输入由规则层命中、不消耗云端调用」（LEARNED 规则命中后置信度为 HIGH，不再触发云端）。需同步修改 `NlEntryPipeline` 的触发判据，并把 D3 的措辞由「每次解析直接走云端」改为「云端在规则层产出不足时补位」。`ai-ml.md` 的「降级」语义不变，只是把「失败」明确为「产出不足」。

### 模型选型（基准实测）

完整数据见 `research/ollama-model-benchmark.md`（5 个模型 × 5 条真实句子，逐字段打分）：

| 模型 | 得分率 | 平均耗时 | 平均输出 tokens | 模型体积 |
|---|---|---|---|---|
| `qwen3:30b-a3b` | 92.9%（13/14，**3/5 超时未完成**） | 252.9s | 84 | 18.6 GB |
| `qwen3-14b-q4km:latest` | 91.7%（33/36） | 147.4s | 462 | 9.0 GB |
| **`gemma3:4b`** | **83.3%（30/36）** | **17.5s** | **101** | 3.3 GB |
| `deepseek-r1:7b` | 75.0%（27/36） | 69.1s | 403 | 4.7 GB |
| `deepseek-r1:1.5b` | 13.9%（5/36） | 21.1s | 447 | 1.1 GB |

**结论：默认模型用 `gemma3:4b`**，`qwen3-14b-q4km:latest` 作为「高质量但慢」的可选。

- `gemma3:4b` 的性价比是碾压性的：**17.5s**，比 qwen3-14b 快 **8.4 倍**，得分反而只低 8.4 个百分点，且比两个 deepseek 模型都快且都准。输出仅 101 tokens（不产思维链）。
- `qwen3:30b-a3b` **3/5 超时**（>420s），不可用于交互式记账；其 92.9% 只建立在 2 条完成的用例上，样本有偏，不可直接比较。
- `deepseek-r1:7b` 被 `gemma3:4b` **全面支配**：更慢（69s vs 17.5s）且更差（75% vs 83.3%），思维链 token 是主要开销。
- `deepseek-r1:1.5b` 不可用：字段名返回错误（`amount`/`category_id`）、单位错（35 而非 3500）、类型串到分类里。

**另外两条基准副产物：**

- **围栏包裹与模型相关**：`gemma3:4b` 与 `qwen3-*` 的返回可直接 `json.loads`，而两个 `deepseek-r1` **每一条**都包了 ```json 围栏。容错抽取仍必须实现（先前云端服务商也加围栏），但它是否被触发取决于用户选的模型——所以**必须有测试覆盖**，不能因为默认模型不加围栏就省掉。
- **小模型能做多笔**：「午饭35 打车28」上 `gemma3:4b`、`qwen3-14b`、`qwen3:30b` 都是满分，说明单句多笔不是大模型专属能力。

## Open Questions

- `deepseek-r1:7b` 的 `occurredAt` 既错单位又错日期。已定「日期以规则层为准」兜底，但**若规则层也没识别出日期**，云端给的不可信日期该丢弃并标记「未能识别」（需求 8），还是回退到当前时间？倾向前者更诚实，实现时定并在草稿上可见。
- 单次解析约 65 s，真机体验是否可接受、是否需要「本次跳过云端」的入口；先按 ≥120 s 超时 + 明确加载态实现，验收时看实际体验。
- `.trellis/spec/android/ai-ml.md` 已通读，与本文无冲突（三级管线、草稿 PENDING、修正写样本、联网默认关且须告知发送目标均一致），Phase 1.3 收进 `implement.jsonl`。该 spec 写的是「要求严格 JSON 输出」，指的是**出参要求**而非 wire 机制，因此只需在 Phase 3.3 微调 `ARCHITECTURE.md` §6 那句「用 JSON Schema 约束」的措辞，`ai-ml.md` 不必改。

## Requirements

1. 规则层解析：从一句话抽出金额、类型、日期、账户、标题；`merchant_rule` 命中直接定分类。全程离线可用，不依赖网络。
2. 解析结果落成 `draft_status = PENDING` 的待确认草稿，用户确认或修正后入库。
3. 云端解析可选、默认关闭；失败或未开启时逐级回退到规则层。
4. 每次用户修正写入一条 `ml_training_sample`。
5. 设置页可配 `baseUrl` / `apiKey` / `model`，`apiKey` 加密存储；提供隐私开关。
6. **一句话可拆多笔**：「午饭35 打车28」应产出两笔草稿，而不是一笔或报错。规则层按 Span 表逐个 AMOUNT 认领产出多笔；云端提示词与草稿确认 UI 同样按「多笔」设计。
7. **修正时学习商户规则**：用户改正 AI 给出的分类时，除写 `ml_training_sample` 外，再写入一条 `source = LEARNED` 的 `merchant_rule`（pattern 取标题关键词，`priority` 必须低于 `USER` 规则的默认值，保证用户手写规则永远优先）。同一商户重复修正不得产生重复规则。
8. **未识别字段要明示**：云端返回的 `categoryId` / `accountId` 若对不上调用方传入的真实列表，该字段标记为「未能识别」并在草稿上显式呈现，由用户选择；不得静默降级成「未分类」或默认账户而不提示。
9. **商户规则管理界面**（`docs/DATA-MODEL.md` 3.8 标注「界面在 M6」）：可查看、新增、编辑、启用/停用、删除 `merchant_rule`；列表需区分 `USER` / `BUILTIN` / `LEARNED` 来源并显示 `hit_count`，使第 7 项学习出来的规则可被审查与清理。

## Acceptance Criteria

- [ ] 断网（或云端关闭）时输入自然语言仍能生成待确认草稿并入库。
- [ ] 云端解析失败（超时 / 非 JSON / 字段缺失）时不阻断记账，可回退规则层。
- [ ] 云端返回不认识的 `response_format` 而 400 时，降级而不是直接失败（基线 `json_object`，strict 为升级档，整体 ≤ 2 次网络调用）。
- [ ] 用户修正草稿后 `ml_training_sample` 增加一条对应样本，且新增/命中一条 `source = LEARNED` 的 `merchant_rule`。
- [ ] 再次输入同一商户的句子时由规则层直接命中分类，**不消耗云端调用**（账户字段见 D8，不计入触发判据）。
- [ ] 「午饭35 打车28」产出两笔草稿，金额分别为 35 与 28。
- [ ] 云端给出不存在的 `categoryId` 时，草稿上该字段显示为「未能识别」而非静默变成未分类。
- [ ] 商户规则管理页可列出、新增、停用、删除规则，并区分 `USER` / `BUILTIN` / `LEARNED` 来源。
- [ ] `apiKey` 不以明文出现在 `shared_prefs` 或日志中；账单文本不进日志。

## Definition of Done

- 功能与测试覆盖验收标准，build、test、lint 通过；模拟器完成核心路径验证。
- 文档/规范（`docs/PLAN.md`、相关 Android spec）与实际行为一致。
- 工作代码提交、Trellis 任务归档、journal 记录按工作流完成；已有未跟踪临时文件保持原状，不纳入提交。

## Out of Scope

- 端侧分类器的训练与 ONNX 导入（M7）。
- Python 训练脚本 `tools/trainer/`（M7）。
- 多轮对话式记账、语音输入、图片/截图解析。
- 云端账单托管，或默认把账单内容上传。

## Technical Notes

- 权威范围：`docs/PLAN.md`、`docs/ARCHITECTURE.md` §6/§7、`docs/DATA-MODEL.md` 3.8/3.10。
- 相关代码：`core/domain/.../model/MerchantRule.kt`、`core/data/.../repository/MerchantRuleRepositoryImpl.kt`、`MlSampleRepositoryImpl.kt`、`core/database/.../dao/MerchantRuleDao.kt`、`MlTrainingSampleDao.kt`、`feature/ledger/.../ui/drafts/*`、`feature/ledger/.../ui/home/HomeViewModel.kt`（待确认入口）、`feature/sync/.../backup/EncryptedSettingsStore.kt`、`app/.../ui/VipLedgerApp.kt`。
- 实现约束：按 `.trellis/spec/android/*`（architecture / ui / database / testing / error-handling / logging-guidelines）执行；`apiKey` 与账单内容不得进日志。
