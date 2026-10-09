package app.morphe.fetch

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * "Find New Apps" browser: fetches the Morphe archive index live on every
 * open (never cached), offers a searchable list of all patched apps, and a
 * per-app detail view with its versions, patches, and source repos.
 *
 * Supports an adaptive two-pane (master-detail) layout on tablets and large screens.
 */
@Composable
internal fun AppBrowserScreen(
    onBack: () -> Unit,
    onGetApk: ((packageName: String, appName: String) -> Unit)? = null
) {
    val isExpanded = isExpandedScreen()
    var apps by remember { mutableStateOf<List<ArchiveApp>?>(null) }
    var sources by remember { mutableStateOf<List<ArchiveSource>?>(null) }
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
        error = null
        val cached = MorpheArchive.cachedIndex ?: withContext(Dispatchers.IO) {
            MorpheArchive.loadFromDisk(context)
        }
        if (cached != null) {
            apps = cached.apps.sortedBy { it.name.lowercase(Locale.US) }
            sources = cached.repos.sortedBy { it.displayName.lowercase(Locale.US) }
            freshness = archiveFreshness(cached.generatedAt)
        } else {
            apps = null
            sources = null
        }

        try {
            val index = withContext(Dispatchers.IO) {
                MorpheArchive.fetchIndex(context, forceNetwork = (loadKey > 0 || cached == null))
            }
            apps = index.apps.sortedBy { it.name.lowercase(Locale.US) }
            sources = index.repos.sortedBy { it.displayName.lowercase(Locale.US) }
            freshness = archiveFreshness(index.generatedAt)
        } catch (e: Exception) {
            if (apps == null && sources == null) {
                error = e.message ?: "Failed to load the archive index"
            }
        }
    }

    BackHandler(enabled = !isExpanded && selected != null) {
        selected = null
    }

    LaunchedEffect(query) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    var filteredApps by remember { mutableStateOf<List<ArchiveApp>>(emptyList()) }
    var filteredSources by remember { mutableStateOf<List<ArchiveSource>>(emptyList()) }
    var matchingSourcesForSearch by remember { mutableStateOf<List<ArchiveSource>>(emptyList()) }

    LaunchedEffect(apps, sources, query, tab, sort, favourites, installedPackages) {
        val loadedApps = apps
        val loadedSources = sources
        if (loadedApps == null && loadedSources == null) {
            filteredApps = emptyList()
            filteredSources = emptyList()
            matchingSourcesForSearch = emptyList()
            return@LaunchedEffect
        }
        val q = query.trim()
        if (q.isNotEmpty()) {
            delay(50)
        }
        withContext(Dispatchers.Default) {
            val qLower = q.lowercase(Locale.US)

            // Sources filtering & ranking
            val matchedSources = (loadedSources ?: emptyList())
                .filter { q.isBlank() || it.matchesQuery(qLower) }
                .let { sList ->
                    if (q.isNotBlank()) {
                        sList.sortedWith(
                            compareByDescending<ArchiveSource> { it.scoreMatch(qLower) }
                                .thenByDescending { it.actualPatchCount }
                        )
                    } else {
                        when (sort) {
                            AppSort.AZ -> sList.sortedBy { it.displayName.lowercase(Locale.US) }
                            AppSort.ZA -> sList.sortedByDescending { it.displayName.lowercase(Locale.US) }
                            AppSort.Sources, AppSort.Newest -> sList.sortedByDescending { it.actualPatchCount }
                        }
                    }
                }
            filteredSources = matchedSources
            matchingSourcesForSearch = if (q.isNotBlank()) matchedSources else emptyList()

            // Apps filtering & ranking
            val matchedApps = if (tab == AppListTab.Sources) {
                emptyList()
            } else {
                (loadedApps ?: emptyList())
                    .filter { app ->
                        q.isBlank() || app.matchesQuery(qLower)
                    }
                    .filter { app ->
                        when (tab) {
                            AppListTab.All -> true
                            AppListTab.Sources -> false
                            AppListTab.Favourites -> app.packageName in favourites
                            AppListTab.Installed -> app.packageName in installedPackages
                            AppListTab.NotInstalled -> app.packageName !in installedPackages
                        }
                    }
                    .let { matches ->
                        if (q.isNotBlank()) {
                            matches
                                .map { it to it.scoreMatch(qLower) }
                                .sortedWith(
                                    compareByDescending<Pair<ArchiveApp, Int>> { it.second }
                                        .thenByDescending { it.first.patchCount }
                                )
                                .map { it.first }
                        } else {
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
            }
            filteredApps = matchedApps
        }
    }

    LaunchedEffect(isExpanded, filteredApps) {
        if (isExpanded && selected == null && filteredApps.isNotEmpty()) {
            selected = filteredApps.first()
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
                    text = if (isExpanded) "Find New Apps" else (if (selected == null) "Find New Apps" else "App Details"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val fresh = freshness
                Text(
                    text = buildString {
                        if (tab == AppListTab.Sources) {
                            append(sources?.let { "${it.size} patch sources" } ?: "Morphe patch sources")
                        } else {
                            append(apps?.let { "${it.size} apps with patches" } ?: "Morphe patch archive")
                        }
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
            HelperHeaderIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                onClick = {
                    if (!isExpanded && selected != null) selected = null else onBack()
                },
                modifier = Modifier.align(Alignment.CenterStart)
            )
            HelperHeaderIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = "Refresh",
                onClick = { loadKey++ },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        val openSourceIntent: (String) -> Unit = remember(context) {
            { url ->
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }
        }

        val masterList = @Composable { paneModifier: Modifier ->
            MasterListPane(
                query = query,
                onQueryChange = { query = it },
                apps = apps,
                sources = sources,
                error = error,
                onRetry = { loadKey++ },
                tab = tab,
                onTabSelect = { tab = it },
                sort = sort,
                onSortSelect = { sort = it },
                filteredApps = filteredApps,
                filteredSources = filteredSources,
                matchingSourcesForSearch = matchingSourcesForSearch,
                favourites = favourites,
                installedPackages = installedPackages,
                selected = selected,
                isExpanded = isExpanded,
                listState = listState,
                onToggleFavourite = { pkg ->
                    scope.launch {
                        favourites = MorpheFavourites.toggleAsync(context, pkg)
                    }
                },
                onSelectApp = { selected = it },
                onOpenSourceUrl = openSourceIntent,
                onAddToMorphe = openSourceIntent,
                onScrollToTop = {
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = paneModifier
            )
        }

        if (isExpanded) {
            // Adaptive Two-Pane Master-Detail Layout
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                masterList(
                    Modifier
                        .width(MorpheDefaults.MasterPaneWidth)
                        .fillMaxHeight()
                )

                MorpheVerticalDivider(
                    modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val current = selected
                    if (current != null) {
                        AppDetailView(
                            app = current,
                            searchQuery = query,
                            onBack = { selected = null },
                            onGetApk = onGetApk,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        SurfaceCard(
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 16.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Explore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Select an app",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Choose an app from the list to view its compatible versions, available patches, and source bundles.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(max = 380.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Single-Pane Phone Layout
            val current = selected
            if (current != null) {
                AppDetailView(
                    app = current,
                    searchQuery = query,
                    onBack = { selected = null },
                    onGetApk = onGetApk,
                    modifier = Modifier.weight(1f)
                )
            } else {
                masterList(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MasterListPane(
    query: String,
    onQueryChange: (String) -> Unit,
    apps: List<ArchiveApp>?,
    sources: List<ArchiveSource>?,
    error: String?,
    onRetry: () -> Unit,
    tab: AppListTab,
    onTabSelect: (AppListTab) -> Unit,
    sort: AppSort,
    onSortSelect: (AppSort) -> Unit,
    filteredApps: List<ArchiveApp>,
    filteredSources: List<ArchiveSource>,
    matchingSourcesForSearch: List<ArchiveSource>,
    favourites: Set<String>,
    installedPackages: Set<String>,
    selected: ArchiveApp?,
    isExpanded: Boolean,
    listState: LazyListState,
    onToggleFavourite: (String) -> Unit,
    onSelectApp: (ArchiveApp) -> Unit,
    onOpenSourceUrl: (String) -> Unit,
    onAddToMorphe: (String) -> Unit,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
    ) {
        AppSearchBar(
            query = query,
            onQueryChange = onQueryChange
        )

        val loaded = apps != null || sources != null
        when {
            error != null -> {
                InfoCard(error)
                HelperButton(
                    text = "Retry",
                    onClick = onRetry,
                    icon = Icons.Outlined.Refresh,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            !loaded -> {
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
                        "Loading archive index…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                ArchiveFilterRow(
                    tab = tab,
                    onTabSelect = onTabSelect,
                    sort = sort,
                    onSortSelect = onSortSelect
                )

                if (tab == AppListTab.Sources) {
                    if (filteredSources.isEmpty()) {
                        InfoCard("No patch sources match \"$query\".")
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
                                    items(filteredSources, key = { it.repo }) { source ->
                                        SourceBrowserCard(
                                            source = source,
                                            searchQuery = query,
                                            onOpenUrl = onOpenSourceUrl,
                                            onAddToMorphe = onAddToMorphe,
                                            onSelectApp = { appItem ->
                                                val match = apps?.firstOrNull { it.packageName == appItem.packageName }
                                                    ?: ArchiveApp(name = appItem.name, packageName = appItem.packageName)
                                                onSelectApp(match)
                                            },
                                            modifier = Modifier.animatedListItem(this)
                                        )
                                    }
                                }
                                LazyListScrollbar(
                                    listState = listState,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                            val showFab by remember {
                                derivedStateOf { listState.firstVisibleItemIndex > 0 }
                            }
                            MorpheFab(
                                visible = showFab,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 24.dp, bottom = 20.dp)
                            ) {
                                Surface(
                                    onClick = onScrollToTop,
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
                } else {
                    val hasSources = query.isNotBlank() && matchingSourcesForSearch.isNotEmpty()
                    if (filteredApps.isEmpty() && !hasSources) {
                        InfoCard(
                            when (tab) {
                                AppListTab.Favourites ->
                                    "No liked apps yet. Tap the heart on any app to pin it here."
                                AppListTab.Installed -> "No patched apps in the index are installed."
                                AppListTab.NotInstalled ->
                                    "Every app in the index is installed on this device."
                                else -> "No apps match \"$query\"."
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
                                    if (hasSources) {
                                        item(key = "header_sources") {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Matching Sources (${matchingSourcesForSearch.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "View all in Sources tab",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        items(matchingSourcesForSearch.take(4), key = { "src_${it.repo}" }) { source ->
                                            SourceBrowserCard(
                                                source = source,
                                                searchQuery = query,
                                                onOpenUrl = onOpenSourceUrl,
                                                onAddToMorphe = onAddToMorphe,
                                                onSelectApp = { appItem ->
                                                    val match = apps?.firstOrNull { it.packageName == appItem.packageName }
                                                        ?: ArchiveApp(name = appItem.name, packageName = appItem.packageName)
                                                    onSelectApp(match)
                                                },
                                                modifier = Modifier.animatedListItem(this)
                                            )
                                        }
                                        if (filteredApps.isNotEmpty()) {
                                            item(key = "header_apps") {
                                                Text(
                                                    text = "Matching Apps (${filteredApps.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    items(filteredApps, key = { it.packageName }) { app ->
                                        AppBrowserRow(
                                            app = app,
                                            favourite = app.packageName in favourites,
                                            installed = app.packageName in installedPackages,
                                            selected = (isExpanded && selected?.packageName == app.packageName),
                                            searchQuery = query,
                                            onToggleFavourite = { onToggleFavourite(app.packageName) },
                                            onClick = { onSelectApp(app) },
                                            modifier = Modifier.animatedListItem(this)
                                        )
                                    }
                                }
                                LazyListScrollbar(
                                    listState = listState,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                            val showFab by remember {
                                derivedStateOf { listState.firstVisibleItemIndex > 0 }
                            }
                            MorpheFab(
                                visible = showFab,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 24.dp, bottom = 20.dp)
                            ) {
                                Surface(
                                    onClick = onScrollToTop,
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
