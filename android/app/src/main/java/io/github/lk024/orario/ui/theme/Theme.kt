package io.github.lk024.orario.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme
import io.github.lk024.orario.data.Settings
import io.github.lk024.orario.data.ThemeMode

/** Colori proposti quando non si usano quelli dello sfondo. */
val SeedColors = listOf(
    0xFF006C4C, 0xFF006A6A, 0xFF0061A4, 0xFF4F5B92, 0xFF6750A4,
    0xFF984061, 0xFFB3261E, 0xFF8B5000, 0xFF6A5F00, 0xFF4C662B,
).map { it.toInt() }

@Composable
fun Settings.isDark(): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun OrarioTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = settings.isDark()
    val colorScheme = if (settings.dynamicColor) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(seedColor = Color(settings.seedColor), isDark = dark, isAmoled = false)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
