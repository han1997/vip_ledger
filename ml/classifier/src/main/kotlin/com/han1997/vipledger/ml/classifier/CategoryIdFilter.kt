package com.han1997.vipledger.ml.classifier

/**
 * 分类候选**后校验**：只放行「当前真实存在」的分类 id（PRD D3#1）。
 *
 * 模型的标签表在训练时固化，而分类是用户可删可改的；模型对已删除分类仍会照常给候选。
 * 不校验就会把不存在的分类塞进草稿，捅穿 M6 立下的「未识别要明示、不许静默兜底」约定
 * （与云端返回值的处理同款）。
 *
 * **接缝（PR3 装配必读）**：`CategoryClassifier` 接口是冻结的（M7 不改接口），因此把「当前合法分类」
 * 通过**实现类**注入——[LinearCategoryClassifier] 的构造参数与 [LinearCategoryClassifier.updateCategoryIdFilter]。
 * PR3 按当前账本的真实分类构造 [from] / [fromCurrent]（或在账本切换时调用 update）。
 *
 * **本过滤器是构造 [LinearCategoryClassifier] 的必填参数**（没有「不过滤」的默认值）：漏接会**编译不过**，
 * 而不是静默接受一切。这是刻意的——一个开着 [ACCEPT_ALL] 的分类器会把已删除分类的陈旧候选送进草稿，
 * 捅穿 M6 的「未识别要明示、不许静默兜底」（PRD Requirement 8 / D3#1）。
 */
fun interface CategoryIdFilter {

    fun isAllowed(categoryId: String): Boolean

    companion object {

        /**
         * 不过滤。**仅供单测与确实不需要后校验的离线路径**，且必须由调用点显式写出——
         * 它不再是构造参数的默认值（改成默认值就等于给 App 装配留了个漏校验的后门）。
         */
        val ACCEPT_ALL: CategoryIdFilter = CategoryIdFilter { true }

        /** 按一份**快照**放行；适合构造期已知当前账本分类集合的场景。 */
        fun from(ids: Collection<String>): CategoryIdFilter {
            val snapshot = ids.toSet()
            return CategoryIdFilter { it in snapshot }
        }

        /**
         * 按动态来源放行（每次调用现读）；适合账本可切换、分类可在别处增删的场景。
         * PR3 可让来源读当前账本的分类表，从而单实例服务所有账本。
         */
        fun fromCurrent(source: () -> Set<String>): CategoryIdFilter =
            CategoryIdFilter { it in source() }
    }
}
