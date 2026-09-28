package com.han1997.vipledger.feature.ai.model

import com.han1997.vipledger.core.domain.ml.MlModelTrainer
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 自动训练触发器的实现（PRD Requirement 3、M7 验收「开关默认关闭；关闭时不发生任何训练」）。
 *
 * 触发判据（ARCHITECTURE §7）：**自动训练开关开启** 且 **未训练样本数 ≥ [AUTO_TRAIN_THRESHOLD]**。
 * 二者缺一都不训练。训练在注入的 [scope]（应用级、`Dispatchers.Default`）上**即发即弃**，
 * 绝不阻塞调用方、也绝不把异常抛出去——修正已经提交，训练失败只让样本保持未训练。
 *
 * 依赖全部以函数/接口注入，便于纯 JVM 单测按「训练是否被调用」计数，而不是去窥探状态。
 */
class AutoTrainTrigger(
    private val isAutoTrainEnabled: () -> Boolean,
    private val listUntrained: suspend (ledgerId: String) -> List<MlTrainingSample>,
    private val sampleTrainer: SampleTrainer,
    private val scope: CoroutineScope,
    private val threshold: Int = AUTO_TRAIN_THRESHOLD,
) : MlModelTrainer {

    /**
     * 修正写入样本后调用（[com.han1997.vipledger.core.domain.usecase.txn.ConfirmAiEntryUseCase]）。
     * 异步评估并训练；开关关闭或样本不足时**什么都不做**。异常被吞掉，不影响用户已保存的修正。
     */
    override fun onTrainingSampleWritten(ledgerId: String) {
        scope.launch {
            runCatching { trainIfEligible(ledgerId) }
        }
    }

    /**
     * 同步判定的挂起版本（便于确定性单测）：开关关闭 → 不训练；未训练样本低于阈值 → 不训练；
     * 否则训练并标记。返回是否真的发生了一次成功训练。
     */
    suspend fun trainIfEligible(ledgerId: String): Boolean {
        if (!isAutoTrainEnabled()) return false
        val untrained = listUntrained(ledgerId)
        if (untrained.size < threshold) return false
        return sampleTrainer.trainAndMark(untrained) != null
    }

    companion object {
        /** 未训练样本阈值（ARCHITECTURE §7）：达到才自动训练。 */
        const val AUTO_TRAIN_THRESHOLD: Int = 30
    }
}
