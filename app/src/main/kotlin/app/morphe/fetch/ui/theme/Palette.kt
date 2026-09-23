package app.morphe.fetch

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils

// ---------------------------------------------------------------------------
// Colour scheme — exact values from morphe-manager's ui/theme/Color.kt
// ---------------------------------------------------------------------------

internal fun morpheDarkColorScheme() = darkColorScheme(
    primary = Color(0xFFA4C9FF),
    onPrimary = Color(0xFF00315D),
    primaryContainer = Color(0xFF004884),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFDABDE2),
    onTertiary = Color(0xFF3D2846),
    tertiaryContainer = Color(0xFF553E5E),
    onTertiaryContainer = Color(0xFFF6D9FF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    inverseOnSurface = Color(0xFF1A1C1E),
    inverseSurface = Color(0xFFE3E2E6),
    inversePrimary = Color(0xFF005FAC),
    surfaceTint = Color(0xFFA4C9FF),
    outlineVariant = Color(0xFF43474E),
    scrim = Color(0xFF000000),
)

internal fun morpheLightColorScheme() = lightColorScheme(
    primary = Color(0xFF005FAC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001C39),
    secondary = Color(0xFF545F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF6D5677),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6D9FF),
    onTertiaryContainer = Color(0xFF271430),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    inverseOnSurface = Color(0xFFF1F0F4),
    inverseSurface = Color(0xFF2F3033),
    inversePrimary = Color(0xFFA4C9FF),
    surfaceTint = Color(0xFF005FAC),
    outlineVariant = Color(0xFFC3C6CF),
    scrim = Color(0xFF000000),
)

/**
 * Neutral palette for [ThemeStyle.MONOCHROME], exactly the manager's
 * `ui/theme/Color.kt#monochrome*ColorScheme`. All color comes from surfaces and
 * typography; the only non-grayscale token is the error role.
 */
internal fun monochromeDarkColorScheme() = morpheDarkColorScheme().copy(
    primary = Color(0xFFE3E2E6),
    onPrimary = Color(0xFF1A1C1E),
    primaryContainer = Color(0xFF2E3135),
    onPrimaryContainer = Color(0xFFE3E2E6),
    secondary = Color(0xFFC7C6CA),
    onSecondary = Color(0xFF1A1C1E),
    secondaryContainer = Color(0xFF35393D),
    onSecondaryContainer = Color(0xFFE3E2E6),
    tertiary = Color(0xFFA8A7AB),
    onTertiary = Color(0xFF1A1C1E),
    tertiaryContainer = Color(0xFF3D4044),
    onTertiaryContainer = Color(0xFFE3E2E6),
    surfaceTint = Color(0xFFE3E2E6),
    inversePrimary = Color(0xFF43474E),
)

internal fun monochromeLightColorScheme() = morpheLightColorScheme().copy(
    primary = Color(0xFF1A1C1E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E2E6),
    onPrimaryContainer = Color(0xFF1A1C1E),
    secondary = Color(0xFF43474E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7E7EC),
    onSecondaryContainer = Color(0xFF1A1C1E),
    tertiary = Color(0xFF5E6064),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDEDF2),
    onTertiaryContainer = Color(0xFF1A1C1E),
    surfaceTint = Color(0xFF1A1C1E),
    inversePrimary = Color(0xFFE3E2E6),
)

/**
 * CompositionLocal carrying the active monochrome toggle. Used by icons and badges
 * that need to collapse to a neutral fill when monochrome mode is active.
 */
internal val LocalMonochromeTheme = staticCompositionLocalOf { false }

/**
 * Resolvers that swap colourful gradients and tints for neutral fills in
 * monochrome mode and pass the originals through otherwise, mirroring the
 * manager's `MonochromeThemeDefaults`.
 */
internal object MonochromeThemeDefaults {
    @Composable
    fun accentColor(base: Color): Color =
        if (LocalMonochromeTheme.current) MaterialTheme.colorScheme.primary else base

    /** Solid neutral fill in monochrome mode, the caller's gradient otherwise. */
    @Composable
    fun iconBackground(gradient: List<Color>): Brush =
        if (LocalMonochromeTheme.current) {
            SolidColor(MaterialTheme.colorScheme.primaryContainer)
        } else {
            Brush.linearGradient(gradient)
        }

    /**
     * The tint paired with [iconBackground]. Primary sits too close to that fill
     * to read against it, so the icon takes the container's own on-colour.
     */
    @Composable
    fun iconTint(base: Color): Color =
        if (LocalMonochromeTheme.current) MaterialTheme.colorScheme.onPrimaryContainer else base
}

/**
 * Parses a stored accent (`#RRGGBB`, `#AARRGGBB` or either without the hash),
 * returning null for anything that is not a colour so a bad value falls back to
 * the style's own accent instead of painting the app black.
 */
internal fun String?.toAccentColorOrNull(): Color? {
    val trimmed = this?.trim()?.removePrefix("#") ?: return null
    val parsed = trimmed.toLongOrNull(16) ?: return null
    return when (trimmed.length) {
        6 -> Color(0xFF000000L or parsed)
        8 -> Color(parsed)
        else -> null
    }
}

internal fun Color.toHexString(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())

/**
 * Re-derives the accent roles around [accent] the way the manager does: the
 * containers are the accent lightened for dark surfaces and darkened for light
 * ones, and every role takes the black-or-white foreground its own fill needs.
 */
internal fun ColorScheme.withCustomAccent(accent: Color, darkTheme: Boolean): ColorScheme {
    val primaryContainer = accent.adjustLightness(if (darkTheme) 0.25f else -0.25f)
    val secondary = accent.adjustLightness(if (darkTheme) 0.15f else -0.15f)
    val secondaryContainer = accent.adjustLightness(if (darkTheme) 0.35f else -0.35f)
    val tertiary = accent.adjustLightness(if (darkTheme) -0.1f else 0.1f)
    val tertiaryContainer = accent.adjustLightness(if (darkTheme) 0.4f else -0.4f)
    return copy(
        primary = accent,
        onPrimary = accent.contrastingForeground(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = primaryContainer.contrastingForeground(),
        secondary = secondary,
        onSecondary = secondary.contrastingForeground(),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = secondaryContainer.contrastingForeground(),
        tertiary = tertiary,
        onTertiary = tertiary.contrastingForeground(),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = tertiaryContainer.contrastingForeground(),
        surfaceTint = accent,
        inversePrimary = accent.adjustLightness(if (darkTheme) -0.4f else 0.4f)
    )
}

/**
 * Paints the dark scheme's backgrounds pure black instead of its near-black, for
 * OLED screens that save power on black pixels. Only the backgrounds move: cards
 * keep their own elevated tone, or every surface would flatten into one block.
 */
internal fun ColorScheme.withPureBlack(): ColorScheme {
    val black = Color.Black
    return copy(background = black, surface = black, surfaceDim = black)
}

private fun Color.adjustLightness(delta: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.contrastingForeground(): Color =
    if (ColorUtils.calculateLuminance(toArgb()) > 0.5) Color.Black else Color.White

/** Manager typography: a single bodyLarge override, everything else M3 default. */
internal val MorpheTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
