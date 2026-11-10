package com.darshan.splitsmart.ui.splash

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Looper
import android.util.TypedValue
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.MainActivity
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class SplashPolicyTest {
    @Test
    fun `the splash holds until both the theme and the first screen are known`() {
        assertThat(SplashPolicy.keepOnScreen(themeLoaded = false, startResolved = false, heldForMs = 0)).isTrue()
        assertThat(SplashPolicy.keepOnScreen(themeLoaded = true, startResolved = false, heldForMs = 0)).isTrue()
        assertThat(SplashPolicy.keepOnScreen(themeLoaded = false, startResolved = true, heldForMs = 0)).isTrue()
        assertThat(SplashPolicy.keepOnScreen(themeLoaded = true, startResolved = true, heldForMs = 0)).isFalse()
    }

    @Test
    fun `a stuck read cannot trap anyone on the splash`() {
        assertThat(SplashPolicy.keepOnScreen(false, false, SplashPolicy.MAX_HOLD_MS - 1)).isTrue()
        assertThat(SplashPolicy.keepOnScreen(false, false, SplashPolicy.MAX_HOLD_MS)).isFalse()
    }

    @Test
    fun `the exit waits for a running icon, within limits, and never delays a slow launch`() {
        assertThat(SplashPolicy.exitDelayMs(nowMs = 1_010, iconStartMs = 1_000, iconDurationMs = 460)).isEqualTo(SplashPolicy.MAX_ICON_WAIT_MS)
        assertThat(SplashPolicy.exitDelayMs(nowMs = 1_400, iconStartMs = 1_000, iconDurationMs = 460)).isEqualTo(60)
        assertThat(SplashPolicy.exitDelayMs(nowMs = 2_000, iconStartMs = 1_000, iconDurationMs = 460)).isEqualTo(0)
    }

    @Test
    fun `reduced motion, in the app or the system, removes the splash at once`() {
        assertThat(SplashPolicy.exit(reduceMotionSetting = false, animatorsEnabled = true)).isEqualTo(SplashExit.SquashAndPop)
        assertThat(SplashPolicy.exit(reduceMotionSetting = true, animatorsEnabled = true)).isEqualTo(SplashExit.Remove)
        assertThat(SplashPolicy.exit(reduceMotionSetting = false, animatorsEnabled = false)).isEqualTo(SplashExit.Remove)
    }

    @Test
    fun `the squash is a squash and the pop grows`() {
        assertThat(SplashPolicy.SQUASH_X).isGreaterThan(1f)
        assertThat(SplashPolicy.SQUASH_Y).isLessThan(1f)
        assertThat(SplashPolicy.POP_SCALE).isGreaterThan(SplashPolicy.SQUASH_X)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SplashResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun splashAttr(attr: Int): TypedValue {
        val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_SplitSmart_Splash, true) }
        return TypedValue().also { assertThat(theme.resolveAttribute(attr, it, true)).isTrue() }
    }

    @Test
    fun `the launcher activity starts on the splash theme and hands over to the app theme`() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertThat(info.theme).isEqualTo(R.style.Theme_SplitSmart_Splash)
        assertThat(splashAttr(androidx.core.splashscreen.R.attr.postSplashScreenTheme).resourceId).isEqualTo(R.style.Theme_SplitSmart)
    }

    @Test
    fun `the splash is marigold in both themes, the launcher icon's colour`() {
        assertThat(splashAttr(androidx.core.splashscreen.R.attr.windowSplashScreenBackground).resourceId).isEqualTo(R.color.ic_launcher_background)
        assertThat(context.getColor(R.color.ic_launcher_background)).isEqualTo(MarigoldLight.primary.toArgb())
        assertThat(context.getColor(R.color.splash_mark)).isEqualTo(MarigoldLight.text.toArgb())
    }

    @Test
    fun `the window behind the first frame is the Marigold page, not the old coral one`() {
        assertThat(context.getColor(R.color.window_background)).isEqualTo(MarigoldLight.bg.toArgb())
    }

    @Test
    fun `the animated mark inflates and lasts the design system's slow screen duration`() {
        assertThat(ContextCompat.getDrawable(context, R.drawable.splash_mark_animated)).isInstanceOf(AnimatedVectorDrawable::class.java)
        assertThat(splashAttr(androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon).resourceId).isEqualTo(R.drawable.splash_mark_animated)
        assertThat(context.resources.getInteger(R.integer.splash_icon_duration)).isEqualTo(MotionDurations.ScreenSlow)
    }

    @Config(sdk = [30])
    @Test
    fun `below API 31 the mark is the static launcher foreground`() {
        assertThat(splashAttr(androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon).resourceId).isEqualTo(R.drawable.ic_launcher_foreground)
    }

    /** The exit a device would play. It has to end by removing the splash, or the app stays covered. */
    @Test
    fun `the exit squashes, pops and fades the splash, then removes it once`() {
        val splash = View(context)
        val icon = View(context)
        var removed = 0
        playSplashExit(splash, icon, delayMs = 100, onDone = { removed++ })
        assertThat(removed).isEqualTo(0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertThat(removed).isEqualTo(1)
        assertThat(splash.alpha).isEqualTo(0f)
        assertThat(icon.alpha).isEqualTo(0f)
        assertThat(icon.scaleX).isEqualTo(SplashPolicy.POP_SCALE)
    }

}
