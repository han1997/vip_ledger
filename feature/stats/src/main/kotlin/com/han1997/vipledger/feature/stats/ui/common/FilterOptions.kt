package com.han1997.vipledger.feature.stats.ui.common

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.model.Tag

/**
 * 多选条件的候选项：`id` 用于查询，`label` 只用于显示。
 *
 * 候选列表的拼装是纯函数，放在这里而不是 Screen 里，才能被单测覆盖
 * （见 .trellis/spec/android/ui.md 的「默认选中/合法性规则写成纯函数」）。
 */
data class FilterOption(
    val id: String,
    val label: String,
)

/**
 * 分类候选：一级分类在前，紧随它的二级分类，二级用「父 / 子」标注归属。
 *
 * 选一级分类时查询会把整棵子树展开（`SearchQueryMapper` + `CategoryTree.subtreeIds`），
 * 所以界面上同时给出两级入口，用户想精确到二级也能直接点。
 */
fun List<CategoryNode>.toCategoryOptions(): List<FilterOption> =
    flatMap { node ->
        listOf(FilterOption(node.category.id, node.category.name)) +
            node.children.map { child -> FilterOption(child.id, node.category.name + " / " + child.name) }
    }

fun List<Account>.toAccountOptions(): List<FilterOption> = map { FilterOption(it.id, it.name) }

fun List<Tag>.toTagOptions(): List<FilterOption> = map { FilterOption(it.id, it.name) }