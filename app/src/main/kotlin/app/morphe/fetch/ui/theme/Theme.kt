package app.morphe.fetch

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Composable
internal fun HelperTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (supportsDynamicColor) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) morpheDarkColorScheme() else morpheLightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MorpheTypography,
        content = content
    )
}

/**
 * Shared metrics, mirroring morphe-manager's `Defaults` in
 * `ui/screen/shared/SettingComponents.kt`.
 */
internal object MorpheDefaults {
    val CardElevation = 2.dp
    val CardCornerRadius = 16.dp
    val CompactCornerRadius = 12.dp
    val SettingsCornerRadius = 14.dp
    val SectionCornerRadius = 18.dp
    val IconSize = 24.dp
    val IconSizeSmall = 20.dp

    val MinTouchTarget = 48.dp
    val TallTouchTarget = 52.dp

    /** Compact pill holding an icon alone. */
    val PillHeight = 36.dp

    /** Pill that carries a label next to its icon. */
    val PillHeightLarge = 40.dp

    /** Fully rounded shape shared by the pill buttons. */
    val PillShape = RoundedCornerShape(50)

    /** Height of a glass tab or toggle. */
    val GlassButtonHeight = MinTouchTarget

    /** Height of a dialog action button. */
    val DialogButtonHeight = TallTouchTarget

    /** Width that keeps a compact action aligned with a wider icon + label sibling. */
    val CompactButtonWidth = 96.dp

    val ContentPaddingSmall = 8.dp
    val ContentPadding = 16.dp
    val ContentPaddingMedium = 24.dp
    val ContentPaddingExpanded = 32.dp
    val ItemSpacing = 12.dp

    val MaxContentWidth = 760.dp
    val MasterPaneWidth = 380.dp

    val DefaultGradientColors = listOf(Color(0xFF1E5AA8), Color(0xFF00AFAE))

    const val ANIMATION_DURATION = 220
    const val ANIMATION_DURATION_SHORT = 180
    const val SCREEN_ENTER_DURATION = 320
    const val DIALOG_SCALE = 0.95f
}

@Composable
internal fun isExpandedScreen(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.screenWidthDp >= 720
}

