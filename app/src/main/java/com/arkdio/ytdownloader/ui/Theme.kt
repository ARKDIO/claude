package com.arkdio.ytdownloader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Clean fintech-style palette: white surfaces, grey fills, one blue accent. */
@Immutable
data class Palette(
    val bg: Color,
    val fill: Color,        // input / card background
    val title: Color,
    val body: Color,
    val sub: Color,
    val blue: Color,
    val blueSoft: Color,    // selected chip / secondary tint
    val onBlue: Color,
    val red: Color,
)

private val Light = Palette(
    bg = Color(0xFFFFFFFF),
    fill = Color(0xFFF2F4F6),
    title = Color(0xFF191F28),
    body = Color(0xFF4E5968),
    sub = Color(0xFF8B95A1),
    blue = Color(0xFF3182F6),
    blueSoft = Color(0xFFE8F3FF),
    onBlue = Color.White,
    red = Color(0xFFF04452),
)

private val Dark = Palette(
    bg = Color(0xFF17171C),
    fill = Color(0xFF2C2C35),
    title = Color(0xFFFFFFFF),
    body = Color(0xFFC3C3C6),
    sub = Color(0xFF7E7E87),
    blue = Color(0xFF3182F6),
    blueSoft = Color(0xFF1E2B44),
    onBlue = Color.White,
    red = Color(0xFFF04452),
)

val LocalPalette = staticCompositionLocalOf { Light }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = p.blue, background = p.bg, surface = p.bg)
    } else {
        lightColorScheme(primary = p.blue, background = p.bg, surface = p.bg)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
