package com.han1997.vipledger.feature.ai.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
