package dev.gmvalentino.monaka.sample

import androidx.compose.ui.window.ComposeUIViewController
import dev.gmvalentino.monaka.App
import platform.UIKit.UIViewController

/**
 * iOS entry point. The Xcode app target wraps this controller in a SwiftUI `UIViewControllerRepresentable`.
 */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
