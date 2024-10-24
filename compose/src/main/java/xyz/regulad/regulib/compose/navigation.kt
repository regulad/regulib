package xyz.regulad.regulib.compose

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

@Suppress("Unused")
private val NavController.withoutHistory: NavOptionsBuilder.() -> Unit
    get() = fun NavOptionsBuilder.() {
        popUpTo(this@withoutHistory.graph.startDestinationId) {
            inclusive = true
        }
        launchSingleTop = true
    }

@Suppress("Unused")
fun NavController.navigateWithoutHistory(route: String) {
    this.navigate(route, withoutHistory)
}
