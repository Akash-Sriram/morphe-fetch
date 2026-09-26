package app.morphe.fetch

import android.content.Intent
import android.net.Uri
import app.morphe.fetch.updater.UpdateInfo
import app.morphe.fetch.updater.UpdateState
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val APK_PICKER_MIME_TYPES = arrayOf(
    "application/vnd.android.package-archive",
    "application/zip",
    "application/octet-stream",
    "*/*"
)

@Composable
internal fun HelperScreen(
    request: HelperRequest?,
    state: UiState,
    settings: HelperSettings,
    logs: List<RequestLogEntry>,
    installedPackageRefreshToken: Int,
    selectedPagerPage: Int,
    onPagerPageChanged: (Int) -> Unit,
    onSettingsChange: (HelperSettings) -> Unit,
    onRefresh: () -> Unit,
    onResolve: (DownloadSource, CandidateOption) -> Unit,
    onDownload: (DownloadCandidate) -> Unit,
    onPickDownloadedFile: (DownloadCandidate, Uri?) -> Unit,
    onUseInstalledApp: (DownloadCandidate) -> Unit,
    onVersionHistory: (DownloadSource) -> Unit,
    onDownloadVersion: (DownloadCandidate) -> Unit,
    onOpenHistoryEntry: (DownloadHistoryEntry) -> Unit,
    onShareHistoryEntry: (DownloadHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onClearLogs: () -> Unit,
    onCancel: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenMorphe: () -> Unit,
    onSendApkToMorphe: (DownloadHistoryEntry) -> Unit = {},
    onSolveCaptcha: (DownloadCandidate) -> Unit,
    onRequestFileTypeChange: (String) -> Unit,
    onSearchPackage: (String, DownloadSource?) -> Unit,
    onClearRequest: () -> Unit,
    onDeliverPendingResult: ((PendingDownloadResult) -> Unit)? = null,
    updateState: UpdateState = UpdateState.Idle,
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: (UpdateInfo) -> Unit = {},
    onInstallUpdate: (File) -> Unit = {},
    onDismissUpdate: () -> Unit = {}
) {
    var showSettings by remember { mutableStateOf(false) }
    var showAppBrowser by remember { mutableStateOf(false) }
    var pendingFilePick by remember { mutableStateOf<DownloadCandidate?>(null) }
    var primaryAction by remember { mutableStateOf<PrimaryAction?>(null) }
    val enabledSources = remember(settings.disabledSources) {
        DownloadSource.entries.filter { it !in settings.disabledSources }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var historyEntries by remember { mutableStateOf<List<DownloadHistoryEntry>>(emptyList()) }
    val refreshHistory: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val entries = DownloadHistoryStore.entries(context)
            withContext(Dispatchers.Main) {
                historyEntries = entries
            }
        }
    }
    LaunchedEffect(Unit) {
        refreshHistory()
        DownloadHistoryStore.updates.collect {
            refreshHistory()
        }
    }
    LaunchedEffect(request, state) {
        if (request == null || state is UiState.Completed) {
            refreshHistory()
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val candidate = pendingFilePick
        pendingFilePick = null
        candidate?.let { onPickDownloadedFile(it, uri) }
    }
    val openDownloadedFilePicker: (DownloadCandidate) -> Unit = { candidate ->
        pendingFilePick = candidate
        filePickerLauncher.launch(APK_PICKER_MIME_TYPES)
    }
    BackHandler(enabled = showSettings || showAppBrowser || request != null || state is UiState.Completed) {
        when {
            showSettings -> showSettings = false
            showAppBrowser -> showAppBrowser = false
            state is UiState.Completed || request != null -> {
                onClearRequest()
                refreshHistory()
            }
        }
    }

    val handleCancel: () -> Unit = {
        if (request != null) {
            onClearRequest()
            refreshHistory()
        } else {
            onCancel()
        }
    }

    val flowVisible = request != null && state is UiState.Ready
    LaunchedEffect(flowVisible) {
        if (!flowVisible) primaryAction = null
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val isExpanded = isExpandedScreen()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (primaryAction == null) Modifier.navigationBarsPadding() else Modifier)
            ) {
                if (isExpanded) {
                    // Tablet / Expanded Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = MorpheDefaults.ContentPadding, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Morphe Fetch",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        HelperHeaderIconButton(
                            icon = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            onClick = {
                                refreshHistory()
                                showSettings = true
                            }
                        )
                    }

                    if (request == null) {
                        // Tablet Idle: Left = Search / Find Apps, Right = Downloaded APKs
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = MorpheDefaults.ContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(0.42f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                            ) {
                                EmptyLaunchState(
                                    onSearch = onSearchPackage,
                                    onOpenMorphe = onOpenMorphe,
                                    onFindApps = { showAppBrowser = true },
                                    enabledSources = enabledSources,
                                    isOverlayOpen = showSettings || showAppBrowser
                                )
                            }

                            MorpheVerticalDivider(
                                modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 340.dp),
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight(),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    MorpheSectionTitle(
                                        text = "Downloaded APKs (${historyEntries.size})",
                                        icon = Icons.Outlined.History
                                    )
                                }
                                if (historyEntries.isNotEmpty()) {
                                    items(historyEntries, key = { "${it.fileName}_${it.timestamp}" }) { entry ->
                                        CachedAppCard(
                                            entry = entry,
                                            onInstall = {
                                                runCatching {
                                                    val uri = Uri.parse(entry.uri)
                                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, fileNameMimeType(entry.fileName))
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(installIntent)
                                                }
                                            },
                                            onSendToMorphe = {
                                                onSendApkToMorphe(entry)
                                            },
                                            onShare = {
                                                runCatching {
                                                    val uri = Uri.parse(entry.uri)
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = fileNameMimeType(entry.fileName)
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share APK"))
                                                }
                                            },
                                            onSearchAgain = { onSearchPackage(entry.packageName, null) },
                                            onRemove = {
                                                scope.launch(Dispatchers.IO) {
                                                    DownloadHistoryStore.remove(context, entry)
                                                    refreshHistory()
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        SurfaceCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            cornerRadius = 16.dp
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(28.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.size(52.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.History,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(26.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "No downloaded APKs yet",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "APKs and split bundles you download will appear here with quick actions to send directly to Morphe Manager, install, or share.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Tablet Active: Left = App Info, Right = Candidates / Progress
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = MorpheDefaults.ContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(0.38f)
                                    .fillMaxHeight(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                            ) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MorpheSectionTitle(text = "App info", icon = Icons.Outlined.Smartphone)
                                        AppInfoCard(
                                            request = request,
                                            onFormatSelected = onRequestFileTypeChange,
                                            onClearRequest = onClearRequest
                                        )
                                    }
                                }
                            }

                            MorpheVerticalDivider(
                                modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .weight(0.62f)
                                    .fillMaxHeight(),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                            ) {
                                when (state) {
                                    UiState.Idle,
                                    UiState.Loading -> item { LoadingState() }
                                    is UiState.Ready -> {
                                        item {
                                            SourcePickerFlow(
                                                request = request,
                                                result = state.result,
                                                selectedPagerPage = selectedPagerPage,
                                                onPagerPageChanged = onPagerPageChanged,
                                                onResolve = onResolve,
                                                onDownload = onDownload,
                                                onPickDownloadedFile = openDownloadedFilePicker,
                                                onUseInstalledApp = onUseInstalledApp,
                                                onSolveCaptcha = onSolveCaptcha,
                                                onVersionHistory = onVersionHistory,
                                                onDownloadVersion = onDownloadVersion,
                                                onRefresh = onRefresh,
                                                onCancel = handleCancel,
                                                installedPackageRefreshToken = installedPackageRefreshToken,
                                                onPrimaryActionChanged = { primaryAction = it }
                                            )
                                        }
                                    }
                                    is UiState.CheckingPickedFile -> item { CheckingPickedFileState(state) }
                                    is UiState.Downloading -> {
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                MorpheSectionTitle(text = "Download progress", icon = Icons.Outlined.Download)
                                                DownloadingState(
                                                    state = state,
                                                    onCancel = onCancelDownload
                                                )
                                            }
                                        }
                                    }
                                    is UiState.Completed -> {
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                MorpheSectionTitle(text = "Complete", icon = Icons.Outlined.CheckCircle)
                                                DownloadCompleteCard(
                                                    result = state.result,
                                                    onInstall = {
                                                        runCatching {
                                                            val uri = Uri.parse(state.result.uri)
                                                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(uri, "application/vnd.android.package-archive")
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(installIntent)
                                                        }
                                                    },
                                                    onShare = {
                                                        runCatching {
                                                            val uri = Uri.parse(state.result.uri)
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "application/vnd.android.package-archive"
                                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(Intent.createChooser(shareIntent, "Share APK"))
                                                        }
                                                    },
                                                    onSendToMorphe = {
                                                        onDeliverPendingResult?.invoke(state.result)
                                                    },
                                                    onDone = {
                                                        onClearRequest()
                                                        refreshHistory()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    is UiState.Error -> item {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ErrorState(
                                                message = state.message,
                                                candidate = state.candidate,
                                                onSolveCaptcha = onSolveCaptcha,
                                                onRefresh = onRefresh,
                                                onCancel = handleCancel
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Phone / Compact Single-Column Layout
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = MorpheDefaults.ContentPadding, vertical = MorpheDefaults.ContentPadding),
                        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Morphe Fetch",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                HelperHeaderIconButton(
                                    icon = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    onClick = {
                                        refreshHistory()
                                        showSettings = true
                                    }
                                )
                            }
                        }

                        if (request == null) {
                            item {
                                EmptyLaunchState(
                                    onSearch = onSearchPackage,
                                    onOpenMorphe = onOpenMorphe,
                                    onFindApps = { showAppBrowser = true },
                                    enabledSources = enabledSources,
                                    isOverlayOpen = showSettings || showAppBrowser
                                )
                            }
                            if (historyEntries.isNotEmpty()) {
                                item {
                                    MorpheSectionTitle(
                                        text = "Downloaded APKs (${historyEntries.size})",
                                        icon = Icons.Outlined.History
                                    )
                                }
                                items(historyEntries, key = { "${it.fileName}_${it.timestamp}" }) { entry ->
                                    CachedAppCard(
                                        entry = entry,
                                        onInstall = {
                                            runCatching {
                                                val uri = Uri.parse(entry.uri)
                                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, fileNameMimeType(entry.fileName))
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(installIntent)
                                            }
                                        },
                                        onSendToMorphe = {
                                            onSendApkToMorphe(entry)
                                        },
                                        onShare = {
                                            runCatching {
                                                val uri = Uri.parse(entry.uri)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = fileNameMimeType(entry.fileName)
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share APK"))
                                            }
                                        },
                                        onSearchAgain = { onSearchPackage(entry.packageName, null) },
                                        onRemove = {
                                            scope.launch(Dispatchers.IO) {
                                                DownloadHistoryStore.remove(context, entry)
                                                refreshHistory()
                                            }
                                        }
                                    )
                                }
                            }
                            return@LazyColumn
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MorpheSectionTitle(text = "App info", icon = Icons.Outlined.Smartphone)
                                AppInfoCard(
                                    request = request,
                                    onFormatSelected = onRequestFileTypeChange,
                                    onClearRequest = onClearRequest
                                )
                            }
                        }
                        when (state) {
                            UiState.Idle,
                            UiState.Loading -> item { LoadingState() }

                            is UiState.Ready -> {
                                item {
                                    SourcePickerFlow(
                                        request = request,
                                        result = state.result,
                                        selectedPagerPage = selectedPagerPage,
                                        onPagerPageChanged = onPagerPageChanged,
                                        onResolve = onResolve,
                                        onDownload = onDownload,
                                        onPickDownloadedFile = openDownloadedFilePicker,
                                        onUseInstalledApp = onUseInstalledApp,
                                        onSolveCaptcha = onSolveCaptcha,
                                        onVersionHistory = onVersionHistory,
                                        onDownloadVersion = onDownloadVersion,
                                        onRefresh = onRefresh,
                                        onCancel = handleCancel,
                                        installedPackageRefreshToken = installedPackageRefreshToken,
                                        onPrimaryActionChanged = { primaryAction = it }
                                    )
                                }
                            }

                            is UiState.CheckingPickedFile -> item { CheckingPickedFileState(state) }
                            is UiState.Downloading -> {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MorpheSectionTitle(text = "Download progress", icon = Icons.Outlined.Download)
                                        DownloadingState(
                                            state = state,
                                            onCancel = onCancelDownload
                                        )
                                    }
                                }
                            }
                            is UiState.Completed -> {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MorpheSectionTitle(text = "Complete", icon = Icons.Outlined.CheckCircle)
                                        DownloadCompleteCard(
                                            result = state.result,
                                            onInstall = {
                                                runCatching {
                                                    val uri = Uri.parse(state.result.uri)
                                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(installIntent)
                                                }
                                            },
                                            onShare = {
                                                runCatching {
                                                    val uri = Uri.parse(state.result.uri)
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/vnd.android.package-archive"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share APK"))
                                                }
                                            },
                                            onSendToMorphe = {
                                                onDeliverPendingResult?.invoke(state.result)
                                            },
                                            onDone = {
                                                onClearRequest()
                                                refreshHistory()
                                            }
                                        )
                                    }
                                }
                            }
                            is UiState.Error -> item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ErrorState(
                                        message = state.message,
                                        candidate = state.candidate,
                                        onSolveCaptcha = onSolveCaptcha,
                                        onRefresh = onRefresh,
                                        onCancel = handleCancel
                                    )
                                }
                            }
                        }
                    }
                }

                val action = primaryAction
                if (action != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.widthIn(max = MorpheDefaults.MaxContentWidth)) {
                            SourceBottomBar(action = action, onRefresh = onRefresh, onCancel = handleCancel)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showSettings,
                enter = MorpheAnimations.pushEnter,
                exit = MorpheAnimations.pushExit
            ) {
                MorphePushedScreen {
                    HelperSettingsScreen(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        logs = logs,
                        onClearLogs = onClearLogs,
                        historyEntries = historyEntries,
                        onOpenHistoryEntry = onOpenHistoryEntry,
                        onShareHistoryEntry = onShareHistoryEntry,
                        onClearHistory = {
                            onClearHistory()
                            refreshHistory()
                        },
                        updateState = updateState,
                        onCheckForUpdates = onCheckForUpdates,
                        onDownloadUpdate = onDownloadUpdate,
                        onInstallUpdate = onInstallUpdate,
                        onDismissUpdate = onDismissUpdate,
                        onBack = { showSettings = false }
                    )
                }
            }

            AnimatedVisibility(
                visible = showAppBrowser,
                enter = MorpheAnimations.pushEnter,
                exit = MorpheAnimations.pushExit
            ) {
                MorphePushedScreen {
                    AppBrowserScreen(
                        onBack = { showAppBrowser = false },
                        onGetApk = { packageName, appName ->
                            showAppBrowser = false
                            onSearchPackage(packageName, null)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadCompleteCard(
    result: PendingDownloadResult,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onSendToMorphe: () -> Unit,
    onDone: () -> Unit
) {
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(MorpheDefaults.CompactCornerRadius))
                        .background(SemanticTone.Success.container),
                    contentAlignment = Alignment.Center
                ) {
                    ThemedIcon(
                        icon = Icons.Outlined.CheckCircle,
                        tint = SemanticTone.Success.content,
                        size = 28.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Complete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = result.fileName,
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
                    text = result.packageName,
                    tone = SemanticTone.Neutral
                )
                result.versionName?.let { ver ->
                    MorpheStatusBadge(
                        text = "Version $ver",
                        tone = SemanticTone.Primary
                    )
                }
                MorpheStatusBadge(
                    text = result.sourceName,
                    tone = SemanticTone.Primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HelperButton(
                    text = "Send to Morphe",
                    icon = Icons.Outlined.Build,
                    onClick = onSendToMorphe,
                    modifier = Modifier.weight(1f)
                )
                HelperOutlinedButton(
                    text = "Install APK",
                    icon = Icons.Outlined.InstallMobile,
                    onClick = onInstall,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HelperOutlinedButton(
                    text = "Share",
                    icon = Icons.Outlined.Share,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
                HelperButton(
                    text = "Done",
                    icon = Icons.Outlined.Check,
                    onClick = onDone,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun CachedAppCard(
    entry: DownloadHistoryEntry,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onSendToMorphe: () -> Unit,
    onSearchAgain: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usable = remember(entry.uri) { context.isHistoryUriUsable(entry.uri) }

    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = MorpheDefaults.CompactCornerRadius
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MorpheDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppAvatar(
                    packageName = entry.packageName,
                    initial = entry.appName.firstOrNull()?.uppercaseChar() ?: 'A',
                    apkUri = entry.uri
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = entry.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HelperIconButton(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove from list",
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                entry.versionName?.let { ver ->
                    MorpheStatusBadge(
                        text = "v$ver",
                        tone = SemanticTone.Primary
                    )
                }
                MorpheStatusBadge(
                    text = entry.sourceName,
                    tone = SemanticTone.Neutral
                )
                MorpheStatusBadge(
                    text = formatHistoryTimestamp(entry.timestamp),
                    tone = SemanticTone.Neutral
                )
            }

            if (usable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HelperButton(
                        text = "Send to Morphe",
                        icon = Icons.Outlined.Build,
                        onClick = onSendToMorphe,
                        modifier = Modifier.weight(1f)
                    )
                    HelperOutlinedButton(
                        text = "Install",
                        icon = Icons.Outlined.InstallMobile,
                        onClick = onInstall,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HelperOutlinedButton(
                        text = "Share",
                        icon = Icons.Outlined.Share,
                        onClick = onShare,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                HelperOutlinedButton(
                    text = "Search again",
                    icon = Icons.Outlined.Download,
                    onClick = onSearchAgain,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

