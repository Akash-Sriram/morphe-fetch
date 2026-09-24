package app.morphe.fetch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Informative banner above the archive list. */
@Composable
internal fun AppDisclaimerBanner() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Community patch index for Morphe Manager. Select an app to explore patches and add sources.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        placeholder = {
            Text(
                "Search apps or packages",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )
}

@Composable
internal fun ArchiveFilterRow(
    tab: AppListTab,
    onTabSelect: (AppListTab) -> Unit,
    sort: AppSort,
    onSortSelect: (AppSort) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppDisclaimerBanner()
        // Row 1: Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppListTab.entries.forEach { t ->
                MorpheFilterChip(
                    selected = tab == t,
                    onClick = { onTabSelect(t) },
                    label = t.label
                )
            }
        }
        // Row 2: Sort Option Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sort:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp)
            )
            AppSort.entries.forEach { s ->
                MorpheFilterChip(
                    selected = sort == s,
                    onClick = { onSortSelect(s) },
                    label = s.label
                )
            }
        }
    }
}

@Composable
internal fun AppBrowserRow(
    app: ArchiveApp,
    favourite: Boolean,
    installed: Boolean = false,
    selected: Boolean = false,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    }
    val borderWidth = if (selected) 1.5.dp else 0.dp
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    SurfaceCard(
        modifier = modifier,
        onClick = onClick,
        cornerRadius = 14.dp,
        color = cardColor,
        borderWidth = borderWidth,
        borderColor = borderColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppAvatar(
                packageName = app.packageName,
                initial = app.name.firstOrNull()?.uppercaseChar() ?: '?',
                isInstalled = installed,
                iconUrl = app.iconUrl
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (installed) {
                        MorpheStatusBadge(
                            text = "Installed",
                            icon = Icons.Outlined.CheckCircle,
                            tone = SemanticTone.Success
                        )
                    }
                }
                Text(
                    text = app.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MorpheStatusBadge(
                        text = "${app.sourceCount} " + if (app.sourceCount == 1) "source" else "sources",
                        icon = Icons.Outlined.Dns,
                        tone = SemanticTone.Primary
                    )
                    formatReleaseDate(app.newestReleaseDate())?.let { released ->
                        Text(
                            text = "Updated $released",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HelperIconButton(
                icon = if (favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (favourite) "Unlike" else "Like",
                onClick = onToggleFavourite,
                tint = if (favourite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Async Avatar with memory LRU and disk caching powered by [AppIconResolver].
 */
@Composable
internal fun AsyncAvatar(
    url: String?,
    fallbackText: String,
    size: Dp = 20.dp,
    cornerRadius: Dp = 6.dp
) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
        } else {
            bitmap = AppIconResolver.resolveUrl(context, url)
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(SemanticTone.Primary.container),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackText,
                color = SemanticTone.Primary.content,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

internal fun openAddSource(context: Context, addUrl: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(addUrl))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val manager = MORPHE_MANAGER_PACKAGES.firstOrNull { pkg ->
        runCatching {
            context.packageManager.queryIntentActivities(intent.setPackage(pkg), 0).isNotEmpty()
        }.getOrDefault(false)
    }
    if (manager != null) intent.setPackage(manager)
    runCatching { context.startActivity(intent) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppDetailView(
    app: ArchiveApp,
    onBack: () -> Unit,
    onGetApk: ((packageName: String, appName: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    val sortedSources = remember(app.sources) {
        app.sources.sortedByDescending { it.latestChanges?.date.orEmpty() }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
    ) {
        item {
            SectionCard {
                Column(
                    modifier = Modifier.padding(MorpheDefaults.ContentPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppAvatar(
                            packageName = app.packageName,
                            initial = app.name.firstOrNull()?.uppercaseChar() ?: '?',
                            iconUrl = app.iconUrl
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MorpheStatusBadge(
                            text = "${app.sourceCount} " + if (app.sourceCount == 1) "source" else "sources",
                            icon = Icons.Outlined.Extension,
                            tone = SemanticTone.Primary
                        )
                        if (app.versions.isNotEmpty()) {
                            MorpheStatusBadge(
                                text = "${app.versions.size} supported " + if (app.versions.size == 1) "version" else "versions",
                                icon = Icons.Outlined.Layers,
                                tone = SemanticTone.Neutral
                            )
                        }
                        formatReleaseDate(app.newestReleaseDate())?.let { released ->
                            MorpheStatusBadge(
                                text = "Updated $released",
                                icon = Icons.Outlined.Schedule,
                                tone = SemanticTone.Neutral
                            )
                        }
                    }

                    if (onGetApk != null) {
                        HelperButton(
                            text = "Get APK",
                            icon = Icons.Outlined.Download,
                            onClick = { onGetApk(app.packageName, app.name) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (app.versions.isNotEmpty()) {
            item {
                MorpheSectionTitle(text = "Supported Versions", icon = Icons.Outlined.Layers)
            }
            item {
                SectionCard {
                    FlowRow(
                        modifier = Modifier.padding(MorpheDefaults.ContentPadding),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        app.versions.forEach { version ->
                            MorpheStatusBadge(
                                text = version,
                                tone = SemanticTone.Neutral
                            )
                        }
                    }
                }
            }
        }
        if (sortedSources.isEmpty()) {
            item { MorpheEmptyState(message = "No patch sources listed for this app.") }
        } else {
            item {
                MorpheSectionTitle(text = "Sources", icon = Icons.Outlined.Extension)
            }
            items(sortedSources, key = { it.repo }) { source ->
                AppSourceCard(
                    source = source,
                    onOpenUrl = openUrl,
                    onAddToMorphe = { openAddSource(context, it) }
                )
            }
        }
    }
}

@Composable
internal fun AppSourceCard(
    source: ArchiveSource,
    onOpenUrl: (String) -> Unit,
    onAddToMorphe: (String) -> Unit
) {
    var expanded by remember(source.repo) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    SettingsItemCard(
        onClick = null,
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncAvatar(
                    url = MorpheArchive.avatarUrlFor(source),
                    fallbackText = source.repo.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    size = 28.dp,
                    cornerRadius = 8.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.repo,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("${source.patches.size} ")
                            append(if (source.patches.size == 1) "patch" else "patches")
                            formatBundleRelease(source.latestChanges)?.let { release ->
                                append(" · ")
                                append(release)
                            }
                        },
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse patches" else "Expand patches",
                    tint = colors.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
            ) {
                source.addUrl?.takeIf { it.isNotBlank() }?.let { addUrl ->
                    MorphePillButton(
                        onClick = { onAddToMorphe(addUrl) },
                        icon = Icons.Outlined.Add,
                        contentDescription = "Add to Morphe",
                        label = "Add to Morphe",
                        tone = SemanticTone.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
                source.webUrl?.takeIf { it.isNotBlank() }?.let { webUrl ->
                    MorphePillButton(
                        onClick = { onOpenUrl(webUrl) },
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open repo",
                        label = "Open repo",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            AnimatedExpand(visible = expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (source.patches.isEmpty()) {
                        Text(
                            "No patches listed for this source.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        val longList = source.patches.size > 5
                        val patchesScroll = rememberScrollState()
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(max = if (longList) 240.dp else Dp.Unspecified)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .then(
                                            if (longList) Modifier.verticalScroll(patchesScroll) else Modifier
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    source.patches.forEach { patch ->
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Extension,
                                                    contentDescription = null,
                                                    tint = colors.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = patch.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.onSurface,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            patch.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                                Text(
                                                    text = desc,
                                                    color = colors.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (longList) {
                                ScrollStateScrollbar(
                                    scrollState = patchesScroll,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
