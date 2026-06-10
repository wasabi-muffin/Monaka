package dev.gmvalentino.monaka

import androidx.navigation3.runtime.NavKey

internal sealed interface Screen : NavKey {
    data object Home : Screen
    data object Counter : Screen
    data object Login : Screen
    data object Checkout : Screen
    data object LiveFeed : Screen
    data object Timer : Screen
}
