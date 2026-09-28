# M7 端侧模型与训练工具链

> 需求走查记录：2026-09-28。依据 `docs/PLAN.md`、`docs/ARCHITECTURE.md` §7、`docs/DATA-MODEL.md` 3.10 与现有代码交叉核对。用户要求按里程碑继续推进；本文把仓库已有决策作为范围依据，不将其表述为本轮逐项口头确认。

## Goal

把 M6 留下的端侧分类器接缝填上真实现：用本地训练的线性分类器给「标题 + 备注」出分类候选，让不联网也能得到分类建议；样本从用户修正中沉淀，训练默认关闭、开启后自动进行；并提供 Python 训练脚本，使模型可在电脑上训练、导出后带回 App。

## What I already know

- M0–M6 已交付。M6 已把接缝定型，M7 只需替换实现、不动管线：
  - `:core:domain/nlp/CategoryClassifier` 接口：`suspend fun classify(title, remark): List<CategorySuggestion>`；`TOP_N = 3`、`MIN_CONFIDENCE = 0.5f`，且 `List<CategorySuggestion>.ranked()` 是**唯一**的阈值/top-N 口径（管线与实现共用）。
  - 管线 `NlEntryPipeline` 只补规则层没定的分类，且**只程序到接口**。
  - `:ml:classifier` 目前只有 `PlaceholderCategoryClassifier`（返回空列表，`PLACEHOLDER = true`），是纯 Kotlin JVM 模块，**已声明 `kotlinx-serialization` 与 `:core:domain` 依赖**。
- 训练闭环的数据接口在 M1 就备好了，M7 不必改表：
  - `MlTrainingSample(id, ledgerId, text, categoryId, source, trainedVersion, createdAt)`；`text` = 标题 + 备注拼接。
  - `MlSampleRepository` 已有 `listUntrained(ledgerId)`（`trained_version` 为空）与 `markTrained(ids, version)`——增量训练所需的两端都在。
  - `MlSampleSource { USER_CORRECTION, IMPORT }`；M6 已在用户修正时写入 `USER_CORRECTION` 样本。
- **版本目录里没有 ONNX**（`gradle/libs.versions.toml` 无 onnx/tensorflow/pytorch）。按 ARCHITECTURE §7，ONNX Runtime Android 要在 M7 引入，属**重型新依赖**（十几 MB 量级）。
- **`tools/` 目录不存在**，Python 训练脚本要从零建。
- **`ml_samples.jsonl` 从未实现**：M5 的备份 spec 里提到它「可选」，但 `core/data/backup/` 里没有任何引用。样本导出路径目前是空的。

## Assumptions

- ARCHITECTURE §7 的特征/模型规格作为实现基准：字符 2–3 gram 哈希到 1<<18 桶的 TF 向量；多项式逻辑回归（softmax）；权重序列化为 JSON（含词表版本与分类标签表）。
- 训练在 `Dispatchers.Default` 上跑，完成后**原子替换**权重文件，避免半写状态被推理读到。
- 「自动训练」开关**默认关闭**；样本数阈值 30；均由用户显式开启才训练。
- **训练触发沿用文档已定的形态**：§7 写的是「训练在 `Dispatchers.Default` 上跑」，即**进程内异步**而非 WorkManager 周期任务——触发点为「开关开启 + 未训练样本 ≥ 30」在修正写入样本后判定，不引入后台调度。

## Decision (ADR-lite)

### D1：ONNX 导入不纳入 M7

**Context**：ARCHITECTURE §7 提到「外部模型：支持导入 .onnx（M7 引入 ONNX Runtime Android）」，但（a）`docs/PLAN.md` M7 行的验收标准只要求「脚本能训出模型并在 App 内加载」，未涉及 ONNX；（b）`gradle/libs.versions.toml` 里没有任何 onnx/tensorflow/pytorch 条目，属**全新的十几 MB 级依赖**；（c）`.trellis/spec/android/architecture.md` 明令 `:ml:classifier` 是纯 JVM 模块**禁止引入 Android SDK**，而 ONNX Runtime Android 是 Android 库——它**装不进分类器模块本身**，必须另开 Android 模块或塞进 `:core:data`，这会推翻 §7 原本「ONNX 与线性模型共存切换」的落位假设。

