package xyz.regulad.regulib.compose

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

private val NavController.withoutHistory: NavOptionsBuilder.() -> Unit
    get() = fun NavOptionsBuilder.() {
        popUpTo(this@withoutHistory.graph.startDestinationId) {
            inclusive = true
        }
        launchSingleTop = true
    }

fun NavController.navigateWithoutHistory(route: String) {
    this.navigate(route, withoutHistory)
}
