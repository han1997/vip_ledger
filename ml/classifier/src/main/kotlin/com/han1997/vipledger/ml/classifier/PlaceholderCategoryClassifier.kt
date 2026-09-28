package com.han1997.vipledger.ml.classifier

import com.han1997.vipledger.core.domain.nlp.CategoryClassifier
import com.han1997.vipledger.core.domain.nlp.CategorySuggestion

/**
 * **占位实现，不是可用分类器。**
 *
 * M6 只预留接缝（PRD D1）：本类对任何输入都返回**空候选列表**，因此管线永远不会从它得到
 * 任何分类建议——它绝不会伪装成「分类成功」而干扰规则层的判定。
 *
 * 真正的字符 n-gram TF + 多项式逻辑回归实现在 M7 落地，届时直接替换本类，管线与阈值形状不变。
 */
class PlaceholderCategoryClassifier : CategoryClassifier {

    override suspend fun classify(title: String, remark: String): List<CategorySuggestion> = emptyList()

    companion object {
        const val PLACEHOLDER: Boolean = true
    }
}
