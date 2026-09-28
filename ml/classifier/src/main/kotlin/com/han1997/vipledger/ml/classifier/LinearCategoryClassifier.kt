package com.han1997.vipledger.ml.classifier

import com.han1997.vipledger.core.domain.nlp.CategoryClassifier
import com.han1997.vipledger.core.domain.nlp.CategorySuggestion
import com.han1997.vipledger.core.domain.nlp.ranked
import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import com.han1997.vipledger.ml.classifier.model.LinearModel
import com.han1997.vipledger.ml.classifier.store.ModelLoadResult
import com.han1997.vipledger.ml.classifier.store.ModelStore
import com.han1997.vipledger.ml.classifier.train.LinearModelTrainer
import com.han1997.vipledger.ml.classifier.train.TrainingConfig
import com.han1997.vipledger.ml.classifier.train.TrainingResult
import com.han1997.vipledger.ml.classifier.train.TrainingSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 端侧分类器的**真实实现**（M7 替换 [PlaceholderCategoryClassifier] 的接缝，管线与阈值形状不变）：
 * 字符 2–3 gram 哈希 TF + 多项式逻辑回归，纯 Kotlin 本地推理与训练，不下载模型、不上传样本。
 *
 * ## 关键不变式
 * - **阈值/top-N 只有一处口径**：本类产出候选后一律经 `ranked()`（`MIN_CONFIDENCE` + `TOP_N`），
 *   这里不复制第二套阈值判断。
 * - **候选必须后校验**：`categoryId` 要过 [CategoryIdFilter]——分类被删/改名后模型给的陈旧 id 被丢弃，
 *   绝不进入草稿（PRD D3#1）。
 * - **无模型 / 样本不足 → 空候选**：没有可用模型、可训练分类少于 2 个、或特征为空时直接返回空列表，
 *   绝不给出低置信度猜测，因此不会干扰规则层与云端（ARCHITECTURE §6 的降级顺序不变）。
 * - **训练失败不破坏旧模型**：权重文件走原子替换；保存失败时内存里仍保留旧模型，`savedToDisk=false`。
 *
 * ## PR3 装配接缝
 * ```kotlin
 * // feature:ai 的 Hilt 绑定（示意）
 * val store = ModelFileStore(File(context.filesDir, ModelFileStore.DEFAULT_MODEL_FILE_NAME))
 * // initialCategoryIdFilter 是**必填**参数：没有「不过滤」的默认值，漏接编译不过。
 * val classifier = LinearCategoryClassifier(
 *     store = store,
 *     initialCategoryIdFilter = CategoryIdFilter.fromCurrent { currentLedgerCategoryIds() },
 * )
 * classifier.loadPersisted()
 * // 账本切换 / 分类增删后再同步一次（fromCurrent 已每次现读；快照式 [CategoryIdFilter.from] 需重设）：
 * classifier.updateCategoryIdFilter(CategoryIdFilter.fromCurrent { currentLedgerCategoryIds() })
 * ```
 * [train] 是 `suspend` 且内部切到 [Dispatchers.Default]（ARCHITECTURE §7「训练在 Dispatchers.Default 上跑」），
 * 由 PR3 在「自动训练开关开启 + 未训练样本 ≥ 30」时调用。
 */
