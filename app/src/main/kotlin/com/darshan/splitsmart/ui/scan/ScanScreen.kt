package com.darshan.splitsmart.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.LoadingView

/** Production entry point: wires the permission and photo-picker launchers, then delegates to the testable route. */
@Composable
fun ScanRoute(viewModel: ScanViewModel = hiltViewModel(), onBack: () -> Unit, onContinue: (String) -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = granted || (context as? Activity)?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: true
        viewModel.onPermissionResult(granted, canAskAgain)
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onImagePicked(it.toString()) }
    }
    ScanRoute(
        viewModel = viewModel,
        onBack = onBack,
        onContinue = onContinue,
        preview = { CameraPreview(viewModel.camera) },
        requestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        pickImage = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

@Composable
fun ScanRoute(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
    preview: @Composable () -> Unit,
    requestPermission: () -> Unit,
    pickImage: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ScanScreen(
        state = state,
        preview = preview,
        callbacks = ScanCallbacks(
            onBack = onBack,
            onCapture = viewModel::capture,
            onPickImage = pickImage,
            onRequestPermission = requestPermission,
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onRetry = viewModel::retry,
            onRescan = viewModel::rescan,
            onContinue = { (state.phase as? ScanPhase.Result)?.let { onContinue(it.draft.id) } },
        ),
    )
}

data class ScanCallbacks(
    val onBack: () -> Unit,
    val onCapture: () -> Unit,
    val onPickImage: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onRetry: () -> Unit,
    val onRescan: () -> Unit,
    val onContinue: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(state: ScanUiState, preview: @Composable () -> Unit, callbacks: ScanCallbacks) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = { IconButton(onClick = callbacks.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val phase = state.phase) {
                ScanPhase.CheckingPermission -> LoadingView()
                is ScanPhase.PermissionRequired -> PermissionView(phase.permanentlyDenied, callbacks)
                ScanPhase.Ready -> CameraView(preview, callbacks)
                ScanPhase.Capturing -> ProgressView(stringResource(R.string.scan_capturing))
                ScanPhase.Recognizing -> ProgressView(stringResource(R.string.scan_recognizing))
                is ScanPhase.Result -> ResultView(phase, callbacks)
                is ScanPhase.Error -> ScanErrorView(phase.error, callbacks)
            }
        }
    }
}

@Composable
private fun CameraView(preview: @Composable () -> Unit, callbacks: ScanCallbacks) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { preview() }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = callbacks.onPickImage, modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.GALLERY_BUTTON)) { Text(stringResource(R.string.scan_pick_gallery)) }
            Button(onClick = callbacks.onCapture, modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.CAPTURE_BUTTON)) { Text(stringResource(R.string.scan_capture)) }
        }
    }
}

@Composable
private fun PermissionView(permanentlyDenied: Boolean, callbacks: ScanCallbacks) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(if (permanentlyDenied) R.string.scan_permission_denied_body else R.string.scan_permission_body),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (permanentlyDenied) {
            Button(onClick = callbacks.onOpenSettings, modifier = Modifier.testTag(SplitSmartTestTags.OPEN_SETTINGS_BUTTON)) { Text(stringResource(R.string.scan_open_settings)) }
        } else {
            Button(onClick = callbacks.onRequestPermission, modifier = Modifier.testTag(SplitSmartTestTags.GRANT_PERMISSION_BUTTON)) { Text(stringResource(R.string.scan_grant_permission)) }
        }
        OutlinedButton(onClick = callbacks.onPickImage, modifier = Modifier.padding(top = 8.dp).testTag(SplitSmartTestTags.GALLERY_BUTTON)) { Text(stringResource(R.string.scan_pick_gallery)) }
    }
}

@Composable
private fun ProgressView(label: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.material3.CircularProgressIndicator()
        Text(label, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ResultView(phase: ScanPhase.Result, callbacks: ScanCallbacks) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.scan_result_title), style = MaterialTheme.typography.titleMedium)
        Text(
            phase.draft.ocrText,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp).verticalScroll(rememberScrollState()).testTag(SplitSmartTestTags.OCR_TEXT),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = callbacks.onRescan, modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.RESCAN_BUTTON)) { Text(stringResource(R.string.scan_rescan)) }
            Button(onClick = callbacks.onContinue, modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.CONTINUE_BUTTON)) { Text(stringResource(R.string.scan_continue)) }
        }
    }
}

@Composable
private fun ScanErrorView(error: ScanError, callbacks: ScanCallbacks) {
    Column(Modifier.fillMaxSize().padding(32.dp).testTag(SplitSmartTestTags.ERROR), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Button(onClick = callbacks.onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
        OutlinedButton(onClick = callbacks.onPickImage, modifier = Modifier.padding(top = 8.dp).testTag(SplitSmartTestTags.GALLERY_BUTTON)) { Text(stringResource(R.string.scan_pick_gallery)) }
    }
}

fun ScanError.messageRes(): Int = when (this) {
    ScanError.NO_TEXT -> R.string.scan_error_no_text
    ScanError.RECOGNITION_FAILED -> R.string.scan_error_recognition
    ScanError.IMAGE_UNREADABLE -> R.string.scan_error_image
    ScanError.CAPTURE_FAILED -> R.string.scan_error_capture
    ScanError.STORAGE_FAILED -> R.string.error_storage
}
