package com.darshan.splitsmart.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.R
import com.darshan.splitsmart.data.ocr.LiveText
import com.darshan.splitsmart.data.ocr.Torch
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyIconButton
import com.darshan.splitsmart.ui.designsystem.components.ClickGuard
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.Illustration
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.SsTopBar
import com.darshan.splitsmart.ui.designsystem.components.rememberAmbientPhase
import com.darshan.splitsmart.ui.designsystem.effects.CollectUiEffects
import com.darshan.splitsmart.ui.designsystem.modifiers.PressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.squash
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldColors
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldDark
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.navigation.LocalNavAnimatedScope
import com.darshan.splitsmart.ui.navigation.StatusBarSurface
import com.darshan.splitsmart.ui.navigation.hasArrived
import com.darshan.splitsmart.ui.theme.LocalMarigoldColors
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/**
 * The camera lives in the night palette in both themes: a camera is a dark room, and a light frame around a live picture would
 * fight it. Every pair it draws is a tested pair of [MarigoldDark] (`ScanContrastTest` adds the ones drawn over the picture).
 */
val CameraPalette: MarigoldColors get() = MarigoldDark

object ScanTags {
    const val SCREEN = "scan_screen"
    const val HINT = "scan_hint"
    const val FLASH = "scan_flash"
    const val READING = "scan_reading"
    const val VIEWFINDER = "scan_viewfinder"
    const val PERMISSION = "scan_permission"
    const val RESULT = "scan_result"
    const val TORCH = "scan_torch"
}

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

/** Testable route: a fake [preview] and launchers. A finished scan hands its draft to [onContinue] once, by itself. */
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
    val liveText = viewModel.liveText.collectAsStateWithLifecycle()
    val torch = viewModel.torch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val continueTo by rememberUpdatedState(onContinue)
    CollectUiEffects(viewModel.effects, onOther = { effect -> if (effect is ScanEffect.OpenReview) continueTo(effect.draftId) })
    ScanScreen(
        state = state,
        preview = preview,
        liveText = liveText,
        torch = torch,
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
            onToggleTorch = viewModel::toggleTorch,
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
    val onToggleTorch: () -> Unit = {},
)

private val NoLiveText: State<LiveText> = mutableStateOf(LiveText.NONE)
private val NoTorch: State<Torch> = mutableStateOf(Torch.Unavailable)

/**
 * The scan flow on Marigold, in the night palette: a full-bleed camera with a receipt-shaped window, breathing marigold corners,
 * a scan line, and an outline around every line of text the camera can already read. The shutter flashes and thuds (heavy
 * haptic, from the view model), the corners close in, and the photo is read on the phone under a sweeping receipt. A gallery
 * pick, the permission ask, errors and a saved scan are pages in the same dark room. The preview stays composed from Ready
 * through the reading, so the camera is never unbound mid-capture.
 */
@Composable
fun ScanScreen(
    state: ScanUiState,
    preview: @Composable () -> Unit,
    callbacks: ScanCallbacks,
    liveText: State<LiveText> = NoLiveText,
    torch: State<Torch> = NoTorch,
) {
    CompositionLocalProvider(LocalMarigoldColors provides CameraPalette) {
        val colors = SplitSmartTheme.colors
        StatusBarSurface(colors.bg, settled = LocalNavAnimatedScope.current?.hasArrived() ?: true)
        val phase = state.phase
        val previewWasUp = remember { BooleanArray(1) }
        val showPreview = ScanLogic.showsPreview(phase, previewWasUp[0]).also { previewWasUp[0] = it }
        Box(Modifier.fillMaxSize().background(colors.bg).testTag(ScanTags.SCREEN)) {
            if (phase == ScanPhase.Ready || phase == ScanPhase.Capturing || phase == ScanPhase.Recognizing) {
                CameraStage(phase, state.afterSave, showPreview, preview, liveText, torch.value, callbacks)
            } else {
                when (phase) {
                    is ScanPhase.PermissionRequired -> PermissionPage(phase.permanentlyDenied, callbacks)
                    is ScanPhase.Error -> ErrorPage(phase.error, callbacks)
                    is ScanPhase.Result -> ResultPage(phase, callbacks)
                    else -> ScanTopBar(callbacks.onBack)
                }
            }
        }
    }
}

