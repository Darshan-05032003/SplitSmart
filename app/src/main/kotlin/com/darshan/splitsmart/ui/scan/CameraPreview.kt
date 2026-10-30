package com.darshan.splitsmart.ui.scan

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.darshan.splitsmart.R
import com.darshan.splitsmart.data.ocr.CameraXReceiptCamera
import com.darshan.splitsmart.data.ocr.ReceiptCamera

/** Live CameraX preview bound to the screen's lifecycle. Device-only; tests inject a plain Box instead. */
@Composable
fun CameraPreview(camera: ReceiptCamera, modifier: Modifier = Modifier) {
    val controller = (camera as? CameraXReceiptCamera)?.controller() ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val description = stringResource(R.string.scan_preview_description)
    AndroidView(
        factory = { context -> PreviewView(context).apply { this.controller = controller } },
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
    )
    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
}
