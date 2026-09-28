package com.han1997.vipledger.ml.classifier.train

import com.han1997.vipledger.ml.classifier.model.LinearModel

/**
 * 一条训练样本：**已拼接**的「标题 + 备注」文本 + 分类 id。
 *
 * 文本口径与 [com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer.joinText] 一致，
 * 也就是 M6 落库的 `ml_training_sample.text`；`USER_CORRECTION` 与 `IMPORT` 两种来源都要喂进来
 * （PRD D3#2），本类型不区分来源、也不对来源加权。
 */
data class TrainingSample(
    val text: String,
    val categoryId: String,
)

/**
 * 训练超参与可配阈值。全部显式给出，保证「同一样本 + 同一超参 + 同一 seed」可复现同构结果。
 *
 * - [minSamplesPerClass]：类别不平衡抑制阈值（PRD D3#3，默认 3，可配）：累计样本数低于它的分类
 *   既不参与训练也不进标签表；
 * - [seed]：训练时打乱样本顺序的种子，必须显式给定，禁止依赖无种子 RNG。
 */
data class TrainingConfig(
    val epochs: Int = DEFAULT_EPOCHS,
    val learningRate: Float = DEFAULT_LEARNING_RATE,
    val minSamplesPerClass: Int = DEFAULT_MIN_SAMPLES_PER_CLASS,
    val seed: Long = DEFAULT_SEED,
) {
    init {
        require(epochs >= 0) { "epochs must be >= 0" }
        require(learningRate > 0f) { "learningRate must be > 0" }
        require(minSamplesPerClass >= 1) { "minSamplesPerClass must be >= 1" }
    }

    companion object {
        const val DEFAULT_EPOCHS: Int = 20
        const val DEFAULT_LEARNING_RATE: Float = 0.5f
        const val DEFAULT_MIN_SAMPLES_PER_CLASS: Int = 3

        /** 固定默认种子（2026-09-28 是 M7 需求走查日，取值无特殊含义，只求显式稳定）。 */
        const val DEFAULT_SEED: Long = 20260928L
    }
}

/**
 * 一次 [LinearModelTrainer.train] 的结果。
 *
 * - [skippedClasses]：因累计样本数低于阈值被跳过的分类 → 累计样本数（升序）。界面据此展示
 *   「哪些分类样本不足、被跳过了」（PRD D3#3），否则用户只会看到「分类器没反应」。
 * - [savedToDisk]：仅由 [com.han1997.vipledger.ml.classifier.LinearCategoryClassifier] 在成功原子
 *   替换权重文件后置 true；[LinearModelTrainer] 自身永远给 false。
 */
data class TrainingResult(
    val model: LinearModel,
    val trainedSamples: Int,
    val epochs: Int,
    val skippedClasses: Map<String, Int>,
    val savedToDisk: Boolean = false,
)