@Composable
private fun ScanTopBar(onBack: () -> Unit) {
    SsTopBar(title = stringResource(R.string.scan_title), onBack = onBack, backLabel = stringResource(R.string.action_back))
}

@Composable
private fun CameraStage(phase: ScanPhase, afterSave: Boolean, showPreview: Boolean, preview: @Composable () -> Unit, liveText: State<LiveText>, torch: Torch, callbacks: ScanCallbacks) {
    val motion = LocalMotion.current
    val area = remember { mutableStateOf(Rect.Zero) }
    val closing = animateFloatAsState(if (phase == ScanPhase.Ready) 0f else 1f, motion.spec(MotionToken.Snap), label = "corners")
    // The outlines change a few times a second; the hint only when its wording would, so the stage never recomposes per frame.
    val hint by remember(phase, afterSave) { derivedStateOf { ScanLogic.hint(phase, liveText.value.boxes.size, afterSave) } }
    Box(Modifier.fillMaxSize()) {
        if (showPreview) Box(Modifier.fillMaxSize()) { preview() }
        Viewfinder(phase, showPreview, area, closing, liveText)
        // Under the chrome: Back stays bright and working while the photo is read.
        AnimatedVisibility(phase == ScanPhase.Recognizing, enter = motion.enter(), exit = motion.exit()) {
            ReadingOverlay(dimmed = showPreview)
        }
        Column(Modifier.fillMaxSize()) {
            ScanTopBar(callbacks.onBack)
            HintPill(hint)
            Box(Modifier.weight(1f).fillMaxWidth().onPlaced { area.value = it.boundsInParent() })
            CameraControls(phase, torch, callbacks)
        }
        ShutterFlash(phase)
    }
}

/** What the camera can see, as a pill under the top bar. TalkBack hears it change ("Text found"). */
@Composable
private fun HintPill(hint: ScanHint) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs), contentAlignment = Alignment.Center) {
        AnimatedContent(hint, transitionSpec = { motion.fadeThrough() }, label = "hint") { shown ->
            val text = when (shown) {
                ScanHint.Frame -> stringResource(R.string.scan_frame_hint)
                ScanHint.Saved -> stringResource(R.string.scan_hint_saved)
                ScanHint.TextFound -> stringResource(R.string.scan_hint_text_found)
                ScanHint.HoldStill -> stringResource(R.string.scan_hint_hold)
                ScanHint.None -> null
            }
            if (text != null) {
                Row(
                    Modifier
                        .background(if (shown == ScanHint.TextFound) colors.primary else colors.surface, Radii.pillShape)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .testTag(ScanTags.HINT)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(text, style = type.labelStrong.copy(color = if (shown == ScanHint.TextFound) colors.onPrimary else colors.text))
                }
            } else {
                Spacer(Modifier.height(Sizes.pillHeight))
            }
        }
    }
}

/**
 * The window, its corners, the scan line and the live text outlines, all in one draw pass. Everything that moves (the corners'
 * breath, the line, the outlines, the corners closing on the shutter) is read here, in draw, so none of it recomposes.
 */
