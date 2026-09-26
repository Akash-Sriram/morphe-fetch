package app.morphe.fetch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun AppInfoCard(
    request: HelperRequest,
    onFormatSelected: (String) -> Unit = {},
    onClearRequest: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    var copied by remember(request.packageName) { mutableStateOf(false) }
    var installed by remember(request.packageName) { mutableStateOf(false) }
    LaunchedEffect(request.packageName) {
        installed = withContext(Dispatchers.IO) {
            context.isPackageInstalled(request.packageName)
        }
    }

    SurfaceCard(
        cornerRadius = MorpheDefaults.SectionCornerRadius
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // App row: Avatar + Name/Package + Copy + Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppAvatar(
                    packageName = request.packageName,
                    initial = request.appName.firstOrNull()?.uppercaseChar() ?: '?'
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = request.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (installed) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Installed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = request.packageName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MorpheDefaults.PillShape)
                        .clickable {
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("package", request.packageName)
                            )
                            copied = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    LaunchedEffect(copied) {
                        if (copied) {
                            delay(2000)
                            copied = false
                        }
                    }
                    Icon(
                        imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = if (copied) "Copied package" else "Copy package",
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onClearRequest != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MorpheDefaults.PillShape)
                            .clickable { onClearRequest() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "New search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Compact chips row: Target Version | Architecture | Format
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Version badge
                val versionText = request.requestedVersionName ?: "Any version"
                InfoBadge(
                    label = versionText,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary
                )

                // Architecture badge
                val archText = request.availableAbis.firstOrNull() ?: "Default ABI"
                InfoBadge(
                    label = archText,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.secondary
                )

                // Format badge
                val formatText = when {
                    request.requestedFileType != null -> request.requestedFileType.uppercase(Locale.US)
                    request.allowSplitArchive -> "APK / Split"
                    else -> "APK"
                }
                InfoBadge(
                    label = formatText,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoBadge(
    label: String,
    color: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color,
        contentColor = contentColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun EmptyLaunchState(
    onSearch: (String, DownloadSource?) -> Unit,
    onOpenMorphe: () -> Unit,
    onFindApps: () -> Unit,
    enabledSources: List<DownloadSource> = DownloadSource.entries,
    isOverlayOpen: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<DownloadSource?>(null) }
    var promptMessage by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    val cachedIndex by MorpheArchive.indexState.collectAsState()
    var isCatalogLoading by remember { mutableStateOf(false) }

    var suggestions by remember { mutableStateOf<List<ArchiveApp>>(emptyList()) }
    var pendingUnconfirmedQuery by remember { mutableStateOf<String?>(null) }
    var pendingSuggestions by remember { mutableStateOf<List<ArchiveApp>>(emptyList()) }

    val isSearching = !isOverlayOpen && (searchQuery.isNotEmpty() || suggestions.isNotEmpty() || isSearchFocused || promptMessage != null)
    BackHandler(enabled = isSearching) {
        searchQuery = ""
        promptMessage = null
        suggestions = emptyList()
        pendingUnconfirmedQuery = null
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(Unit) {
        if (MorpheArchive.cachedIndex == null) {
            isCatalogLoading = true
            withContext(Dispatchers.IO) {
                MorpheArchive.getOrFetchIndex()
            }
            isCatalogLoading = false
        }
    }

    LaunchedEffect(searchQuery, cachedIndex) {
        promptMessage = null
        val query = searchQuery.trim()
        if (query.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(100)
        val results = withContext(Dispatchers.IO) {
            if (MorpheArchive.cachedIndex == null) {
                isCatalogLoading = true
                MorpheArchive.getOrFetchIndex()
                isCatalogLoading = false
            }
            MorpheArchive.searchCatalog(query).take(30)
        }
        suggestions = results
    }

    val handleSearchSubmission = {
        val q = searchQuery.trim()
        if (q.isNotBlank()) {
            val looksLikePackage = looksLikePackageName(q)
            val exactMatch = MorpheArchive.findExactMatch(q)

            if (looksLikePackage) {
                keyboardController?.hide()
                focusManager.clearFocus()
                promptMessage = null
                onSearch(q, selectedSource)
            } else {
                val currentMatches = if (suggestions.isNotEmpty()) suggestions else MorpheArchive.searchCatalog(q)
                keyboardController?.hide()
                focusManager.clearFocus()
                promptMessage = null
                pendingUnconfirmedQuery = q
                pendingSuggestions = buildList {
                    if (exactMatch != null) add(exactMatch)
                    addAll(currentMatches.filter { it.packageName != exactMatch?.packageName })
                }.take(3)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)) {
        SurfaceCard(
            cornerRadius = MorpheDefaults.SectionCornerRadius
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MorpheDefaults.ContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Search APK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Search by app name, package, or patch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isSearchFocused = it.isFocused },
                    placeholder = {
                        Text(
                            text = "e.g. YouTube, Photos, SponsorBlock...",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { handleSearchSubmission() }
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                promptMessage = null
                                suggestions = emptyList()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )

                if (promptMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = promptMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (isCatalogLoading && suggestions.isEmpty() && searchQuery.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Loading app catalog...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (suggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            if (suggestions.size > 3) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Matching apps (${suggestions.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (suggestions.size > 4) {
                                        Text(
                                            text = "Scroll for more",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                MorpheDivider(fullWidth = true)
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .padding(vertical = 4.dp)
                            ) {
                                items(
                                    items = suggestions,
                                    key = { it.packageName }
                                ) { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                promptMessage = null
                                                onSearch(app.packageName, selectedSource)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AppAvatar(
                                            packageName = app.packageName,
                                            initial = app.name.firstOrNull()?.uppercaseChar() ?: '?',
                                            iconUrl = app.iconUrl,
                                            size = 36.dp,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
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
                                        if (app.patchCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ) {
                                                Text(
                                                    text = "${app.patchCount} patches",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Source Provider",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            val selected = selectedSource == null
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedSource = null }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Layers,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "All Sources",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        items(enabledSources, key = { it.name }) { source ->
                            val selected = selectedSource == source
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedSource = if (selectedSource == source) null else source
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SourceAvatar(source = source, size = 16.dp)
                                    Text(
                                        text = source.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                HelperButton(
                    text = if (selectedSource != null) "Search on ${selectedSource!!.label}" else "Search APK",
                    onClick = handleSearchSubmission,
                    icon = Icons.Outlined.Search,
                    enabled = searchQuery.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        HelperOutlinedButton(
            text = "Find New Apps",
            onClick = onFindApps,
            icon = Icons.Outlined.Explore,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (pendingUnconfirmedQuery != null) {
        val unconfirmed = pendingUnconfirmedQuery!!
        AlertDialog(
            onDismissRequest = { pendingUnconfirmedQuery = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "App Name Entered",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "\"$unconfirmed\" was not selected from the list and is not a package name (e.g. com.example.app).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "APK repositories require an exact package name to locate downloads. Searching by app name directly may fail to find APKs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (pendingSuggestions.isNotEmpty()) {
                        Text(
                            text = "Did you mean:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            pendingSuggestions.forEach { app ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val pkg = app.packageName
                                            pendingUnconfirmedQuery = null
                                            searchQuery = pkg
                                            onSearch(pkg, selectedSource)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AppAvatar(
                                            packageName = app.packageName,
                                            initial = app.name.firstOrNull()?.uppercaseChar() ?: '?',
                                            iconUrl = app.iconUrl,
                                            size = 32.dp,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                HelperButton(
                    text = "Proceed to search",
                    onClick = {
                        val raw = unconfirmed
                        pendingUnconfirmedQuery = null
                        onSearch(raw, selectedSource)
                    }
                )
            },
            dismissButton = {
                HelperOutlinedButton(
                    text = "Correct info",
                    onClick = {
                        pendingUnconfirmedQuery = null
                    }
                )
            }
        )
    }
}
