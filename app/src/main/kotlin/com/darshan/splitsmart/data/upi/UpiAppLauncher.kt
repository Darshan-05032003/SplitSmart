package com.darshan.splitsmart.data.upi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.darshan.splitsmart.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

sealed interface UpiLaunchResult {
    data object Opened : UpiLaunchResult
    data object NoUpiApp : UpiLaunchResult
    data object Invalid : UpiLaunchResult
}

/** Opens UPI apps and the share sheet. Wrapped so JVM tests record intents instead of launching them. */
interface UpiAppLauncher {
    fun openUpiLink(uri: String): UpiLaunchResult
    fun shareText(text: String): Boolean
}

class AndroidUpiAppLauncher @Inject constructor(@ApplicationContext private val context: Context) : UpiAppLauncher {
    override fun openUpiLink(uri: String): UpiLaunchResult {
        // Only a link we can parse as a well-formed INR upi://pay request is ever handed to another app.
        UpiLinkBuilder.parse(uri) ?: return UpiLaunchResult.Invalid
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) return UpiLaunchResult.NoUpiApp
        return try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.settle_pay_with)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            UpiLaunchResult.Opened
        } catch (e: ActivityNotFoundException) {
            UpiLaunchResult.NoUpiApp
        }
    }

    override fun shareText(text: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.settle_share_with)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