@Composable
private fun Viewfinder(phase: ScanPhase, showPreview: Boolean, area: State<Rect>, closing: State<Float>, liveText: State<LiveText>) {
    val colors = SplitSmartTheme.colors
    val ambient = rememberAmbientPhase()
    val sweep = if (phase == ScanPhase.Ready) rememberAmbientPhase(MotionDurations.ScanSweep) else null
    val ready = phase == ScanPhase.Ready
    val description = stringResource(R.string.scan_live_text)
    Spacer(
        Modifier
            .fillMaxSize()
            .testTag(ScanTags.VIEWFINDER)
            .clearAndSetSemantics { contentDescription = description }
            .drawWithCache {
                val bounds = area.value
                val window = ScanLogic.window(bounds.width, bounds.height, 0f, bounds.height).translate(bounds.topLeft)
                val radius = Radii.large.toPx()
                val windowPath = Path().apply { addRoundRect(RoundRect(window, CornerRadius(radius))) }
                val bracket = Stroke(Strokes.bracket.toPx(), cap = StrokeCap.Round)
                val outline = Stroke(Strokes.outline.toPx())
                val halo = Stroke(Strokes.focusGlow.toPx())
                val arm = Sizes.viewfinderBracket.toPx()
                val breath = Spacing.xxs.toPx()
                val inset = Spacing.sm.toPx()
                val textCorner = CornerRadius(Sizes.textOutlineCorner.toPx())
                val beamWidth = Strokes.beam.toPx()
                val glowHeight = Spacing.xxl.toPx()
                val glow = Brush.verticalGradient(listOf(Color.Transparent, colors.primary), startY = 0f, endY = glowHeight)
                onDrawBehind {
                    if (window.width <= 0f || window.height <= 0f) return@onDrawBehind
                    if (showPreview) clipPath(windowPath, ClipOp.Difference) { drawRect(colors.scrim) }
                    if (ready) {
                        clipPath(windowPath) {
                            val live = liveText.value
                            for (box in live.boxes) {
                                val topLeft = Offset(box.left, box.top)
                                val size = Size(box.right - box.left, box.bottom - box.top)
                                drawRoundRect(colors.primary, topLeft, size, textCorner, alpha = TEXT_FILL_ALPHA)
                                // A night halo under the marigold line, so the outline reads on white paper and dark tables alike.
                                drawRoundRect(colors.bg, topLeft, size, textCorner, alpha = HALO_ALPHA, style = halo)
                                drawRoundRect(colors.primary, topLeft, size, textCorner, style = outline)
                            }
                            val p = sweep?.value
                            if (p != null) {
                                val beam = ScanLogic.beam(p)
                                val y = window.top + beam.y * window.height
                                translate(left = window.left, top = y - glowHeight) {
                                    drawRect(glow, size = Size(window.width, glowHeight), alpha = beam.alpha * GLOW_ALPHA)
                                }
                                drawLine(colors.primary, Offset(window.left, y), Offset(window.right, y), beamWidth, StrokeCap.Round, alpha = beam.alpha)
                            }
                        }
                    }
                    val wave = ambient?.value?.let { kotlin.math.sin(it * 2f * Math.PI.toFloat()) } ?: 0f
                    val out = breath * wave - inset * closing.value
                    drawCorners(window.inflate(out), radius, arm, bracket, colors.primary)
                }
            },
    )
}

/** Four rounded corner brackets hugging [window]. Arcs and lines only: nothing is allocated per frame. */
private fun DrawScope.drawCorners(window: Rect, radius: Float, arm: Float, stroke: Stroke, color: Color) {
    val d = radius * 2f
    val straight = (arm - radius).coerceAtLeast(0f)
    // Top left, top right, bottom right, bottom left: each an arc plus its two arms.
    drawArc(color, 180f, 90f, false, Offset(window.left, window.top), Size(d, d), style = stroke)
    drawLine(color, Offset(window.left + radius, window.top), Offset(window.left + radius + straight, window.top), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(window.left, window.top + radius), Offset(window.left, window.top + radius + straight), stroke.width, StrokeCap.Round)
    drawArc(color, 270f, 90f, false, Offset(window.right - d, window.top), Size(d, d), style = stroke)
    drawLine(color, Offset(window.right - radius, window.top), Offset(window.right - radius - straight, window.top), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(window.right, window.top + radius), Offset(window.right, window.top + radius + straight), stroke.width, StrokeCap.Round)
    drawArc(color, 0f, 90f, false, Offset(window.right - d, window.bottom - d), Size(d, d), style = stroke)
    drawLine(color, Offset(window.right - radius, window.bottom), Offset(window.right - radius - straight, window.bottom), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(window.right, window.bottom - radius), Offset(window.right, window.bottom - radius - straight), stroke.width, StrokeCap.Round)
    drawArc(color, 90f, 90f, false, Offset(window.left, window.bottom - d), Size(d, d), style = stroke)
    drawLine(color, Offset(window.left + radius, window.bottom), Offset(window.left + radius + straight, window.bottom), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(window.left, window.bottom - radius), Offset(window.left, window.bottom - radius - straight), stroke.width, StrokeCap.Round)
}

private const val TEXT_FILL_ALPHA = 0.16f
private const val HALO_ALPHA = 0.7f
private const val GLOW_ALPHA = 0.35f
private const val FLASH_ALPHA = 0.85f

