package com.han1997.vipledger.core.domain.support

import java.util.UUID

/** id 生成器：主键都是 UUID v4 字符串（docs/DATA-MODEL.md 第 1 节），测试里可替换成固定值。 */
fun interface IdGenerator {
    fun newId(): String
}

object UuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}