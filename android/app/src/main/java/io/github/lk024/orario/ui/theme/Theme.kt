package io.github.lk024.orario.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import io.github.lk024.orario.data.ColorStyle
import io.github.lk024.orario.data.Settings
import io.github.lk024.orario.data.ThemeMode

/** Colori proposti quando non si usano quelli dello sfondo. */
val SeedColors = listOf(
    0xFFD93025, 0xFF8B5000, 0xFF6A5F00, 0xFF4C662B, 0xFF006C4C,
    0xFF006A6A, 0xFF0061A4, 0xFF4F5B92, 0xFF6750A4, 0xFF984061,
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
        // Regole colore 2025 di Material 3 Expressive
        val standard = rememberDynamicColorScheme(
            seedColor = Color(settings.seedColor),
            isDark = dark,
            isAmoled = false,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
        if (settings.colorStyle == ColorStyle.Vibrant) {
            val vibrant = rememberDynamicColorScheme(
                seedColor = Color(settings.seedColor),
                isDark = dark,
                isAmoled = false,
                style = PaletteStyle.Vibrant,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
            // Elementi colorati al massimo della saturazione, ma sfondi e superfici neutri come in Standard
            vibrant.copy(
                background = standard.background,
                onBackground = standard.onBackground,
                surface = standard.surface,
                onSurface = standard.onSurface,
                surfaceVariant = standard.surfaceVariant,
                onSurfaceVariant = standard.onSurfaceVariant,
                surfaceDim = standard.surfaceDim,
                surfaceBright = standard.surfaceBright,
                surfaceContainerLowest = standard.surfaceContainerLowest,
                surfaceContainerLow = standard.surfaceContainerLow,
                surfaceContainer = standard.surfaceContainer,
                surfaceContainerHigh = standard.surfaceContainerHigh,
                surfaceContainerHighest = standard.surfaceContainerHighest,
                inverseSurface = standard.inverseSurface,
                inverseOnSurface = standard.inverseOnSurface,
                outline = standard.outline,
                outlineVariant = standard.outlineVariant,
            )
        } else {
            standard
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
