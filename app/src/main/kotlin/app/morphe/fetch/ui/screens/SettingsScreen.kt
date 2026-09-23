package app.morphe.fetch

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import app.morphe.fetch.BuildConfig
import app.morphe.fetch.updater.UpdateInfo
import app.morphe.fetch.updater.UpdateState
import java.io.File
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun HelperSettingsScreen(
    settings: HelperSettings,
    onSettingsChange: (HelperSettings) -> Unit,
    logs: List<RequestLogEntry>,
    onClearLogs: () -> Unit,
    historyEntries: List<DownloadHistoryEntry>,
    onOpenHistoryEntry: (DownloadHistoryEntry) -> Unit,
    onShareHistoryEntry: (DownloadHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    updateState: UpdateState = UpdateState.Idle,
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: (UpdateInfo) -> Unit = {},
    onInstallUpdate: (File) -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onBack: () -> Unit
) {
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val context = LocalContext.current
    var cacheBytes by remember(context) { mutableStateOf(context.temporaryDownloadsSize()) }
    var downloadsBytes by remember(context) { mutableStateOf(context.downloadsCopySize()) }
    var locationDialog by remember { mutableStateOf(false) }
    var policyDialog by remember { mutableStateOf(false) }
    var sourcesDialog by remember { mutableStateOf(false) }
    var historyDialog by remember { mutableStateOf(false) }
    var logsDialog by remember { mutableStateOf(false) }
    val locations = DownloadLocation.entries
    val policies = NetworkPolicy.entries
    val enabledSources = remember(settings.disabledSources) {
        DownloadSource.entries.filter { it !in settings.disabledSources }
    }

    if (sourcesDialog) {
        AlertDialog(
            onDismissRequest = { sourcesDialog = false },
            confirmButton = {
                HelperButton(text = "Done", onClick = { sourcesDialog = false })
            },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Download Sources", fontWeight = FontWeight.Bold)
                    Text(
                        text = "${enabledSources.size} of ${DownloadSource.entries.size} active • Tap to toggle or customize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.clickable {
                                onSettingsChange(
                                    settings.copy(
                                        disabledSources = emptySet()
                                    )
                                )
                            }
                        ) {
                            Text(
                                text = "Enable All",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (settings.preferredSource == null) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.clickable {
                                onSettingsChange(settings.copy(preferredSource = null))
                            }
                        ) {
                            Text(
                                text = if (settings.preferredSource == null) "Default: Auto (First)" else "Reset to Auto",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (settings.preferredSource == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Source list
                    DownloadSource.entries.forEach { source ->
                        val isEnabled = source !in settings.disabledSources
                        val isPreferred = settings.preferredSource == source

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isPreferred -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                            },
                            border = if (isPreferred) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isEnabled) {
                                        val enabledCount = DownloadSource.entries.count { it !in settings.disabledSources }
                                        if (enabledCount > 1) {
                                            onSettingsChange(
                                                settings.copy(
                                                    disabledSources = settings.disabledSources + source,
                                                    preferredSource = if (isPreferred) null else settings.preferredSource
                                                )
                                            )
                                        }
                                    } else {
                                        onSettingsChange(
                                            settings.copy(
                                                disabledSources = settings.disabledSources - source
                                            )
                                        )
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SourceAvatar(source = source, size = 30.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isEnabled) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isPreferred) {
                                            Text(
                                                text = "★ Default Preferred",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else if (isEnabled) {
                                            Text(
                                                text = "Set Default",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.clickable {
                                                    onSettingsChange(settings.copy(preferredSource = source))
                                                }
                                            )
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = "Only this",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.clickable {
                                                    val allOthers = DownloadSource.entries.filter { it != source }.toSet()
                                                    onSettingsChange(
                                                        settings.copy(
                                                            disabledSources = allOthers,
                                                            preferredSource = source
                                                        )
                                                    )
                                                }
                                            )
                                        } else {
                                            Text(
                                                text = "Disabled",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        if (!checked) {
                                            val enabledCount = DownloadSource.entries.count { it !in settings.disabledSources }
                                            if (enabledCount > 1) {
                                                onSettingsChange(
                                                    settings.copy(
                                                        disabledSources = settings.disabledSources + source,
                                                        preferredSource = if (isPreferred) null else settings.preferredSource
                                                    )
                                                )
                                            }
                                        } else {
                                            onSettingsChange(
                                                settings.copy(
                                                    disabledSources = settings.disabledSources - source
                                                )
                                            )
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    Text(
                        text = "At least one source must remain active.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        )
    }

    if (historyDialog) {
        AlertDialog(
            onDismissRequest = { historyDialog = false },
            confirmButton = {
                HelperButton(text = "Close", onClick = { historyDialog = false })
            },
            title = {
                Text("Download History", fontWeight = FontWeight.Bold)
            },
            text = {
                Box(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    DownloadHistorySection(
                        entries = historyEntries,
                        onClear = onClearHistory,
                        onOpen = onOpenHistoryEntry,
                        onShare = onShareHistoryEntry
                    )
                }
            }
        )
    }

    if (logsDialog) {
        AlertDialog(
            onDismissRequest = { logsDialog = false },
            confirmButton = {
                HelperButton(text = "Close", onClick = { logsDialog = false })
            },
            title = {
                Text("Activity Logs", fontWeight = FontWeight.Bold)
            },
            text = {
                Box(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    RequestLogsCard(
                        logs = logs,
                        onClearLogs = onClearLogs
                    )
                }
            }
        )
    }

    if (locationDialog) {
        SettingsChoiceDialog(
            title = "Save downloads",
            choices = locations.map { SettingsChoice(it.icon(), it.title, it.description) },
            selectedIndex = locations.indexOf(settings.downloadLocation),
            onSelect = { index ->
                onSettingsChange(settings.copy(downloadLocation = locations[index]))
                locationDialog = false
            },
            onDismiss = { locationDialog = false }
        )
    }

    if (policyDialog) {
        SettingsChoiceDialog(
            title = "Connection",
            choices = policies.map { SettingsChoice(it.icon(), it.title, it.description) },
            selectedIndex = policies.indexOf(settings.networkPolicy),
            onSelect = { index ->
                onSettingsChange(settings.copy(networkPolicy = policies[index]))
                policyDialog = false
            },
            onDismiss = { policyDialog = false }
        )
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        if (updateState is UpdateState.Idle) {
            onCheckForUpdates()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ContentPadding)
            .pointerInput(swipeThresholdPx) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (totalDrag >= swipeThresholdPx) onBack()
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            },
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
    ) {
        // Top Header: Title and Close button (NO BACK ARROW)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            HelperHeaderIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "Close",
                onClick = onBack
            )
        }

        // STYLE 1: Top 2x2 Primary Operational Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile 1: Download Sources
            PrimaryMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Dns,
                title = "Download Sources",
                status = "${enabledSources.size} of ${DownloadSource.entries.size} Active",
                badge = settings.preferredSource?.label ?: "Auto",
                onClick = { sourcesDialog = true }
            )

            // Tile 2: Cache Storage with Clean Action
            CacheMetricTile(
                modifier = Modifier.weight(1f),
                cacheBytes = cacheBytes + downloadsBytes,
                onClean = {
                    context.clearTemporaryDownloads()
                    context.clearDownloadsCopies()
                    cacheBytes = 0L
                    downloadsBytes = 0L
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile 3: Download History
            PrimaryMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.History,
                title = "Download History",
                status = "${historyEntries.size} APKs",
                badge = "View",
                onClick = { historyDialog = true }
            )

            // Tile 4: Activity Logs
            PrimaryMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.BugReport,
                title = "Activity Logs",
                status = "${logs.size} Events",
                badge = "View",
                onClick = { logsDialog = true }
            )
        }

        // STYLE 2: Bottom 2x2 Control Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile 5: Save Location
            ControlClickableTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.FolderOpen,
                title = "Save Location",
                subtitle = if (settings.downloadLocation == DownloadLocation.DOWNLOADS) "Downloads" else "App Cache",
                onClick = { locationDialog = true }
            )

            // Tile 6: Network Policy
            ControlClickableTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Wifi,
                title = "Network Policy",
                subtitle = if (settings.networkPolicy == NetworkPolicy.WIFI_AND_MOBILE) "Wi-Fi & Data" else "Unmetered",
                onClick = { policyDialog = true }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile 7: Auto-download Best Match with direct Switch
            ControlSwitchTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.AutoAwesome,
                title = "Auto-fetch",
                subtitle = "Morphe zero-click",
                checked = settings.autoDownloadBestMatch,
                onCheckedChange = {
                    onSettingsChange(settings.copy(autoDownloadBestMatch = it))
                }
            )

            // Tile 8: Auto-clear Handoff with direct Switch
            ControlSwitchTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.CleaningServices,
                title = "Auto-clear",
                subtitle = "Purge temp APKs",
                checked = settings.deleteTemporaryAfterHandoff,
                onCheckedChange = {
                    onSettingsChange(settings.copy(deleteTemporaryAfterHandoff = it))
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tile 9: Log to Logcat with direct Switch
            ControlSwitchTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Bolt,
                title = "Log to Logcat",
                subtitle = "Stream to ADB",
                checked = settings.logcatLogging,
                onCheckedChange = {
                    onSettingsChange(settings.copy(logcatLogging = it))
                }
            )

            // Tile 10: Check Updates Tile
            ControlClickableTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Download,
                title = "Check Updates",
                subtitle = when (updateState) {
                    is UpdateState.Checking -> "Checking..."
                    is UpdateState.Available -> "${updateState.info.tagName} Available!"
                    is UpdateState.UpToDate -> "Latest (v${BuildConfig.VERSION_NAME})"
                    is UpdateState.Downloading -> "Downloading..."
                    is UpdateState.ReadyToInstall -> "Ready to install"
                    is UpdateState.Error -> "Check failed"
                    is UpdateState.Idle -> "v${BuildConfig.VERSION_NAME}"
                },
                onClick = onCheckForUpdates
            )
        }

        // Bottom Inline 3-Theme Selector
        Surface(
            shape = RoundedCornerShape(MorpheDefaults.CompactCornerRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val themeOptions = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                themeOptions.forEach { mode ->
                    val isSelected = settings.themeMode == mode
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
                        onClick = { onSettingsChange(settings.copy(themeMode = mode)) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = mode.icon(),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Updates Card
        UpdatesCard(
            updateState = updateState,
            onCheckForUpdates = onCheckForUpdates,
            onDownloadUpdate = onDownloadUpdate,
            onInstallUpdate = onInstallUpdate,
            onDismissUpdate = onDismissUpdate
        )
    }
}

@Composable
private fun UpdatesCard(
    updateState: UpdateState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (UpdateInfo) -> Unit,
    onInstallUpdate: (File) -> Unit,
    onDismissUpdate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(MorpheDefaults.CompactCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "App Updates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            when (updateState) {
                is UpdateState.Idle -> {
                    Text(
                        text = "Check GitHub for the latest releases, features, and fixes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HelperButton(
                        text = "Check for updates",
                        onClick = onCheckForUpdates,
                        icon = Icons.Outlined.Refresh,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is UpdateState.Checking -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Checking GitHub releases...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is UpdateState.UpToDate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Morphe Fetch is up to date",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "Check again",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onCheckForUpdates() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                is UpdateState.Available -> {
                    val info = updateState.info
                    val sizeMb = "%.1f".format(Locale.US, info.apkSize / (1024f * 1024f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "New Version Available",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${info.tagName} • $sizeMb MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!info.changelog.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = info.changelog.take(350).trim() + if (info.changelog.length > 350) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    HelperButton(
                        text = "Download & Install (${info.tagName})",
                        onClick = { onDownloadUpdate(info) },
                        icon = Icons.Outlined.Download,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is UpdateState.Downloading -> {
                    val progress = updateState.progress
                    val copiedMb = "%.1f".format(Locale.US, updateState.downloadedBytes / (1024f * 1024f))
                    val totalMb = "%.1f".format(Locale.US, updateState.totalBytes / (1024f * 1024f))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading ${updateState.info.tagName}...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "$copiedMb / $totalMb MB (${(progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${updateState.info.tagName} ready to install",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        HelperButton(
                            text = "Install Now",
                            onClick = { onInstallUpdate(updateState.apkFile) },
                            icon = Icons.Outlined.Download,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is UpdateState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Update error: ${updateState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HelperButton(
                                text = "Retry",
                                onClick = onCheckForUpdates,
                                icon = Icons.Outlined.Refresh,
                                modifier = Modifier.weight(1f)
                            )
                            HelperOutlinedButton(
                                text = "Dismiss",
                                onClick = onDismissUpdate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

