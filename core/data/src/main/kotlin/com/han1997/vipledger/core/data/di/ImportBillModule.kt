package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.data.importexport.yimu.YimuLedgerImporter
import com.han1997.vipledger.core.domain.usecase.importbill.BillImporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 账单导入的 Hilt 绑定:接口 `BillImporter` 在 `:core:domain`,实现在 `:core:data`。
 *
 * `feature:*` 只注入接口;这样 `feature:ledger` 的导入页无需依赖 `:core:data`
 * (architecture.md「接口向内、实现向外」)。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ImportBillModule {

    @Binds
    @Singleton
    abstract fun bindBillImporter(impl: YimuLedgerImporter): BillImporter
}