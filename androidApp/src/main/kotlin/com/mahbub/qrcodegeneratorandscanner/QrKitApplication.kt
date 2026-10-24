package com.mahbub.qrcodegeneratorandscanner

import android.app.Application
import com.mahbub.qrcodegeneratorandscanner.di.initKoin

class QrKitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
