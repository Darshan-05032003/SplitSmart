package com.darshan.splitsmart.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.SplitSmartTestTags

@Composable
fun LoadingView(modifier: Modifier = Modifier, label: String? = null) {
    val description = label ?: stringResource(R.string.loading)
    Box(
        modifier = modifier.fillMaxSize().testTag(SplitSmartTestTags.LOADING).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            if (label != null) Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Friendly empty state: illustration, title, explanation and an optional primary action. */
@Composable
fun EmptyView(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    illustration: Painter? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp).testTag(SplitSmartTestTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (illustration != null) EmptyIllustration(main = illustration, modifier = Modifier.padding(bottom = 24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (action != null) Box(Modifier.padding(top = 24.dp)) { action() }
    }
}

@Composable
fun ErrorView(reason: ErrorReason, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp).testTag(SplitSmartTestTags.ERROR),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBubble(Icons.Default.Warning, size = 72.dp, container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
        Text(
            stringResource(reason.messageRes()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

fun ErrorReason.messageRes(): Int = when (this) {
    ErrorReason.NOT_FOUND -> R.string.error_not_found
    ErrorReason.STORAGE -> R.string.error_storage
    ErrorReason.UNKNOWN -> R.string.error_unknown
}
