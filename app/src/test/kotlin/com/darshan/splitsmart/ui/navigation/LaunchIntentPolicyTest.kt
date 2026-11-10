package com.darshan.splitsmart.ui.navigation

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * MainActivity is exported (it is the launcher entry), so any app can start it with any intent. Navigation reads the launch
 * intent for deep links, including its private "explicit deep link" extras that name destinations and their arguments. SplitSmart
 * has no deep links, so the launch intent is cut down to a plain launcher intent before Navigation sees it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LaunchIntentPolicyTest {
    private val component = ComponentName("com.darshan.splitsmart", "com.darshan.splitsmart.MainActivity")

    @Test
    fun `navigation's deep-link extras, data and a view action are all dropped`() {
        val hostile = Intent(Intent.ACTION_VIEW, Uri.parse("splitsmart://settle/123")).apply {
            this.component = this@LaunchIntentPolicyTest.component
            putExtra("android-support-nav:controller:deepLinkIds", intArrayOf(1, 2, 3))
            putExtra("android-support-nav:controller:deepLinkArgs", arrayListOf(Bundle()))
            putExtra("android-support-nav:controller:deepLinkIntent", Intent(Intent.ACTION_VIEW))
            putExtra("anything", "else")
        }
        val clean = LaunchIntentPolicy.sanitize(hostile)
        assertThat(clean.action).isEqualTo(Intent.ACTION_MAIN)
        assertThat(clean.data).isNull()
        assertThat(clean.extras).isNull()
        assertThat(clean.component).isEqualTo(component)
        assertThat(clean.hasCategory(Intent.CATEGORY_LAUNCHER)).isTrue()
    }

    @Test
    fun `a normal launcher intent survives unchanged in what matters`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
        val clean = LaunchIntentPolicy.sanitize(launcher)
        assertThat(clean.action).isEqualTo(Intent.ACTION_MAIN)
        assertThat(clean.component).isEqualTo(component)
    }

    @Test
    fun `no intent at all is fine`() {
        assertThat(LaunchIntentPolicy.sanitize(null).action).isEqualTo(Intent.ACTION_MAIN)
    }
}
