package com.han1997.vipledger.autobook

import android.view.accessibility.AccessibilityNodeInfo

/** Preserve each row's label/value subtree before moving to the next row. */
object PaymentNodeTextReader {
    fun read(root: AccessibilityNodeInfo): String {
        val result = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var count = 0
        while (stack.isNotEmpty() && count++ < 300 && result.length < 16_384) {
            val node = stack.removeLast()
            if (node.isPassword) continue
            if (node.isVisibleToUser) {
                val text = node.text?.toString().orEmpty()
                val description = node.contentDescription?.toString().orEmpty()
                if (text.isNotBlank()) result.append(text).append('\n')
                if (description.isNotBlank() && description != text) result.append(description).append('\n')
            }
            for (i in node.childCount.coerceAtMost(80) - 1 downTo 0) node.getChild(i)?.let(stack::addLast)
        }
        return result.toString().take(16_384)
    }
}
