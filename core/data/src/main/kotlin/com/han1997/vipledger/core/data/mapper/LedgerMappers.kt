package com.han1997.vipledger.core.data.mapper

import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.CategoryEntity
import com.han1997.vipledger.core.database.entity.LedgerEntity
import com.han1997.vipledger.core.database.entity.TagEntity
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.Ledger
import com.han1997.vipledger.core.domain.model.Tag
import com.han1997.vipledger.core.model.money.Money

internal fun LedgerEntity.toDomain() = Ledger(
    id = id,
    name = name,
    iconKey = iconKey,
    currencyCode = currencyCode,
    sortOrder = sortOrder,
    isDefault = isDefault,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Ledger.toEntity() = LedgerEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    currencyCode = currencyCode,
    sortOrder = sortOrder,
    isDefault = isDefault,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun AccountEntity.toDomain() = Account(
    id = id,
    ledgerId = ledgerId,
    name = name,
    type = type,
    iconKey = iconKey,
    colorArgb = colorArgb,
    initialBalance = Money.ofCents(initialBalanceCents),
    currentBalance = Money.ofCents(currentBalanceCents),
    creditLimit = creditLimitCents?.let { Money.ofCents(it) },
    billingDay = billingDay,
    dueDay = dueDay,
    includeInNetWorth = includeInNetWorth,
    isArchived = isArchived,
    sortOrder = sortOrder,
    remark = remark,
    cardNo = cardNo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Account.toEntity() = AccountEntity(
    id = id,
    ledgerId = ledgerId,
    name = name,
    type = type,
    iconKey = iconKey,
    colorArgb = colorArgb,
    initialBalanceCents = initialBalance.cents,
    currentBalanceCents = currentBalance.cents,
    creditLimitCents = creditLimit?.cents,
    billingDay = billingDay,
    dueDay = dueDay,
    includeInNetWorth = includeInNetWorth,
    isArchived = isArchived,
    sortOrder = sortOrder,
    remark = remark,
    cardNo = cardNo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun CategoryEntity.toDomain() = Category(
    id = id,
    ledgerId = ledgerId,
    parentId = parentId,
    name = name,
    kind = kind,
    iconKey = iconKey,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    isSystem = isSystem,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Category.toEntity() = CategoryEntity(
    id = id,
    ledgerId = ledgerId,
    parentId = parentId,
    name = name,
    kind = kind,
    iconKey = iconKey,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    isSystem = isSystem,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TagEntity.toDomain() = Tag(
    id = id,
    ledgerId = ledgerId,
    name = name,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Tag.toEntity() = TagEntity(
    id = id,
    ledgerId = ledgerId,
    name = name,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)