class LinearCategoryClassifier(
    private val store: ModelStore? = null,
    private val config: TrainingConfig = TrainingConfig(),
    private val featurizer: CharNgramFeaturizer = CharNgramFeaturizer,
    private val clock: () -> Long = System::currentTimeMillis,
    initialCategoryIdFilter: CategoryIdFilter,
) : CategoryClassifier {

    /** 当前内存中的模型；`@Volatile` 保证训练线程替换后推理线程立刻可见（不会读到半更新引用）。 */
    @Volatile
    var currentModel: LinearModel? = null
        private set

    @Volatile
    private var categoryIdFilter: CategoryIdFilter = initialCategoryIdFilter

    /**
     * 当前**已采纳模型**（内存里的这一份）因样本不足被跳过的分类 → 累计样本数；供模型管理页展示（PRD D3#3）。
     * 训练成功换上新模型时更新；写盘失败保留旧模型时也保持与旧模型一致（不谎报）。
     */
    @Volatile
    var lastSkippedClasses: Map<String, Int> = emptyMap()
        private set

    /** 替换「当前合法分类」后校验器。PR3 在账本切换 / 分类增删后调用；App 内必须接。 */
    fun updateCategoryIdFilter(filter: CategoryIdFilter) {
        categoryIdFilter = filter
    }

    /** 从 [store] 载入权重；载入成功则替换内存模型。无 store / 无文件 / 文件损坏都不抛异常。 */
    fun loadPersisted(): ModelLoadResult {
        val modelStore = store ?: return ModelLoadResult.Missing
        val result = modelStore.load()
        if (result is ModelLoadResult.Loaded) {
            currentModel = result.model
            // pendingCounts 是「样本不足被跳过」的持久化来源；载入后同步它，重启后管理页仍能显示真相。
            lastSkippedClasses = result.model.pendingCounts
        }
        return result
    }

    /**
     * 直接替换内存模型，不碰文件：供「导入模型 / 回滚旧版本」（PRD Requirement 4）与单测注入使用；
     * 传 null 清空。
     */
    fun loadModel(model: LinearModel?) {
        currentModel = model
        lastSkippedClasses = model?.pendingCounts ?: emptyMap()
    }

    override suspend fun classify(title: String, remark: String): List<CategorySuggestion> {
        val model = currentModel ?: return emptyList()
        // 只有 ≥2 个可训练分类时 softmax 才有区分意义：单分类模型的概率恒为 1.0，
        // 会给任意文本一个 100% 置信度的建议——那正是「推一个模型无从判断的分类」，故不出建议。
        if (model.classCount < MIN_CLASSES_FOR_SUGGESTION) return emptyList()
        val filter = categoryIdFilter
        val features = featurizer.featuresOf(title, remark)
        if (features.size == 0) return emptyList()
        val probabilities = model.probabilitiesOf(features)
        val suggestions = ArrayList<CategorySuggestion>(model.classCount)
        for (c in 0 until model.classCount) {
            val categoryId = model.labels[c]
            // 后校验：陈旧/不存在的分类在此被丢弃，不进入 ranked() 的竞争。
            if (!filter.isAllowed(categoryId)) continue
            suggestions += CategorySuggestion(categoryId, probabilities[c])
        }
        // 唯一的阈值/top-N 口径在 ranked()，这里不自己再写一遍。
        return suggestions.ranked()
    }

    /**
     * 增量训练并原子替换权重（ARCHITECTURE §7 的 `train(samples, epochs, lr)` 形状）。
     *
     * 返回 null 仅在「既没有样本、也没有可继承的历史计数」时出现。若样本都还没达到类别阈值，
     * 仍会返回一个**标签表为空**的模型——它把「待定计数」持久化下来，让下一轮能继续攒够阈值
     * （[TrainingResult.skippedClasses] 即待定分类与计数）；此时 [classify] 一律返回空候选。
     * 训练在 [Dispatchers.Default] 上进行，不阻塞调用方（通常是界面）的线程。
     */
    suspend fun train(
        samples: List<TrainingSample>,
        epochs: Int = config.epochs,
        learningRate: Float = config.learningRate,
    ): TrainingResult? = withContext(Dispatchers.Default) {
        val effective = config.copy(epochs = epochs, learningRate = learningRate)
        val now = clock()
        val result = LinearModelTrainer.train(
            base = currentModel,
            samples = samples,
            config = effective,
            featurizer = featurizer,
            now = now,
            modelVersion = LinearModelTrainer.modelVersionFor(now),
        ) ?: return@withContext null

        val saved = persist(result.model)
        // 无 store 时（纯内存 / 单测）也接受新模型；有 store 但写失败时保留旧模型，保持内存与文件一致。
        val adopted = store == null || saved
        if (adopted) {
            currentModel = result.model
            // 只有真正换上新模型时才更新「被跳过的分类」，否则管理页会拿旧模型的 weights 配新模型的跳过表。
            lastSkippedClasses = result.skippedClasses
        }
        result.copy(savedToDisk = saved)
    }

    /** 供模型管理页展示的摘要（PRD Requirement 6）：版本、样本数、上次训练时间、被跳过的分类。 */
    fun modelSummary(): ModelSummary? {
        val model = currentModel ?: return null
        return ModelSummary(
            modelVersion = model.modelVersion,
            featureVersion = model.featureVersion,
            vocabVersion = model.vocabVersion,
            labels = model.labels,
            labelCounts = model.labelCounts.toList(),
            sampleCount = model.sampleCount,
            trainedAt = model.trainedAt,
            // 以当前模型的 pendingCounts 为准（它随权重一起持久化），这样重启后管理页仍能显示被跳过的分类，
            // 而不是拿内存里可能已空的 lastSkippedClasses 谎报「没有被跳过的分类」。
            skippedClasses = model.pendingCounts,
        )
    }

    private fun persist(model: LinearModel): Boolean {
        val modelStore = store ?: return false
        return try {
            modelStore.save(model)
            true
        } catch (_: Exception) {
            // 不吞：结果里以 savedToDisk=false 明确回报；此处不打印模型/账目文本（隐私红线）。
            false
        }
    }

    companion object {
        /** 少于这么多个可训练分类时不出建议：单分类 softmax 恒为 1.0，无区分意义（见 [classify]）。 */
        const val MIN_CLASSES_FOR_SUGGESTION: Int = 2
    }
}

/** 模型管理页展示用摘要。 */
data class ModelSummary(
    val modelVersion: String,
    val featureVersion: Int,
    val vocabVersion: String,
    val labels: List<String>,
    val labelCounts: List<Int>,
    val sampleCount: Int,
    val trainedAt: Long,
    val skippedClasses: Map<String, Int>,
)
