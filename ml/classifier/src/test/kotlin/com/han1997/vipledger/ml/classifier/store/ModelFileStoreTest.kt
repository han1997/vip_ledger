package com.han1997.vipledger.ml.classifier.store

import com.han1997.vipledger.ml.classifier.feature.CharNgramFeaturizer
import com.han1997.vipledger.ml.classifier.model.LinearModel
import com.han1997.vipledger.ml.classifier.model.MINIMUM_FEATURE_VERSION
import com.han1997.vipledger.ml.classifier.model.ModelFormatException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModelFileStoreTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("ml-model-store").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun toyModel(
        bias: Float = 0f,
        featureVersion: Int = MINIMUM_FEATURE_VERSION,
    ): LinearModel {
        val dim = CharNgramFeaturizer.BUCKETS
        return LinearModel(
            labels = listOf("cat-a"),
            dim = dim,
            weights = Array(1) { FloatArray(dim) },
            bias = floatArrayOf(bias),
            labelCounts = intArrayOf(5),
            trainedAt = 1L,
            modelVersion = "m-1",
            featureVersion = featureVersion,
        )
    }

    private fun target() = File(dir, ModelFileStore.DEFAULT_MODEL_FILE_NAME)

    @Test
    fun reportsMissingForAbsentFile() {
        assertEquals(ModelLoadResult.Missing, ModelFileStore(target()).load())
    }

    @Test
    fun savesAndLoadsModel() {
        val store = ModelFileStore(target())
        store.save(toyModel(bias = 0.25f))

        val loaded = store.load()
        assertIs<ModelLoadResult.Loaded>(loaded)
        assertEquals(listOf("cat-a"), loaded.model.labels)
        assertEquals(0.25f, loaded.model.bias[0], 0f)
        assertEquals("m-1", loaded.model.modelVersion)
    }

    @Test
    fun halfWrittenTempFileIsNeverReadByInference() {
        val store = ModelFileStore(target())
        store.save(toyModel(bias = 0.125f))

        // 模拟「训练写到一半崩溃」：临时文件残留垃圾，正式文件未被触碰。
        val temp = File(dir, ModelFileStore.DEFAULT_MODEL_FILE_NAME + ".tmp")
        temp.writeText("{ half written and broken")

        val loaded = store.load()
        assertIs<ModelLoadResult.Loaded>(loaded)
        assertEquals(0.125f, loaded.model.bias[0], 0f)

        // 下一次成功训练原子覆盖，临时文件不再残留。
        store.save(toyModel(bias = 0.5f))
        assertFalse(temp.exists())
        assertEquals(0.5f, (store.load() as ModelLoadResult.Loaded).model.bias[0], 0f)
    }

    @Test
    fun failedSaveLeavesPreviousWeightsLoadable() {
        val store = ModelFileStore(target())
        store.save(toyModel(bias = 0.125f))

        // 非法模型（特征版本不兼容）在碰文件之前就抛错 → 旧权重不变、无残留临时文件。
        assertFailsWith<ModelFormatException> {
            store.save(toyModel(bias = 0.9f, featureVersion = MINIMUM_FEATURE_VERSION + 1))
        }
        assertEquals(0.125f, (store.load() as ModelLoadResult.Loaded).model.bias[0], 0f)
        assertFalse(File(dir, ModelFileStore.DEFAULT_MODEL_FILE_NAME + ".tmp").exists())
    }

    @Test
    fun writeFailureMidSaveLeavesPreviousModelLoadable() {
        val store = ModelFileStore(target())
        store.save(toyModel(bias = 0.125f))

        // 让临时文件路径被一个**目录**占据，从而 FileOutputStream 在写临时文件时必然失败——
        // 这模拟「写盘中途 I/O 失败」，而不是只信任编码期就失败的快路径。
        val tempPath = File(dir, ModelFileStore.DEFAULT_MODEL_FILE_NAME + ".tmp")
        assertTrue(tempPath.mkdirs())

        assertFailsWith<IOException> { store.save(toyModel(bias = 0.9f)) }

        // 正式文件从未被触碰：仍是上一份完整模型。
        assertEquals(0.125f, (store.load() as ModelLoadResult.Loaded).model.bias[0], 0f)
    }

    @Test
    fun corruptTargetIsReportedAsInvalidNotCrash() {
        target().writeText("{ not a model")
        val result = ModelFileStore(target()).load()
        assertIs<ModelLoadResult.Invalid>(result)
        assertTrue(result.reason.isNotBlank())
    }
}
