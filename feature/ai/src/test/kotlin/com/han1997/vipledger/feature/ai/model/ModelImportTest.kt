package com.han1997.vipledger.feature.ai.model

import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import com.han1997.vipledger.ml.classifier.model.LinearModel
import com.han1997.vipledger.ml.classifier.model.ModelCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 模型导入的校验（PRD Requirement 6）：外来 / 不兼容的 JSON 必须被拒绝，
 * 只有经 `ModelCodec` 校验通过的权重文件才允许采纳。
 */
class ModelImportTest {

    private fun validModelJson(): String {
        val dim = CharNgramFeaturizer.BUCKETS
        val model = LinearModel(
            labels = listOf("cat-food", "cat-transport"),
            dim = dim,
            weights = Array(2) { FloatArray(dim) },
            bias = FloatArray(2) { 0.5f },
            labelCounts = intArrayOf(5, 5),
            trainedAt = 1L,
            modelVersion = "v1-1000",
        )
        return ModelCodec.encode(model)
    }

    @Test
    fun acceptsModelProducedByCodec() {
        val parsed = ModelImport.parse(validModelJson())
        assertNotNull(parsed, "由 ModelCodec 产出的权重应可导入")
        assertEquals(listOf("cat-food", "cat-transport"), parsed.labels)
    }

    @Test
    fun rejectsForeignJson() {
        assertNull(ModelImport.parse("{\"foo\": 1, \"bar\": [1,2,3]}"), "外来 JSON 不得被当成模型")
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(ModelImport.parse("not even json"))
        assertNull(ModelImport.parse(""))
    }

    @Test
    fun rejectsIncompatibleFormatVersion() {
        val tampered = validModelJson().replace("\"formatVersion\":1", "\"formatVersion\":2")
        assertNull(ModelImport.parse(tampered), "格式版本不兼容必须被拒绝")
    }
}
