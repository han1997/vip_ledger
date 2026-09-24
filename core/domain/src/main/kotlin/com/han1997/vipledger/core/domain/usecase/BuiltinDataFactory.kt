package com.han1997.vipledger.core.domain.usecase

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.support.BuiltinCategory
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.money.Money

/**
 * 新建账本时要落库的初始数据（默认现金账户 + 内置两级分类）。
 *
 * 首次启动初始化与「新建账本」共用同一份构造逻辑，避免两处各写一遍导致口径漂移。
 */
internal object BuiltinDataFactory {

    const val DEFAULT_CASH_ACCOUNT_NAME: String = "现金"

    fun cashAccount(
        ledgerId: String,
        idGenerator: IdGenerator,
        now: Long,
        name: String = DEFAULT_CASH_ACCOUNT_NAME,
    ): Account = Account(
        id = idGenerator.newId(),
        ledgerId = ledgerId,
        name = name,
        type = AccountType.CASH,
        initialBalance = Money.ZERO,
        currentBalance = Money.ZERO,
        sortOrder = 0,
        createdAt = now,
        updatedAt = now,
    )

    fun categories(
        source: BuiltinCategorySource,
        ledgerId: String,
        idGenerator: IdGenerator,
        now: Long,
    ): List<Category> {
        val result = mutableListOf<Category>()
        var topSort = 0
        source.categories().forEach { builtin ->
            val parentId = idGenerator.newId()
            result += builtin.toCategory(ledgerId, parentId, null, topSort++, now)
            builtin.children.forEachIndexed { index, child ->
                result += child.toCategory(ledgerId, idGenerator.newId(), parentId, index, now)
            }
        }
        return result
    }

    private fun BuiltinCategory.toCategory(
        ledgerId: String,
        id: String,
        parentId: String?,
        sortOrder: Int,
        now: Long,
    ) = Category(
        id = id,
        ledgerId = ledgerId,
        parentId = parentId,
        name = name,
        kind = kind,
        iconKey = iconKey,
        sortOrder = sortOrder,
        isSystem = true,
        createdAt = now,
        updatedAt = now,
    )
}