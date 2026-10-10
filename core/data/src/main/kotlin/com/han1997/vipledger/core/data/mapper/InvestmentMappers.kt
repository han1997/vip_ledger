package com.han1997.vipledger.core.data.mapper

import com.han1997.vipledger.core.database.entity.InvestmentEntity
import com.han1997.vipledger.core.database.entity.InvestmentLinkEntity
import com.han1997.vipledger.core.domain.model.Investment
import com.han1997.vipledger.core.domain.model.InvestmentLink

internal fun InvestmentEntity.toDomain() = Investment(
    id = id,
    ledgerId = ledgerId,
    name = name,
    accountId = accountId,
    remark = remark,
    closed = closed,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Investment.toEntity() = InvestmentEntity(
    id = id,
    ledgerId = ledgerId,
    name = name,
    accountId = accountId,
    remark = remark,
    closed = closed,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun InvestmentLinkEntity.toDomain() = InvestmentLink(
    txnId = txnId,
    investmentId = investmentId,
    kind = kind,
    createdAt = createdAt,
)

internal fun InvestmentLink.toEntity() = InvestmentLinkEntity(
    txnId = txnId,
    investmentId = investmentId,
    kind = kind,
    createdAt = createdAt,
)