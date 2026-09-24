package com.han1997.vipledger.core.data.builtin

import android.content.Context
import com.han1997.vipledger.core.data.R
import com.han1997.vipledger.core.domain.support.BuiltinCategory
import com.han1997.vipledger.core.domain.support.BuiltinCategorySource
import com.han1997.vipledger.core.model.CategoryKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 内置两级分类（docs/PLAN.md 3.1「分类」）。
 *
 * 分类名是**写进数据库的数据**而不是界面文案，但仍放资源文件，方便以后加英文。
 * 图标键留给 M2 的界面层映射到 drawable。
 */
class ResourceBuiltinCategorySource @Inject constructor(
    @ApplicationContext private val context: Context,
) : BuiltinCategorySource {

    override fun categories(): List<BuiltinCategory> =
        EXPENSE_TREE.map { it.toBuiltin(CategoryKind.EXPENSE) } +
            INCOME_TREE.map { it.toBuiltin(CategoryKind.INCOME) }

    private fun Node.toBuiltin(kind: CategoryKind) = BuiltinCategory(
        name = context.getString(nameRes),
        kind = kind,
        iconKey = iconKey,
        children = children.map { BuiltinCategory(name = context.getString(it), kind = kind) },
    )

    private data class Node(
        val nameRes: Int,
        val iconKey: String? = null,
        val children: List<Int> = emptyList(),
    )

    private companion object {
        val EXPENSE_TREE = listOf(
            Node(R.string.builtin_category_food, "restaurant", listOf(
                R.string.builtin_category_food_breakfast,
                R.string.builtin_category_food_lunch,
                R.string.builtin_category_food_dinner,
                R.string.builtin_category_food_takeout,
                R.string.builtin_category_food_snack,
            )),
            Node(R.string.builtin_category_transport, "transport", listOf(
                R.string.builtin_category_transport_public,
                R.string.builtin_category_transport_taxi,
                R.string.builtin_category_transport_fuel,
                R.string.builtin_category_transport_parking,
                R.string.builtin_category_transport_travel,
            )),
            Node(R.string.builtin_category_shopping, "shopping", listOf(
                R.string.builtin_category_shopping_daily,
                R.string.builtin_category_shopping_clothes,
                R.string.builtin_category_shopping_digital,
                R.string.builtin_category_shopping_beauty,
            )),
            Node(R.string.builtin_category_housing, "housing", listOf(
                R.string.builtin_category_housing_rent,
                R.string.builtin_category_housing_mortgage,
                R.string.builtin_category_housing_utilities,
                R.string.builtin_category_housing_management,
                R.string.builtin_category_housing_internet,
            )),
            Node(R.string.builtin_category_entertainment, "entertainment", listOf(
                R.string.builtin_category_entertainment_movie,
                R.string.builtin_category_entertainment_game,
                R.string.builtin_category_entertainment_trip,
                R.string.builtin_category_entertainment_sports,
            )),
            Node(R.string.builtin_category_medical, "medical", listOf(
                R.string.builtin_category_medical_clinic,
                R.string.builtin_category_medical_medicine,
                R.string.builtin_category_medical_checkup,
            )),
            Node(R.string.builtin_category_education, "education", listOf(
                R.string.builtin_category_education_tuition,
                R.string.builtin_category_education_books,
                R.string.builtin_category_education_course,
            )),
            Node(R.string.builtin_category_social, "social", listOf(
                R.string.builtin_category_social_redpacket,
                R.string.builtin_category_social_treat,
                R.string.builtin_category_social_gift,
            )),
            Node(R.string.builtin_category_communication, "communication", listOf(
                R.string.builtin_category_communication_phone,
                R.string.builtin_category_communication_data,
            )),
            Node(R.string.builtin_category_other_expense, "other"),
        )

        val INCOME_TREE = listOf(
            Node(R.string.builtin_category_salary, "salary"),
            Node(R.string.builtin_category_bonus, "bonus"),
            Node(R.string.builtin_category_investment, "investment"),
            Node(R.string.builtin_category_income_redpacket, "redpacket"),
            Node(R.string.builtin_category_reimburse, "reimburse"),
            Node(R.string.builtin_category_other_income, "other"),
        )
    }
}