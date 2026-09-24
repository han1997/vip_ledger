package com.han1997.vipledger.feature.ledger.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.han1997.vipledger.core.common.result.AppResult
import com.han1997.vipledger.feature.ledger.R

/**
 * 管理类页面的一次性提示。
 *
 * 状态里只放枚举、不放中文：文案在界面层查表得到（见 .trellis/spec/android/ui.md）。
 */
enum class ManageNotice {
    SAVED,
    DELETED,
    ARCHIVED,
    UNARCHIVED,
    DEFAULT_SET,
    SWITCHED,
    FAILED,
}

/**
 * 失败结果里的用户可读原因（`AppError.message`）；成功时为 null。
 * 领域层写好的说明（如「分类已被账目引用」）比笼统的失败文案有用得多。
 */
fun AppResult<*>.failureMessage(): String? = (this as? AppResult.Failure)?.error?.message

/** 提示文案：失败且有原因时优先显示原因，否则退回通用文案。 */
@Composable
fun manageNoticeMessage(notice: ManageNotice, detail: String? = null): String {
    if (notice == ManageNotice.FAILED && !detail.isNullOrBlank()) return detail
    return stringResource(
        when (notice) {
            ManageNotice.SAVED -> R.string.notice_saved
            ManageNotice.DELETED -> R.string.notice_deleted
            ManageNotice.ARCHIVED -> R.string.notice_archived
            ManageNotice.UNARCHIVED -> R.string.notice_unarchived
            ManageNotice.DEFAULT_SET -> R.string.notice_default_set
            ManageNotice.SWITCHED -> R.string.notice_switched
            ManageNotice.FAILED -> R.string.notice_failed
        },
    )
}