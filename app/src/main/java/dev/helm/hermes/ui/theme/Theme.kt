package dev.helm.hermes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalHelm = staticCompositionLocalOf { NightColors }

/** Radii are a hierarchy, not a house style. Rules never round; panels do. */
object HelmShape {
    val rule = 2.dp       // state rails, ticks, the live trace
    val notch = 4.dp      // chips, inline lamps, tight controls
    val panel = 8.dp      // bounded surfaces: composer, approval, tool failure
    val sheet = 14.dp     // sheets and the one card allowed to feel like an object
}

@Composable
fun HelmTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) NightColors else DayColors

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.signal,
            onPrimary = Color(0xFF140A03),
            secondary = colors.data,
            onSecondary = Color(0xFF04161A),
            error = colors.alarm,
            onError = Color.White,
            background = colors.ground,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.panel,
            onSurfaceVariant = colors.textMuted,
            outline = colors.rule,
            outlineVariant = colors.ruleFaint,
        )
    } else {
        lightColorScheme(
            primary = colors.signal,
            onPrimary = Color.White,
            secondary = colors.data,
            onSecondary = Color.White,
            error = colors.alarm,
            onError = Color.White,
            background = colors.ground,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.raised,
            onSurfaceVariant = colors.textMuted,
            outline = colors.rule,
            outlineVariant = colors.ruleFaint,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalHelm provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HelmType,
            shapes = androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(HelmShape.notch),
                small = androidx.compose.foundation.shape.RoundedCornerShape(HelmShape.notch),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(HelmShape.panel),
                large = androidx.compose.foundation.shape.RoundedCornerShape(HelmShape.sheet),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(HelmShape.sheet),
            ),
            content = content,
        )
    }
}
