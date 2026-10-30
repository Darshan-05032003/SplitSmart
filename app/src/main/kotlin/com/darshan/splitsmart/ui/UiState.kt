package com.darshan.splitsmart.ui

/** Every screen renders exactly one of these for its main content. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Empty : LoadState<Nothing>
    data class Error(val reason: ErrorReason) : LoadState<Nothing>
    data class Content<T>(val data: T) : LoadState<T>
}

enum class ErrorReason { NOT_FOUND, STORAGE, UNKNOWN }

fun Throwable.toErrorReason(): ErrorReason = when (this) {
    is android.database.sqlite.SQLiteException, is java.io.IOException -> ErrorReason.STORAGE
    else -> ErrorReason.UNKNOWN
}

/** Test tags shared by the screens and the Compose tests. */
object SplitSmartTestTags {
    const val LOADING = "loading"
    const val EMPTY = "empty"
    const val ERROR = "error"
    const val DIALOG_CONFIRM = "dialog_confirm"
    const val FAB_ADD_GROUP = "fab_add_group"
    const val GROUP_NAME_FIELD = "group_name_field"
    const val GROUP_ITEM = "group_item"
    const val ADD_MEMBER_BUTTON = "add_member_button"
    const val MEMBER_NAME_FIELD = "member_name_field"
    const val MEMBER_VPA_FIELD = "member_vpa_field"
    const val MEMBER_ITEM = "member_item"
    const val ADD_BILL_BUTTON = "add_bill_button"
    const val BILL_TITLE_FIELD = "bill_title_field"
    const val BILL_AMOUNT_FIELD = "bill_amount_field"
    const val BILL_ITEM = "bill_item"
    const val BILL_ITEM_ROW = "bill_item_row"
    const val DELETE_GROUP_BUTTON = "delete_group_button"
    const val DELETE_BILL_BUTTON = "delete_bill_button"
    const val SCAN_BUTTON = "scan_button"
    const val CAPTURE_BUTTON = "capture_button"
    const val GALLERY_BUTTON = "gallery_button"
    const val GRANT_PERMISSION_BUTTON = "grant_permission_button"
    const val OPEN_SETTINGS_BUTTON = "open_settings_button"
    const val OCR_TEXT = "ocr_text"
    const val CONTINUE_BUTTON = "continue_button"
    const val RESCAN_BUTTON = "rescan_button"
}
