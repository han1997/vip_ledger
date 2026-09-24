package com.han1997.vipledger.core.designsystem.format

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.CategoryNode
import com.han1997.vipledger.core.domain.model.Tag

/**
 * 明细列表、搜索结果与统计下钻共用的「id → 名字」查表。
 *
 * 账目只存 categoryId / accountId / tagIds，展示层统一用这三个函数补齐名字，
 * 避免各页面各写一份展开逻辑（分类树要摊平成一级 + 二级）。
 */

/** 分类树摊平成一级 + 二级的 id → 名字。 */
fun List<CategoryNode>.categoryNameMap(): Map<String, String> =
    flatMap { node -> listOf(node.category) + node.children }.associate { it.id to it.name }

fun List<Account>.accountNameMap(): Map<String, String> = associate { it.id to it.name }

fun List<Tag>.tagNameMap(): Map<String, String> = associate { it.id to it.name }