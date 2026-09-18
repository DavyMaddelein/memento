package com.memento.app.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point consumed by the SwiftUI host in `iosApp`. Returns a `UIViewController` hosting the
 * shared Compose app.
 */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
