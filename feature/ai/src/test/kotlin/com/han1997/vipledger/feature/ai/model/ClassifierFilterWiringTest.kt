package com.han1997.vipledger.feature.ai.model

import com.han1997.vipledger.ml.classifier.CategoryIdFilter
import com.han1997.vipledger.ml.classifier.LinearCategoryClassifier
import com.han1997.vipledger.ml.classifier.train.TrainingSample
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * PR3 装配的分类后校验（PRD Requirement 8 / D3#1）：
 * 模型的标签表训练时固化，**当前账本没有的分类**绝不能出现在建议里。
 */
class ClassifierFilterWiringTest {

    private val food = listOf("午饭 面", "晚饭 米饭", "早餐 包子", "午饭 盖饭", "夜宵 烧烤", "午饭 快餐")
    private val stale = listOf("机票 改签", "酒店 预订", "高铁 车票", "打车 机场", "民宿 退订", "景点 门票")

    @Test
    fun wiredFilterNeverSuggestsCategoryMissingFromCurrentLedger() = runTest {
        // 训练出「餐饮 + 出行」两类，但当前账本只有餐饮：出行（陈旧/已删除）必须被过滤掉。
        val subject = LinearCategoryClassifier(
            initialCategoryIdFilter = CategoryIdFilter.from(setOf("cat-food")),
        )
        val samples = food.map { TrainingSample(it, "cat-food") } + stale.map { TrainingSample(it, "cat-travel") }
        subject.train(samples, epochs = 200, learningRate = 0.5f)

        assertTrue(
            subject.classify("打车 机场", "").none { it.categoryId == "cat-travel" },
            "不在当前账本分类表里的模型标签不得进入建议",
        )
    }

    @Test
    fun fromCurrentTracksLedgerChangesLive() {
        val current = mutableSetOf("cat-food")
        val filter = CategoryIdFilter.fromCurrent { current.toSet() }

        assertTrue(filter.isAllowed("cat-food"))
        assertFalse(filter.isAllowed("cat-travel"), "账本里没有的分类先被挡住")

        // 账本切换 / 分类增删：来源现读，立即生效（PR3 用收集器持续更新来源）。
        current += "cat-travel"
        assertTrue(filter.isAllowed("cat-travel"))

        current.clear()
        assertFalse(filter.isAllowed("cat-food"), "分类被删除后立即被挡住")
    }
}