**Decision**（2026-09-28 确认）：M7 只做线性分类器（纯 Kotlin 推理 + 增量训练）与 Python 训练脚本（同构导出权重 JSON）。ONNX 导入留到后续里程碑，届时单开 Android 模块。

**Consequences**：M7 不引入重型依赖，验收标准可完整覆盖。代价是 §7 的「外部模型」一段在 M7 后仍属未实现——Phase 3.3 更新 spec 时需明确标为后续项，避免读者以为已交付。

### D2：Python 训练脚本只用标准库

**Context**：`:ml:classifier` 的权重是 FloatArray 序列化成的 JSON，Python 侧必须与 Kotlin 侧**同构**（同特征、同模型结构、同导出格式），否则训出来的权重 App 读不对。

**Decision**（2026-09-28 确认）：`tools/trainer/` 只用 Python 标准库实现特征哈希与 SGD，零第三方依赖，`python train.py` 直接可跑。

**Consequences**：用户拿到脚本即可用，不必装 numpy；且因为两侧都用朴素的浮点运算，同构验证更容易（少一层第三方库的舍入差异）。代价是训练略慢——但样本量在几百到几千量级，可忽略。

### D3：M7 纳入三项扩展（后校验 / 导入样本 / 不平衡抑制）

**Context**：扩张扫掠时识别出三项与"模型输出能不能安全落地"直接相关的能力。第一项尤其重要：模型的分类标签表在训练时固化，而分类是**用户可删可改**的。

**Decision**（2026-09-28 确认）：

1. **分类候选后校验**——候选 `categoryId` 必须对得上当前真实分类，否则丢弃。这与 M6 对云端返回值的处理同款（那次的教训是「幻觉 id 必须落 unresolved，不许静默兜底」），不校验就等于给管线开了个后门。
2. **导入样本纳入训练**——`MlSampleSource.IMPORT` 与 `USER_CORRECTION` 都喂进去，冷启动即有数据。
3. **类别不平衡抑制**——样本数低于阈值（建议 3，可配）的分类不参与训练也不出建议。

**Consequences**：三项都不改表、都在既有结构内（`MlSampleSource` 已有 IMPORT，分类校验只需查当前分类表）。风险点是第 3 项的阈值定得过严会让模型长期不出建议——因此阈值要可配、且在模型管理页能看到「哪些分类样本不足、被跳过了」，否则用户只会看到"分类器没反应"而无从判断。

## Open Questions

- 模型管理页放哪、展示什么（D2 已把 AI 相关页收敛在 `:feature:ai`，倾向照此）。
- 样本导出（`ml_samples.jsonl`）的具体出口：SAF 导出（同 M5 的 CSV/JSON 导出）还是随备份走。

## Requirements

