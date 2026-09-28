package com.han1997.vipledger.feature.ai.model

import android.content.Context
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import com.han1997.vipledger.core.domain.repository.MlSampleRepository
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.ml.classifier.CategoryIdFilter
import com.han1997.vipledger.ml.classifier.LinearCategoryClassifier
import com.han1997.vipledger.ml.classifier.ModelSummary
import com.han1997.vipledger.ml.classifier.store.ModelFileStore
import com.han1997.vipledger.ml.classifier.train.TrainingSample
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 端侧模型的**唯一持有者**（PRD D3 / Requirement 6）——`:feature:ai` 里的应用级单例。
 *
 * ## 为什么是单例、且只有这一处
 * 管线（推理）、自动训练触发点、模型管理页**必须共用同一个** [LinearCategoryClassifier]：
 * 两处实例会导致「训练一份、推理另一份」。这里集中持有它，并把它
 * 以 [CategoryClassifier][com.han1997.vipledger.core.domain.nlp.CategoryClassifier] 的形式暴露给 Hilt，
 * 训练与页面则用同一个实例的 `train` / `modelSummary` / `loadModel`。
 *
 * ## 分类后校验（PRD Requirement 8 / D3#1）
 * `initialCategoryIdFilter` 是构造**必填**参数（`:ml:classifier` 刻意没有「不过滤」默认值）。
 * 这里注入 [CategoryIdFilter.fromCurrent]，来源是**当前账本的非归档分类 id**，由 [scope] 上的收集器
 * 随「当前账本 / 分类增删」持续刷新——账本在运行中切换、分类被删除后，陈旧标签立刻被丢弃。
 * **绝不使用 `ACCEPT_ALL`**（那正是 D3#1 要关掉的后门）。
 *
 * ## 权重文件
 * `ModelFileStore(filesDir/category_model.json)`；训练与导入都走它的原子替换（PRD AC「原子替换」）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OnDeviceModelManager @Inject constructor(
    @ApplicationContext context: Context,
    private val sampleRepository: MlSampleRepository,
    observeCurrentLedger: ObserveCurrentLedgerUseCase,
    observeCategoryTree: ObserveCategoryTreeUseCase,
    private val scope: CoroutineScope,
) {

    private val modelFile = File(context.filesDir, ModelFileStore.DEFAULT_MODEL_FILE_NAME)
    private val store = ModelFileStore(modelFile)

    /** 当前账本的合法分类 id 快照；由收集器持续更新，过滤器每次现读它。 */
    private val currentCategoryIds = AtomicReference<Set<String>>(emptySet())

    /** 共享的分类器实例：管线推理、训练、模型页读的都是它。 */
    val classifier = LinearCategoryClassifier(
        store = store,
        initialCategoryIdFilter = CategoryIdFilter.fromCurrent { currentCategoryIds.get() },
    )

    init {
        // 载入既有权重：无文件/损坏都安全降级为「无可用模型」，不崩（error-handling）。
        classifier.loadPersisted()
        // 持续跟踪「当前账本的真实分类」：账本切换、分类被删/改后立即生效。
        scope.launch {
            observeCurrentLedger()
                .flatMapLatest { ledger ->
                    if (ledger == null) {
                        flowOf(emptySet())
                    } else {
                        // includeArchived = false：已删除（归档）的分类不算「当前真实分类」，
                        // 模型给它们的陈旧候选必须被过滤掉（PRD Requirement 8）。
                        observeCategoryTree(ledger.id, includeArchived = false)
                            .map { nodes -> categoryIdsOf(nodes) }
                            // 账本切换：新账本的真实分类到达前先清空快照，避免「新账本里还在用旧账本分类」
                            // 的窗口（陈旧标签必须**立刻**被丢弃，不能等到新分类流首次发射）。
                            // 同账本内的分类增删不经过这里，由 Room 流直接刷新，无需清空。
                            .onStart { emit(emptySet()) }
                    }
                }
                .collect { currentCategoryIds.set(it) }
        }
    }

    /** 模型管理页展示用摘要；没有可用模型时为 null。 */
    fun summary(): ModelSummary? = classifier.modelSummary()

    /** 原始训练入口：训练并**原子落盘**；返回产出的模型版本，未落盘/无样本返回 null。 */
    suspend fun trainRaw(samples: List<MlTrainingSample>): String? {
        if (samples.isEmpty()) return null
        val result = classifier.train(samples.map { TrainingSample(text = it.text, categoryId = it.categoryId) })
            ?: return null
        // 只有真正写入权重文件才算成功：否则重启后模型丢失、样本却被标记为已训练。
        return if (result.savedToDisk) result.model.modelVersion else null
    }

    suspend fun untrainedCount(ledgerId: String): Int = sampleRepository.listUntrained(ledgerId).size

    suspend fun totalSampleCount(ledgerId: String): Int = sampleRepository.countByLedger(ledgerId)

    /**
     * 把 `ml_training_sample` 导出为 Python 训练脚本消费的 **JSON Lines**（PRD Requirement 7）。
     *
     * 一行一个对象：`{"text": <已拼接的标题+备注>, "categoryId": <非空白>}`——与
     * `tools/trainer/samples_io.py` 的契约逐字对齐（`text` 已是库里的拼接文本，不再拆标题/备注）。
     * 用 `org.json` 做转义，避免手拼字符串在引号/换行上产出非法 JSON。**不打印样本内容**（隐私红线）。
     */
    suspend fun exportSamplesJsonl(ledgerId: String): String =
        sampleRepository.listByLedger(ledgerId)
            // categoryId 非空白是 `samples_io.py` 契约的硬要求（全空白等于没有分类）。
            // 正常情况下库里不会出现空白分类 id，但导出侧再挡一道，保证产出的文件一定可被 Python 解析
            // ——否则 Python 会因一行畸形**整体**拒绝这份文件，导出等于白导。
            .filter { it.categoryId.isNotBlank() }
            .joinToString(separator = "\n") { sample ->
                JSONObject()
                    .put("text", sample.text)
                    .put("categoryId", sample.categoryId)
                    .toString()
            }

    /** 读取当前权重文件原文用于导出；没有模型时返回 null。 */
    fun exportModelText(): String? =
        if (modelFile.isFile) runCatching { modelFile.readText(Charsets.UTF_8) }.getOrNull() else null

    /**
     * 导入外部模型文件（PRD Requirement 6）：先用 [ModelImport.parse] 经 `ModelCodec` 严格校验，
     * 通过后原子落盘并切为当前模型；不兼容/损坏一律**拒绝**并保留原模型。
     */
    fun importModel(text: String): ModelImportResult {
        val model = ModelImport.parse(text) ?: return ModelImportResult.Invalid
        return try {
            store.save(model)
            classifier.loadModel(model)
            ModelImportResult.Imported(model.modelVersion)
        } catch (_: Exception) {
            ModelImportResult.PersistFailed
        }
    }

    private fun categoryIdsOf(nodes: List<CategoryNode>): Set<String> =
        nodes.flatMapTo(mutableSetOf()) { node -> listOf(node.category.id) + node.children.map { it.id } }
}
