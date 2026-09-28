package com.han1997.vipledger.ml.classifier.train

import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import com.han1997.vipledger.ml.classifier.model.LinearModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinearModelTrainerTest {

    private val food = listOf("午饭 面", "晚饭 米饭", "早餐 包子", "午饭 盖饭", "夜宵 烧烤", "午饭 快餐")
    private val transport = listOf("打车 回家", "地铁 上班", "公交 两元", "打车 机场", "高铁 车票", "地铁 通勤")

    private fun dataset(): List<TrainingSample> =
        food.map { TrainingSample(it, "cat-food") } + transport.map { TrainingSample(it, "cat-transport") }

    private fun halfA(): List<TrainingSample> =
        food.take(3).map { TrainingSample(it, "cat-food") } + transport.take(3).map { TrainingSample(it, "cat-transport") }

    private fun halfB(): List<TrainingSample> =
        food.drop(3).map { TrainingSample(it, "cat-food") } + transport.drop(3).map { TrainingSample(it, "cat-transport") }

    private fun predict(model: LinearModel, text: String): String {
        val probabilities = model.probabilitiesOf(CharNgramFeaturizer.featuresOfText(text))
        var best = 0
        for (i in probabilities.indices) if (probabilities[i] > probabilities[best]) best = i
        return model.labels[best]
    }

    @Test
    fun learnsTriviallySeparableToySet() {
        val result = LinearModelTrainer.train(
            base = null,
            samples = dataset(),
            config = TrainingConfig(epochs = 200, learningRate = 0.5f, minSamplesPerClass = 3),
            now = 1000L,
        )!!
        assertEquals(listOf("cat-food", "cat-transport"), result.model.labels)
        dataset().forEach { sample -> assertEquals(sample.categoryId, predict(result.model, sample.text)) }
    }

    @Test
    fun isDeterministicForSameSeedAndSamples() {
        val config = TrainingConfig(epochs = 30, learningRate = 0.5f, seed = 12345L)
        val first = LinearModelTrainer.train(null, dataset(), config, now = 1L)!!
        val second = LinearModelTrainer.train(null, dataset(), config, now = 1L)!!
        assertEquals(first.model.labels, second.model.labels)
        assertContentEquals(first.model.bias, second.model.bias)
        first.model.weights.forEachIndexed { c, row -> assertContentEquals(row, second.model.weights[c]) }
    }

    @Test
    fun incrementalTrainingOnHalvesMatchesUnionAccuracy() {
        val config = TrainingConfig(epochs = 200, learningRate = 0.5f, minSamplesPerClass = 3)
        val union = LinearModelTrainer.train(null, halfA() + halfB(), config, now = 1L)!!.model
        val step1 = LinearModelTrainer.train(null, halfA(), config, now = 1L)!!.model
        val step2 = LinearModelTrainer.train(step1, halfB(), config, now = 2L)!!.model

        assertEquals(listOf("cat-food", "cat-transport"), step2.labels)
        (halfA() + halfB()).forEach { sample ->
            assertEquals(sample.categoryId, predict(union, sample.text))
            assertEquals(sample.categoryId, predict(step2, sample.text))
        }
    }

    @Test
    fun skipsClassesBelowSampleThreshold() {
        val samples = (1..5).map { TrainingSample("午饭 $it", "cat-food") } +
            (1..2).map { TrainingSample("机票 $it", "cat-travel") }
        val result = LinearModelTrainer.train(
            base = null,
            samples = samples,
            config = TrainingConfig(minSamplesPerClass = 3),
            now = 1L,
        )!!
        assertEquals(listOf("cat-food"), result.model.labels)
        assertEquals(mapOf("cat-travel" to 2), result.skippedClasses)
        assertEquals(2, result.model.pendingCounts["cat-travel"])
    }

    @Test
    fun pendingCountsAccumulateAcrossIncrementalCalls() {
        val config = TrainingConfig(minSamplesPerClass = 3, epochs = 10)
        val first = LinearModelTrainer.train(
            base = null,
            samples = listOf(TrainingSample("机票 1", "cat-travel")),
            config = config,
            now = 1L,
        )!!
        assertTrue(first.model.labels.isEmpty())
        assertEquals(mapOf("cat-travel" to 1), first.model.pendingCounts)

        val second = LinearModelTrainer.train(
            base = first.model,
            samples = (2..3).map { TrainingSample("机票 $it", "cat-travel") },
            config = config,
            now = 2L,
        )!!
        assertEquals(listOf("cat-travel"), second.model.labels)
        assertTrue(second.model.pendingCounts.isEmpty())
    }

    @Test
    fun carriesBaseWeightsForwardWhenNoNewSamples() {
        val config = TrainingConfig(epochs = 10, minSamplesPerClass = 3)
        val trained = LinearModelTrainer.train(null, dataset(), config, now = 1L)!!
        val continued = LinearModelTrainer.train(trained.model, emptyList(), config, now = 2L)!!
        assertEquals(trained.model.labels, continued.model.labels)
        assertContentEquals(trained.model.bias, continued.model.bias)
        trained.model.weights.forEachIndexed { c, row -> assertContentEquals(row, continued.model.weights[c]) }
    }

    @Test
    fun returnsNullWhenThereIsNothingToRecord() {
        assertNull(LinearModelTrainer.train(null, emptyList(), TrainingConfig(), now = 1L))
    }
}
