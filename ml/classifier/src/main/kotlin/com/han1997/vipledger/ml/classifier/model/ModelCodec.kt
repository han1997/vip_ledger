package com.han1997.vipledger.ml.classifier.model

import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 权重文件不可读 / 不兼容时抛出的异常；调用方（[ModelFileStore]）转成「无可用模型」而不是崩溃。 */
class ModelFormatException(message: String) : Exception(message)

/**
 * 权重文件的 JSON 编解码（ARCHITECTURE §7：「权重为 `FloatArray`，序列化为 JSON（含词表版本、分类标签表）」）。
 *
 * ## 文件结构（`tools/trainer/` 的 Python 侧必须产出同名字段）
 *
 * ```json
 * {
 *   "formatVersion": 1,
 *   "featureVersion": 1,
 *   "vocabVersion": "char2-3gram-fnv1a-v1",
 *   "buckets": 262144,
 *   "modelVersion": "v1-1695800000000",
 *   "trainedAt": 1695800000000,
 *   "sampleCount": 120,
 *   "labels": ["cat-food", "cat-transport"],
 *   "labelCounts": [40, 80],
 *   "pendingCounts": { "cat-travel": 2 },
 *   "bias": [0.1, -0.2],
 *   "weights": [
 *     { "indices": [12, 4567], "values": [0.51, -0.12] },
 *     { "indices": [12], "values": [-0.40] }
 *   ]
 * }
 * ```
 *
 * - `labels` **升序**，`labelCounts`/`bias`/`weights` 与之逐行对齐；
 * - `pendingCounts` 存「累计样本数仍低于阈值、尚未进标签表」的分类及计数（可缺省，缺省视为空）；
 * - `weights` 只存**非零桶**（稠密的 `1<<18` 桶 × 分类数会让文件膨胀到几十 MB），
 *   `indices` 升序、`values` 一一对应，未列出的桶恒为 0；
 * - 解码时严格校验 `formatVersion`/`featureVersion`/`buckets` 与维度对齐：
 *   版本不符或结构损坏一律抛 [ModelFormatException]（「当前词表不可加载」）。
 */
object ModelCodec {

    /** 权重文件容器格式版本；字段增删或语义变化时 +1（与 [CharNgramFeaturizer.FEATURE_VERSION] 独立）。 */
    const val FORMAT_VERSION: Int = 1

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(model: LinearModel): String {
        validate(model)
        val dto = ModelFileDto(
            formatVersion = FORMAT_VERSION,
            featureVersion = model.featureVersion,
            vocabVersion = model.vocabVersion,
            buckets = model.dim,
            modelVersion = model.modelVersion,
            trainedAt = model.trainedAt,
            sampleCount = model.sampleCount,
            labels = model.labels,
            labelCounts = model.labelCounts.toList(),
            pendingCounts = model.pendingCounts,
            bias = model.bias.toList(),
            weights = model.weights.map { row ->
                val indices = ArrayList<Int>()
                val values = ArrayList<Float>()
                for (f in row.indices) {
                    val v = row[f]
                    if (v != 0f) {
                        indices += f
                        values += v
                    }
                }
                SparseRowDto(indices, values)
            },
        )
        return json.encodeToString(dto)
    }

    /** 解析并校验；任何不兼容/损坏都抛 [ModelFormatException]。 */
    fun decode(text: String): LinearModel {
        val dto = try {
            json.decodeFromString(ModelFileDto.serializer(), text)
        } catch (e: Exception) {
            throw ModelFormatException("malformed weights json: ${e.message}")
        }
        if (dto.formatVersion != FORMAT_VERSION) {
            throw ModelFormatException("unsupported formatVersion=${dto.formatVersion}")
        }
        if (dto.featureVersion != CharNgramFeaturizer.FEATURE_VERSION) {
            throw ModelFormatException(
                "featureVersion=${dto.featureVersion} != ${CharNgramFeaturizer.FEATURE_VERSION}",
            )
        }
        if (dto.buckets != CharNgramFeaturizer.BUCKETS) {
            throw ModelFormatException("buckets=${dto.buckets} != ${CharNgramFeaturizer.BUCKETS}")
        }
        val n = dto.labels.size
        if (dto.labelCounts.size != n || dto.bias.size != n || dto.weights.size != n) {
            throw ModelFormatException("labels/bias/weights/labelCounts size mismatch")
        }
        val weights = Array(n) { c ->
            val row = dto.weights[c]
            if (row.indices.size != row.values.size) {
                throw ModelFormatException("row $c indices/values size mismatch")
            }
            val dense = FloatArray(dto.buckets)
            for (i in row.indices.indices) {
                val f = row.indices[i]
                if (f < 0 || f >= dto.buckets) throw ModelFormatException("row $c index $f out of range")
                dense[f] = row.values[i]
            }
            dense
        }
        return try {
            LinearModel(
                labels = dto.labels,
                dim = dto.buckets,
                weights = weights,
                bias = dto.bias.toFloatArray(),
                labelCounts = dto.labelCounts.toIntArray(),
                trainedAt = dto.trainedAt,
                modelVersion = dto.modelVersion,
                featureVersion = dto.featureVersion,
                vocabVersion = dto.vocabVersion,
                sampleCount = dto.sampleCount,
                pendingCounts = dto.pendingCounts,
            )
        } catch (e: IllegalArgumentException) {
            throw ModelFormatException(e.message ?: "invalid model")
        }
    }

    private fun validate(model: LinearModel) {
        if (model.featureVersion != CharNgramFeaturizer.FEATURE_VERSION) {
            throw ModelFormatException("featureVersion=${model.featureVersion} not encodable")
        }
        if (model.dim != CharNgramFeaturizer.BUCKETS) {
            throw ModelFormatException("dim=${model.dim} != ${CharNgramFeaturizer.BUCKETS}")
        }
        if (model.labels.size != model.weights.size ||
            model.labels.size != model.bias.size ||
            model.labels.size != model.labelCounts.size
        ) {
            throw ModelFormatException("labels/bias/weights/labelCounts size mismatch")
        }
    }

    @Serializable
    private data class ModelFileDto(
        val formatVersion: Int,
        val featureVersion: Int,
        val vocabVersion: String,
        val buckets: Int,
        val modelVersion: String,
        val trainedAt: Long,
        val sampleCount: Int,
        val labels: List<String>,
        val labelCounts: List<Int>,
        val pendingCounts: Map<String, Int> = emptyMap(),
        val bias: List<Float>,
        val weights: List<SparseRowDto>,
    )

    @Serializable
    private data class SparseRowDto(
        val indices: List<Int>,
        val values: List<Float>,
    )
}
