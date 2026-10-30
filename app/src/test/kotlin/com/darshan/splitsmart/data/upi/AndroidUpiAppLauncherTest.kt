package com.darshan.splitsmart.data.upi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidUpiAppLauncherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launcher = AndroidUpiAppLauncher(context)
    private val uri = "upi://pay?pa=asha%40okicici&pn=Asha&am=1.00&cu=INR"

    private fun installFakeUpiApp() {
        val filter = IntentFilter(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_DEFAULT); addCategory(Intent.CATEGORY_BROWSABLE); addDataScheme("upi") }
        val component = ComponentName("com.fake.upi", "com.fake.upi.PayActivity")
        shadowOf(context.packageManager).addActivityIfNotPresent(component)
        shadowOf(context.packageManager).addIntentFilterForActivity(component, filter)
    }

    @Test
    fun `without a upi app the result is NoUpiApp and nothing is started`() {
        assertThat(launcher.openUpiLink(uri)).isEqualTo(UpiLaunchResult.NoUpiApp)
        assertThat(shadowOf(context as android.app.Application).nextStartedActivity).isNull()
    }

    @Test
    fun `with a upi app a chooser over an ACTION_VIEW intent is started`() {
        installFakeUpiApp()
        assertThat(launcher.openUpiLink(uri)).isEqualTo(UpiLaunchResult.Opened)
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_CHOOSER)
        val target = started.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertThat(target.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(target.data).isEqualTo(Uri.parse(uri))
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `only upi scheme links are ever launched`() {
        installFakeUpiApp()
        assertThat(launcher.openUpiLink("http://evil.example/pay")).isEqualTo(UpiLaunchResult.Invalid)
        assertThat(launcher.openUpiLink("javascript:alert(1)")).isEqualTo(UpiLaunchResult.Invalid)
        assertThat(launcher.openUpiLink("upi://collect?pa=a@upi")).isEqualTo(UpiLaunchResult.Invalid)
        assertThat(shadowOf(context as android.app.Application).nextStartedActivity).isNull()
    }

    @Test
    fun `share text uses ACTION_SEND text plain through a chooser`() {
        assertThat(launcher.shareText("Pay me: $uri")).isTrue()
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_CHOOSER)
        val target = started.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertThat(target.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(target.type).isEqualTo("text/plain")
        assertThat(target.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("Pay me: $uri")
    }
}
