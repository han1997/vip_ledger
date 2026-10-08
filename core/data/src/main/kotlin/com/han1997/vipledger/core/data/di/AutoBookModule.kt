package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.data.autobook.RoomAutoBookRepository
import com.han1997.vipledger.core.domain.autobook.AutoBookRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AutoBookModule {
    @Binds abstract fun repository(impl: RoomAutoBookRepository): AutoBookRepository
}
