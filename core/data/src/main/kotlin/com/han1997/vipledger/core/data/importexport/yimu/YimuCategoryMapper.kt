package com.han1997.vipledger.core.data.importexport.yimu

import com.han1997.vipledger.core.model.CategoryKind
import com.han1997.vipledger.core.model.TransactionType

/**
 * 一木记账分类 → VIP Ledger 内置分类树（纯函数，`.trellis/spec/android/import-export.md` 一木一节）。
 *
 * 目标 `kind` **只由「收支类型」列决定，不由一级类别决定**（陷阱 1）：同一个一级类别（如「其他」「奖金」
 * 「工资」「礼金人情」）可能混有支出与收入两条方向，绝不能从一级名推 direction。
 *
 * 复用粒度按 D9（同义复用）：语义相同就落内置二级，一木特有的一律新建。
 */
object YimuCategoryMapper {

    /** 映射结果：[parentName] 内置一级名；[childName] 为 null 时落一级本身；[isNewChild] 表示该二级需新建。 */
    data class Target(
        val parentName: String,
        val childName: String?,
        val isNewChild: Boolean,
    )

    /**
     * @param primary 一木一级类别列。
     * @param secondary 一木二级分类列（可为空）。
     * @param type 该行「收支类型」列决定的方向。
     */
    fun map(primary: String, secondary: String, type: TransactionType): Target {
        val kind = type.kind
        val normalizedPrimary = primary.trim()
        val normalizedSecondary = secondary.trim()
        val parent = when (kind) {
            CategoryKind.EXPENSE -> expenseParent(normalizedPrimary)
            CategoryKind.INCOME -> incomeParent(normalizedPrimary)
            CategoryKind.TRANSFER -> throw IllegalArgumentException("一木账单不支持转账类型的分类映射")
        }
        if (normalizedSecondary.isEmpty()) return Target(parent, null, isNewChild = false)

        val child = CHILD_MAP["$kind|$normalizedPrimary"]?.get(normalizedSecondary)
        return when {
            child == null -> Target(parent, normalizedSecondary, isNewChild = true)
            child.builtinName != null -> Target(parent, child.builtinName, isNewChild = false)
            else -> Target(parent, normalizedSecondary, isNewChild = true)
        }
    }

    /** 空二级时落一级本身；一级映射按方向选择。 */
    fun parentOf(primary: String, type: TransactionType): String =
        when (type.kind) {
            CategoryKind.EXPENSE -> expenseParent(primary.trim())
            CategoryKind.INCOME -> incomeParent(primary.trim())
            CategoryKind.TRANSFER -> throw IllegalArgumentException("一木账单不支持转账类型的分类映射")
        }

    private fun expenseParent(primary: String): String = EXPENSE_PRIMARY[primary] ?: FALLBACK_EXPENSE

    private fun incomeParent(primary: String): String = INCOME_PRIMARY[primary] ?: FALLBACK_INCOME

    private val TransactionType.kind: CategoryKind
        get() = when (this) {
            TransactionType.EXPENSE, TransactionType.REFUND -> CategoryKind.EXPENSE
            TransactionType.INCOME -> CategoryKind.INCOME
            else -> CategoryKind.TRANSFER
        }

    /** [builtinName] 非空表示复用内置二级；为 null 表示在内置父级下新建同名二级。 */
    private data class ChildMap(val builtinName: String?)

    private fun new(): ChildMap = ChildMap(null)

    private fun reuse(builtinName: String): ChildMap = ChildMap(builtinName)

    // ---- 一级映射（支出方向） ----

    private val EXPENSE_PRIMARY = mapOf(
        "食品餐饮" to "餐饮",
        "购物消费" to "购物",
        "出行交通" to "交通",
        "送礼人情" to "人情",
        "居家生活" to "居住",
        "健康医疗" to "医疗",
        "文化教育" to "教育",
        "休闲娱乐" to "娱乐",
        "其他" to "其他支出",
    )

    // ---- 一级映射（收入方向）：其他 → 投资收益，收入 → 其他收入，其余同名内置一级 ----

    private val INCOME_PRIMARY = mapOf(
        "收入" to "其他收入",
        "其他" to "投资收益",
        "奖金" to "奖金",
        "礼金人情" to "红包收入",
        "工资" to "工资",
    )

    private const val FALLBACK_EXPENSE = "其他支出"

    private const val FALLBACK_INCOME = "其他收入"

    // ---- 二级映射：key = "kind|一木一级"，先命中先用 ----

    private val CHILD_MAP: Map<String, Map<String, ChildMap>> = mapOf(
        "EXPENSE|食品餐饮" to mapOf(
            "早餐" to reuse("早餐"),
            "午餐" to reuse("午餐"),
            "晚餐" to reuse("晚餐"),
            "饮料酒水" to new(),
            "休闲零食" to new(),
            "生鲜食品" to new(),
            "买菜" to new(),
            "奶茶" to new(),
            "粮油调味" to new(),
            "请客吃饭" to new(),
        ),
        "EXPENSE|购物消费" to mapOf(
            "手机数码" to reuse("数码"),
            "个护美妆" to reuse("美妆"),
            "服饰运动" to reuse("服饰"),
            "日常家居" to reuse("日用品"),
            "虚拟充值" to new(),
            "宠物用品" to new(),
            "装修装饰" to new(),
            "配饰腕表" to new(),
            "生活电器" to new(),
            "母婴玩具" to new(),
            "办公用品" to new(),
        ),
        "EXPENSE|出行交通" to mapOf(
            "火车" to reuse("火车飞机"),
            "公共交通" to reuse("公交地铁"),
            "打车" to reuse("打车"),
            "加油" to reuse("加油"),
            "停车费" to reuse("停车"),
            "高速费" to new(),
            "汽车充电" to new(),
            "保养修车" to new(),
        ),
        "EXPENSE|居家生活" to mapOf(
            "话费宽带" to reuse("话费"),
            "房租还贷" to reuse("房贷"),
            "电费" to reuse("水电燃气"),
            "水费" to reuse("水电燃气"),
            "理发" to new(),
            "家政清洁" to new(),
            "车位费" to new(),
        ),
        "EXPENSE|健康医疗" to mapOf(
            "医院" to reuse("门诊"),
            "买药" to reuse("药品"),
            "滋补保健" to new(),
        ),
        "EXPENSE|文化教育" to mapOf(
            "学费" to reuse("学费"),
            "书报杂志" to reuse("书籍"),
            "培训考试" to reuse("课程"),
        ),
        "EXPENSE|休闲娱乐" to mapOf(
            "电影唱歌" to reuse("电影"),
            "旅游度假" to reuse("旅行"),
            "运动健身" to reuse("运动"),
            "棋牌桌游" to new(),
        ),
        "EXPENSE|送礼人情" to mapOf(
            "红包" to reuse("红包"),
            "礼物" to reuse("礼物"),
            "孝敬长辈" to new(),
            "借出" to new(),
        ),
        "EXPENSE|其他" to mapOf(
            "理财支出" to new(),
        ),
        "INCOME|收入" to mapOf(
            "其他" to new(),
            "补贴" to new(),
            "工资" to new(),
            "礼金人情" to new(),
            "奖金" to new(),
        ),
        "INCOME|奖金" to mapOf(
            "中奖" to new(),
        ),
        "INCOME|工资" to mapOf(
            "兼职外快" to new(),
            "公积金" to new(),
        ),
        "INCOME|礼金人情" to mapOf(
            "收红包" to new(),
            "借入" to new(),
        ),
        "INCOME|其他" to mapOf(
            "理财盈利" to new(),
        ),
    )
}
