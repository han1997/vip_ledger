package com.han1997.vipledger.core.domain.ml

/**
 * 端侧分类器的**自动训练触发**接缝（PRD Requirement 3、M7 验收「开关默认关闭；关闭时不发生任何训练」）。
 *
 * 接口定义在 `:core:domain`，实现在 `:feature:ai`（与 `CategoryClassifier` / `MlSampleRepository` 同构的
 * 「接口向内、实现向外」摆法）。`ConfirmAiEntryUseCase` 在**修正写入训练样本之后**调用本接口；
 * 具体是否训练由实现判断（自动训练开关是否开启、未训练样本是否达到阈值）。
 *
 * ## 为什么是「非挂起、即发即弃」
 * [ConfirmAiEntryUseCase] 的调用方（界面）必须立刻拿到「已入账」结果，训练在 `Dispatchers.Default` 上
 * 进程内异步进行（ARCHITECTURE §7），不阻塞用户动作。因此本方法的实现**必须**自行在应用级协程作用域里
 * 启动训练，并**吞掉一切异常**——训练失败不得影响已经保存的修正（PRD Requirement 3）。
 */
fun interface MlModelTrainer {

    /**
     * 一次修正写入 `ml_training_sample` 后被调用。实现内部自行判断开关与样本阈值并异步训练；
     * **不得抛出异常**（异常只会在实现内部被吞掉，训练失败时样本保持未训练）。
     */
    fun onTrainingSampleWritten(ledgerId: String)
}
