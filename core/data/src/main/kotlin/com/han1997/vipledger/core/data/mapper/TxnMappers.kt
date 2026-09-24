package com.han1997.vipledger.core.data.mapper

import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.database.entity.TxnWithTagIds
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.money.Money

/** 不带标签的映射：只算余额、做校验时用得上，省一次关联查询。 */
internal fun TxnEntity.toDomain(): Txn = toDomain(tagIds = emptyList())

internal fun TxnEntity.toDomain(tagIds: List<String>) = Txn(
    id = id,
    ledgerId = ledgerId,
    type = type,
    amount = Money.ofCents(amountCents),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    occurredAt = occurredAt,
    title = title,
    remark = remark,
    source = source,
    draftStatus = draftStatus,
    excludeFromStats = excludeFromStats,
    recurringRuleId = recurringRuleId,
    installmentPlanId = installmentPlanId,
    installmentPeriodNo = installmentPeriodNo,
    importHash = importHash,
    tagIds = tagIds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TxnWithTagIds.toDomain() = txn.toDomain(tagLinks.map { it.tagId })

internal fun Txn.toEntity() = TxnEntity(
    id = id,
    ledgerId = ledgerId,
    type = type,
    amountCents = amount.cents,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    occurredAt = occurredAt,
    title = title,
    remark = remark,
    source = source,
    draftStatus = draftStatus,
    excludeFromStats = excludeFromStats,
    recurringRuleId = recurringRuleId,
    installmentPlanId = installmentPlanId,
    installmentPeriodNo = installmentPeriodNo,
    importHash = importHash,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)