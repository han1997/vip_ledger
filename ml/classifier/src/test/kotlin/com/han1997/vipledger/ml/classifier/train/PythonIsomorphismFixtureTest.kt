package com.han1997.vipledger.ml.classifier.train

import com.han1997.vipledger.ml.classifier.model.ModelCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 同构 fixture 的 **Kotlin 侧护栏**（PRD AC「Python 脚本与 Kotlin 侧对同一份样本、同一组超参产出的权重
 * 在容差内一致（同构验证）」的其中一半）。
 *
 * ## 两侧分工（读这段再改代码，别把「护栏」误当成「跨语言比对」）
 *
 * 同构验证只允许有**一个**真正的跨语言比对方向，否则就会自证其说。当前分工：
 *
 * - **真·跨语言比对 = Python 半边**：`tools/trainer/selfcheck.py`（`python train.py --selfcheck`）
 *   用 **Python** 重新训练，与 checked-in golden（**Kotlin 产出**）按容差比对。这是唯一能发现
 *   「Kotlin 与 Python 口径不一致」的地方，而且它**必须看到非零差异**（两侧分别是 float32 / float64，
 *   逐位相同才是异常）。注意它**不在 Gradle 里**，要单独跑（见 `tools/trainer/README.md`）。
 * - **护栏 = 本文件**（[kotlinTrainingIsDeterministicAndMatchesPinnedGolden]）：Kotlin 两次训练应产出
 *   逐字节相同的 JSON，且等于 checked-in golden。这**不是**跨语言比对（两侧都是 Kotlin），而是
 *   「Kotlin 侧口径漂移 + golden 过期」的护栏：Kotlin 一变，这里先红，提示先重生成 golden，
 *   再让 Python selfcheck 去暴露真正的语言间分歧。
 *
 * golden `expected_model.json` 由 **Kotlin** `ModelCodec.encode` 产出（设备侧才是权重格式的权威），
 * 重生成入口见 [regenerateGoldenWhenRequested]，**不要手改浮点**。
 *
 * ## 容差
 * 容差（`fixture.json` 的 `tolerance`，见 README 对取值理由的说明）只被 **Python 半边**消费；
 * 本文件做的是逐字节比对，不存在容差问题。
 */
class PythonIsomorphismFixtureTest {

    @Serializable
    private data class Fixture(
        val epochs: Int,
        val learningRate: Float,
        val minSamplesPerClass: Int,
        val seed: Long,
        val trainedAt: Long,
        val tolerance: Double,
    )

    @Serializable
    private data class SampleLine(
        val text: String,
        val categoryId: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** 从 `user.dir` 逐级向上找 `tools/trainer/fixtures`；找不到就大声失败（不静默跳过）。 */
    private fun fixtureDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "tools/trainer/fixtures")
            if (File(candidate, "fixture.json").isFile) return candidate
            dir = dir.parentFile
        }
        error(
            "fixture dir 'tools/trainer/fixtures' not found walking up from " +
                System.getProperty("user.dir"),
        )
    }

    /**
     * 用 fixture 的超参在 Kotlin 侧重训，返回 `ModelCodec.encode` 出的**逐字节**权重 JSON。
     * golden 就是同一函数的产物，因此本函数与 golden 比对即「口径未漂移」。
     */
    private fun trainAndEncode(): String {
        val dir = fixtureDir()
        val fixture = json.decodeFromString(Fixture.serializer(), File(dir, "fixture.json").readText())
        val samples = File(dir, "samples.jsonl").readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> json.decodeFromString(SampleLine.serializer(), line) }
            .map { TrainingSample(it.text, it.categoryId) }
        val config = TrainingConfig(
            epochs = fixture.epochs,
            learningRate = fixture.learningRate,
            minSamplesPerClass = fixture.minSamplesPerClass,
            seed = fixture.seed,
        )
        val actual = LinearModelTrainer.train(
            base = null,
            samples = samples,
            config = config,
            now = fixture.trainedAt,
        )!!.model
        return ModelCodec.encode(actual)
    }

    /**
     * **护栏（drift guard），不是跨语言比对**——两侧都是 Kotlin。
     *
     * 断言两件事：
     * 1. **确定性**：同一样本 + 同一超参 + 同一 seed，Kotlin 连训两次产出逐字节相同的 JSON
     *    （若训练里混入无种子 RNG 或 HashMap 迭代序，这里会红）；
     * 2. **golden 未过期**：上面那串字节等于 checked-in 的 `expected_model.json`
     *    （golden 由 Kotlin 产出；Kotlin 特征/洗牌/SGD/导出格式任一处改动都会让它红，
     *    此时先跑 [regenerateGoldenWhenRequested] 重生成，再看 Python selfcheck 是否变红）。
     *
     * 真正的跨语言一致性由 `tools/trainer/selfcheck.py` 负责，见类注释。
     */
    @Test
    fun kotlinTrainingIsDeterministicAndMatchesPinnedGolden() {
        val first = trainAndEncode()
        val second = trainAndEncode()
        assertEquals(first, second, "Kotlin training must be byte-for-byte deterministic")

        val golden = File(fixtureDir(), "expected_model.json").readText().trim()
        val encoded = first.trim()
        println(
            "kotlin drift guard: encodedBytes=${encoded.length} goldenBytes=${golden.length} " +
                "labelsMentioned=${encoded.contains("\"labels\"")}",
        )
        assertTrue(
            encoded.length > 0,
            "encoded model must be non-empty",
        )
        assertEquals(
            encoded,
            golden,
            "Kotlin output drifted from the checked-in golden; regenerate it via " +
                "regenerateGoldenWhenRequested (see class KDoc) and then re-run the Python selfcheck",
        )
    }

    /**
     * golden 重生成入口（默认不写文件）。
     *
     * 只在环境变量 `REGENERATE_ISOMORPHISM_GOLDEN` 设为非空时，用 Kotlin 训练结果覆盖
     * `tools/trainer/fixtures/expected_model.json`。**golden 的权威是 Kotlin**（设备侧定义格式），
     * 因此重生成必须走 Kotlin，禁止手工编辑浮点。
     *
     * ```powershell
     * $env:REGENERATE_ISOMORPHISM_GOLDEN=1
     * .\gradlew.bat :ml:classifier:test --rerun-tasks `
     *   --tests "*PythonIsomorphismFixtureTest.regenerateGoldenWhenRequested"
     * ```
     *
     * 未设置环境变量时本测试不做任何事（始终绿），以免误写。
     */
    @Test
    fun regenerateGoldenWhenRequested() {
        val flag = System.getenv("REGENERATE_ISOMORPHISM_GOLDEN")
        if (flag.isNullOrBlank()) return
        val encoded = trainAndEncode()
        val target = File(fixtureDir(), "expected_model.json")
        target.writeText(encoded)
        println("regenerated golden: ${target.absolutePath} (${encoded.length} bytes)")
    }
}
