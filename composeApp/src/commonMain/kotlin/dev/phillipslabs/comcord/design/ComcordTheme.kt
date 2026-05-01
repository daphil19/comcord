package dev.phillipslabs.comcord.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ComcordColors {
    val AppBackground = Color(0xFF171A20)
    val SurfacePrimary = Color(0xFF1E232C)
    val SurfaceSecondary = Color(0xFF252B35)
    val SurfaceTertiary = Color(0xFF303744)
    val Accent = Color(0xFF5C7CFA)
    val TextStrong = Color(0xFFE8ECF3)
    val TextMuted = Color(0xFF9EA6B5)
    val Online = Color(0xFF3ECF8E)
    val Unread = Color(0xFF8FB2FF)
    val Destructive = Color(0xFFFF7272)
}

@Composable
fun ComcordTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = ComcordColors.Accent,
                background = ComcordColors.AppBackground,
                surface = ComcordColors.SurfacePrimary,
                surfaceVariant = ComcordColors.SurfaceSecondary,
                onPrimary = ComcordColors.TextStrong,
                onBackground = ComcordColors.TextStrong,
                onSurface = ComcordColors.TextStrong,
                onSurfaceVariant = ComcordColors.TextMuted,
                error = ComcordColors.Destructive,
            ),
        content = content,
    )
}
