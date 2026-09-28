package com.han1997.vipledger.ml.classifier.model

import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import com.han1997.vipledger.ml.classifier.feature.SparseFeatures
import kotlin.math.exp
import kotlin.math.max

/**
 * 分类器可加载的特征规格版本；权重文件的 `featureVersion` 必须等于它才允许推理。
 * 与 [CharNgramFeaturizer.FEATURE_VERSION] 绑定，改一处即两处同步（const 编译期求值）。
 */
const val MINIMUM_FEATURE_VERSION: Int = CharNgramFeaturizer.FEATURE_VERSION

/**
 * 训练完成的线性分类器模型：**多项式逻辑回归（softmax）**（ARCHITECTURE §7）。
 *
 * - [labels]：分类 id 标签表，**升序**且与 [weights]/[bias]/[labelCounts] 逐行对齐。
 *   Python 侧导出时也必须用同一排序，否则同构对比会错位。
 * - [weights]：`FloatArray`，形状 `labels.size × dim`（[dim] 必须等于 [CharNgramFeaturizer.BUCKETS]）；
 *   `weights[c][f]` 是分类 c 在桶 f 上的权重。
 * - [bias]：每类一个偏置，长度 `labels.size`。
 * - [labelCounts]：每个标签**累计**见过的样本数，跨增量训练累加；用于类别不平衡抑制（PRD D3#3）。
 * - [pendingCounts]：累计样本数仍低于阈值、因此**未进标签表**的分类 → 累计样本数。
 *   持久化它是为了让「每轮只来一两条」的分类能慢慢攒够阈值后转为可训练（PRD D3#3 的冷启动），
 *   否则计数每轮从零开始、永远够不着阈值。键与 [labels] 不相交。
 */
class LinearModel(
    val labels: List<String>,
    val dim: Int,
    val weights: Array<FloatArray>,
    val bias: FloatArray,
    val labelCounts: IntArray,
    val trainedAt: Long,
    val modelVersion: String,
    val featureVersion: Int = MINIMUM_FEATURE_VERSION,
    val vocabVersion: String = CharNgramFeaturizer.VOCAB_VERSION,
    val sampleCount: Int = labelCounts.sum(),
    val pendingCounts: Map<String, Int> = emptyMap(),
) {

    /** 训练/加载期即校验结构，非法模型不允许进入推理路径。 */
    init {
        require(labels.size == weights.size) { "labels/weights size mismatch" }
        require(labels.size == bias.size) { "labels/bias size mismatch" }
        require(labels.size == labelCounts.size) { "labels/labelCounts size mismatch" }
        require(labels.size == labels.toSet().size) { "duplicate labels" }
        require(labels == labels.sorted()) { "labels must be sorted ascending" }
        require(dim > 0) { "dim must be positive" }
        require(pendingCounts.keys.none { it in labels }) { "pendingCounts overlaps labels" }
        weights.forEachIndexed { c, row ->
            require(row.size == dim) { "weights[$c] size ${row.size} != dim $dim" }
        }
    }

    val classCount: Int get() = labels.size

    /** softmax 概率；[logits] 长度必须等于 [classCount]。空模型返回空数组。 */
    fun probabilities(logits: FloatArray): FloatArray {
        require(logits.size == classCount) { "logits size ${logits.size} != classCount $classCount" }
        return softmaxOf(logits)
    }

    /** 对稀疏特征做前向：`logits[c] = bias[c] + Σ_f weights[c][f] * x[f]`（特征索引升序累加）。 */
    fun logits(features: SparseFeatures): FloatArray {
        val out = FloatArray(classCount)
        for (c in 0 until classCount) out[c] = bias[c]
        for (i in features.indices.indices) {
            val f = features.indices[i]
            val v = features.values[i]
            for (c in 0 until classCount) out[c] += weights[c][f] * v
        }
        return out
    }

    fun probabilitiesOf(features: SparseFeatures): FloatArray = probabilities(logits(features))

    override fun toString(): String =
        "LinearModel(version=$modelVersion, featureVersion=$featureVersion, labels=${labels.size}, " +
            "dim=$dim, sampleCount=$sampleCount)"
}

private fun FloatArray.max(): Float {
    var m = this[0]
    for (i in 1 until size) m = max(m, this[i])
    return m
}

/**
 * 数值稳定的 softmax。**训练与推理共用同一份实现**，`tools/trainer/` 的 Python 侧也要照抄这个顺序
 * （先减最大值、再 `exp`、最后除以总和），否则同构对比会因浮点顺序产生偏差。
 */
internal fun softmaxOf(logits: FloatArray): FloatArray {
    if (logits.isEmpty()) return FloatArray(0)
    val maxLogit = logits.max()
    val out = FloatArray(logits.size)
    var sum = 0f
    for (c in logits.indices) {
        val e = exp((logits[c] - maxLogit).toDouble()).toFloat()
        out[c] = e
        sum += e
    }
    val inv = 1f / sum
    for (c in logits.indices) out[c] *= inv
    return out
}
