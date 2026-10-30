package com.darshan.splitsmart.testutil

import com.darshan.splitsmart.data.upi.UpiAppLauncher
import com.darshan.splitsmart.data.upi.UpiLaunchResult

class FakeUpiAppLauncher(var result: UpiLaunchResult = UpiLaunchResult.Opened) : UpiAppLauncher {
    val opened = mutableListOf<String>()
    val shared = mutableListOf<String>()
    var shareSucceeds = true
    override fun openUpiLink(uri: String): UpiLaunchResult { opened += uri; return result }
    override fun shareText(text: String): Boolean { shared += text; return shareSucceeds }
}
