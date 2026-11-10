package com.darshan.splitsmart.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.canRetry
import com.darshan.splitsmart.ui.designsystem.components.ErrorState
import com.darshan.splitsmart.ui.messageRes

/**
 * The one error page every screen shows when its data could not be read: "Couldn't load …" as the title, the reason in plain
 * words, and one button. A read that failed offers Try again; something that is gone offers Back, because loading it again
 * cannot work. Scrolls, so 1.3× text never pushes the button off screen.
 */
@Composable
fun LoadErrorState(title: String, reason: ErrorReason, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ErrorState(
            title = title,
            message = stringResource(reason.messageRes()),
            onRetry = if (reason.canRetry) onRetry else onBack,
            retryLabel = stringResource(if (reason.canRetry) R.string.retry_label else R.string.action_back),
            modifier = Modifier.testTag(SplitSmartTestTags.ERROR),
        )
    }
}
