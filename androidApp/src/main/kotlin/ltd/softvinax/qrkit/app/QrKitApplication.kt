package ltd.softvinax.qrkit.app

import android.app.Application
import ltd.softvinax.qrkit.app.di.initKoin

class QrKitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
