package ltd.softvinax.qrkit.app.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatformTools

/**
 * Starts Koin once for the process. Safe to call from the Android Application and from the iOS
 * entry point, including Compose previews and repeated view-controller creation.
 */
fun initKoin(additionalModules: List<Module> = emptyList()) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        modules(appModule + additionalModules)
    }
}
