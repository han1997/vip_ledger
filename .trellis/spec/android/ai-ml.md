# AI 与端侧模型

## 解析管线（自然语言记账）

按顺序尝试，任何一级都不得抛出异常到 UI：

1. **规则层（离线必备）**：`core:domain/nlp`，Span 表 + 优先级认领抽金额与日期、账户关键词、
   `merchant_rule` 命中直接定分类。
2. **端侧分类器**：`ml:classifier` 对「标题 + 备注」给出分类候选与置信度，低于阈值不出建议。
3. **云端 LLM（用户显式开启才联网）**：见下方调用契约。

> **Warning**：「降级」不等于「只有前一级彻底失败才走下一级」。云端在**产出不足**时就要补位，
> 判据是三者任一：规则层产出**零条目**、有字段落入 `unresolved`、或整体置信度**非 HIGH**。
> 只按「零条目」判定会让「昨天午饭 35 块」这类句子永远走不到云端——规则层总能抽出金额，
> 于是开关打开也看不到 AI 起作用。

结果一律落成 `draft_status = PENDING` 的草稿，用户确认或修正后入库；每次修正写一条
`ml_training_sample`（`source = USER_CORRECTION`），并 upsert 一条 `source = LEARNED` 的
`merchant_rule`。

### 字段未识别的表示（贯穿全管线）

`ParsedEntry.unresolved: Set<ParsedField>` 是**唯一的**「没有识别出来」表示，禁止用默认值冒充：

- 云端给的 `categoryId`/`accountId` 对不上调用方传入的真实列表 → 进 `unresolved`，
  **不得**静默变成「未分类」或默认账户。
- 规则层没抽到类型/账户/分类/标题 → 进 `unresolved`。
- 例外：**日期没有线索时取「今天」且不进 `unresolved`**（确定性默认，非降级）；
  但**给了一个不可信的日期**（见错误矩阵）必须进 `unresolved`。
- UI 必须把 `unresolved` 渲染成「未能识别」并要求用户选择，未清空前不可确认。

## 云端 LLM 调用契约（`:core:data/ai`）

### 1. Scope / Trigger

跨层契约：网络响应 → 领域模型。任何改动都直接影响云端解析的可用性与「失败可回退」这条验收。

### 2. Signatures

```kotlin
// core:domain —— 接口在 domain，实现在 data（依赖只能向内）
interface LlmParserClient {
    suspend fun parse(text: String, context: ParseContext): AppResult<List<ParsedEntry>>
}
data class ParseContext(val today: LocalDate, val accounts: List<...>, val categories: List<...>)

interface AiSettingsRepository { fun settings(): AiSettings; fun save(settings: AiSettings) }
data class AiSettings(
    val baseUrl: String = "", val apiKey: String = "",
    val model: String = "gemma3:4b", val networkEnabled: Boolean = false,
)
```

`POST {baseUrl}/chat/completions`

### 3. Contracts

请求体：`model`、`messages`（system 里必须带**今天的日期**、八个字段的说明、以及**调用方真实的
分类/账户候选列表**）、`stream=false`、`temperature=0`、`response_format={"type":"json_object"}`。

请求头：`Content-Type: application/json`；有 key 时 `Authorization: Bearer <key>`；
**`User-Agent` 必须显式设置**（见下方 Gotcha，不设置会在部分网关必然 403）。

期望响应字段：`amountCents`(int，分)、`type`(expense/income/transfer)、`categoryId`、`accountId`、
`occurredAt`(epoch 毫秒)、`title`、`remark`、`confidence`(0..1)；一句话多笔时返回 `{"items":[...]}`。

### 4. Validation & Error Matrix

| 情况 | 处理 |
|---|---|
| 401 / 403 / 404 / 429 / 5xx / 传输层错误 | **不重试**，直接 `AppResult.Failure` |
| `response_format` 被拒（400/422 或提示不支持） | 去掉 `response_format` 做**一次**提示式修复重试 |
| 内容解析失败（围栏/think 包裹、截断、非 JSON） | 同上，**一次**修复重试 |
| 两次都失败 | `Failure`，回退规则层，异常不上抛 |
| **总请求数** | **≤ 2**（实现里不得出现循环） |
| 开关 `networkEnabled=false` | 一次请求都不发，直接 `Failure` |
| `occurredAt` 缺失/null | 视为「没有日期」，用「今天」，**不**标记未识别 |
| `occurredAt` 10 位整数 | 按 **epoch 秒** ×1000 |
| `occurredAt` 超出今天 ±366 天 | 标记 `DATE` 未识别 |
| `categoryId` / `accountId` 不在传入列表 | 标记未识别，**不**静默降级、**不**丢弃整条 |
| `confidence` 越界 | 夹到 `0..1` |
| 金额为小数 | 按「元」×100 视为分（整数分不猜） |

### 5. Good / Base / Bad Cases

