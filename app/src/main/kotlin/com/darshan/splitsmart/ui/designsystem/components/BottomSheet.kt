package com.darshan.splitsmart.ui.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Velocity
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/**
 * A bottom sheet that bounces up on the Bouncy spring and follows the finger, including from inside scrollable content.
 *
 * It is drawn in the screen's own window, not a dialog window, so it shares the screen's window flags, never outlives the
 * screen and can't sit above an unrelated window. Place it last in a full-screen `Box`. Content behind it should be hidden from
 * TalkBack by the caller while it is open.
 *
 * [dismissible] false locks it while something must not be interrupted (a settle-up in flight): no scrim tap, no drag, and back is
 * swallowed. [onDismissRequest] asks the owner to set [visible] false; if the owner refuses, the sheet springs back open.
 */
@Composable
fun SsBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    dismissLabel: String = "Close",
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = LocalMotion.current
    val colors = SplitSmartTheme.colors
    val currentVisible by rememberUpdatedState(visible)
    val currentDismissible by rememberUpdatedState(dismissible)
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    var composed by remember { mutableStateOf(visible) }
    var height by remember { mutableFloatStateOf(0f) }
    // A plain state written straight from the finger: a suspending snapTo per drag delta could land after the release and cancel
    // the settle animation. Animations and drags take turns through the mutex.
    var offset by remember { mutableFloatStateOf(0f) }
    val mutex = remember { MutatorMutex() }
    val fade = remember { Animatable(0f) }
    if (visible && !composed) composed = true
    if (!composed) return

    suspend fun animateOffset(target: Float, token: MotionToken, velocity: Float = 0f) {
        mutex.mutate { animate(offset, target, velocity, motion.spec(token)) { value, _ -> offset = value } }
    }

    suspend fun settleAfterRelease(velocity: Float) {
        when (SheetPhysics.settle(offset, height, velocity, currentDismissible)) {
            SheetTarget.Expanded -> animateOffset(0f, MotionToken.Bounce, velocity)
            SheetTarget.Dismissed -> {
                animateOffset(height, MotionToken.Settle, velocity)
                currentOnDismiss()
                withFrameNanos { }
                if (currentVisible) animateOffset(0f, MotionToken.Bounce)
            }
        }
    }

    LaunchedEffect(visible, height > 0f) {
        if (height <= 0f) return@LaunchedEffect
        if (visible) {
            if (motion.reduced) {
                offset = 0f
                fade.animateTo(1f, motion.spec(MotionToken.Micro))
            } else {
                if (fade.value == 0f) offset = height
                fade.snapTo(1f)
                animateOffset(0f, MotionToken.Bounce)
            }
        } else {
            if (motion.reduced) {
                fade.animateTo(0f, motion.spec(MotionToken.Micro))
            } else if (offset < height) {
                animateOffset(height, MotionToken.ScreenExit)
            }
            fade.snapTo(0f)
            composed = false
        }
    }

    BackHandler(enabled = visible) { if (dismissible) onDismissRequest() }

    val dragState = rememberDraggableState { delta -> offset = SheetPhysics.applyDrag(offset, delta) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && offset > 0f && source == NestedScrollSource.UserInput) {
                    val consumed = maxOf(available.y, -offset)
                    offset += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (currentDismissible && available.y > 0f && source == NestedScrollSource.UserInput) {
                    offset += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset > 0f) {
                    settleAfterRelease(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(colors.scrim, alpha = SheetPhysics.scrimAlpha(offset, height) * fade.value) }
                .pointerInput(Unit) { detectTapGestures { if (currentDismissible) currentOnDismiss() } }
                .clearAndSetSemantics {
                    if (dismissible) {
                        onClick(dismissLabel) {
                            currentOnDismiss()
                            true
                        }
                    }
                },
        )
        val sheetColor = if (colors.isDark) colors.raised else colors.surface
        val handle = colors.outline
        Column(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .onSizeChanged { height = it.height.toFloat() }
                .graphicsLayer {
                    translationY = offset
                    alpha = fade.value
                }
                .drawBehind {
                    // Runs past the bottom edge, so the spring's overshoot never shows a gap under the sheet.
                    val radius = Radii.large.toPx()
                    drawRoundRect(sheetColor, size = Size(size.width, size.height + Spacing.giant.toPx()), cornerRadius = CornerRadius(radius))
                }
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = -1f
                }
                .nestedScroll(nested)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = dismissible,
                    onDragStarted = { mutex.mutate(MutatePriority.UserInput) {} },
                    onDragStopped = { velocity -> settleAfterRelease(velocity) },
                )
                .pointerInput(Unit) { detectTapGestures { /* A tap on the sheet must not reach the scrim. */ } }
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.xs), contentAlignment = Alignment.Center) {
                Spacer(
                    Modifier
                        .size(Sizes.sheetHandleWidth, Sizes.sheetHandleHeight)
                        .drawBehind { if (dismissible) drawRoundRect(handle, cornerRadius = CornerRadius(size.height / 2), alpha = HANDLE_ALPHA) },
                )
            }
            content()
        }
    }
}

private const val HANDLE_ALPHA = 0.5f
