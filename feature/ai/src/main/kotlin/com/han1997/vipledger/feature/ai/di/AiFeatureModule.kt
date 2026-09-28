package com.han1997.vipledger.feature.ai.di

import android.content.Context
import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.ai.AiSettingsRepository
import com.han1997.vipledger.core.domain.ml.MlModelTrainer
import com.han1997.vipledger.core.domain.nlp.CategoryClassifier
import com.han1997.vipledger.core.domain.repository.MlSampleRepository
import com.han1997.vipledger.core.domain.usecase.category.ObserveCategoryTreeUseCase
import com.han1997.vipledger.core.domain.usecase.ledger.ObserveCurrentLedgerUseCase
import com.han1997.vipledger.feature.ai.model.AutoTrainTrigger
import com.han1997.vipledger.feature.ai.model.DefaultSampleTrainer
import com.han1997.vipledger.feature.ai.model.OnDeviceModelManager
import com.han1997.vipledger.feature.ai.model.SampleTrainer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * `:feature:ai` 自己的 Hilt 装配。
 *
 * 端侧分类器接缝（PRD D1）的接口在 `:core:domain`、实现在 `:ml:classifier`；M7 在此把
 * 占位实现换成真实的 [com.han1997.vipledger.ml.classifier.LinearCategoryClassifier]，
 * 且**只放这里一处**——管线、自动训练、模型管理页共用 [OnDeviceModelManager] 持有的同一个实例，
 * 避免「训练一份、推理另一份」。
 *
 * 依赖仍只指向 `core:*` 与 `ml:classifier`（architecture.md：不新增跨模块依赖）。
 */
@Module
@InstallIn(SingletonComponent::class)
object AiFeatureModule {

    /** 应用级协程作用域：分类后校验的收集器与自动训练都跑在它上面（进程内、不引后台调度）。 */
    @Provides
    @Singleton
    fun provideModelScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideOnDeviceModelManager(
        @ApplicationContext context: Context,
        sampleRepository: MlSampleRepository,
        observeCurrentLedger: ObserveCurrentLedgerUseCase,
        observeCategoryTree: ObserveCategoryTreeUseCase,
        scope: CoroutineScope,
    ): OnDeviceModelManager = OnDeviceModelManager(
        context = context,
        sampleRepository = sampleRepository,
        observeCurrentLedger = observeCurrentLedger,
        observeCategoryTree = observeCategoryTree,
        scope = scope,
    )

    /** 管线拿到的分类器 = 管理器持有的**同一个**真实实例（不再是占位实现）。 */
    @Provides
    @Singleton
    fun provideCategoryClassifier(manager: OnDeviceModelManager): CategoryClassifier = manager.classifier

    /** 「训练并标记」的共享原子动作：自动触发与模型页手动训练共用，保证标记用产出模型版本。 */
    @Provides
    @Singleton
    fun provideSampleTrainer(
        manager: OnDeviceModelManager,
        sampleRepository: MlSampleRepository,
    ): SampleTrainer = DefaultSampleTrainer(
        train = { samples -> manager.trainRaw(samples) },
        markTrained = { ids, version -> sampleRepository.markTrained(ids, version).isSuccess },
    )

    /** 自动训练触发点：修正写入样本后由 `ConfirmAiEntryUseCase` 调用；开关默认关闭。 */
    @Provides
    @Singleton
    fun provideMlModelTrainer(
        settingsRepository: AiSettingsRepository,
        sampleRepository: MlSampleRepository,
        sampleTrainer: SampleTrainer,
        scope: CoroutineScope,
    ): MlModelTrainer = AutoTrainTrigger(
        isAutoTrainEnabled = { settingsRepository.settings().autoTrainEnabled },
        listUntrained = { ledgerId -> sampleRepository.listUntrained(ledgerId) },
        sampleTrainer = sampleTrainer,
        scope = scope,
    )
}