1. 端侧分类器实现：按 ARCHITECTURE §7 的特征与模型规格，纯 Kotlin、无模型下载即可推理。
2. 替换 `:ml:classifier` 的占位实现，管线行为不变（阈值与 top-N 仍走 `ranked()`）。
3. 增量训练：`train(samples, epochs, lr)`，完成后原子替换权重文件；受「自动训练」开关控制且默认关闭。
4. 权重序列化为 JSON（词表版本 + 分类标签表 + 权重），可版本化与回滚。
5. Python 训练脚本与 App 侧**同构**（同特征、同模型结构、同权重导出格式），能训出模型并被 App 加载。
6. 模型管理页：查看当前模型版本、样本数、上次训练时间；开关自动训练；导入/导出模型。
7. 样本导出：把 `ml_training_sample` 导出为可供 Python 脚本消费的格式。
8. **分类候选必须后校验**：模型输出的 `categoryId` 必须对得上**当前真实分类**，否则丢弃该候选。模型标签表是训练时固化的，分类被删除或改名后它仍会输出陈旧 id——不校验就会把不存在的分类塞进草稿，捅穿 M6 已建立的「未识别要明示、不许静默兜底」约定。与云端返回值的处理同款。
9. **导入样本也纳入训练**：`MlSampleSource.IMPORT`（M5 导入账单时产生）与 `USER_CORRECTION` 一样作为训练数据，使冷启动就有样本可用。二者是否区分权重由实现决定，但至少都要喂进去。
10. **类别不平衡抑制**：样本数低于阈值（建议 3，可配）的分类不参与训练、也不出建议——避免一两个样本把模型拉偏，向用户推一个从没学过的分类。

## Acceptance Criteria

- [ ] `tools/trainer/` 能训出模型，且产出的权重文件能被 App 加载并用于推理（PLAN.md M7 行的验收）。
- [ ] 「自动训练」开关默认关闭；关闭时**不发生任何训练**。
- [ ] 分类器在样本不足或模型未训练时不出建议（返回空候选），不影响规则层与云端。
- [ ] 权重文件替换是原子的：训练过程中推理仍读到完整旧模型，不会读到半写文件。
- [ ] 管线的「低于阈值不出建议」仍只有一处口径（`ranked()`），M7 未复制出第二套。
- [ ] 分类被删除后，模型**不再**给出该分类的候选（后校验生效），且不产生空指针或崩溃。
- [ ] 样本数低于阈值的分类不出现在建议里。
- [ ] `IMPORT` 来源的样本确实进入了训练集。**（2026-09-28 记为待办）** 训练路径本身两种来源都喂（`AutoTrainTriggerTest.importSourceSamplesAreFedIntoTraining` 用假数据证明），但**没有任何生产代码产生 `IMPORT` 样本**——M5 的 CSV 导入器不写 `ml_training_sample`。用户决定**先记账、以后再说**，因此这条 AC 在本轮**降级为"训练路径接受 IMPORT 样本"**，生产者留到后续里程碑。
- [ ] Python 脚本与 Kotlin 侧对**同一份样本、同一组超参**产出的权重在容差内一致（同构验证）。

## Definition of Done

- 功能与测试覆盖验收标准，build、test、lint 通过；模拟器完成核心路径验证。
- 文档/规范（`docs/PLAN.md`、`docs/ARCHITECTURE.md` §7、相关 Android spec）与实际行为一致。
- 工作代码提交、Trellis 任务归档、journal 记录按工作流完成；已有未跟踪临时文件保持原状，不纳入提交。

## Out of Scope

- 云端 LLM 与自然语言入口的改动（M6 已交付，M7 不回头改管线）。
- 深度模型 / 端上神经网络推理（除非 ONNX 被纳入，见 Open Questions）。
- 多语言分词、词向量或预训练模型下载。

## Technical Notes

- 权威范围：`docs/PLAN.md` M7 行、`docs/ARCHITECTURE.md` §7、`docs/DATA-MODEL.md` 3.10。
- 相关代码：`core/domain/.../nlp/CategoryClassifier.kt`、`core/domain/.../repository/MlSampleRepository.kt`、`core/domain/.../model/SupportModels.kt`、`ml/classifier/**`、`core/data/.../repository/MlSampleRepositoryImpl.kt`、`core/data/.../usecase/txn/ConfirmAiEntryUseCase.kt`（M6 写样本处）。
- 实现约束：按 `.trellis/spec/android/*` 执行；`:ml:classifier` 是纯 JVM 模块，**禁止引入 Android SDK**（`architecture.md`），因此 ONNX 若纳入需要放在 Android 模块而不是这里。
