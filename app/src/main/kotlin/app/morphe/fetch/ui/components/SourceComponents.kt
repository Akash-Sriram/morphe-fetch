package app.morphe.fetch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun SourcePickerFlow(
    request: HelperRequest,
    result: CandidateResult,
    selectedPagerPage: Int,
    onPagerPageChanged: (Int) -> Unit,
    onResolve: (DownloadSource, CandidateOption) -> Unit,
    onDownload: (DownloadCandidate) -> Unit,
    onPickDownloadedFile: (DownloadCandidate) -> Unit,
    onUseInstalledApp: (DownloadCandidate) -> Unit,
    onSolveCaptcha: (DownloadCandidate) -> Unit,
    onVersionHistory: (DownloadSource) -> Unit,
    onDownloadVersion: (DownloadCandidate) -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    installedPackageRefreshToken: Int,
    onPrimaryActionChanged: (PrimaryAction?) -> Unit
) {
    val groups = result.sourceGroups

    if (groups.isEmpty()) {
        SideEffect { onPrimaryActionChanged(null) }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            InfoCard(
                "All sources are disabled. Enable at least one source in " +
                    "Settings → Sources to resolve or download."
            )
        }
        return
    }

    val maxPage = groups.size
    val initialPage = selectedPagerPage.coerceIn(0, maxPage)
    var currentPage by rememberSaveable { mutableIntStateOf(initialPage) }
    androidx.compose.runtime.LaunchedEffect(selectedPagerPage) {
        currentPage = selectedPagerPage.coerceIn(0, maxPage)
    }
    SideEffect { onPagerPageChanged(currentPage) }

    val currentGroup = if (currentPage > 0) groups[(currentPage - 1).coerceIn(0, groups.lastIndex)] else null

    val openCandidateLink: (DownloadCandidate) -> Unit = onSolveCaptcha

    val action = remember(currentGroup, groups, request, currentPage) {
        if (currentGroup != null) {
            buildPrimaryAction(
                request = request,
                group = currentGroup,
                onResolve = onResolve,
                onDownload = onDownload,
                onVersionHistory = onVersionHistory,
                openLink = openCandidateLink
            )
        } else {
            buildAllSourcesPrimaryAction(
                groups = groups,
                onResolveAll = {
                    groups.forEach { group ->
                        val targetOption = if (group.source.supportsRecommended && request.hasRequestedVersionRequest) {
                            CandidateOption.REQUESTED
                        } else {
                            CandidateOption.LATEST
                        }
                        onResolve(group.source, targetOption)
                    }
                }
            )
        }
    }
    SideEffect { onPrimaryActionChanged(action) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Horizontal compact source chips: All Sources + individual sources
        SourceChipRow(
            groups = groups,
            selectedIndex = currentPage,
            onSelect = { currentPage = it }
        )

        // Focused content
        if (currentPage == 0) {
            AllSourcesPageContent(
                request = request,
                groups = groups,
                onResolve = onResolve,
                onDownload = onDownload,
                onPickDownloadedFile = onPickDownloadedFile,
                onUseInstalledApp = onUseInstalledApp,
                onSolveCaptcha = onSolveCaptcha,
                onVersionHistory = onVersionHistory,
                onDownloadVersion = onDownloadVersion,
                installedPackageRefreshToken = installedPackageRefreshToken
            )
        } else {
            currentGroup?.let { group ->
                SourcePageContent(
                    request = request,
                    group = group,
                    onResolve = onResolve,
                    onDownload = onDownload,
                    onPickDownloadedFile = onPickDownloadedFile,
                    onUseInstalledApp = onUseInstalledApp,
                    onSolveCaptcha = onSolveCaptcha,
                    onVersionHistory = onVersionHistory,
                    onDownloadVersion = onDownloadVersion,
                    installedPackageRefreshToken = installedPackageRefreshToken
                )
            }
        }
    }
}

