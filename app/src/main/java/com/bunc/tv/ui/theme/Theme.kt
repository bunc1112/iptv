package com.bunc.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BuncNavyDeep = Color(0xFF0A1130)
val BuncNavy = Color(0xFF121C3C)
val BuncNavyTop = Color(0xFF0E1634)
val BuncGold = Color(0xFFFFB63C)
val BuncCyan = Color(0xFF5FCDFF)
val BuncOrange = Color(0xFFFF8A3C)
val BuncText = Color(0xFFEAF0FF)
val BuncTextDim = Color(0xFF9FB0D0)

private val BuncColors = darkColorScheme(
    primary = BuncGold,
    onPrimary = BuncNavyDeep,
    secondary = BuncCyan,
    onSecondary = BuncNavyDeep,
    tertiary = BuncOrange,
    background = BuncNavyDeep,
    onBackground = BuncText,
    surface = BuncNavy,
    onSurface = BuncText,
    surfaceVariant = BuncNavyTop,
    onSurfaceVariant = BuncTextDim,
)

@Composable
fun BuncTheme(
    darkTheme: Boolean = true, // Bunc is always its night-city dark
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BuncColors,
        typography = Typography(),
        content = content
    )
}
