package com.darshan.splitsmart.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.model.VpaError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = DATE_FORMAT.format(instant.atZone(zone))

@Composable
fun NameError.label(): String = stringResource(
    when (this) {
        NameError.BLANK -> R.string.error_name_blank
        NameError.TOO_LONG -> R.string.error_name_too_long
        NameError.INVALID_CHARS -> R.string.error_name_invalid
    },
)

@Composable
fun VpaError.label(): String = stringResource(
    when (this) {
        VpaError.BLANK -> R.string.error_vpa_blank
        VpaError.MISSING_HANDLE -> R.string.error_vpa_missing_handle
        VpaError.INVALID_CHARS -> R.string.error_vpa_invalid
        VpaError.TOO_LONG -> R.string.error_vpa_too_long
    },
)

@Composable
fun BillStatus.label(): String = stringResource(
    when (this) {
        BillStatus.DRAFT -> R.string.bill_status_draft
        BillStatus.SPLIT -> R.string.bill_status_split
        BillStatus.SETTLED -> R.string.bill_status_settled
    },
)

@Composable
fun BillSource.label(): String = stringResource(
    when (this) {
        BillSource.MANUAL -> R.string.bill_source_manual
        BillSource.SCAN_LLM -> R.string.bill_source_llm
        BillSource.SCAN_REGEX -> R.string.bill_source_regex
    },
)
