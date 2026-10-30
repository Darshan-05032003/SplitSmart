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
    const val REVIEW_ITEM = "review_item"
    const val REVIEW_ITEM_NAME = "review_item_name"
    const val REVIEW_ITEM_QTY = "review_item_qty"
    const val REVIEW_ITEM_AMOUNT = "review_item_amount"
    const val REVIEW_ADD_ITEM = "review_add_item"
    const val REVIEW_TITLE = "review_title"
    const val REVIEW_TAX = "review_tax"
    const val REVIEW_TIP = "review_tip"
    const val REVIEW_TOTAL = "review_total"
    const val REVIEW_SAVE = "review_save"
    const val REVIEW_RERUN = "review_rerun"
    const val FALLBACK_BANNER = "fallback_banner"
    const val REFINING_BANNER = "refining_banner"
    const val AI_READY_CARD = "ai_ready_card"
    const val AI_APPLY = "ai_apply"
    const val REVIEW_SOURCE = "review_source"
    const val MISMATCH_BANNER = "mismatch_banner"
    const val USE_COMPUTED_TOTAL = "use_computed_total"
    const val SPLIT_BUTTON = "split_button"
    const val SHARE_ROW = "share_row"
    const val DEBT_ITEM = "debt_item"
    const val SPLIT_MODE_EQUAL = "split_mode_equal"
    const val SPLIT_MODE_BY_ITEM = "split_mode_by_item"
    const val SPLIT_MODE_BY_SHARE = "split_mode_by_share"
    const val SPLIT_PARTICIPANT = "split_participant"
    const val SPLIT_PAYER_CHIP = "split_payer_chip"
    const val SPLIT_ITEM_CHIP = "split_item_chip"
    const val SPLIT_SHARE_FIELD = "split_share_field"
    const val SPLIT_PREVIEW_ROW = "split_preview_row"
    const val SPLIT_SAVE = "split_save"
    const val SETTLE_UP_BUTTON = "settle_up_button"
    const val SETTLE_ITEM = "settle_item"
    const val SETTLE_PAY = "settle_pay"
    const val SETTLE_SHARE = "settle_share"
    const val SETTLE_QR = "settle_qr"
    const val SETTLE_ADD_VPA = "settle_add_vpa"
    const val QR_IMAGE = "qr_image"
}
