package com.han1997.vipledger.core.domain.nlp

/**
 * 端侧分类器接缝（PRD D1）：接口定义在 `:core:domain`，实现放 `:ml:classifier`。
 *
 * 管线调用约定在 M6 定型、M7 只替换实现：
 * - 输入是「标题 + 备注」；
 * - 返回 **top-N 候选 + 置信度**；
 * - 低于 [MIN_CONFIDENCE] 的候选视同「不出建议」（用 [ranked] 过滤）。
 *
 * M6 只提供返回空列表的占位实现；阈值与 top-N 形状固定，避免 M7 回头改管线。
 */
interface CategoryClassifier {

    suspend fun classify(title: String, remark: String): List<CategorySuggestion>

    companion object {
        /** 只保留前 3 个候选（ARCHITECTURE §7）。 */
        const val TOP_N: Int = 3

        /** 低于该置信度不出建议（ARCHITECTURE §7）。 */
        const val MIN_CONFIDENCE: Float = 0.5f
    }
}

/** 分类候选：分类 id + 置信度。id 必须由实现按真实分类标签表产出，不使用自由文本。 */
data class CategorySuggestion(
    val categoryId: String,
    val confidence: Float,
)

/** 按阈值过滤并取 top-N，供管线与实现共用，保证「低于阈值不出建议」只有一处口径。 */
fun List<CategorySuggestion>.ranked(): List<CategorySuggestion> =
    filter { it.confidence >= CategoryClassifier.MIN_CONFIDENCE }
        .sortedByDescending { it.confidence }
        .take(CategoryClassifier.TOP_N)
