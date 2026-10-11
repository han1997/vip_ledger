package com.han1997.vipledger.feature.ai.ui.entry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 图片批量识别结果 → 一次性提示的归类规则（P5 图片切片）。 */
class ImageParseNoticeTest {

    @Test
    fun allImagesParsedReusesParsedNotice() {
        assertEquals(AiNotice.PARSED, imageParseNotice(parsedCount = 3, failedCount = 0))
    }

    @Test
    fun partialFailureUsesPartialNotice() {
        assertEquals(AiNotice.IMAGES_PARTIAL, imageParseNotice(parsedCount = 2, failedCount = 1))
    }

    @Test
    fun allFailuresPromptManualInput() {
        assertEquals(AiNotice.IMAGES_NO_TEXT, imageParseNotice(parsedCount = 0, failedCount = 2))
    }

    @Test
    fun noImagesProduceNoNotice() {
        assertNull(imageParseNotice(parsedCount = 0, failedCount = 0))
    }
}