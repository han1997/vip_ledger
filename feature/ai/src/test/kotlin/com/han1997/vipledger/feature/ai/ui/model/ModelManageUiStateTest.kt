package com.han1997.vipledger.feature.ai.ui.model

import com.han1997.vipledger.ml.classifier.train.TrainingConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 模型管理页的纯状态规则（PRD Requirement 6 / D3#3）。
 *
 * 覆盖：被跳过分类的 id 排序 / 名字回退、样本导出的可用性、阈值与开关默认值。
 */
class ModelManageUiStateTest {

    @Test
    fun skippedClassesSortedByIdWithNameLookupAndFallback() {
        val rows = skippedClassesOf(
            pendingCounts = mapOf("c2" to 2, "c1" to 3, "c9" to 1),
            names = mapOf("c1" to "餐饮", "c2" to "交通"),
        )

        assertEquals(listOf("c1", "c2", "c9"), rows.map { it.categoryId }, "按分类 id 稳定排序")
        assertEquals(listOf("餐饮", "交通", ""), rows.map { it.name }, "缺名字回退空串，界面再回退成 id")
        assertEquals(listOf(3, 2, 1), rows.map { it.sampleCount })
    }

    @Test
    fun emptySkippedClassesYieldsEmptyList() {
        assertTrue(skippedClassesOf(emptyMap(), emptyMap()).isEmpty())
    }

    @Test
    fun hasSamplesToExportReflectsTotalSamples() {
        assertFalse(ModelManageUiState(totalSamples = 0).hasSamplesToExport)
        assertTrue(ModelManageUiState(totalSamples = 1).hasSamplesToExport)
    }

    @Test
    fun autoTrainThresholdAndSwitchDefaults() {
        val state = ModelManageUiState()
        assertEquals(30, state.autoTrainThreshold, "阈值取 ARCHITECTURE §7 的 30")
        assertFalse(state.autoTrainEnabled, "自动训练开关默认关闭（M7 验收）")
        assertFalse(state.hasModel, "初始没有模型")
    }

    @Test
    fun perClassThresholdIsSourcedFromTrainingConfigAndNotConflatedWithAutoTrainThreshold() {
        // 回归保护：单类阈值（D3#3，默认 3）与自动训练触发线（§7，默认 30）语义不同。
        // 曾把「样本不足被跳过的分类」文案误接成 autoTrainThreshold，页面因此在同页给出两个互相矛盾的数。
        val state = ModelManageUiState()
        assertEquals(
            TrainingConfig.DEFAULT_MIN_SAMPLES_PER_CLASS,
            state.minSamplesPerClass,
            "每类阈值必须直连 TrainingConfig 默认值，不写死、不漂移",
        )
        assertEquals(3, state.minSamplesPerClass, "PRD D3#3 的单类阈值是 3")
        assertEquals(30, state.autoTrainThreshold, "自动训练触发线仍是 30")
        assertNotEquals(
            state.minSamplesPerClass,
            state.autoTrainThreshold,
            "两个阈值是两回事，不得再用同一个数描述",
        )
    }
}
