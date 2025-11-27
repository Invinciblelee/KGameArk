package com.example.cmp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

@Composable
fun MainScreen() {
    // Create a back stack, specifying the route the app should start with.
    val backStack = remember { mutableStateListOf<Any>(Home) }

    // A NavDisplay displays your back stack. Whenever the back stack changes, the display updates.
    NavDisplay(
        backStack = backStack,

        // Specify what should happen when the user goes back
        onBack = { backStack.removeLastOrNull() },

        // An entry provider converts a route into a NavEntry which contains the content for that route.
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen { backStack.add(it) }
            }
            entry<Product> { route ->
                ProductScreen(route) { backStack.removeLastOrNull() }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}