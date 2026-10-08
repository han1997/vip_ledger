package com.han1997.vipledger.autobook

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentNodeTextReaderTest {
    private fun node(text: String? = null, description: String? = null, visible: Boolean = true,
                     password: Boolean = false, vararg children: AccessibilityNodeInfo): AccessibilityNodeInfo = mockk {
        every { getText() } returns text
        every { getContentDescription() } returns description
        every { isVisibleToUser } returns visible
        every { isPassword } returns password
        every { childCount } returns children.size
        children.forEachIndexed { index, child -> every { getChild(index) } returns child }
    }

    @Test fun nestedValueStaysWithItsLabelInsteadOfFollowingOtherRows() {
        val root = node(children = arrayOf(
            node(children = arrayOf(node("付款方式"), node(children = arrayOf(node("测试信用卡(1234)"))))),
            node(children = arrayOf(node("订单号"), node("test-order-123"))),
        ))
        assertEquals("付款方式\n测试信用卡(1234)\n订单号\ntest-order-123\n", PaymentNodeTextReader.read(root))
    }

    @Test fun invisibleContainerDoesNotHideVisibleChildrenAndPasswordSubtreeIsSkipped() {
        val root = node(visible = false, children = arrayOf(
            node("交易成功", "交易成功"), node(password = true, children = arrayOf(node("secret"))),
        ))
        assertEquals("交易成功\n", PaymentNodeTextReader.read(root))
    }
}
