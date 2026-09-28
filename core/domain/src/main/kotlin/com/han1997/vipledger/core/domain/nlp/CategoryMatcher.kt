package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.domain.model.Category

/**
 * P8 分类匹配：`merchant_rule` 未命中时，用标题文本匹配调用方传入的分类名，
 * 产出分类 id 候选（不自由生成）。名称按长度降序，避免「餐饮」被「餐」抢先。
 */
object CategoryMatcher {

    fun match(title: String, categories: List<Category>): String? {
        if (title.isBlank()) return null
        return categories
            .filter { it.name.isNotBlank() && !it.isArchived }
            .sortedByDescending { it.name.length }
            .firstOrNull { title.contains(it.name) }
            ?.id
    }
}
