package dev.phillipslabs.comcord.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSizeClass {
    Compact,
    Medium,
    Expanded,
}

fun windowSizeClass(width: Dp): WindowSizeClass =
    when {
        width < 900.dp -> WindowSizeClass.Compact
        width < 1280.dp -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }

@Composable
fun rememberWindowSizeClass(width: Dp): WindowSizeClass = windowSizeClass(width)
