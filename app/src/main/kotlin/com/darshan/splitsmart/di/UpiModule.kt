package com.darshan.splitsmart.di

import com.darshan.splitsmart.data.upi.AndroidUpiAppLauncher
import com.darshan.splitsmart.data.upi.QrCodeGenerator
import com.darshan.splitsmart.data.upi.UpiAppLauncher
import com.darshan.splitsmart.data.upi.ZxingQrCodeGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UpiModule {
    @Binds abstract fun upiAppLauncher(impl: AndroidUpiAppLauncher): UpiAppLauncher
    @Binds abstract fun qrCodeGenerator(impl: ZxingQrCodeGenerator): QrCodeGenerator
}
