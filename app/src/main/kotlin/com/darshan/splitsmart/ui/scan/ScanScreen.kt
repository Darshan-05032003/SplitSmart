package com.darshan.splitsmart.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.components.AppIcons
import com.darshan.splitsmart.ui.components.BannerTone
import com.darshan.splitsmart.ui.components.EmptyIllustration
import com.darshan.splitsmart.ui.components.IconBubble
import com.darshan.splitsmart.ui.components.InfoBanner
import com.darshan.splitsmart.ui.components.LoadingView
import com.darshan.splitsmart.ui.components.appIcon

/** Production entry point: wires the permission and photo-picker launchers, then delegates to the testable route. */
@Composable
fun ScanRoute(viewModel: ScanViewModel = hiltViewModel(), onBack: () -> Unit, onContinue: (String) -> Unit, startWithGallery: Boolean = false) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = granted || (context as? Activity)?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: true
        viewModel.onPermissionResult(granted, canAskAgain)
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onImagePicked(it.toString()) }
    }
    // "Add bill → From gallery" opens the photo picker straight away, once (survives rotation / process death).
    var galleryOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startWithGallery) {
        if (startWithGallery && !galleryOpened) {
            galleryOpened = true
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
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
                ScanPhase.Capturing -> LoadingView(label = stringResource(R.string.scan_capturing))
                ScanPhase.Recognizing -> LoadingView(label = stringResource(R.string.scan_recognizing))
                is ScanPhase.Result -> ResultView(phase, callbacks)
                is ScanPhase.Error -> ScanErrorView(phase.error, callbacks)
            }
        }
    }
}

@Composable
private fun CameraView(preview: @Composable () -> Unit, callbacks: ScanCallbacks) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            preview()
            FramingGuide(Modifier.fillMaxSize())
            Surface(
                color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = CircleShape,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            ) {
                Text(stringResource(R.string.scan_frame_hint), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Surface(
                    onClick = callbacks.onPickImage, color = Color.White.copy(alpha = 0.16f), contentColor = Color.White, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag(SplitSmartTestTags.GALLERY_BUTTON),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(appIcon(AppIcons.Gallery), contentDescription = null)
                        Text(stringResource(R.string.scan_gallery_short), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val captureLabel = stringResource(R.string.scan_capture)
            Surface(
                onClick = callbacks.onCapture, color = Color.White, shape = CircleShape,
                border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(76.dp).testTag(SplitSmartTestTags.CAPTURE_BUTTON).semantics { contentDescription = captureLabel },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(appIcon(AppIcons.Camera), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Dims everything outside a receipt-shaped window and draws coral corner brackets around it. */
@Composable
private fun FramingGuide(modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width * 0.78f
        val h = minOf(size.height * 0.82f, w * 1.7f)
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        val corner = 28.dp.toPx()
        val radius = CornerRadius(corner, corner)
        val window = Path().apply { addRoundRect(RoundRect(left, top, left + w, top + h, radius)) }
        clipPath(window, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.45f)) }
        val len = 36.dp.toPx()
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        listOf(
            Offset(left, top) to Offset(1f, 1f), Offset(left + w, top) to Offset(-1f, 1f),
            Offset(left, top + h) to Offset(1f, -1f), Offset(left + w, top + h) to Offset(-1f, -1f),
        ).forEach { (p, d) ->
            val path = Path().apply {
                moveTo(p.x, p.y + d.y * (len + corner / 2))
                lineTo(p.x, p.y + d.y * corner / 2)
                quadraticTo(p.x, p.y, p.x + d.x * corner / 2, p.y)
                lineTo(p.x + d.x * (len + corner / 2), p.y)
            }
            drawPath(path, accent, style = stroke)
        }
    }
}

@Composable
private fun PermissionView(permanentlyDenied: Boolean, callbacks: ScanCallbacks) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustration(main = appIcon(AppIcons.Camera), modifier = Modifier.padding(bottom = 24.dp))
        Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            stringResource(if (permanentlyDenied) R.string.scan_permission_denied_body else R.string.scan_permission_body),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        if (permanentlyDenied) {
            Button(onClick = callbacks.onOpenSettings, modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.OPEN_SETTINGS_BUTTON)) { Text(stringResource(R.string.scan_open_settings)) }
        } else {
            Button(onClick = callbacks.onRequestPermission, modifier = Modifier.fillMaxWidth().testTag(SplitSmartTestTags.GRANT_PERMISSION_BUTTON)) {
                Icon(appIcon(AppIcons.Camera), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scan_grant_permission))
            }
        }
        OutlinedButton(onClick = callbacks.onPickImage, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(SplitSmartTestTags.GALLERY_BUTTON)) {
            Icon(appIcon(AppIcons.Gallery), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.scan_pick_gallery))
        }
    }
}

@Composable
private fun ResultView(phase: ScanPhase.Result, callbacks: ScanCallbacks) {
    val lines = phase.draft.ocrText.lines().count { it.isNotBlank() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoBanner(BannerTone.SUCCESS, icon = rememberVectorPainter(Icons.Default.CheckCircle)) {
            Text(stringResource(R.string.scan_result_title), style = MaterialTheme.typography.titleSmall)
            Text(pluralStringResource(R.plurals.scan_result_lines, lines, lines), style = MaterialTheme.typography.bodyMedium)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Text(
                phase.draft.ocrText,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()).testTag(SplitSmartTestTags.OCR_TEXT),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = callbacks.onRescan, modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.RESCAN_BUTTON)) { Text(stringResource(R.string.scan_rescan)) }
            Button(onClick = callbacks.onContinue, modifier = Modifier.weight(1f).testTag(SplitSmartTestTags.CONTINUE_BUTTON)) {
                Icon(appIcon(AppIcons.Sparkle), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scan_continue))
            }
        }
    }
}

@Composable
private fun ScanErrorView(error: ScanError, callbacks: ScanCallbacks) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag(SplitSmartTestTags.ERROR),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        IconBubble(Icons.Default.Warning, size = 72.dp, container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
        Text(stringResource(error.messageRes()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Button(onClick = callbacks.onRetry, modifier = Modifier.padding(top = 20.dp).fillMaxWidth()) { Text(stringResource(R.string.retry)) }
        OutlinedButton(onClick = callbacks.onPickImage, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().testTag(SplitSmartTestTags.GALLERY_BUTTON)) {
            Text(stringResource(R.string.scan_pick_gallery))
        }
    }
}

fun ScanError.messageRes(): Int = when (this) {
    ScanError.NO_TEXT -> R.string.scan_error_no_text
    ScanError.RECOGNITION_FAILED -> R.string.scan_error_recognition
    ScanError.IMAGE_UNREADABLE -> R.string.scan_error_image
    ScanError.CAPTURE_FAILED -> R.string.scan_error_capture
    ScanError.STORAGE_FAILED -> R.string.error_storage
}
