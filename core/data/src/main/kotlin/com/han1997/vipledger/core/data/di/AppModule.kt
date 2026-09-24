package com.han1997.vipledger.core.data.di

import com.han1997.vipledger.core.common.dispatcher.DefaultDispatcherProvider
import com.han1997.vipledger.core.common.dispatcher.DispatcherProvider
import com.han1997.vipledger.core.common.time.SystemTimeProvider
import com.han1997.vipledger.core.common.time.TimeProvider
import com.han1997.vipledger.core.domain.support.IdGenerator
import com.han1997.vipledger.core.domain.support.UuidIdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = UuidIdGenerator
}