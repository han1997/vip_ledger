package com.han1997.vipledger.core.domain.model

/** 标签（docs/DATA-MODEL.md 3.4）。一笔账可以挂多个标签。 */
data class Tag(
    val id: String,
    val ledgerId: String,
    val name: String,
    val colorArgb: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)