- **Good**：模型返回裸 JSON `{"amountCents":3500,...}` → 直接进 `org.json` 校验。
- **Base**：模型包了 ` ```json ` 围栏或前置 ` thinking` 块 → 容错抽取后仍成功（这是**常态**，不是异常）。
- **Bad**：返回散文加 JSON、尾逗号、`finish_reason=length` 截断 → 先容错抽取，失败才触发修复重试。

### 6. Tests Required

`MockWebServer` + Robolectric（`org.json` 在纯 JVM 下是 stub），断言点：

- 首个请求带 `response_format`、修复重试**不带**；用 `RecordedRequest` 断言。
- **每个**请求都带 `User-Agent`（含重试那次）。
- 各失败路径断言 `requestCount`：401/403/404/429/5xx → **1**；两次失败 → **2**。
- 围栏包裹 / 无语言标签围栏 / 散文包裹 / ` thinking` 前缀 / 尾逗号 → 都能解出。
- 幻觉 `categoryId`/`accountId` → 落 `unresolved`；epoch 秒 → 换算；越界日期 → 标记未识别。
- 开关关闭 → 0 请求。

### 7. Wrong vs Correct

```kotlin
// Wrong —— 模型说它输出 JSON 就直接信
val parsed = JSONObject(content)              // 实测两个服务商都会加 ```json 围栏，必然抛
val date   = parsed.getLong("occurredAt")     // 实测 5/5 本地模型把日期算错（见下）
```

```kotlin
// Correct —— 容错抽取 + 日期以规则层为准
val parsed = StrictJson.extract(content) ?: return AppResult.Failure(AppError.Parse)
val date   = ruleEntry.date ?: trustCloudDate(parsed.opt("occurredAt"), context.today)
```

## 实测约束（2026-09-28，真实端点，勿凭直觉推翻）

- **相对日期一律由规则层负责，不要采信模型**。5 个本地模型 × 5 条真实句子，即便系统提示词里
  写明「今天是 2026-09-28」，**没有一次算对**：最好的一档稳定给训练期锚点日期，另有 epoch 秒与
  `null`/`0` 两种错法。规则层对「昨天/上周三」是确定性的且可单测。**合并时日期以规则层为准**，
  模型给的日期只在规则层未识别出日期时参考。
- **围栏包裹与模型相关，不能假设**。`gemma3:4b` / `qwen3-*` 返回可直接解析，两个 `deepseek-r1`
  **每一条**都包围栏；先前一个在线服务商也加围栏。所以抽取容错是必需品且必须有测试覆盖，
  不能因为默认模型不加围栏就省掉。
- **读超时必须放宽**。7B 本地模型单次解析实测 **65 s**，14B 到 **147–210 s**。read timeout
  ≥120 s，且 UI 必须有明确的「正在解析」状态，否则用户以为卡死。
- **小模型能做多笔**：`午饭35 打车28` 在 `gemma3:4b`、`qwen3-14b`、`qwen3:30b` 上都是满分，
  单句多笔不是大模型专属能力。

> **Gotcha：部分网关必须带 `User-Agent`，否则必然 403。**
>
> 实测某在线网关不带 UA 时一律 `HTTP 403`，body 是 Cloudflare 的
> `{"title":"Error 1010: Access denied"}`（按浏览器签名封禁）；OkHttp 默认的 `okhttp/4.x`
> 与 Python 默认 UA 一样属于被拦签名，带上浏览器风格 UA 后立即 200。客户端已把它做成具名常量
> 并注明来历——**不要当噪音删掉**，删了该网关下整条云端链路直接不可用。
>
> 更干净的替代是让用户在网关侧放行 API 客户端，但那不改变「代码里必须能带上 UA」。

## 端侧分类器

- 特征：字符 2 至 3 gram 哈希到 1<<18 桶的 TF 向量；纯 Kotlin，不下载模型。
- 模型：多项式逻辑回归（softmax），权重序列化为 JSON（含版本号与分类标签表）。
- 推理：`classify(text)` 返回 top-3 候选与置信度。**阈值规则只在一处表达**
  （`CategoryClassifier.ranked()`，`TOP_N=3`、`MIN_CONFIDENCE=0.5f`），不要在调用点各写一遍。
- 在线训练：`train(samples, epochs, lr)` 增量 SGD；受「自动训练」开关控制，**默认关闭**；
  样本数阈值 30；训练在后台线程完成后原子替换权重文件。
- 外部模型：支持导入 ONNX（tokenizer 配置随模型一起导入），可与线性模型共存切换。
- Python 训练脚本在 `tools/trainer/`，必须与 App 侧保持同构（同特征、同模型结构、同权重导出格式）。

> **M7 之前 `:ml:classifier` 里是占位实现**（返回空候选，`PLACEHOLDER = true`）。它必须一眼可辨，
> 不得伪装成可用分类器。管线程序到 `:core:domain` 的接口，M7 只换实现、不动管线。

## 隐私红线

- 联网开关默认关闭；开启时明确告知「账目文本会发送到该端点」。
- **`apiKey` 与请求/响应正文（用户的账目文字）在任何路径都不得进日志**，错误信息只用固定字符串，
  不得把 key、`Authorization` 头、含凭据的 URL 或 body 拼进异常消息。
- 持有 `apiKey` 的数据类**必须覆写 `toString()` 脱敏**：`data class` 自动生成的 `toString()`
  会把 key 原样吐出来，一旦被日志或崩溃报告带走就泄露（掩码 key、剥离 URL 里的 userinfo）。
- 端侧分类器的训练与推理完全本地，不上传样本。
- 模型文件与训练样本导出由用户主动触发，路径可自定义。

## 常见坑

- **两类错误别混**：`ml_training_sample` 与 `LEARNED` 规则的写入去重**按 pattern 且不看 source**。
  只看 `source == LEARNED` 会让「升级为用户规则」的那条被重复学习出第二条同 pattern 规则
  （两条优先级相同时谁赢只是碰巧由 `created_at` 次序决定，不是设计保证）。
- **确认 AI 草稿前要跑 `TxnValidator`**：AI 页允许用户改最终值，绕过校验会写进非法数据
  （转账转出被改成与转入相同、金额为 0 或负数——`Money.parse` 是接受符号的）。
- **合并云端多笔时按金额对齐，不要按位置**：模型可能调换顺序，按位置会把 A 笔的分类/账户
  贴到 B 笔的金额与标题上。
