package com.darshan.splitsmart.ui.designsystem.modifiers

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.motion.SplitSmartMotion
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pure shake curve: a few decaying wiggles. 0 and 1 are at rest. */
object ShakeMath {
    const val WIGGLES = 3

    fun offsetFraction(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return (sin(p * WIGGLES * 2 * PI) * (1f - p)).toFloat()
    }
}

/** Error wiggle for a field or a refused action. Under reduced motion it does not move: the error colour and text carry it. */
@Stable
class ShakeState internal constructor(private val scope: CoroutineScope, private val motion: SplitSmartMotion) {
    private val animatable = Animatable(0f)
    val progress: Float get() = animatable.value

    fun shake() {
        if (motion.reduced) return
        scope.launch {
            animatable.snapTo(0f)
            animatable.animateTo(1f, motion.spec(MotionToken.Shake))
        }
    }
}

@Composable
fun rememberShakeState(): ShakeState {
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    return remember(motion) { ShakeState(scope, motion) }
}

fun Modifier.shake(state: ShakeState): Modifier = graphicsLayer {
    translationX = ShakeMath.offsetFraction(state.progress) * SHAKE_AMPLITUDE.toPx()
}

private val SHAKE_AMPLITUDE = 8.dp

/**
 * First-appearance entrance for list items and cards: hops up from slightly small and fades in, item [index] after
 * `staggerDelayMs(index)`. Under reduced motion it is a short fade with no delay. Runs once per [key].
 */
@Composable
fun Modifier.popInEntrance(index: Int, key: Any? = Unit): Modifier {
    val motion = LocalMotion.current
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key, motion) {
        if (progress.value >= 1f) return@LaunchedEffect
        val wait = motion.staggerDelayMs(index)
        if (wait > 0) delay(wait.toLong())
        progress.animateTo(1f, motion.spec(if (motion.reduced) MotionToken.Micro else MotionToken.Bounce))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        if (!motion.reduced) {
            val scale = ENTRANCE_SCALE + (1f - ENTRANCE_SCALE) * p
            scaleX = scale
            scaleY = scale
            translationY = (1f - p) * ENTRANCE_RISE.toPx()
        }
    }
}

private const val ENTRANCE_SCALE = 0.88f
private val ENTRANCE_RISE = 16.dp
