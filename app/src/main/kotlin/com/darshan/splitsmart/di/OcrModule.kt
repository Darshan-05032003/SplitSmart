package com.darshan.splitsmart.di

import com.darshan.splitsmart.data.ocr.AndroidCameraPermission
import com.darshan.splitsmart.data.ocr.CacheReceiptImageStore
import com.darshan.splitsmart.data.ocr.CameraPermission
import com.darshan.splitsmart.data.ocr.CameraXReceiptCamera
import com.darshan.splitsmart.data.ocr.MlKitTextRecognizer
import com.darshan.splitsmart.data.ocr.ReceiptCamera
import com.darshan.splitsmart.data.ocr.ReceiptImageStore
import com.darshan.splitsmart.data.ocr.TextRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds abstract fun textRecognizer(impl: MlKitTextRecognizer): TextRecognizer
    @Binds abstract fun imageStore(impl: CacheReceiptImageStore): ReceiptImageStore
    @Binds abstract fun cameraPermission(impl: AndroidCameraPermission): CameraPermission
}

/** One camera controller per scan ViewModel: it lives exactly as long as the scan screen. */
@Module
@InstallIn(ViewModelComponent::class)
abstract class CameraModule {
    @Binds @ViewModelScoped abstract fun camera(impl: CameraXReceiptCamera): ReceiptCamera
}
