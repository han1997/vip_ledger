package com.han1997.vipledger.core.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PasswordFieldTest {
    @Test fun secretFieldsMaskCharactersWithoutChangingCursorOffsets() {
        listOf(KeyboardType.Password, KeyboardType.NumberPassword).forEach { type ->
            val input = AnnotatedString("Test123!")
            val result = textFieldVisualTransformation(type).filter(input)
            assertEquals(input.length, result.text.length)
            assertFalse(result.text.text.contains("Test"))
            assertEquals(1, result.text.text.toSet().size)
            (0..input.length).forEach { offset ->
                assertEquals(offset, result.offsetMapping.originalToTransformed(offset))
                assertEquals(offset, result.offsetMapping.transformedToOriginal(offset))
            }
        }
    }

    @Test fun ordinaryAndAmountFieldsRemainReadable() {
        listOf(KeyboardType.Text, KeyboardType.Decimal).forEach { type ->
            val input = AnnotatedString("123.45")
            assertEquals(input, textFieldVisualTransformation(type).filter(input).text)
        }
    }
}
