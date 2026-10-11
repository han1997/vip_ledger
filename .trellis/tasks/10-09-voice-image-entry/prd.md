# P5 文字、语音、截图

Status: voice slice implemented; image/share slices remain planned.

- 四种输入统一编辑器/草稿确认；一句多笔可逐笔修改、批量确认，区分未识别字段。
- 语音按住/松开/取消/重试/转写编辑，离页停止，不存原始音频。系统端侧检测；联网转写独立开关默认关；无服务/权限/超时可改手工。
- 主动选图及系统分享、多图逐张 OCR，复用本机中文识别；金额/商户/时间/支付方式/订单号。冲突须补充；主动截图始终先确认。原图默认不存，可选附件。
- 技术参考：https://developer.android.com/reference/android/speech/SpeechRecognizer 、https://developer.android.com/training/data-storage/shared/photopicker 。系统识别可能联网，不能将普通服务冒称离线。

## 本轮交付：语音首切片

- `SpeechTranscriber` 接缝位于 `:core:domain`，系统实现位于 `:core:data`；API 31+ 优先使用端侧识别，普通识别回退明确标注「系统识别可能联网」。
- AI 入口支持按住开始、松开结束、上滑取消、失败重试、离页取消；成功转写只填入可编辑输入框，不自动解析或入账。
- `RECORD_AUDIO` 按需申请；拒绝、无服务、超时、无结果和未知错误都保留手工输入路径。
- `AiSettings.voiceOnlineTranscription` 默认关闭，并独立持久化；本轮无数据库表、迁移或原始音频存储变更。

## 后续切片

- 主动选图、Photo Picker、系统分享、多图 OCR 与附件选项尚未实现。

See docs/FEATURE-ROADMAP.md for shared contracts, acceptance and delivery requirements.

## 实施决策（本批）

### 输入来源统一
- 四种输入（文字 / 语音 / 主动选图 / 系统分享）统一落到既有「候选草稿」审阅：`AiDraft` 逐笔修改（金额/类型/账户/目标账户/分类/标题）+ `ConfirmAll` 批量确认；未识别字段沿用 `unresolved` 阻塞规则（`DATE`/`TIME` 不阻塞）。
- 文字与「一句多笔」已具备（`draftsOf` 每个条目各成一条），本批不重写，只新增语音与图片来源。

### 语音（本批首个切片）
- 接缝 `SpeechTranscriber`（`:core:domain`）只暴露开始/停止/取消与回调；纯 Kotlin 状态机 `VoiceInputState` 负责界面相位。
- Android 实现用系统 `SpeechRecognizer`：优先 `createOnDeviceSpeechRecognizer`（API 31+ 且 `isOnDeviceRecognitionAvailable`），回退普通识别时界面必须标注「可能联网」，绝不冒称离线。
- 交互：按住开始、松开结束、上滑取消、失败可重试；离页停止并复位；取消/离页不提交文本。
- 转写结果进入既有可编辑输入框，不自动确认；只保留文本，不保存原始音频。
- 失败分类：无服务 / 权限被拒 / 超时 / 无结果 / 未知 → 提示并可改手工输入，不静默失败。
- 联网转写独立开关 `AiSettings.voiceOnlineTranscription`，默认关；关闭时只用端侧识别，端侧不可用即提示改手工。

### 图片与系统分享（本批后续切片）
- 主动选图（Photo Picker）与 `ACTION_SEND image/*` 分享入口；多图逐张 OCR，复用 `PaymentOcr` 的本机中文识别，OCR 文本进入同一解析。
- 主动截图/分享始终先确认，不自动入账；原图默认不保存（可选附件）。冲突字段仍由 `unresolved` 要求补充。

### 交付面与约束
- 无新表、无迁移（P5 不改 schema，沿用 schema6 / 快照格式 6）。
- `:feature:ai` 用户可见文案前缀 `ai_`；`RECORD_AUDIO` 运行时权限按需申请，拒绝后仍可手工输入。
