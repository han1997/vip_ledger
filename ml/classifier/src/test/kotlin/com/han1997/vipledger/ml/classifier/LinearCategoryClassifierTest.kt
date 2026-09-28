package com.han1997.vipledger.ml.classifier

import com.han1997.vipledger.core.domain.nlp.CategoryClassifier
import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import com.han1997.vipledger.ml.classifier.model.LinearModel
import com.han1997.vipledger.ml.classifier.store.ModelLoadResult
import com.han1997.vipledger.ml.classifier.store.ModelStore
import com.han1997.vipledger.ml.classifier.train.TrainingSample
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinearCategoryClassifierTest {

    private val food = listOf("午饭 面", "晚饭 米饭", "早餐 包子", "午饭 盖饭", "夜宵 烧烤", "午饭 快餐")
    private val transport = listOf("打车 回家", "地铁 上班", "公交 两元", "打车 机场", "高铁 车票", "地铁 通勤")

    private fun dataset(): List<TrainingSample> =
        food.map { TrainingSample(it, "cat-food") } + transport.map { TrainingSample(it, "cat-transport") }

    private fun classifier(vararg validIds: String) = LinearCategoryClassifier(
        initialCategoryIdFilter = CategoryIdFilter.from(validIds.toSet()),
    )

    private fun flatModel(labels: List<String>): LinearModel {
        val dim = CharNgramFeaturizer.BUCKETS
        return LinearModel(
            labels = labels.sorted(),
            dim = dim,
            weights = Array(labels.size) { FloatArray(dim) },
            bias = FloatArray(labels.size),
            labelCounts = IntArray(labels.size) { 5 },
            trainedAt = 1L,
            modelVersion = "m-1",
        )
    }

    @Test
    fun returnsNoSuggestionsWithoutModel() = runTest {
        val subject = classifier("cat-food", "cat-transport")
        assertTrue(subject.classify("午饭", "面").isEmpty())
        assertNull(subject.modelSummary())
    }

    @Test
    fun suggestsLearnedCategoryThroughRankedThreshold() = runTest {
        val subject = classifier("cat-food", "cat-transport")
        subject.train(dataset(), epochs = 200, learningRate = 0.5f)

        val top = subject.classify("午饭", "面").first()
        assertEquals("cat-food", top.categoryId)
        assertTrue(top.confidence >= CategoryClassifier.MIN_CONFIDENCE)
    }

    @Test
    fun dropsStaleCategoryIdsAfterDeletion() = runTest {
        val subject = classifier("cat-food", "cat-transport")
        subject.train(dataset(), epochs = 200, learningRate = 0.5f)
        assertEquals("cat-transport", subject.classify("打车", "回家").first().categoryId)

        // 分类被删除：只剩 cat-food 是当前真实分类，陈旧 id 必须被丢弃、且不崩。
        subject.updateCategoryIdFilter(CategoryIdFilter.from(setOf("cat-food")))
        assertTrue(subject.classify("打车", "回家").none { it.categoryId == "cat-transport" })
    }

    @Test
    fun neverSuggestsBelowThresholdClasses() = runTest {
        val samples = (1..5).map { TrainingSample("午饭 $it", "cat-food") } +
            listOf(TrainingSample("机票 改签", "cat-travel"))
        val subject = classifier("cat-food", "cat-travel")
        val result = subject.train(samples, epochs = 50)!!

        assertEquals(mapOf("cat-travel" to 1), result.skippedClasses)
        assertTrue(subject.classify("机票", "改签").none { it.categoryId == "cat-travel" })
    }

    @Test
    fun doesNotSuggestFromSingleClassModel() = runTest {
        val subject = classifier("cat-food")
        subject.train((1..5).map { TrainingSample("午饭 $it", "cat-food") }, epochs = 50)

        // 单分类 softmax 恒为 1.0；此时不该给任意文本 100% 置信度的建议。
        assertEquals(listOf("cat-food"), subject.currentModel?.labels)
        assertTrue(subject.classify("午饭", "面").isEmpty())
    }

    @Test
    fun dropsCandidatesBelowConfidenceThreshold() = runTest {
        val subject = classifier("a", "b", "c")
        subject.loadModel(flatModel(listOf("a", "b", "c")))

        // 零权重 → 每类概率 1/3，全部低于 MIN_CONFIDENCE，ranked() 应把它们全部滤掉。
        assertTrue(subject.classify("午饭", "面").isEmpty())
    }

    @Test
    fun loadModelNullClearsInference() = runTest {
        val subject = classifier("cat-food", "cat-transport")
        subject.train(dataset(), epochs = 200, learningRate = 0.5f)
        assertTrue(subject.classify("午饭", "面").isNotEmpty())

        subject.loadModel(null)
        assertTrue(subject.classify("午饭", "面").isEmpty())
    }

    @Test
    fun loadPersistedUsesStoreAndReportsMissing() = runTest {
        val stored = flatModel(listOf("cat-food"))
        val store = InMemoryStore(ModelLoadResult.Loaded(stored))
        // 过滤器是必填参数；这条路径只验加载，显式选 ACCEPT_ALL（后校验由 dropsStaleCategoryIdsAfterDeletion 覆盖）。
        val subject = LinearCategoryClassifier(store = store, initialCategoryIdFilter = CategoryIdFilter.ACCEPT_ALL)
        assertIs<ModelLoadResult.Loaded>(subject.loadPersisted())
        assertEquals(listOf("cat-food"), subject.currentModel?.labels)

        val empty = LinearCategoryClassifier(
            store = InMemoryStore(ModelLoadResult.Missing),
            initialCategoryIdFilter = CategoryIdFilter.ACCEPT_ALL,
        )
        assertEquals(ModelLoadResult.Missing, empty.loadPersisted())
        assertNull(empty.currentModel)
    }

    @Test
    fun modelSummaryReportsSkippedClassesAfterReload() = runTest {
        // 训练出一个「label 表为空、只有 pending 分类」的模型，序列化再载入（模拟重启）。
        val subject = classifier("cat-travel")
        val result = subject.train(listOf(TrainingSample("机票 改签", "cat-travel")), epochs = 5)!!
        assertTrue(result.model.labels.isEmpty())

        val reloaded = LinearCategoryClassifier(
            store = InMemoryStore(ModelLoadResult.Loaded(result.model)),
            initialCategoryIdFilter = CategoryIdFilter.ACCEPT_ALL,
        )
        reloaded.loadPersisted()

        // 重启后不得谎报「没有被跳过的分类」：pendingCounts 随权重持久化，摘要必须照实给出。
        assertEquals(mapOf("cat-travel" to 1), reloaded.modelSummary()?.skippedClasses)
        // 标签表为空的模型端到端安全：不出任何建议、不崩。
        assertTrue(reloaded.classify("机票", "改签").isEmpty())
    }

    private class InMemoryStore(private val result: ModelLoadResult) : ModelStore {
        override fun load(): ModelLoadResult = result
        override fun save(model: LinearModel) = Unit
    }
}
