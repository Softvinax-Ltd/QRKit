package com.mahbub.qrcodegeneratorandscanner

import androidx.compose.ui.window.ComposeUIViewController
import com.mahbub.qrcodegeneratorandscanner.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}
