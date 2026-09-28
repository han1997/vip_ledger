package com.han1997.vipledger.ml.classifier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** 占位实现的行为：任何输入都返回空候选，绝不伪装成可用分类器。 */
class PlaceholderCategoryClassifierTest {

    private val classifier = PlaceholderCategoryClassifier()

    @Test
    fun returnsNoCandidatesForAnyInput() = runTest {
        assertEquals(emptyList(), classifier.classify("午饭", ""))
        assertEquals(emptyList(), classifier.classify("星巴克", "咖啡"))
        assertTrue(classifier.classify("", "").isEmpty())
    }

    @Test
    fun isExplicitlyMarkedAsPlaceholder() {
        assertTrue(PlaceholderCategoryClassifier.PLACEHOLDER)
    }
}
