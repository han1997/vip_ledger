package com.han1997.vipledger.ml.classifier.model

import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelCodecTest {

    private fun sampleModel(
        featureVersion: Int = MINIMUM_FEATURE_VERSION,
        dim: Int = CharNgramFeaturizer.BUCKETS,
    ): LinearModel = LinearModel(
        labels = listOf("cat-a", "cat-b"),
        dim = dim,
        weights = Array(2) { FloatArray(dim) },
        bias = FloatArray(2),
        labelCounts = intArrayOf(10, 20),
        trainedAt = 1_700_000_000_000L,
        modelVersion = "v1-1700000000000",
        featureVersion = featureVersion,
        pendingCounts = mapOf("cat-c" to 2),
    )

    private fun validJson(
        formatVersion: Int = ModelCodec.FORMAT_VERSION,
        featureVersion: Int = CharNgramFeaturizer.FEATURE_VERSION,
        buckets: Int = CharNgramFeaturizer.BUCKETS,
        labelCounts: String = "[3]",
        bias: String = "[0.0]",
        weights: String = """[{"indices":[1],"values":[0.5]}]""",
    ): String = """{"formatVersion":$formatVersion,"featureVersion":$featureVersion,""" +
        """"vocabVersion":"char2-3gram-fnv1a-v1","buckets":$buckets,"modelVersion":"m",""" +
        """"trainedAt":1,"sampleCount":3,"labels":["cat-a"],"labelCounts":$labelCounts,""" +
        """"bias":$bias,"weights":$weights}"""

    @Test
    fun roundTripsWeightsExactly() {
        val original = sampleModel()
        original.weights[0][3] = 0.25f
        original.weights[0][123_456] = -0.75f
        original.weights[1][7] = 0.5f
        original.bias[0] = 0.125f
        original.bias[1] = -0.5f
        val probe = CharNgramFeaturizer.featuresOfText("午饭 面")

        val decoded = ModelCodec.decode(ModelCodec.encode(original))

        assertEquals(original.labels, decoded.labels)
        assertEquals(original.featureVersion, decoded.featureVersion)
        assertEquals(original.vocabVersion, decoded.vocabVersion)
        assertEquals(original.modelVersion, decoded.modelVersion)
        assertEquals(original.trainedAt, decoded.trainedAt)
        assertEquals(original.sampleCount, decoded.sampleCount)
        assertContentEquals(original.bias, decoded.bias)
        assertContentEquals(original.labelCounts, decoded.labelCounts)
        assertEquals(original.pendingCounts, decoded.pendingCounts)
        original.weights.forEachIndexed { c, row -> assertContentEquals(row, decoded.weights[c]) }
        assertContentEquals(original.probabilitiesOf(probe), decoded.probabilitiesOf(probe))
    }

    @Test
    fun onlyNonZeroBucketsAreSerialized() {
        val model = sampleModel()
        model.weights[0][9] = 0.3f
        val text = ModelCodec.encode(model)
        assertTrue(text.contains("\"indices\":[9]"), text)
        // cat-b 一行全零 → 序列化为空行，不出现一大串 0。
        assertTrue(text.contains("\"indices\":[]"), text)
        // 稀疏编码：文件体积远小于稠密 2×262144 个浮点，否则权重文件会膨胀到几十 MB。
        assertTrue(text.length < 5_000, "size=${text.length}")
    }

    @Test
    fun encodeRejectsIncompatibleFeatureVersion() {
        assertFailsWith<ModelFormatException> {
            ModelCodec.encode(sampleModel(featureVersion = CharNgramFeaturizer.FEATURE_VERSION + 1))
        }
    }

    @Test
    fun encodeRejectsWrongDimension() {
        assertFailsWith<ModelFormatException> {
            ModelCodec.encode(sampleModel(dim = 8))
        }
    }

    @Test
    fun roundTripsEmptyLabelModelWithPendingCounts() {
        // 「所有分类都没攒够阈值」会产出一个标签表为空的模型；它必须能被序列化/反序列化（PRD D3#3 冷启动）。
        val model = LinearModel(
            labels = emptyList(),
            dim = CharNgramFeaturizer.BUCKETS,
            weights = emptyArray(),
            bias = FloatArray(0),
            labelCounts = IntArray(0),
            trainedAt = 7L,
            modelVersion = "m-empty",
            pendingCounts = mapOf("cat-travel" to 2),
        )
        val decoded = ModelCodec.decode(ModelCodec.encode(model))
        assertTrue(decoded.labels.isEmpty())
        assertEquals(mapOf("cat-travel" to 2), decoded.pendingCounts)
        assertEquals("m-empty", decoded.modelVersion)
        assertContentEquals(FloatArray(0), decoded.bias)
        assertTrue(decoded.probabilitiesOf(CharNgramFeaturizer.featuresOfText("机票")).isEmpty())
    }

    @Test
    fun decodeReadsValidFile() {
        val model = ModelCodec.decode(validJson())
        assertEquals(listOf("cat-a"), model.labels)
        assertEquals(0.5f, model.weights[0][1], 0f)
        assertTrue(model.pendingCounts.isEmpty())
    }

    @Test
    fun decodeRejectsWrongFormatVersion() {
        assertFailsWith<ModelFormatException> { ModelCodec.decode(validJson(formatVersion = 99)) }
    }

    @Test
    fun decodeRejectsIncompatibleFeatureVersion() {
        assertFailsWith<ModelFormatException> { ModelCodec.decode(validJson(featureVersion = 99)) }
    }

    @Test
    fun decodeRejectsWrongBucketCount() {
        assertFailsWith<ModelFormatException> { ModelCodec.decode(validJson(buckets = 8)) }
    }

    @Test
    fun decodeRejectsMismatchedSizesAndMalformedJson() {
        assertFailsWith<ModelFormatException> { ModelCodec.decode(validJson(bias = "[0.0,0.0]")) }
        assertFailsWith<ModelFormatException> { ModelCodec.decode("{ not json") }
    }
}
