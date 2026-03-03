package com.gmaingret.outlinergod.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun OutlinerGodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    densityScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val customDensity = Density(
        density = LocalDensity.current.density * densityScale,
        fontScale = LocalDensity.current.fontScale
    )
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        typography = Typography(),
    ) {
        CompositionLocalProvider(
            LocalDensity provides customDensity,
            content = content,
        )
    }
}
