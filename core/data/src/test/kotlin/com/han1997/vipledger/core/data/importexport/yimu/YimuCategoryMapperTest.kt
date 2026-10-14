package com.han1997.vipledger.core.data.importexport.yimu

import com.han1997.vipledger.core.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 一木 13 个一级 × 2 方向的映射契约(R4,`.trellis/spec/android/import-export.md` 一木一节)。
 *
 * 核心陷阱 1:**方向只由「收支类型」列决定**,同一个一级名(「其他」)在两个方向下落到不同内置一级。
 */
class YimuCategoryMapperTest {

    private fun expense(primary: String, secondary: String = "") =
        YimuCategoryMapper.map(primary, secondary, TransactionType.EXPENSE)

    private fun income(primary: String, secondary: String = "") =
        YimuCategoryMapper.map(primary, secondary, TransactionType.INCOME)

    // ---- 一级(支出方向)逐条 ----

    @Test
    fun expensePrimariesMapToBuiltinTree() {
        val table = mapOf(
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
        table.forEach { (source, target) ->
            val mapped = expense(source)
            assertEquals(target, mapped.parentName, "支出「$source」应落内置一级「$target」")
            assertNull(mapped.childName)
        }
    }

    // ---- 一级(收入方向)逐条 ----

    @Test
    fun incomePrimariesMapToBuiltinTree() {
        val table = mapOf(
            "收入" to "其他收入",
            "其他" to "投资收益",
            "奖金" to "奖金",
            "礼金人情" to "红包收入",
            "工资" to "工资",
        )
        table.forEach { (source, target) ->
            val mapped = income(source)
            assertEquals(target, mapped.parentName, "收入「$source」应落内置一级「$target」")
            assertNull(mapped.childName)
        }
    }

    // ---- 陷阱 1:「其他」双方向 ----

    @Test
    fun otherPrimaryResolvesByDirectionNotByPrimaryName() {
        assertEquals("其他支出", expense("其他", "理财支出").parentName)
        assertEquals("投资收益", income("其他", "理财盈利").parentName)
    }

    // ---- 二级:复用 / 新建 ----

    @Test
    fun expenseSecondariesReuseBuiltinOrCreate() {
        // 复用
        assertEquals("早餐", expense("食品餐饮", "早餐").childName)
        assertFalse(expense("食品餐饮", "早餐").isNewChild)
        assertEquals("数码", expense("购物消费", "手机数码").childName)
        assertFalse(expense("购物消费", "手机数码").isNewChild)
        assertEquals("美妆", expense("购物消费", "个护美妆").childName)
        assertEquals("服饰", expense("购物消费", "服饰运动").childName)
        assertEquals("日用品", expense("购物消费", "日常家居").childName)
        assertEquals("火车飞机", expense("出行交通", "火车").childName)
        assertEquals("公交地铁", expense("出行交通", "公共交通").childName)
        assertEquals("打车", expense("出行交通", "打车").childName)
        assertEquals("加油", expense("出行交通", "加油").childName)
        assertEquals("停车", expense("出行交通", "停车费").childName)
        assertEquals("话费", expense("居家生活", "话费宽带").childName)
        assertEquals("房贷", expense("居家生活", "房租还贷").childName)
        assertEquals("水电燃气", expense("居家生活", "电费").childName)
        assertEquals("水电燃气", expense("居家生活", "水费").childName)
        assertEquals("门诊", expense("健康医疗", "医院").childName)
        assertEquals("药品", expense("健康医疗", "买药").childName)
        assertEquals("学费", expense("文化教育", "学费").childName)
        assertEquals("书籍", expense("文化教育", "书报杂志").childName)
        assertEquals("课程", expense("文化教育", "培训考试").childName)
        assertEquals("电影", expense("休闲娱乐", "电影唱歌").childName)
        assertEquals("旅行", expense("休闲娱乐", "旅游度假").childName)
        assertEquals("运动", expense("休闲娱乐", "运动健身").childName)
        assertEquals("红包", expense("送礼人情", "红包").childName)
        assertEquals("礼物", expense("送礼人情", "礼物").childName)
    }

    @Test
    fun expenseSecondariesNotInBuiltinTreeAreCreatedUnderSameParent() {
        val createAt = listOf(
            "食品餐饮" to "饮料酒水",
            "食品餐饮" to "休闲零食",
            "食品餐饮" to "生鲜食品",
            "食品餐饮" to "买菜",
            "食品餐饮" to "奶茶",
            "食品餐饮" to "粮油调味",
            "食品餐饮" to "请客吃饭",
            "购物消费" to "虚拟充值",
            "购物消费" to "宠物用品",
            "购物消费" to "装修装饰",
            "购物消费" to "配饰腕表",
            "购物消费" to "生活电器",
            "购物消费" to "母婴玩具",
            "购物消费" to "办公用品",
            "出行交通" to "高速费",
            "出行交通" to "汽车充电",
            "出行交通" to "保养修车",
            "居家生活" to "理发",
            "居家生活" to "家政清洁",
            "居家生活" to "车位费",
            "健康医疗" to "滋补保健",
            "休闲娱乐" to "棋牌桌游",
            "送礼人情" to "孝敬长辈",
            "送礼人情" to "借出",
            "其他" to "理财支出",
        )
        val parentOf = mapOf(
            "食品餐饮" to "餐饮", "购物消费" to "购物", "出行交通" to "交通",
            "居家生活" to "居住", "健康医疗" to "医疗", "休闲娱乐" to "娱乐",
            "送礼人情" to "人情", "其他" to "其他支出",
        )
        createAt.forEach { (primary, secondary) ->
            val mapped = expense(primary, secondary)
            assertEquals(parentOf.getValue(primary), mapped.parentName)
            assertEquals(secondary, mapped.childName, "一木特有二级「$secondary」应在父级下同名新建")
            assertTrue(mapped.isNewChild, "「$secondary」应标记为新建")
        }
    }

    @Test
    fun incomeSecondariesAreAllCreatedUnderBuiltinIncomeParents() {
        val cases = listOf(
            Triple("收入", "其他", "其他收入"),
            Triple("收入", "补贴", "其他收入"),
            Triple("收入", "工资", "其他收入"),
            Triple("收入", "礼金人情", "其他收入"),
            Triple("收入", "奖金", "其他收入"),
            Triple("奖金", "中奖", "奖金"),
            Triple("工资", "兼职外快", "工资"),
            Triple("工资", "公积金", "工资"),
            Triple("礼金人情", "收红包", "红包收入"),
            Triple("礼金人情", "借入", "红包收入"),
            Triple("其他", "理财盈利", "投资收益"),
        )
        cases.forEach { (primary, secondary, parent) ->
            val mapped = income(primary, secondary)
            assertEquals(parent, mapped.parentName)
            assertEquals(secondary, mapped.childName)
            assertTrue(mapped.isNewChild)
        }
    }

    // ---- 空二级 → 落一级本身 ----

    @Test
    fun emptySecondaryFallsBackToParentItself() {
        val empty = expense("食品餐饮")
        assertEquals("餐饮", empty.parentName)
        assertNull(empty.childName)
        assertFalse(empty.isNewChild)

        val emptyIncome = income("收入")
        assertEquals("其他收入", emptyIncome.parentName)
        assertNull(emptyIncome.childName)
    }

    // ---- 未知一级兜底 ----

    @Test
    fun unknownPrimaryFallsBackByDirection() {
        assertEquals("其他支出", expense("外星类别").parentName)
        assertEquals("其他收入", income("外星类别").parentName)
    }

    // ---- 同义复用:两个源名落同一内置二级(避免重跑建重复分类) ----

    @Test
    fun synonymousSourceNamesCollapseToSameBuiltinChild() {
        assertEquals(expense("居家生活", "电费").childName, expense("居家生活", "水费").childName)
        assertEquals("水电燃气", expense("居家生活", "电费").childName)
    }

    // ---- 转账不支持 ----

    @Test
    fun transferTypeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            YimuCategoryMapper.map("食品餐饮", "早餐", TransactionType.TRANSFER)
        }
        assertFailsWith<IllegalArgumentException> {
            YimuCategoryMapper.parentOf("食品餐饮", TransactionType.TRANSFER)
        }
    }

    @Test
    fun parentOfMatchesMapParent() {
        assertEquals("餐饮", YimuCategoryMapper.parentOf("食品餐饮", TransactionType.EXPENSE))
        assertEquals("投资收益", YimuCategoryMapper.parentOf("其他", TransactionType.INCOME))
    }
}