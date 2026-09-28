package com.han1997.vipledger.core.domain.nlp

import com.han1997.vipledger.core.domain.model.Account
import com.han1997.vipledger.core.domain.model.Category
import com.han1997.vipledger.core.domain.model.MerchantRule
import com.han1997.vipledger.core.model.AccountType
import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.RuleSource
import kotlinx.datetime.LocalDate

/** 规则层单测共用的参考日：2026-09-28（星期一，ISO weekday 1），与研究语料一致。 */
val REF: LocalDate = LocalDate(2026, 9, 28)

fun account(
    id: String,
    name: String,
    type: AccountType = AccountType.VIRTUAL,
): Account = Account(
    id = id,
    ledgerId = "l1",
    name = name,
    type = type,
    createdAt = 0,
    updatedAt = 0,
)

fun category(
    id: String,
    name: String,
    kind: CategoryKind = CategoryKind.EXPENSE,
): Category = Category(
    id = id,
    ledgerId = "l1",
    name = name,
    kind = kind,
    createdAt = 0,
    updatedAt = 0,
)

fun merchantRule(
    id: String,
    pattern: String,
    categoryId: String,
    priority: Int = 100,
    enabled: Boolean = true,
    source: RuleSource = RuleSource.USER,
): MerchantRule = MerchantRule(
    id = id,
    ledgerId = "l1",
    pattern = pattern,
    categoryId = categoryId,
    priority = priority,
    source = source,
    enabled = enabled,
    createdAt = 0,
    updatedAt = 0,
)

/** 研究 §5 语料使用的账户集合。 */
val CORPUS_ACCOUNTS: List<Account> = listOf(
    account("acct_wechat", "微信"),
    account("acct_alipay", "支付宝"),
    account("acct_icbc", "工商银行", AccountType.BANK_DEBIT),
    account("acct_credit", "信用卡", AccountType.CREDIT_CARD),
)

/** 研究 §5 语料使用的分类集合。 */
val CORPUS_CATEGORIES: List<Category> = listOf(
    category("cat_food", "餐饮"),
    category("cat_transport", "交通"),
    category("cat_rent", "房租"),
    category("cat_shopping", "购物"),
    category("cat_salary", "工资", CategoryKind.INCOME),
    category("cat_refund", "退款", CategoryKind.INCOME),
)

fun corpusParser(rules: List<MerchantRule> = emptyList()): RuleEntryParser =
    RuleEntryParser(CORPUS_ACCOUNTS, CORPUS_CATEGORIES, rules)
