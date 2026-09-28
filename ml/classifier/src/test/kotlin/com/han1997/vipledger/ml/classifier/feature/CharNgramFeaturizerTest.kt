package com.han1997.vipledger.ml.classifier.feature

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 特征口径的回归锚点。这里钉死的桶值与 [CharNgramFeaturizer] 里写的 FNV-1a + mask 规则一一对应：
 * 任何「不知不觉换了哈希/归一化口径」的改动都会让它变红，提醒同步改 `tools/trainer/` 的 Python 侧。
 */
class CharNgramFeaturizerTest {

    @Test
    fun hashesKnownNgramsToStableBuckets() {
        val twoGram = CharNgramFeaturizer.featuresOfText("午饭")
        assertContentEquals(intArrayOf(204402), twoGram.indices)
        assertContentEquals(floatArrayOf(1f), twoGram.values)

        val threeGram = CharNgramFeaturizer.featuresOfText("吃午饭")
        assertContentEquals(intArrayOf(204402, 228793, 253254), threeGram.indices)
        threeGram.values.forEach { assertEquals(1f / 3f, it, 1e-6f) }
    }

    @Test
    fun isDeterministicForSameInput() {
        assertEquals(
            CharNgramFeaturizer.featuresOfText("打车回家 12"),
            CharNgramFeaturizer.featuresOfText("打车回家 12"),
        )
    }

    @Test
    fun returnsEmptyForBlankOrTooShortText() {
        assertEquals(SparseFeatures.EMPTY, CharNgramFeaturizer.featuresOfText(""))
        assertEquals(SparseFeatures.EMPTY, CharNgramFeaturizer.featuresOfText("   \t\n "))
        assertEquals(SparseFeatures.EMPTY, CharNgramFeaturizer.featuresOfText("a"))
    }

    @Test
    fun tfValuesSumToOne() {
        val features = CharNgramFeaturizer.featuresOfText("午饭 打 车 12")
        assertTrue(features.size > 0)
        assertEquals(1f, features.values.sum(), 1e-5f)
    }

    @Test
    fun normalizationLowercasesAndCollapsesWhitespace() {
        assertEquals(
            CharNgramFeaturizer.featuresOfText("starbucks coffee"),
            CharNgramFeaturizer.featuresOfText("  STARBUCKS   Coffee  "),
        )
    }

    @Test
    fun splitsByCodePointNotUtf16Unit() {
        // 单个 emoji 是 1 个码点：「🍜面」应只产出一个 2-gram；若按 UTF-16 code unit 切会得到 2–3 个。
        assertEquals(1, CharNgramFeaturizer.featuresOfText("🍜面").size)
    }

    @Test
    fun joinTextMatchesTrainingSampleConvention() {
        assertEquals("午饭", CharNgramFeaturizer.joinText("午饭", ""))
        assertEquals("备注", CharNgramFeaturizer.joinText("", "备注"))
        assertEquals("午饭 好吃", CharNgramFeaturizer.joinText("午饭", "好吃"))
        assertEquals("", CharNgramFeaturizer.joinText("", ""))
    }

    @Test
    fun featuresOfTitleAndRemarkMatchesJoinedText() {
        assertEquals(
            CharNgramFeaturizer.featuresOfText(CharNgramFeaturizer.joinText("午饭", "面")),
            CharNgramFeaturizer.featuresOf("午饭", "面"),
        )
    }
}
