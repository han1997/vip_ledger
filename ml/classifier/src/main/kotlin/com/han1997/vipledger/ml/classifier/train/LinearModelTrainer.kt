package com.han1997.vipledger.ml.classifier.train

import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import com.han1997.vipledger.ml.classifier.model.LinearModel
import com.han1997.vipledger.ml.classifier.model.MINIMUM_FEATURE_VERSION
import com.han1997.vipledger.ml.classifier.model.softmaxOf

/**
 * 多项式逻辑回归（softmax）+ L1-TF 特征的**增量** SGD 训练（ARCHITECTURE §7）。
 *
 * ## 增量语义
 * 传入 [base]（上一轮权重）即从它继续，而不是从零重训：已有标签的权重原样继承，新标签补零行。
 * 同时把每个标签**累计**样本数记进模型（[LinearModel.labelCounts]）——类别不平衡抑制（PRD D3#3）
 * 依据的是累计量而非单批样本量，否则「每轮只到 1 条」的成熟分类会在每轮被误判为样本不足。
 *
 * ## 类别不平衡抑制（PRD D3#3）
 * 累计样本数 < [TrainingConfig.minSamplesPerClass] 的分类：不参与本轮训练、不进标签表，
 * 因此也永远不会出现在建议里；它们以「分类 id → 累计样本数」写进 [TrainingResult.skippedClasses]。
 * 计数**照样累加**，所以当某个分类慢慢攒够阈值后会自动转为可训练（冷启动延迟，符合预期）。
 *
 * ## 确定性
 * 同一样本集合 + 同一 [TrainingConfig]（含 seed）+ 同一 [base] → 逐位相同的权重（Kotlin 侧）。
 * 洗牌走 [DeterministicRandom]，零权重初始化不引入任何 RNG。
 *
 * ## 与 Python 同构
 * Python 侧必须复刻：特征（见 [CharNgramFeaturizer] 注释）、softmax（[softmaxOf] 的顺序）、
 * 前向 `logits[c] += w[c][f] * v`、更新 `step = lr*diff; w[c][f] -= step*v`、以及每 epoch 的
 * `DeterministicRandom(seed + epoch)` 洗牌。注意 Kotlin 侧是 **float32**、Python 标准库是 float64，
 * 因此两边只能做**容差**比较，或在 Python 侧显式按 float32 取整。
 */
object LinearModelTrainer {

    fun train(
        base: LinearModel?,
        samples: List<TrainingSample>,
        config: TrainingConfig = TrainingConfig(),
        featurizer: CharNgramFeaturizer = CharNgramFeaturizer,
        now: Long,
        modelVersion: String = modelVersionFor(now),
    ): TrainingResult? {
        // 1. 累计每个分类的样本数（继承 base 的历史计数，含尚未进标签表的「待定」分类）。
        val mergedCounts = LinkedHashMap<String, Int>()
        if (base != null) {
            for (i in base.labels.indices) {
                mergedCounts[base.labels[i]] = base.labelCounts[i]
            }
            for ((label, count) in base.pendingCounts) {
                mergedCounts[label] = (mergedCounts[label] ?: 0) + count
            }
        }
        for (sample in samples) {
            mergedCounts[sample.categoryId] = (mergedCounts[sample.categoryId] ?: 0) + 1
        }

        // 一条样本都没有、也没有可继承的历史计数：没有可记录的东西。
        if (mergedCounts.isEmpty()) return null

        // 2. 按阈值分流出可训练标签与「待定」标签；标签升序是确定性契约的一部分。
        val eligibleLabels = mergedCounts.filterValues { it >= config.minSamplesPerClass }.keys.sorted()
        val pendingCounts = mergedCounts
            .filterValues { it < config.minSamplesPerClass }
            .toSortedMap()

        val pendingSet = pendingCounts.keys
        val trainable = samples.filter { it.categoryId !in pendingSet }

        // 3. 继承 base 已有标签的权重/偏置，新标签补零（零初始化，无需 RNG）。
        val dim = CharNgramFeaturizer.BUCKETS
        val weights = Array(eligibleLabels.size) { FloatArray(dim) }
        val bias = FloatArray(eligibleLabels.size)
        if (base != null) {
            val baseIndex = HashMap<String, Int>(base.labels.size)
            for (i in base.labels.indices) baseIndex[base.labels[i]] = i
            for (c in eligibleLabels.indices) {
                val from = baseIndex[eligibleLabels[c]] ?: continue
                weights[c] = base.weights[from].copyOf()
                bias[c] = base.bias[from]
            }
        }
        val labelCounts = IntArray(eligibleLabels.size) { mergedCounts.getValue(eligibleLabels[it]) }
        val labelIndex = HashMap<String, Int>(eligibleLabels.size)
        for (c in eligibleLabels.indices) labelIndex[eligibleLabels[c]] = c

        // 4. 逐 epoch 打乱后做单样本 SGD。
        if (trainable.isNotEmpty() && eligibleLabels.isNotEmpty() && config.epochs > 0) {
            val lr = config.learningRate
            val classCount = eligibleLabels.size
            for (epoch in 0 until config.epochs) {
                val order = DeterministicRandom(config.seed + epoch).permutation(trainable.size)
                for (position in order) {
                    val sample = trainable[position]
                    val features = featurizer.featuresOfText(sample.text)
                    if (features.size == 0) continue

                    val logits = FloatArray(classCount) { bias[it] }
                    for (i in features.indices.indices) {
                        val f = features.indices[i]
                        val v = features.values[i]
                        for (c in 0 until classCount) logits[c] += weights[c][f] * v
                    }
                    val probs = softmaxOf(logits)
                    val target = labelIndex.getValue(sample.categoryId)
                    for (c in 0 until classCount) {
                        val diff = probs[c] - if (c == target) 1f else 0f
                        if (diff == 0f) continue
                        val step = lr * diff
                        bias[c] -= step
                        for (i in features.indices.indices) {
                            weights[c][features.indices[i]] -= step * features.values[i]
                        }
                    }
                }
            }
        }

        val model = LinearModel(
            labels = eligibleLabels,
            dim = dim,
            weights = weights,
            bias = bias,
            labelCounts = labelCounts,
            trainedAt = now,
            modelVersion = modelVersion,
            featureVersion = MINIMUM_FEATURE_VERSION,
            sampleCount = labelCounts.sum(),
            pendingCounts = pendingCounts,
        )
        return TrainingResult(
            model = model,
            trainedSamples = trainable.size,
            epochs = config.epochs,
            skippedClasses = pendingCounts,
        )
    }

    /** 模型版本号：人类可读、含特征规格版本与训练时间，可用于版本化与回滚。 */
    fun modelVersionFor(now: Long): String = "v$MINIMUM_FEATURE_VERSION-$now"
}
