package app.morphe.fetch

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val MORPHE_MANAGER_PACKAGES = listOf(
    "app.morphe.manager",
    "app.morphe.manager.debug"
)

internal const val MORPHE_MANAGER_SITE_URL = "https://morphe.software/"

/** Which primary section of the archive to browse: Apps or Bundles. */
internal enum class AppListTab(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    Apps("Apps", Icons.AutoMirrored.Outlined.ViewList, "Apps"),
    Bundles("Bundles", Icons.Outlined.Layers, "Patch Bundles")
}

/** Status filter for apps in the archive list. */
internal enum class AppStatusFilter(
    val label: String,
    val icon: ImageVector
) {
    Installed("Installed", Icons.Outlined.CheckCircle),
    NotInstalled("Not installed", Icons.Outlined.Block),
    Favourites("Liked", Icons.Outlined.FavoriteBorder)
}

/** How the archive list is ordered. */
internal enum class AppSort(val label: String, val icon: ImageVector, val rotation: Float = 0f) {
    AZ("A–Z", Icons.Outlined.SortByAlpha),
    ZA("Z–A", Icons.Outlined.SortByAlpha, 180f)
}

/**
 * Persisted favourites for the "Find New Apps" browser.
 */
internal object MorpheFavourites {
    private const val PREFS = "morphe_favourites"
    private const val KEY = "packages"

    fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            .orEmpty()

    suspend fun loadAsync(context: Context): Set<String> = withContext(Dispatchers.IO) {
        load(context)
    }

    fun toggle(context: Context, packageName: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (!current.add(packageName)) current.remove(packageName)
        prefs.edit().putStringSet(KEY, current).apply()
        return current
    }

    suspend fun toggleAsync(context: Context, packageName: String): Set<String> = withContext(Dispatchers.IO) {
        toggle(context, packageName)
    }
}