@Composable
private fun CameraControls(phase: ScanPhase, torch: Torch, callbacks: ScanCallbacks) {
    val ready = phase == ScanPhase.Ready
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .graphicsLayer { alpha = if (phase == ScanPhase.Recognizing) 0f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            LabelledControl(stringResource(R.string.scan_gallery_short)) {
                ChunkyIconButton(
                    ImageVector.vectorResource(R.drawable.ic_gallery),
                    stringResource(R.string.scan_pick_gallery),
                    callbacks.onPickImage,
                    Modifier.testTag(SplitSmartTestTags.GALLERY_BUTTON),
                    enabled = ready,
                )
            }
        }
        ShutterButton(enabled = ready, onClick = callbacks.onCapture, modifier = Modifier.testTag(SplitSmartTestTags.CAPTURE_BUTTON))
        // The torch balances the gallery on the right, only on a camera that has one. Lit, it turns marigold.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (torch != Torch.Unavailable) TorchButton(on = torch == Torch.On, enabled = ready || phase == ScanPhase.Capturing, onToggle = callbacks.onToggleTorch)
        }
    }
}

/** A round control with its short name under it; TalkBack hears the button's own label once. */
@Composable
private fun LabelledControl(label: String, content: @Composable () -> Unit) {
    val colors = SplitSmartTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        content()
        BasicText(label, Modifier.clearAndSetSemantics { }, style = SplitSmartTheme.type.label.copy(color = colors.textSecondary))
    }
}

/**
 * The torch switch: secondary when off, marigold when lit. TalkBack hears "Torch is off" or "Torch is on" as a switch, so the
 * state is said, not only shown.
 */
@Composable
private fun TorchButton(on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (on) R.string.scan_torch_on else R.string.scan_torch_off)
    LabelledControl(stringResource(R.string.scan_torch_short)) {
        ChunkyIconButton(
            ImageVector.vectorResource(R.drawable.ic_torch),
            state,
            onToggle,
            Modifier.testTag(ScanTags.TORCH).semantics {
                role = Role.Switch
                toggleableState = ToggleableState(on)
            },
            style = if (on) ButtonStyle.Primary else ButtonStyle.Secondary,
            enabled = enabled,
        )
    }
}

/**
 * The shutter: a marigold ring around a cream disc that squashes under the thumb from the first frame of the press. One picture
 * per tap window ([ClickGuard]); the view model guards the rest and plays the heavy haptic, so a tap that is ignored stays silent.
 */
@Composable
internal fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, press: PressFeedback = rememberPressFeedback()) {
    val colors = SplitSmartTheme.colors
    val label = stringResource(R.string.scan_capture)
    val guard = remember { ClickGuard() }
    val currentOnClick by rememberUpdatedState(onClick)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(Sizes.shutter)
            .trackPress(press, enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (guard.tryAccept(SystemClock.uptimeMillis())) currentOnClick()
            }
            .semantics { contentDescription = label }
            .drawBehind {
                val ring = Strokes.bracket.toPx()
                drawCircle(colors.primary, radius = size.minDimension / 2f - ring / 2f, style = Stroke(ring))
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(Sizes.shutterInner).squash(press).background(if (enabled) colors.text else colors.textDisabled, Radii.circleShape))
    }
}

/**
 * The flash after the shutter: the screen lights up cream and fades on the exit curve. Under reduced motion it dims instead of
 * lighting up, a gentler cue that the picture was taken. Drawn in a layer; it exists only while it shows.
 */
@Composable
private fun ShutterFlash(phase: ScanPhase) {
    val colors = SplitSmartTheme.colors
    val motion = LocalMotion.current
    val flash = remember { Animatable(0f) }
    LaunchedEffect(phase == ScanPhase.Capturing) {
        if (phase == ScanPhase.Capturing) {
            flash.snapTo(1f)
            flash.animateTo(0f, motion.spec(MotionToken.ScreenExit))
        }
    }
    val showing by remember { derivedStateOf { flash.value > 0f } }
    if (showing) {
        val tint = if (motion.reduced) colors.scrim else colors.text
        Spacer(
            Modifier
                .fillMaxSize()
                .testTag(ScanTags.FLASH)
                .clearAndSetSemantics { }
                .drawBehind { drawRect(tint, alpha = flash.value * FLASH_ALPHA) },
        )
    }
}

