package com.han1997.vipledger.feature.ai.model

import com.han1997.vipledger.core.domain.model.MlTrainingSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * 「训练并标记」原子动作的规则（PRD Requirement 3）：
 * 标记必须用**实际产出**的模型版本；训练失败 / 空样本时**不得标记**（否则会重复训练同批样本）。
 */
class DefaultSampleTrainerTest {

    private val samples: List<MlTrainingSample> = listOf(
        trainingSample("s1"),
        trainingSample("s2"),
    )

    @Test
    fun marksSamplesWithProducedVersion() = runTest {
        val marked = mutableListOf<Pair<List<String>, String>>()
        val subject = DefaultSampleTrainer(
            train = { "v1-42" },
            markTrained = { ids, version ->
                marked += ids to version
                true
            },
        )

        assertEquals("v1-42", subject.trainAndMark(samples))
        assertEquals(listOf(listOf("s1", "s2") to "v1-42"), marked, "必须用产出的模型版本标记样本")
    }

    @Test
    fun failedTrainingDoesNotMarkSamples() = runTest {
        var marked = false
        val subject = DefaultSampleTrainer(
            train = { null },
            markTrained = { _, _ ->
                marked = true
                true
            },
        )

        assertNull(subject.trainAndMark(samples))
        assertTrue(!marked, "训练失败时样本必须保持未训练")
    }

    @Test
    fun failedMarkReportsFailure() = runTest {
        val subject = DefaultSampleTrainer(
            train = { "v1-42" },
            markTrained = { _, _ -> false },
        )

        assertNull(subject.trainAndMark(samples), "标记失败不得谎报成功")
    }

    @Test
    fun emptySamplesSkipTrainingAndMarking() = runTest {
        var trained = false
        var marked = false
        val subject = DefaultSampleTrainer(
            train = {
                trained = true
                "v1-42"
            },
            markTrained = { _, _ ->
                marked = true
                true
            },
        )

        assertNull(subject.trainAndMark(emptyList()))
        assertTrue(!trained && !marked, "没有样本时不训练、不标记")
    }
}
