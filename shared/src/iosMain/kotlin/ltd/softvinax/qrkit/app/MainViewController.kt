package ltd.softvinax.qrkit.app

import androidx.compose.ui.window.ComposeUIViewController
import ltd.softvinax.qrkit.app.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}