/** The photo being read: the picture dims and, on an opaque card so no text ever sits on the photo, a receipt is swept by the scan line. */
@Composable
private fun ReadingOverlay(dimmed: Boolean) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val title = stringResource(R.string.scan_recognizing)
    Box(
        Modifier
            .fillMaxSize()
            .then(if (dimmed) Modifier.background(colors.scrim) else Modifier)
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, Radii.largeShape)
                .padding(Spacing.xl)
                .testTag(ScanTags.READING)
                .semantics(mergeDescendants = true) {
                    contentDescription = title
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReadingReceipt(Modifier.size(Sizes.illustrationSmall))
            Spacer(Modifier.height(Spacing.md))
            BasicText(title, style = type.title.copy(color = colors.text, textAlign = TextAlign.Center))
            Spacer(Modifier.height(Spacing.xs))
            BasicText(stringResource(R.string.scan_reading_body), style = type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
        }
    }
}

/**
 * A code-drawn receipt with the scan line sweeping down it; the lines it has passed turn marigold, as if read. Decorative: under
 * reduced motion the line rests halfway and the top half reads as done.
 */
@Composable
private fun ReadingReceipt(modifier: Modifier) {
    val art = SplitSmartTheme.colors.illustration
    val sweep = rememberAmbientPhase(MotionDurations.ScanSweep)
    Spacer(
        modifier.drawWithCache {
            val u = size.minDimension / 100f
            val paper = Path().apply {
                val left = 22f * u
                val right = 78f * u
                val top = 8f * u
                val bottom = 92f * u
                val tooth = 5f * u
                moveTo(left, top + 4f * u)
                quadraticTo(left, top, left + 4f * u, top)
                lineTo(right - 4f * u, top)
                quadraticTo(right, top, right, top + 4f * u)
                lineTo(right, bottom)
                var x = right
                var down = false
                while (x > left) {
                    x = (x - tooth).coerceAtLeast(left)
                    lineTo(x, if (down) bottom else bottom - tooth * 0.7f)
                    down = !down
                }
                close()
            }
            val lineWidth = 3.2f * u
            val shadow = Stroke(5f * u)
            onDrawBehind {
                val p = sweep?.value ?: HALFWAY
                val beam = ScanLogic.beam(p)
                val beamY = (14f + beam.y * 72f) * u
                drawPath(paper, art.ink, alpha = 0.12f, style = shadow)
                drawPath(paper, art.paper)
                for (i in 0 until RECEIPT_LINES) {
                    val y = (20f + i * 10f) * u
                    val read = y < beamY
                    val color = if (read) art.petal else art.ink
                    val alpha = if (read) 0.9f else 0.28f
                    drawLine(color, Offset(30f * u, y), Offset((48f + (i % 3) * 6f) * u, y), lineWidth, StrokeCap.Round, alpha = alpha)
                    drawLine(color, Offset(60f * u, y), Offset(70f * u, y), lineWidth, StrokeCap.Round, alpha = alpha)
                }
                drawLine(art.petalDeep, Offset(16f * u, beamY), Offset(84f * u, beamY), 2.6f * u, StrokeCap.Round, alpha = maxOf(beam.alpha, 0.6f))
            }
        },
    )
}

private const val RECEIPT_LINES = 7
private const val HALFWAY = 0.5f

@Composable
private fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        content = content,
    )
}

@Composable
private fun PageTitle(text: String) {
    BasicText(
        text,
        Modifier.semantics { heading() },
        style = SplitSmartTheme.type.display.copy(color = SplitSmartTheme.colors.text, textAlign = TextAlign.Center),
    )
}

@Composable
private fun PageBody(text: String) {
    BasicText(text, Modifier.widthIn(max = Sizes.sheetListMax), style = SplitSmartTheme.type.body.copy(color = SplitSmartTheme.colors.textSecondary, textAlign = TextAlign.Center))
}

