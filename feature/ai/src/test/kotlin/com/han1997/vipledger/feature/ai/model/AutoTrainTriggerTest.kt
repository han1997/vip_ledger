package com.han1997.vipledger.feature.ai.model

import com.han1997.vipledger.core.common.result.isSuccess
import com.han1997.vipledger.core.domain.model.MlTrainingSample
import com.han1997.vipledger.core.model.MlSampleSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * 自动训练触发点的领域规则（PRD Requirement 3、M7 验收）。
 *
 * 全部以「训练被调用的次数」断言，而不是窥探内部状态——这正是验收要求的判据：
 * 开关关闭、样本不足时**训练一次都不发生**；达到阈值才训练一次。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoTrainTriggerTest {

    private fun trigger(
        enabled: Boolean,
        untrained: suspend () -> List<MlTrainingSample>,
        trainer: SampleTrainer,
        scope: CoroutineScope,
        threshold: Int = AutoTrainTrigger.AUTO_TRAIN_THRESHOLD,
    ) = AutoTrainTrigger(
        isAutoTrainEnabled = { enabled },
        listUntrained = { untrained() },
        sampleTrainer = trainer,
        scope = scope,
        threshold = threshold,
    )

    @Test
    fun switchOffMeansNoTraining() = runTest {
        val trainer = CountingSampleTrainer()
        val subject = trigger(enabled = false, untrained = { untrainedSamples(100) }, trainer = trainer, scope = this)

        subject.onTrainingSampleWritten("l1")
        advanceUntilIdle()

        assertEquals(0, trainer.calls, "开关关闭时不得发生任何训练（M7 验收）")
        assertFalse(subject.trainIfEligible("l1"))
    }

    @Test
    fun belowThresholdMeansNoTraining() = runTest {
        val trainer = CountingSampleTrainer()
        val subject = trigger(enabled = true, untrained = { untrainedSamples(29) }, trainer = trainer, scope = this)

        assertFalse(subject.trainIfEligible("l1"))
        assertEquals(0, trainer.calls, "未训练样本 < 30 时不得训练")
    }

    @Test
    fun atThresholdTrainsOnce() = runTest {
        val trainer = CountingSampleTrainer()
        val subject = trigger(enabled = true, untrained = { untrainedSamples(30) }, trainer = trainer, scope = this)

        assertTrue(subject.trainIfEligible("l1"))
        assertEquals(1, trainer.calls)
        assertEquals(30, trainer.lastSampleCount, "训练应喂入全部未训练样本")
    }

    @Test
    fun aboveThresholdTrainsThroughFireAndForget() = runTest {
        val trainer = CountingSampleTrainer()
        val subject = trigger(enabled = true, untrained = { untrainedSamples(50) }, trainer = trainer, scope = this)

        subject.onTrainingSampleWritten("l1")
        advanceUntilIdle()

        assertEquals(1, trainer.calls)
    }

    @Test
    fun trainingFailureDoesNotPropagate() = runTest {
        // 训练实现返回 null（失败）时，即发即弃的调用不得抛异常（修正已保存，训练失败必须无害）。
        val trainer = CountingSampleTrainer(version = null)
        val subject = trigger(enabled = true, untrained = { untrainedSamples(40) }, trainer = trainer, scope = this)

        subject.onTrainingSampleWritten("l1")
        advanceUntilIdle()

        assertEquals(1, trainer.calls)
        assertFalse(subject.trainIfEligible("l1"))
    }

    @Test
    fun sampleQueryFailureIsSwallowed() = runTest {
        // 读取未训练样本本身失败（如后台清理）也不得把异常抛给用户确认路径。
        val subject = trigger(
            enabled = true,
            untrained = { throw IllegalStateException("db boom") },
            trainer = CountingSampleTrainer(),
            scope = this,
        )

        subject.onTrainingSampleWritten("l1")
        advanceUntilIdle()
    }

    @Test
    fun importSourceSamplesAreFedIntoTraining() = runTest {
        // PRD Requirement 9 / D3#2：IMPORT 与 USER_CORRECTION 都要进训练集，不按来源加权或过滤。
        val repo = FakeMlSampleRepository(
            listOf(
                trainingSample("u1", categoryId = "c1").copy(source = MlSampleSource.USER_CORRECTION),
                trainingSample("i1", categoryId = "c2").copy(source = MlSampleSource.IMPORT),
            ),
        )
        val captured = mutableListOf<MlTrainingSample>()
        val trainer = DefaultSampleTrainer(
            train = { batch ->
                captured += batch
                "v1-77"
            },
            markTrained = { ids, version -> repo.markTrained(ids, version).isSuccess },
        )
        val subject = AutoTrainTrigger(
            isAutoTrainEnabled = { true },
            listUntrained = { ledgerId -> repo.listUntrained(ledgerId) },
            sampleTrainer = trainer,
            scope = this,
            threshold = 2,
        )

        assertTrue(subject.trainIfEligible("l1"))
        assertEquals(
            setOf(MlSampleSource.USER_CORRECTION, MlSampleSource.IMPORT),
            captured.map { it.source }.toSet(),
            "导入样本必须与用户修正样本一起进入训练集",
        )
        // 标记用的是产出的模型版本，且标记后这批样本不再是「未训练」。
        assertEquals("v1-77", repo.marked.single().second)
        assertTrue(repo.listUntrained("l1").isEmpty())
    }
}
