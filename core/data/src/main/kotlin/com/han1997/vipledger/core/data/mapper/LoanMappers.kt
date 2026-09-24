package com.han1997.vipledger.core.data.mapper

import com.han1997.vipledger.core.database.entity.AttachmentEntity
import com.han1997.vipledger.core.database.entity.InstallmentPeriodEntity
import com.han1997.vipledger.core.database.entity.InstallmentPlanEntity
import com.han1997.vipledger.core.database.entity.MerchantRuleEntity
import com.han1997.vipledger.core.database.entity.MlTrainingSampleEntity
import com.han1997.vipledger.core.database.entity.RecurringRuleEntity
import com.han1997.vipledger.core.domain.model.Attachment
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.model.money.Money
import com.han1997.vipledger.core.model.time.toDateColumnEpochMillis
import com.han1997.vipledger.core.model.time.toDateColumnLocalDate
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

internal fun decodeTagIds(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
}

internal fun encodeTagIds(ids: List<String>): String? = if (ids.isEmpty()) null else json.encodeToString(ids)

internal fun RecurringRuleEntity.toDomain() = RecurringRule(
    id = id,
    ledgerId = ledgerId,
    name = name,
    txnType = txnType,
    amount = Money.ofCents(amountCents),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    tagIds = decodeTagIds(tagIdsJson),
    title = title,
    remark = remark,
    freq = freq,
    interval = interval,
    byWeekday = byWeekday,
    byMonthDay = byMonthDay,
    byMonth = byMonth,
    startAt = startAt,
    endType = endType,
    endDate = endDate,
    maxOccurrences = maxOccurrences,
    generatedCount = generatedCount,
    nextOccurrenceAt = nextOccurrenceAt,
    lastGeneratedAt = lastGeneratedAt,
    generateMode = generateMode,
    missedPolicy = missedPolicy,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id,
    ledgerId = ledgerId,
    name = name,
    txnType = txnType,
    amountCents = amount.cents,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    tagIdsJson = encodeTagIds(tagIds),
    title = title,
    remark = remark,
    freq = freq,
    interval = interval,
    byWeekday = byWeekday,
    byMonthDay = byMonthDay,
    byMonth = byMonth,
    startAt = startAt,
    endType = endType,
    endDate = endDate,
    maxOccurrences = maxOccurrences,
    generatedCount = generatedCount,
    nextOccurrenceAt = nextOccurrenceAt,
    lastGeneratedAt = lastGeneratedAt,
    generateMode = generateMode,
    missedPolicy = missedPolicy,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun InstallmentPlanEntity.toDomain() = InstallmentPlan(
    id = id,
    ledgerId = ledgerId,
    accountId = accountId,
    name = name,
    method = method,
    principal = Money.ofCents(principalCents),
    periods = periods,
    annualRateBp = annualRateBp,
    upfrontFee = Money.ofCents(upfrontFeeCents),
    perPeriodFee = Money.ofCents(perPeriodFeeCents),
    firstDueDate = firstDueDate.toDateColumnLocalDate(),
    dueDay = dueDay,
    status = status,
    remark = remark,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun InstallmentPlan.toEntity() = InstallmentPlanEntity(
    id = id,
    ledgerId = ledgerId,
    accountId = accountId,
    name = name,
    method = method,
    principalCents = principal.cents,
    periods = periods,
    annualRateBp = annualRateBp,
    upfrontFeeCents = upfrontFee.cents,
    perPeriodFeeCents = perPeriodFee.cents,
    firstDueDate = firstDueDate.toDateColumnEpochMillis(),
    dueDay = dueDay,
    status = status,
    remark = remark,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun InstallmentPeriodEntity.toDomain() = InstallmentPeriod(
    id = id,
    planId = planId,
    periodNo = periodNo,
    dueDate = dueDate.toDateColumnLocalDate(),
    principal = Money.ofCents(principalCents),
    interest = Money.ofCents(interestCents),
    fee = Money.ofCents(feeCents),
    status = status,
    paidTxnId = paidTxnId,
    paidAt = paidAt,
)

internal fun InstallmentPeriod.toEntity() = InstallmentPeriodEntity(
    id = id,
    planId = planId,
    periodNo = periodNo,
    dueDate = dueDate.toDateColumnEpochMillis(),
    principalCents = principal.cents,
    interestCents = interest.cents,
    feeCents = fee.cents,
    totalCents = total.cents,
    status = status,
    paidTxnId = paidTxnId,
    paidAt = paidAt,
)

internal fun MerchantRuleEntity.toDomain() = MerchantRule(
    id = id,
    ledgerId = ledgerId,
    pattern = pattern,
    matchType = matchType,
    targetField = targetField,
    categoryId = categoryId,
    priority = priority,
    source = source,
    enabled = enabled,
    hitCount = hitCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun MerchantRule.toEntity() = MerchantRuleEntity(
    id = id,
    ledgerId = ledgerId,
    pattern = pattern,
    matchType = matchType,
    targetField = targetField,
    categoryId = categoryId,
    priority = priority,
    source = source,
    enabled = enabled,
    hitCount = hitCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun AttachmentEntity.toDomain() = Attachment(
    id = id,
    txnId = txnId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    relativePath = relativePath,
    createdAt = createdAt,
    deletedAt = deletedAt,
)

internal fun Attachment.toEntity() = AttachmentEntity(
    id = id,
    txnId = txnId,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    relativePath = relativePath,
    createdAt = createdAt,
    deletedAt = deletedAt,
)

internal fun MlTrainingSampleEntity.toDomain() = MlTrainingSample(
    id = id,
    ledgerId = ledgerId,
    text = text,
    categoryId = categoryId,
    source = source,
    trainedVersion = trainedVersion,
    createdAt = createdAt,
)

internal fun MlTrainingSample.toEntity() = MlTrainingSampleEntity(
    id = id,
    ledgerId = ledgerId,
    text = text,
    categoryId = categoryId,
    source = source,
    trainedVersion = trainedVersion,
    createdAt = createdAt,
)