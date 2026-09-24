package com.han1997.vipledger.core.domain.support

import com.han1997.vipledger.core.model.CategoryKind

/** 内置分类定义（两级）。名字是用户可见文案，由数据层从资源文件提供。 */
data class BuiltinCategory(
    val name: String,
    val kind: CategoryKind,
    val iconKey: String? = null,
    val children: List<BuiltinCategory> = emptyList(),
)

/** 新建账本时要落库的默认分类集合。 */
interface BuiltinCategorySource {
    fun categories(): List<BuiltinCategory>
}