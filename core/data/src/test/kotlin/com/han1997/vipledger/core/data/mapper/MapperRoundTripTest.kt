package com.han1997.vipledger.core.data.mapper

import com.han1997.vipledger.core.database.entity.AccountEntity
import com.han1997.vipledger.core.database.entity.InstallmentPeriodEntity
import com.han1997.vipledger.core.database.entity.InstallmentPlanEntity
import com.han1997.vipledger.core.database.entity.RecurringRuleEntity
import com.han1997.vipledger.core.database.entity.TxnEntity
import com.han1997.vipledger.core.database.entity.TxnTagEntity
import com.han1997.vipledger.core.database.entity.TxnWithTagIds
import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.InstallmentPeriod
import com.han1997.vipledger.core.domain.model.InstallmentPlan
import com.han1997.vipledger.core.domain.model.RecurringRule
import com.han1997.vipledger.core.domain.model.Txn
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.InstallmentMethod
import com.han1997.vipledger.core.model.RecurrenceFrequency
import com.han1997.vipledger.core.model.TransactionType
import com.han1997.vipledger.core.model.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapperRoundTripTest {

    @Test
    fun txnSurvivesEntityRoundTripIncludingTags() {
        val txn = Txn(
            id = "t1",
            ledgerId = "l1",
            type = TransactionType.TRANSFER,
            amount = Money.ofCents(12_345),
            accountId = "cash",
            toAccountId = "card",
            categoryId = null,
            occurredAt = 1_790_208_000_000L,
            title = "还信用卡",
            remark = "备注",
            excludeFromStats = true,
            tagIds = listOf("a", "b"),
            createdAt = 1,
            updatedAt = 2,
        )
        val entity = txn.toEntity()
        assertEquals(12_345L, entity.amountCents)
        assertNull(entity.categoryId)
        assertEquals(txn, entity.toDomain(tagIds = listOf("a", "b")))
        assertEquals(emptyList(), entity.toDomain().tagIds)
        assertEquals(txn, TxnWithTagIds(entity, listOf(TxnTagEntity("t1", "a"), TxnTagEntity("t1", "b"))).toDomain())
    }

    @Test
    fun accountMoneyAndFlagsRoundTrip() {
        val account = Account(
            id = "a1",
            ledgerId = "l1",
            name = "信用卡",
            type = AccountType.CREDIT_CARD,
            initialBalance = Money.ofCents(-50_000),
            currentBalance = Money.ofCents(-12_345),
            creditLimit = Money.ofCents(5_000_000),
            billingDay = 5,
            dueDay = 20,
            createdAt = 1,
            updatedAt = 2,
        )
        assertEquals(account, account.toEntity().toDomain())
        assertEquals(5_000_000L, AccountEntity::class.let { account.toEntity().creditLimitCents })
        assertEquals(AccountType.CREDIT_CARD.name, account.toEntity().type.name)
    }

    @Test
    fun installmentDatesUseTheUtcMidnightColumnConvention() {
        val plan = InstallmentPlan(
            id = "p1",
            ledgerId = "l1",
            accountId = "card",
            name = "装修分期",
            method = InstallmentMethod.EQUAL_PRINCIPAL,
            principal = Money.ofCents(1_200_000),
            periods = 12,
            annualRateBp = 1200,
            firstDueDate = LocalDate(2026, 1, 15),
            dueDay = 15,
            createdAt = 1,
            updatedAt = 2,
        )
        val entity: InstallmentPlanEntity = plan.toEntity()
        assertEquals(LocalDate(2026, 1, 15).let { 1790208000000L + (it.toEpochDays() - LocalDate(2026, 9, 24).toEpochDays()) * 86_400_000L }, entity.firstDueDate)
        assertEquals(plan, entity.toDomain())
    }

    @Test
    fun installmentPeriodTotalIsDerived() {
        val period = InstallmentPeriod(
            id = "ip1",
            planId = "p1",
            periodNo = 3,
            dueDate = LocalDate(2026, 4, 15),
            principal = Money.ofCents(100_000),
            interest = Money.ofCents(1_000),
            fee = Money.ofCents(500),
        )
        assertEquals(Money.ofCents(101_500), period.total)
        assertEquals(period, period.toEntity().toDomain())
        assertEquals(101_500L, period.toEntity().totalCents)
    }

    @Test
    fun recurringRuleTagIdsAreStoredAsJson() {
        val rule = RecurringRule(
            id = "r1",
            ledgerId = "l1",
            name = "房租",
            txnType = TransactionType.EXPENSE,
            amount = Money.ofCents(350_000),
            accountId = "cash",
            categoryId = "housing",
            tagIds = listOf("fixed", "home"),
            freq = RecurrenceFrequency.MONTHLY,
            byMonthDay = 1,
            startAt = 1_790_208_000_000L,
            createdAt = 1,
            updatedAt = 2,
        )
        val entity: RecurringRuleEntity = rule.toEntity()
        assertEquals("""["fixed","home"]""", entity.tagIdsJson)
        assertEquals(rule, entity.toDomain())
        assertNull(RecurringRule::class.let { rule.copy(tagIds = emptyList()).toEntity().tagIdsJson })
    }

    @Test
    fun unknownEntityFieldsFallBackToEmptyTagIds() {
        val entity = TxnEntity(
            id = "t1",
            ledgerId = "l1",
            type = TransactionType.EXPENSE,
            amountCents = 100,
            accountId = "cash",
            occurredAt = 0,
            createdAt = 0,
            updatedAt = 0,
        )
        assertEquals(CategoryKind.EXPENSE, com.han1997.vipledger.core.domain.calculator.TxnValidator.expectedKind(entity.type))
        assertEquals(emptyList(), entity.toDomain().tagIds)
    }
}