@Composable
internal fun SourceChipRow(
    groups: List<SourceCandidateGroup>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tab 0: All Sources
        item(key = "all_sources") {
            val selected = selectedIndex == 0
            val anyHasCandidate = groups.any { group ->
                ((group.latest as? ResolveState.Done)?.candidates?.isNotEmpty() == true ||
                    (group.recommended as? ResolveState.Done)?.candidates?.isNotEmpty() == true)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
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
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(0) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "All Sources",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (anyHasCandidate) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Available",
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Tabs 1..N: Individual Sources
        items(groups.size, key = { groups[it].source.name }) { index ->
            val group = groups[index]
            val tabIndex = index + 1
            val selected = tabIndex == selectedIndex
            val hasCandidate = ((group.latest as? ResolveState.Done)?.candidates?.isNotEmpty() == true ||
                (group.recommended as? ResolveState.Done)?.candidates?.isNotEmpty() == true)

            Surface(
                shape = RoundedCornerShape(12.dp),
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
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(tabIndex) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SourceAvatar(source = group.source, size = 20.dp)
                    Text(
                        text = group.source.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (hasCandidate) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Available",
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun buildAllSourcesPrimaryAction(
    groups: List<SourceCandidateGroup>,
    onResolveAll: () -> Unit
): PrimaryAction? {
    val anyLoading = groups.any { it.latest is ResolveState.Loading || it.recommended is ResolveState.Loading }
    if (anyLoading) {
        return PrimaryAction(
            label = "Checking sources…",
            icon = Icons.Outlined.Search,
            enabled = false,
            loading = true,
            run = {}
        )
    }
    val allIdle = groups.all { it.latest is ResolveState.Idle && it.recommended is ResolveState.Idle }
    if (allIdle) {
        return PrimaryAction(
            label = "Check All Sources",
            icon = Icons.Outlined.Search,
            enabled = true,
            loading = false,
            run = onResolveAll
        )
    }
    return null
}

internal fun List<SourceCandidateGroup>.sortedForDisplay(request: HelperRequest): List<SourceCandidateGroup> {
    return sortedWith(
        compareBy<SourceCandidateGroup> { it.sortPriority(request) }
            .thenBy { it.source.sortIndex }
    )
}

internal fun SourceCandidateGroup.sortPriority(request: HelperRequest): Int {
    val targetOption = if (source.supportsRecommended && request.hasRequestedVersionRequest) {
        CandidateOption.REQUESTED
    } else {
        CandidateOption.LATEST
    }
    val primaryState = if (targetOption == CandidateOption.REQUESTED) recommended else latest
    val primaryCandidates = (primaryState as? ResolveState.Done)?.candidates.orEmpty()
    val otherCandidates = (latest as? ResolveState.Done)?.candidates.orEmpty()

    return when {
        // Provider has actual direct download APK for requested target
        primaryCandidates.any { it.directDownload } -> 0
        // Provider has candidates for requested target
        primaryCandidates.isNotEmpty() -> 1
        // Fallback: provider has direct APK for latest version
        targetOption == CandidateOption.REQUESTED && otherCandidates.any { it.directDownload } -> 2
        // Fallback: provider has any candidates for latest version
        targetOption == CandidateOption.REQUESTED && otherCandidates.isNotEmpty() -> 3
        // Provider is currently searching/loading
        primaryState is ResolveState.Loading || (targetOption == CandidateOption.REQUESTED && latest is ResolveState.Loading) -> 4
        // Provider is idle
        primaryState is ResolveState.Idle -> 5
        // No APKs found or error
        else -> 6
    }
}

@Composable
internal fun AllSourcesPageContent(
    request: HelperRequest,
    groups: List<SourceCandidateGroup>,
    onResolve: (DownloadSource, CandidateOption) -> Unit,
    onDownload: (DownloadCandidate) -> Unit,
    onPickDownloadedFile: (DownloadCandidate) -> Unit,
    onUseInstalledApp: (DownloadCandidate) -> Unit,
    onSolveCaptcha: (DownloadCandidate) -> Unit,
    onVersionHistory: (DownloadSource) -> Unit,
    onDownloadVersion: (DownloadCandidate) -> Unit,
    installedPackageRefreshToken: Int
) {
    val sortedGroups = remember(groups, request.hasRequestedVersionRequest) {
        groups.sortedForDisplay(request)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        sortedGroups.forEach { group ->
            key(group.source) {
                SourceSectionCard(
                    request = request,
                    group = group,
                    onResolve = onResolve,
                    onDownload = onDownload,
                    onPickDownloadedFile = onPickDownloadedFile,
                    onUseInstalledApp = onUseInstalledApp,
                    onSolveCaptcha = onSolveCaptcha,
                    onVersionHistory = onVersionHistory,
                    onDownloadVersion = onDownloadVersion,
                    installedPackageRefreshToken = installedPackageRefreshToken
                )
            }
        }
    }
}

@Composable
internal fun SourceSectionCard(
    request: HelperRequest,
    group: SourceCandidateGroup,
    onResolve: (DownloadSource, CandidateOption) -> Unit,
    onDownload: (DownloadCandidate) -> Unit,
    onPickDownloadedFile: (DownloadCandidate) -> Unit,
    onUseInstalledApp: (DownloadCandidate) -> Unit,
    onSolveCaptcha: (DownloadCandidate) -> Unit,
    onVersionHistory: (DownloadSource) -> Unit,
    onDownloadVersion: (DownloadCandidate) -> Unit,
    installedPackageRefreshToken: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            SourceAvatar(source = group.source, size = 20.dp)
            Text(
                text = group.source.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        SourcePageContent(
            request = request,
            group = group,
            onResolve = onResolve,
            onDownload = onDownload,
            onPickDownloadedFile = onPickDownloadedFile,
            onUseInstalledApp = onUseInstalledApp,
            onSolveCaptcha = onSolveCaptcha,
            onVersionHistory = onVersionHistory,
            onDownloadVersion = onDownloadVersion,
            installedPackageRefreshToken = installedPackageRefreshToken
        )
    }
}

internal fun buildPrimaryAction(
    request: HelperRequest,
    group: SourceCandidateGroup,
    onResolve: (DownloadSource, CandidateOption) -> Unit,
    onDownload: (DownloadCandidate) -> Unit,
    onVersionHistory: (DownloadSource) -> Unit,
    openLink: (DownloadCandidate) -> Unit
): PrimaryAction? {
    val targetOption = if (group.source.supportsRecommended && request.hasRequestedVersionRequest) {
        CandidateOption.REQUESTED
    } else {
        CandidateOption.LATEST
    }
    val state = if (targetOption == CandidateOption.REQUESTED) group.recommended else group.latest
    return when (state) {
        ResolveState.Loading -> PrimaryAction(
            label = "Checking ${group.source.label}…",
            icon = Icons.Outlined.Search,
            enabled = false,
            loading = true,
            run = {}
        )
        is ResolveState.Done -> {
            if (state.candidates.isNotEmpty()) {
                // When candidates are already resolved, each card has its own dedicated
                // download button tailored to its specific architecture/variant.
                // Suppress redundant bottom download button to prevent duplicate actions
                // and avoid blindly picking the wrong architecture variant.
                null
            } else {
                PrimaryAction(
                    label = "Search ${group.source.label}",
                    icon = Icons.Outlined.Search,
                    run = { onResolve(group.source, targetOption) }
                )
            }
        }
        else -> PrimaryAction(
            label = "Search ${group.source.label}",
            icon = Icons.Outlined.Search,
            run = { onResolve(group.source, targetOption) }
        )
    }
}

@Composable
internal fun SourcePageContent(
    request: HelperRequest,
    group: SourceCandidateGroup,
    onResolve: (DownloadSource, CandidateOption) -> Unit,
    onDownload: (DownloadCandidate) -> Unit,
    onPickDownloadedFile: (DownloadCandidate) -> Unit,
    onUseInstalledApp: (DownloadCandidate) -> Unit,
    onSolveCaptcha: (DownloadCandidate) -> Unit,
    onVersionHistory: (DownloadSource) -> Unit,
    onDownloadVersion: (DownloadCandidate) -> Unit,
    installedPackageRefreshToken: Int
) {
    var showHistory by rememberSaveable(group.source) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val targetOption = if (group.source.supportsRecommended && request.hasRequestedVersionRequest) {
            CandidateOption.REQUESTED
        } else {
            CandidateOption.LATEST
        }
        val candidateState = if (targetOption == CandidateOption.REQUESTED) {
            group.recommended
        } else {
            group.latest
        }

        CandidateResolveSection(
            request = request,
            group = group,
            state = candidateState,
            emptyText = if (request.hasRequestedVersionRequest) {
                "Requested version not found on ${group.source.label}."
            } else {
                "No APKs found on ${group.source.label}."
            },
            onSearch = { onResolve(group.source, targetOption) },
            onDownload = onDownload,
            onPickDownloadedFile = onPickDownloadedFile,
            onUseInstalledApp = onUseInstalledApp,
            onSolveCaptcha = onSolveCaptcha,
            installedPackageRefreshToken = installedPackageRefreshToken
        )

        // Secondary controls: older versions toggle + external site button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (group.source.supportsHistory) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showHistory = !showHistory
                            if (showHistory && group.history is VersionHistoryState.Idle) {
                                onVersionHistory(group.source)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showHistory) Icons.Outlined.ExpandLess else Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (showHistory) "Hide versions" else "Versions",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            val manual = group.manual.firstOrNull()
            if (manual != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSolveCaptcha(manual) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInBrowser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Open site",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showHistory) {
            VersionHistorySection(
                state = group.history,
                onDownloadVersion = onDownloadVersion,
                onSolveCaptcha = onSolveCaptcha
            )
        }
    }
}

@Composable
internal fun SourceBottomBar(
    action: PrimaryAction,
    onRefresh: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        MorpheDivider(fullWidth = true)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MorpheDefaults.ContentPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HelperIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = "Refresh",
                onClick = onRefresh
            )
            HelperIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "Cancel",
                onClick = onCancel
            )
            val primaryInteractionSource = remember { MutableInteractionSource() }
            Button(
                onClick = action.run,
                enabled = action.enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(MorpheDefaults.DialogButtonHeight)
                    .pressScale(primaryInteractionSource, enabled = action.enabled),
                interactionSource = primaryInteractionSource,
                shape = RoundedCornerShape(MorpheDefaults.CardCornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = MorpheDefaults.ContentPadding)
            ) {
                if (action.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(MorpheDefaults.IconSizeSmall)
                    )
                }
                Spacer(Modifier.width(MorpheDefaults.ContentPaddingSmall))
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun VersionHistorySection(
    state: VersionHistoryState,
    onDownloadVersion: (DownloadCandidate) -> Unit,
    onSolveCaptcha: (DownloadCandidate) -> Unit
) {
    when (state) {
        VersionHistoryState.Idle,
        VersionHistoryState.Loading -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        is VersionHistoryState.Error -> {
            InfoCard(state.message)
        }

        is VersionHistoryState.Done -> {
            if (state.candidates.isEmpty()) {
                InfoCard("No version list was available for this source.")
            } else {
                state.candidates.forEach { candidate ->
                    VersionHistoryRow(
                        candidate = candidate,
                        showOpenLink = candidate.identityKey() in state.noDirectDownloadKeys,
                        onDownloadVersion = { onDownloadVersion(candidate) },
                        onSolveCaptcha = { onSolveCaptcha(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun VersionHistoryRow(
    candidate: DownloadCandidate,
    showOpenLink: Boolean,
    onDownloadVersion: () -> Unit,
    onSolveCaptcha: (DownloadCandidate) -> Unit
) {
    val context = LocalContext.current
    SurfaceCard(cornerRadius = MorpheDefaults.CompactCornerRadius) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = candidate.versionDisplay,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!candidate.releaseSuffix.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (candidate.releaseSuffix.equals("secondary", ignoreCase = true)) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(
                                text = candidate.releaseSuffix,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (candidate.releaseSuffix.equals("secondary", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                val statusText = when {
                    candidate.captchaUrl != null && !candidate.directDownload ->
                        "Captcha required in app"
                    showOpenLink -> "Website only"
                    candidate.fileKind.equals("web", ignoreCase = true) ->
                        "Direct download"
                    else -> candidate.fileKind.uppercase(Locale.US)
                }
                val subtitle = if (!candidate.releaseDate.isNullOrBlank()) {
                    "$statusText • ${candidate.releaseDate}"
                } else {
                    statusText
                }
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when {
                showOpenLink || (candidate.captchaUrl != null && !candidate.directDownload) -> {
                    HelperButton(
                        text = "Captcha",
                        onClick = { onSolveCaptcha(candidate) },
                        icon = Icons.Outlined.VerifiedUser,
                        modifier = Modifier.widthIn(min = 100.dp)
                    )
                }
                showOpenLink -> {
                    HelperOutlinedButton(
                        text = "Open",
                        onClick = { onSolveCaptcha(candidate) },
                        icon = Icons.Outlined.OpenInBrowser,
                        modifier = Modifier.widthIn(min = 90.dp)
                    )
                }
                else -> {
                    HelperButton(
                        text = "Get",
                        onClick = onDownloadVersion,
                        icon = Icons.Outlined.Download,
                        modifier = Modifier.widthIn(min = 90.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CandidateResolveSection(
    request: HelperRequest,
    group: SourceCandidateGroup,
    state: ResolveState,
    emptyText: String,
    onSearch: () -> Unit,
    onDownload: (DownloadCandidate) -> Unit,
    onPickDownloadedFile: (DownloadCandidate) -> Unit,
    onUseInstalledApp: (DownloadCandidate) -> Unit,
    onSolveCaptcha: (DownloadCandidate) -> Unit,
    installedPackageRefreshToken: Int
) {
    when (state) {
        ResolveState.Idle -> {
            SurfaceCard(
                cornerRadius = 14.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSearch
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SourceAvatar(source = group.source, size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.source.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (request.hasRequestedVersionRequest) {
                                "Find version ${request.requestedVersionName ?: ""}"
                            } else {
                                "Find latest APK release"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        ResolveState.Loading -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Checking ${group.source.label}…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is ResolveState.Done -> {
            if (state.candidates.isEmpty()) {
                InfoCard(emptyText)
            } else {
                state.candidates.forEach { candidate ->
                    CandidateCard(
                        request = request,
                        candidate = candidate,
                        onDownload = { onDownload(candidate) },
                        onPickDownloadedFile = { onPickDownloadedFile(candidate) },
                        onUseInstalledApp = { onUseInstalledApp(candidate) },
                        onSolveCaptcha = { onSolveCaptcha(candidate) },
                        installedPackageRefreshToken = installedPackageRefreshToken
                    )
                }
            }
        }

        is ResolveState.Error -> {
            InfoCard(state.message)
            state.fallbackCandidate?.let { candidate ->
                CandidateCard(
                    request = request,
                    candidate = candidate,
                    onDownload = { onDownload(candidate) },
                    onPickDownloadedFile = { onPickDownloadedFile(candidate) },
                    onUseInstalledApp = { onUseInstalledApp(candidate) },
                    onSolveCaptcha = { onSolveCaptcha(candidate) },
                    installedPackageRefreshToken = installedPackageRefreshToken
                )
            }
        }
    }
}