/** The ask, designed: what the camera is for, what stays on the phone, and the gallery as an equal way in. */
@Composable
private fun PermissionPage(permanentlyDenied: Boolean, callbacks: ScanCallbacks) {
    Column(Modifier.fillMaxSize().testTag(ScanTags.PERMISSION)) {
        ScanTopBar(callbacks.onBack)
        PageColumn(Modifier.weight(1f)) {
            Illustration(IllustrationKind.Camera)
            PageTitle(stringResource(R.string.scan_permission_title))
            PageBody(stringResource(if (permanentlyDenied) R.string.scan_permission_denied_body else R.string.scan_permission_body))
            Spacer(Modifier.height(Spacing.xs))
            if (permanentlyDenied) {
                ChunkyButton(
                    stringResource(R.string.scan_open_settings),
                    callbacks.onOpenSettings,
                    Modifier.fillMaxWidth().testTag(SplitSmartTestTags.OPEN_SETTINGS_BUTTON),
                    emoji = "⚙️",
                )
            } else {
                ChunkyButton(
                    stringResource(R.string.scan_grant_permission),
                    callbacks.onRequestPermission,
                    Modifier.fillMaxWidth().testTag(SplitSmartTestTags.GRANT_PERMISSION_BUTTON),
                    emoji = "📸",
                )
            }
            ChunkyButton(
                stringResource(R.string.scan_pick_gallery),
                callbacks.onPickImage,
                Modifier.fillMaxWidth().testTag(SplitSmartTestTags.GALLERY_BUTTON),
                style = ButtonStyle.Secondary,
                icon = ImageVector.vectorResource(R.drawable.ic_gallery),
            )
        }
    }
}

@Composable
private fun ErrorPage(error: ScanError, callbacks: ScanCallbacks) {
    Column(Modifier.fillMaxSize().testTag(SplitSmartTestTags.ERROR)) {
        ScanTopBar(callbacks.onBack)
        PageColumn(Modifier.weight(1f)) {
            Illustration(IllustrationKind.Error)
            PageTitle(stringResource(R.string.scan_error_title))
            PageBody(stringResource(error.messageRes()))
            Spacer(Modifier.height(Spacing.xs))
            ChunkyButton(stringResource(R.string.retry_label), callbacks.onRetry, Modifier.fillMaxWidth())
            ChunkyButton(
                stringResource(R.string.scan_pick_gallery),
                callbacks.onPickImage,
                Modifier.fillMaxWidth().testTag(SplitSmartTestTags.GALLERY_BUTTON),
                style = ButtonStyle.Secondary,
                icon = ImageVector.vectorResource(R.drawable.ic_gallery),
            )
        }
    }
}

/**
 * A scan that is already saved (the user came back from the review, or the app was restored): the slip shows what the camera
 * read, and the next step is the review. Recognised text is shown, never logged.
 */
@Composable
private fun ResultPage(phase: ScanPhase.Result, callbacks: ScanCallbacks) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val lines = remember(phase.draft.ocrText) { phase.draft.ocrText.lines().filter { it.isNotBlank() } }
    Column(Modifier.fillMaxSize().testTag(ScanTags.RESULT)) {
        ScanTopBar(callbacks.onBack)
        PageColumn(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, Radii.mediumShape)
                    .padding(Spacing.md)
                    .testTag(SplitSmartTestTags.OCR_TEXT),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                for (line in lines.take(SLIP_LINES)) {
                    BasicText(DisplayText.safe(line), style = type.numeric.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (lines.size > SLIP_LINES) {
                    BasicText(
                        pluralStringResource(R.plurals.scan_more_lines, lines.size - SLIP_LINES, lines.size - SLIP_LINES),
                        style = type.labelStrong.copy(color = colors.accent),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            PageTitle(stringResource(R.string.scan_result_title))
            PageBody(pluralStringResource(R.plurals.scan_result_lines, lines.size, lines.size))
            Spacer(Modifier.height(Spacing.xs))
            ChunkyButton(stringResource(R.string.scan_continue), callbacks.onContinue, Modifier.fillMaxWidth().testTag(SplitSmartTestTags.CONTINUE_BUTTON), emoji = "✨")
            ChunkyButton(stringResource(R.string.scan_rescan), callbacks.onRescan, Modifier.fillMaxWidth().testTag(SplitSmartTestTags.RESCAN_BUTTON), style = ButtonStyle.Ghost)
        }
    }
}

private const val SLIP_LINES = 8

fun ScanError.messageRes(): Int = when (this) {
    ScanError.NO_TEXT -> R.string.scan_error_no_text
    ScanError.RECOGNITION_FAILED -> R.string.scan_error_recognition
    ScanError.IMAGE_UNREADABLE -> R.string.scan_error_image
    ScanError.CAPTURE_FAILED -> R.string.scan_error_capture
    ScanError.STORAGE_FAILED -> R.string.scan_error_storage
}
