package app.morphe.fetch

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import app.morphe.fetch.aurora.MicroGAccountTokenProvider
import app.morphe.fetch.aurora.GPlayHttpClient
import kotlinx.coroutines.launch
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
import androidx.compose.material3.IconButton
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

private enum class SettingsSection(
    val title: String,
    val icon: ImageVector
) {
    PREFERENCES("Preferences", Icons.Outlined.Tune),
    SOURCES("Download Sources", Icons.Outlined.Dns),
    HISTORY("Download History", Icons.Outlined.History),
    LOGS("Activity Logs", Icons.Outlined.BugReport),
    UPDATES("Updates & About", Icons.Outlined.Download)
}

@Composable
internal fun DownloadSourcesContent(
    settings: HelperSettings,
    onSettingsChange: (HelperSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledSources = remember(settings.disabledSources) {
        DownloadSource.entries.filter { it !in settings.disabledSources }
    }
    Column(
        modifier = modifier,
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

        var showAuroraAccountDialog by remember { mutableStateOf(false) }

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

                            if (source == DownloadSource.AURORA && isEnabled) {
                                val hasAccount = !settings.auroraAuthToken.isNullOrBlank()
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = if (!settings.auroraEmail.isNullOrBlank()) settings.auroraEmail else if (hasAccount) "Custom" else "Anonymous",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasAccount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { showAuroraAccountDialog = true }
                                )
                            }
                        }
                    }

                    if (source == DownloadSource.AURORA && isEnabled) {
                        val hasAccount = !settings.auroraAuthToken.isNullOrBlank()
                        IconButton(
                            onClick = { showAuroraAccountDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Aurora Account Settings",
                                tint = if (hasAccount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
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

        if (showAuroraAccountDialog) {
            AuroraAccountDialog(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onDismiss = { showAuroraAccountDialog = false }
            )
        }

        Text(
            text = "At least one source must remain active.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun AuroraAccountDialog(
    settings: HelperSettings,
    onSettingsChange: (HelperSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    var showManualDialog by remember { mutableStateOf(false) }
    var inputEmail by rememberSaveable { mutableStateOf(settings.auroraEmail.orEmpty()) }
    var inputToken by rememberSaveable { mutableStateOf(settings.auroraAuthToken.orEmpty()) }
    var inputType by rememberSaveable { mutableStateOf(settings.auroraTokenType) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val tokenProvider = remember {
        MicroGAccountTokenProvider(
            context = context,
            httpClient = GPlayHttpClient(MorpheHttpClient.gplayClient)
        )
    }
    val availableAccounts = remember(tokenProvider) {
        tokenProvider.getAvailableAccounts()
    }

    val accountChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val chosenAccountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val chosenAccountType = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        if (!chosenAccountName.isNullOrBlank()) {
            isAuthenticating = true
            scope.launch {
                try {
                    val targetActivity = hostActivity ?: context.findActivity()
                    val token = tokenProvider.fetchTokenForEmail(chosenAccountName, targetActivity, chosenAccountType)
                    onSettingsChange(
                        settings.copy(
                            auroraEmail = chosenAccountName,
                            auroraAuthToken = token,
                            auroraTokenType = "AUTH"
                        )
                    )
                    Toast.makeText(context, "Google account connected: $chosenAccountName", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("AuroraAccount", "Auth error: ${e.message}", e)
                    Toast.makeText(context, "Auth error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isAuthenticating = false
                }
            }
        }
    }

    val isConfigured = !settings.auroraAuthToken.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Google Play Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isConfigured) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (isConfigured) "Logged In" else "Anonymous",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConfigured) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isConfigured) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Account: ${settings.auroraEmail ?: "Custom"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Token: ${settings.auroraAuthToken.take(12)}... (${settings.auroraTokenType})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HelperOutlinedButton(
                            text = "Edit Token",
                            onClick = {
                                inputEmail = settings.auroraEmail.orEmpty()
                                inputToken = settings.auroraAuthToken.orEmpty()
                                inputType = settings.auroraTokenType
                                showManualDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                        HelperOutlinedButton(
                            text = "Sign Out",
                            onClick = {
                                onSettingsChange(
                                    settings.copy(
                                        auroraEmail = null,
                                        auroraAuthToken = null
                                    )
                                )
                                Toast.makeText(context, "Switched to Anonymous Google Play session", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text(
                        text = "Sign in using your device's MicroG-RE account for higher rate limits, or stay anonymous.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isAuthenticating) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Authenticating with Google Play via MicroG...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        if (availableAccounts.isNotEmpty()) {
                            Text(
                                text = "Detected device accounts:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            availableAccounts.forEach { acc ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAuthenticating) {
                                            isAuthenticating = true
                                            scope.launch {
                                                try {
                                                    val targetActivity = hostActivity ?: context.findActivity()
                                                    val token = tokenProvider.fetchTokenForEmail(acc.name, targetActivity, acc.type)
                                                    onSettingsChange(
                                                        settings.copy(
                                                            auroraEmail = acc.name,
                                                            auroraAuthToken = token,
                                                            auroraTokenType = "AUTH"
                                                        )
                                                    )
                                                    Toast.makeText(context, "Connected: ${acc.name}", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    android.util.Log.e("AuroraAccount", "Auth error: ${e.message}", e)
                                                    Toast.makeText(context, "Auth error: ${e.message}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isAuthenticating = false
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = acc.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = if (acc.type == MicroGAccountTokenProvider.REVANCED_ACCOUNT_TYPE) "MicroG-RE" else "Google Account",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "Login",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HelperButton(
                                text = "Other Account",
                                onClick = {
                                    val intent = AccountManager.newChooseAccountIntent(
                                        null,
                                        null,
                                        tokenProvider.getSupportedAccountTypes(),
                                        false,
                                        null,
                                        null,
                                        null,
                                        null
                                    )
                                    accountChooserLauncher.launch(intent)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            HelperOutlinedButton(
                                text = "Enter Token",
                                onClick = {
                                    inputEmail = settings.auroraEmail.orEmpty()
                                    inputToken = settings.auroraAuthToken.orEmpty()
                                    inputType = settings.auroraTokenType
                                    showManualDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            HelperButton(
                text = "Close",
                onClick = onDismiss
            )
        }
    )

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Google Play Account Token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your Google email and auth token (OAuth or AAS token) to authorize direct downloads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Email (e.g. user@gmail.com)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = { Text("Account Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Type:", style = MaterialTheme.typography.labelMedium)
                        listOf("AUTH", "AAS").forEach { type ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (inputType == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { inputType = type }
                            ) {
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (inputType == type) FontWeight.Bold else FontWeight.Normal,
                                    color = if (inputType == type) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                HelperButton(
                    text = "Save",
                    onClick = {
                        val cleanToken = inputToken.trim()
                        val cleanEmail = inputEmail.trim()
                        if (cleanToken.isNotBlank()) {
                            onSettingsChange(
                                settings.copy(
                                    auroraEmail = cleanEmail.ifBlank { null },
                                    auroraAuthToken = cleanToken,
                                    auroraTokenType = inputType
                                )
                            )
                            showManualDialog = false
                            Toast.makeText(context, "Account token saved", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Token cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            dismissButton = {
                HelperOutlinedButton(
                    text = "Cancel",
                    onClick = { showManualDialog = false }
                )
            }
        )
    }
}

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
    val isExpanded = isExpandedScreen()
    var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.PREFERENCES) }
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

    if (!isExpanded && sourcesDialog) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    DownloadSourcesContent(
                        settings = settings,
                        onSettingsChange = onSettingsChange
                    )
                }
            }
        )
    }
    if (!isExpanded && historyDialog) {
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

    if (!isExpanded && logsDialog) {
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

    if (isExpanded) {
        // Adaptive Two-Pane Master-Detail Tablet Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(MorpheDefaults.ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Master Navigation Pane
            Column(
                modifier = Modifier
                    .width(MorpheDefaults.MasterPaneWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
            ) {
                // Header: Title, version and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Morphe Fetch v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HelperHeaderIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Close",
                        onClick = onBack
                    )
                }

                // Navigation Category Items
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsSection.entries.forEach { section ->
                        val isSelected = selectedSection == section
                        SurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            },
                            borderWidth = if (isSelected) 1.dp else 0.dp,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                            onClick = { selectedSection = section }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = section.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    val subtitle = when (section) {
                                        SettingsSection.PREFERENCES -> "Theme, cache & network"
                                        SettingsSection.SOURCES -> "${enabledSources.size} of ${DownloadSource.entries.size} active"
                                        SettingsSection.HISTORY -> "${historyEntries.size} APKs"
                                        SettingsSection.LOGS -> "${logs.size} Events"
                                        SettingsSection.UPDATES -> when (updateState) {
                                            is UpdateState.Available -> "Update available!"
                                            is UpdateState.Checking -> "Checking..."
                                            else -> "v${BuildConfig.VERSION_NAME}"
                                        }
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (section == SettingsSection.UPDATES && updateState is UpdateState.Available) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Trailing badge
                                when (section) {
                                    SettingsSection.SOURCES -> {
                                        MorpheStatusBadge(
                                            text = settings.preferredSource?.label ?: "Auto",
                                            tone = SemanticTone.Primary
                                        )
                                    }
                                    SettingsSection.UPDATES -> {
                                        if (updateState is UpdateState.Available) {
                                            MorpheStatusBadge(
                                                text = "New",
                                                tone = SemanticTone.Primary
                                            )
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }

                // Bottom Quick Cache Cleanup Tile
                CacheMetricTile(
                    modifier = Modifier.fillMaxWidth(),
                    cacheBytes = cacheBytes + downloadsBytes,
                    onClean = {
                        context.clearTemporaryDownloads()
                        context.clearDownloadsCopies()
                        cacheBytes = 0L
                        downloadsBytes = 0L
                        Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Vertical Divider
            MorpheVerticalDivider(
                modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
            )

            // Right Detail Pane
            SurfaceCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(MorpheDefaults.ContentPaddingMedium),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedSection) {
                        SettingsSection.PREFERENCES -> {
                            SettingsPreferencesDetail(
                                settings = settings,
                                onSettingsChange = onSettingsChange,
                                cacheBytes = cacheBytes + downloadsBytes,
                                onCleanCache = {
                                    context.clearTemporaryDownloads()
                                    context.clearDownloadsCopies()
                                    cacheBytes = 0L
                                    downloadsBytes = 0L
                                },
                                onOpenLocationDialog = { locationDialog = true },
                                onOpenPolicyDialog = { policyDialog = true }
                            )
                        }
                        SettingsSection.SOURCES -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Download Sources",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Manage repositories and mirrors queried during APK searches. Star a source to set it as preferred.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DownloadSourcesContent(
                                    settings = settings,
                                    onSettingsChange = onSettingsChange
                                )
                            }
                        }
                        SettingsSection.HISTORY -> {
                            DownloadHistorySection(
                                entries = historyEntries,
                                onClear = onClearHistory,
                                onOpen = onOpenHistoryEntry,
                                onShare = onShareHistoryEntry
                            )
                        }
                        SettingsSection.LOGS -> {
                            RequestLogsCard(
                                logs = logs,
                                onClearLogs = onClearLogs
                            )
                        }
                        SettingsSection.UPDATES -> {
                            UpdatesCard(
                                updateState = updateState,
                                onCheckForUpdates = onCheckForUpdates,
                                onDownloadUpdate = onDownloadUpdate,
                                onInstallUpdate = onInstallUpdate,
                                onDismissUpdate = onDismissUpdate
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Single-Column Phone Layout with swipe-to-dismiss
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = MorpheDefaults.MaxContentWidth)
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

                // Theme Mode Selector at the Top (Appearance)
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

                // 1. Sources & Automation
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorpheSectionTitle(text = "Sources & Automation", icon = Icons.Outlined.Dns)
                    SectionCard {
                        Column {
                            // Download Sources
                            SettingsRow(
                                icon = Icons.Outlined.Dns,
                                title = "Download Sources",
                                subtitle = "${enabledSources.size} of ${DownloadSource.entries.size} active • Tap to configure",
                                trailing = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MorpheStatusBadge(
                                            text = settings.preferredSource?.label ?: "Auto",
                                            tone = SemanticTone.Primary
                                        )
                                        ForwardChevronIcon(size = MorpheDefaults.IconSizeSmall)
                                    }
                                },
                                onClick = { sourcesDialog = true }
                            )

                            MorpheDivider(fullWidth = true)

                            // Auto-fetch (Zero-Click)
                            SettingsSwitchItem(
                                icon = Icons.Outlined.AutoAwesome,
                                title = "Auto-fetch",
                                subtitle = "Automatically download and return best match for Morphe",
                                checked = settings.autoDownloadBestMatch,
                                onToggle = {
                                    onSettingsChange(settings.copy(autoDownloadBestMatch = !settings.autoDownloadBestMatch))
                                }
                            )

                            MorpheDivider(fullWidth = true)

                            // Network Policy
                            SettingsRow(
                                icon = Icons.Outlined.Wifi,
                                title = "Network Policy",
                                subtitle = when (settings.networkPolicy) {
                                    NetworkPolicy.WIFI_AND_MOBILE -> "Wi-Fi & Mobile data allowed"
                                    NetworkPolicy.WIFI_ONLY -> "Wi-Fi connections only"
                                    NetworkPolicy.MOBILE_DATA_ONLY -> "Mobile data only"
                                },
                                onClick = { policyDialog = true }
                            )
                        }
                    }
                }

                // 2. Storage & Cache
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorpheSectionTitle(text = "Storage & Cache", icon = Icons.Outlined.SdStorage)
                    SectionCard {
                        Column {
                            // Save Location / App Cache Selector
                            SettingsRow(
                                icon = Icons.Outlined.FolderOpen,
                                title = "Download Location",
                                subtitle = if (settings.downloadLocation == DownloadLocation.DOWNLOADS) {
                                    "Downloads / Morphe Fetch"
                                } else {
                                    "App Cache (Temporary)"
                                },
                                trailing = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MorpheStatusBadge(
                                            text = if (settings.downloadLocation == DownloadLocation.DOWNLOADS) "Downloads" else "App Cache",
                                            tone = SemanticTone.Neutral
                                        )
                                        ForwardChevronIcon(size = MorpheDefaults.IconSizeSmall)
                                    }
                                },
                                onClick = { locationDialog = true }
                            )

                            MorpheDivider(fullWidth = true)

                            // Auto-clear Cache
                            SettingsSwitchItem(
                                icon = Icons.Outlined.CleaningServices,
                                title = "Auto-clear Cache",
                                subtitle = "Purge temporary APKs after returning to Morphe",
                                checked = settings.deleteTemporaryAfterHandoff,
                                onToggle = {
                                    onSettingsChange(settings.copy(deleteTemporaryAfterHandoff = !settings.deleteTemporaryAfterHandoff))
                                }
                            )

                            MorpheDivider(fullWidth = true)

                            // Clear Cache Action Row
                            val totalCache = cacheBytes + downloadsBytes
                            SettingsRow(
                                icon = Icons.Outlined.DeleteOutline,
                                title = "Clear Cache",
                                subtitle = if (totalCache > 0L) {
                                    "${totalCache.formatBytes()} used by cached APKs"
                                } else {
                                    "Cache is clean (0 B)"
                                },
                                trailing = {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (totalCache > 0L) {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        },
                                        border = if (totalCache > 0L) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                                        } else null,
                                        onClick = {
                                            if (totalCache > 0L) {
                                                context.clearTemporaryDownloads()
                                                context.clearDownloadsCopies()
                                                cacheBytes = 0L
                                                downloadsBytes = 0L
                                                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "Clear",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (totalCache > 0L) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            },
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            )

                            MorpheDivider(fullWidth = true)

                            // Download History
                            SettingsRow(
                                icon = Icons.Outlined.History,
                                title = "Download History",
                                subtitle = "${historyEntries.size} saved APKs • Tap to view or share",
                                trailing = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MorpheStatusBadge(
                                            text = "${historyEntries.size} APKs",
                                            tone = SemanticTone.Neutral
                                        )
                                        ForwardChevronIcon(size = MorpheDefaults.IconSizeSmall)
                                    }
                                },
                                onClick = { historyDialog = true }
                            )
                        }
                    }
                }

                // 3. Diagnostics & Logs
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorpheSectionTitle(text = "Diagnostics & Logs", icon = Icons.Outlined.BugReport)
                    SectionCard {
                        Column {
                            // Activity Logs
                            SettingsRow(
                                icon = Icons.Outlined.BugReport,
                                title = "Activity Logs",
                                subtitle = "${logs.size} recorded events • Network & resolution trace",
                                trailing = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MorpheStatusBadge(
                                            text = "${logs.size} Events",
                                            tone = SemanticTone.Neutral
                                        )
                                        ForwardChevronIcon(size = MorpheDefaults.IconSizeSmall)
                                    }
                                },
                                onClick = { logsDialog = true }
                            )

                            MorpheDivider(fullWidth = true)

                            // Log to Logcat
                            SettingsSwitchItem(
                                icon = Icons.Outlined.Bolt,
                                title = "Log to Logcat",
                                subtitle = "Stream diagnostic events to ADB / system logcat",
                                checked = settings.logcatLogging,
                                onToggle = {
                                    onSettingsChange(settings.copy(logcatLogging = !settings.logcatLogging))
                                }
                            )
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
    }
}

@Composable
private fun SettingsPreferencesDetail(
    settings: HelperSettings,
    onSettingsChange: (HelperSettings) -> Unit,
    cacheBytes: Long,
    onCleanCache: () -> Unit,
    onOpenLocationDialog: () -> Unit,
    onOpenPolicyDialog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Section 1: Appearance
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
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
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = mode.icon(),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Storage & Connection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Storage & Connection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            SectionCard {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.FolderOpen,
                        title = "Download Location",
                        subtitle = if (settings.downloadLocation == DownloadLocation.DOWNLOADS) {
                            "Downloads / Morphe Fetch"
                        } else {
                            "App Cache (Temporary)"
                        },
                        trailing = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MorpheStatusBadge(
                                    text = if (settings.downloadLocation == DownloadLocation.DOWNLOADS) "Downloads" else "App Cache",
                                    tone = SemanticTone.Neutral
                                )
                                ForwardChevronIcon(size = MorpheDefaults.IconSizeSmall)
                            }
                        },
                        onClick = onOpenLocationDialog
                    )

                    MorpheDivider(fullWidth = true)

                    SettingsRow(
                        icon = Icons.Outlined.Wifi,
                        title = "Network Policy",
                        subtitle = when (settings.networkPolicy) {
                            NetworkPolicy.WIFI_AND_MOBILE -> "Wi-Fi & Mobile data allowed"
                            NetworkPolicy.WIFI_ONLY -> "Wi-Fi connections only"
                            NetworkPolicy.MOBILE_DATA_ONLY -> "Mobile data only"
                        },
                        trailing = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MorpheStatusBadge(
                                    text = if (settings.networkPolicy == NetworkPolicy.WIFI_AND_MOBILE) "Wi-Fi & Data" else "Unmetered",
                                    tone = SemanticTone.Neutral
                                )
                                ForwardChevronIcon(size = MorpheDefaults.IconSizeSmall)
                            }
                        },
                        onClick = onOpenPolicyDialog
                    )

                    MorpheDivider(fullWidth = true)

                    SettingsRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Clear Cache",
                        subtitle = if (cacheBytes > 0L) {
                            "${cacheBytes.formatBytes()} used by cached APKs"
                        } else {
                            "Cache is clean (0 B)"
                        },
                        trailing = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (cacheBytes > 0L) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                },
                                border = if (cacheBytes > 0L) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                                } else null,
                                onClick = onCleanCache
                            ) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cacheBytes > 0L) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        // Section 3: Automation & Diagnostics
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Automation & Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            SectionCard {
                Column {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Auto-fetch",
                        subtitle = "Automatically download best matching candidate for Morphe without extra prompt",
                        checked = settings.autoDownloadBestMatch,
                        onToggle = {
                            onSettingsChange(settings.copy(autoDownloadBestMatch = !settings.autoDownloadBestMatch))
                        }
                    )

                    MorpheDivider(fullWidth = true)

                    SettingsSwitchItem(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Auto-clear handoff",
                        subtitle = "Purge temporary APK hand-off files after delivery to Morphe",
                        checked = settings.deleteTemporaryAfterHandoff,
                        onToggle = {
                            onSettingsChange(settings.copy(deleteTemporaryAfterHandoff = !settings.deleteTemporaryAfterHandoff))
                        }
                    )

                    MorpheDivider(fullWidth = true)

                    SettingsSwitchItem(
                        icon = Icons.Outlined.Bolt,
                        title = "Log to Logcat",
                        subtitle = "Stream detailed Morphe Fetch diagnostic events to Android Logcat / ADB",
                        checked = settings.logcatLogging,
                        onToggle = {
                            onSettingsChange(settings.copy(logcatLogging = !settings.logcatLogging))
                        }
                    )
                }
            }
        }
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

