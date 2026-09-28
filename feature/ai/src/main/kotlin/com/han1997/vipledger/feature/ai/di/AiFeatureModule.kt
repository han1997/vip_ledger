package com.han1997.vipledger.feature.ai.di

import com.han1997.vipledger.core.domain.nlp.CategoryClassifier
import com.han1997.vipledger.ml.classifier.PlaceholderCategoryClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `:feature:ai` 自己的 Hilt 装配。
 *
 * 端侧分类器接缝（PRD D1）的接口在 `:core:domain`、实现在 `:ml:classifier`；M6 只提供返回空候选的
 * 占位实现，在 `:feature:ai` 内绑定即可，避免为此给 `:core:data` 增加 `:ml:classifier` 依赖
 * （architecture.md：能不新增跨模块依赖就不新增）。M7 落地真实实现后替换这里的一行。
 */
@Module
@InstallIn(SingletonComponent::class)
object AiFeatureModule {

    @Provides
    @Singleton
    fun provideCategoryClassifier(): CategoryClassifier = PlaceholderCategoryClassifier()
}
