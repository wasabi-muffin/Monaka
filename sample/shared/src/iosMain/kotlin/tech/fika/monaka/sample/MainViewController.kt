package tech.fika.monaka.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import tech.fika.monaka.App

/**
 * iOS entry point. The Xcode app target wraps this controller in a SwiftUI `UIViewControllerRepresentable`.
 */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
