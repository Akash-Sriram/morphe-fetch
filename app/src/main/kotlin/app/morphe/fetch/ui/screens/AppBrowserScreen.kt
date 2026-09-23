package app.morphe.fetch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * "Find New Apps" browser: fetches the Morphe archive index live on every
 * open (never cached), offers a searchable list of all patched apps, and a
 * per-app detail view with its versions, patches, and source repos.
 */
@Composable
internal fun AppBrowserScreen(
    onBack: () -> Unit,
    onGetApk: ((packageName: String, appName: String) -> Unit)? = null
) {
    var apps by remember { mutableStateOf<List<ArchiveApp>?>(null) }
    var freshness by remember { mutableStateOf<ArchiveFreshness?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ArchiveApp?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(AppListTab.All) }
    var sort by remember { mutableStateOf(AppSort.AZ) }
    val context = LocalContext.current
    var favourites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        favourites = MorpheFavourites.loadAsync(context)
        installedPackages = withContext(Dispatchers.IO) {
            context.packageManager.getInstalledApplications(0)
                .mapNotNull { it.packageName.takeIf(String::isNotBlank) }
                .toSet()
        }
    }

    LaunchedEffect(loadKey) {
        apps = null
        error = null
        try {
            val index = MorpheArchive.fetchIndex()
            apps = index.apps.sortedBy { it.name.lowercase(Locale.US) }
            freshness = archiveFreshness(index.generatedAt)
        } catch (e: Exception) {
            error = e.message ?: "Failed to load the app index"
        }
    }

    BackHandler(enabled = selected != null) {
        selected = null
    }

    LaunchedEffect(query) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = MorpheDefaults.ContentPadding,
                vertical = MorpheDefaults.ContentPadding
            ),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
    ) {
        // Screen Header
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (selected == null) "Find New Apps" else "App Details",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (selected == null) {
                    val fresh = freshness
                    Text(
                        text = buildString {
                            append(apps?.let { "${it.size} apps with patches" } ?: "Morphe patch archive")
                            fresh?.let { append(" · index ${it.label}") }
                        },
                        color = if (fresh?.stale == true) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HelperHeaderIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                onClick = {
                    if (selected != null) selected = null else onBack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (selected == null) {
                HelperHeaderIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    onClick = { loadKey++ },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        val current = selected
        if (current != null) {
            AppDetailView(
                app = current,
                onBack = { selected = null },
                onGetApk = onGetApk,
                modifier = Modifier.weight(1f)
            )
        } else {
            AppSearchBar(
                query = query,
                onQueryChange = { query = it }
            )

            val loaded = apps
            when {
                error != null -> {
                    InfoCard(error!!)
                    HelperButton(
                        text = "Retry",
                        onClick = { loadKey++ },
                        icon = Icons.Outlined.Refresh,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                loaded == null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(MorpheDefaults.ContentPaddingSmall))
                        Text(
                            "Loading app index…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val filtered = remember(loaded, query, tab, sort, favourites, installedPackages) {
                        loaded
                            .filter { app ->
                                query.isBlank() ||
                                    app.name.contains(query, ignoreCase = true) ||
                                    app.packageName.contains(query, ignoreCase = true)
                            }
                            .filter { app ->
                                when (tab) {
                                    AppListTab.All -> true
                                    AppListTab.Favourites -> app.packageName in favourites
                                    AppListTab.Installed -> app.packageName in installedPackages
                                    AppListTab.NotInstalled -> app.packageName !in installedPackages
                                }
                            }
                            .let { matches ->
                                when (sort) {
                                    AppSort.AZ -> matches.sortedBy { it.name.lowercase(Locale.US) }
                                    AppSort.ZA -> matches.sortedByDescending { it.name.lowercase(Locale.US) }
                                    AppSort.Sources -> matches.sortedByDescending { it.sourceCount }
                                    AppSort.Newest -> matches
                                        .map { app -> app.newestReleaseDate().orEmpty() to app }
                                        .sortedByDescending { it.first }
                                        .map { it.second }
                                }
                            }
                    }

                    ArchiveFilterRow(
                        tab = tab,
                        onTabSelect = { tab = it },
                        sort = sort,
                        onSortSelect = { sort = it }
                    )

                    if (filtered.isEmpty()) {
                        InfoCard(
                            when (tab) {
                                AppListTab.Favourites ->
                                    "No liked apps yet. Tap the heart on any app to pin it here."
                                AppListTab.Installed -> "No patched apps in the index are installed."
                                AppListTab.NotInstalled ->
                                    "Every app in the index is installed on this device."
                                AppListTab.All -> "No apps match \"$query\"."
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(bottom = 64.dp),
                                    verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                                ) {
                                    items(filtered, key = { it.packageName }) { app ->
                                        AppBrowserRow(
                                            app = app,
                                            favourite = app.packageName in favourites,
                                            installed = app.packageName in installedPackages,
                                            onToggleFavourite = {
                                                scope.launch {
                                                    favourites = MorpheFavourites.toggleAsync(context, app.packageName)
                                                }
                                            },
                                            onClick = { selected = app },
                                            modifier = Modifier.animatedListItem(this)
                                        )
                                    }
                                }
                                LazyListScrollbar(
                                    listState = listState,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                            MorpheFab(
                                visible = listState.firstVisibleItemIndex > 0,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 32.dp, bottom = 20.dp)
                            ) {
                                Surface(
                                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                                    shape = CircleShape,
                                    color = SemanticTone.Primary.container,
                                    contentColor = SemanticTone.Primary.content
                                ) {
                                    ThemedIcon(
                                        icon = Icons.Outlined.KeyboardArrowUp,
                                        contentDescription = "Go to top",
                                        tint = SemanticTone.Primary.content,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
