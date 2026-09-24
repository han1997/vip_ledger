package com.han1997.vipledger.core.domain.model

import com.han1997.vipledger.core.model.MlSampleSource

/** 交易附件（docs/DATA-MODEL.md 3.9）。文件本体在应用私有目录，库里只存相对路径。 */
data class Attachment(
    val id: String,
    val txnId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
    val createdAt: Long,
    val deletedAt: Long? = null,
)

/** 端侧分类器训练样本（docs/DATA-MODEL.md 3.10）。 */
data class MlTrainingSample(
    val id: String,
    val ledgerId: String,
    val text: String,
    val categoryId: String,
    val source: MlSampleSource,
    val trainedVersion: String? = null,
    val createdAt: Long,
)