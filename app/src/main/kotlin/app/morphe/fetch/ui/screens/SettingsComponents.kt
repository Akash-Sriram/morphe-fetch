package app.morphe.fetch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun PrimaryMetricTile(
    icon: ImageVector,
    title: String,
    status: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(
        modifier = modifier,
        cornerRadius = MorpheDefaults.CompactCornerRadius,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                MorpheStatusBadge(
                    text = badge,
                    tone = SemanticTone.Primary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun CacheMetricTile(
    cacheBytes: Long,
    onClean: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(
        modifier = modifier,
        cornerRadius = MorpheDefaults.CompactCornerRadius
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    onClick = onClean
                ) {
                    Text(
                        text = "Clean",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Cache Storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cacheBytes.formatBytes(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ControlClickableTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(
        modifier = modifier,
        cornerRadius = MorpheDefaults.CompactCornerRadius,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ControlSwitchTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(
        modifier = modifier,
        cornerRadius = MorpheDefaults.CompactCornerRadius
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun CompactClickableRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CompactSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
    ) {
        MorpheSectionTitle(text = title, icon = icon)
        SectionCard {
            Column(content = content)
        }
    }
}

@Composable
internal fun SettingsRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MorpheDefaults.SettingsCornerRadius))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(
                horizontal = MorpheDefaults.ContentPadding,
                vertical = MorpheDefaults.ContentPaddingSmall + 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
    ) {
        if (leading != null) {
            leading()
        } else if (icon != null) {
            ThemedIcon(icon = icon, modifier = Modifier.padding(top = 2.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary
                )
            }
        }

        Box(
            modifier = Modifier.align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            if (trailing != null) trailing() else if (onClick != null) ForwardChevronIcon(size = MorpheDefaults.IconSizeSmall)
        }
    }
}

@Composable
internal fun SettingsSwitchItem(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onToggle: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    val enabledLabel = "Enabled"
    val disabledLabel = "Disabled"
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        leading = leading,
        onClick = onToggle,
        trailing = {
            Box(
                modifier = Modifier.semantics { stateDescription = if (checked) enabledLabel else disabledLabel }
            ) {
                MorpheToggleSwitch(checked = checked, onCheckedChange = null)
            }
        }
    )
}

internal data class SettingsChoice(
    val icon: ImageVector?,
    val title: String,
    val description: String,
    val source: DownloadSource? = null
)

@Composable
internal fun SettingsChoiceDialog(
    title: String,
    choices: List<SettingsChoice>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Choices list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    choices.forEachIndexed { index, choice ->
                        SettingsOptionCard(
                            icon = choice.icon,
                            title = choice.title,
                            description = choice.description,
                            selected = index == selectedIndex,
                            source = choice.source,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

internal fun DownloadLocation.icon(): ImageVector = when (this) {
    DownloadLocation.TEMPORARY -> Icons.Outlined.SdStorage
    DownloadLocation.DOWNLOADS -> Icons.Outlined.SaveAlt
    DownloadLocation.CUSTOM -> Icons.Outlined.FolderOpen
}

internal fun NetworkPolicy.icon(): ImageVector = when (this) {
    NetworkPolicy.WIFI_ONLY -> Icons.Outlined.Wifi
    NetworkPolicy.WIFI_AND_MOBILE -> Icons.Outlined.NetworkCheck
}

internal fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Outlined.Smartphone
    ThemeMode.DARK -> Icons.Outlined.DarkMode
    ThemeMode.LIGHT -> Icons.Outlined.LightMode
}

@Composable
internal fun SettingsRowSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = if (selected) {
            colors.primary.copy(alpha = 0.10f)
        } else {
            colors.surfaceVariant.copy(alpha = 0.40f)
        },
        contentColor = colors.onSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        content()
    }
}

@Composable
internal fun SettingsOptionCard(
    icon: ImageVector?,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    source: DownloadSource? = null
) {
    val colors = MaterialTheme.colorScheme
    SettingsRowSurface(selected = selected, enabled = enabled, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (source != null) {
                SourceAvatar(source = source, size = 32.dp)
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) colors.primary.copy(alpha = 0.15f)
                            else colors.onSurface.copy(alpha = 0.06f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) colors.primary else colors.onSurfaceVariant
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = description,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp
                )
            }
            RadioDot(selected = selected)
        }
    }
}

@Composable
internal fun DownloadHistorySection(
    entries: List<DownloadHistoryEntry>,
    onClear: () -> Unit,
    onOpen: (DownloadHistoryEntry) -> Unit,
    onShare: (DownloadHistoryEntry) -> Unit,
    showHeader: Boolean = true
) {
    val context = LocalContext.current
    var localCleared by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Download history",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                HelperOutlinedButton(
                    text = "Clear",
                    enabled = entries.isNotEmpty(),
                    onClick = {
                        onClear()
                        localCleared++
                    },
                    modifier = Modifier.width(MorpheDefaults.CompactButtonWidth)
                )
            }
        }

        val currentEntries = remember(entries.size, entries.lastOrNull(), localCleared) { entries.toList() }
        if (currentEntries.isEmpty()) {
            InfoCard("No hand-offs recorded yet. Downloads and picked files you return to Morphe show up here.")
        } else {
            currentEntries.forEach { entry ->
                val usable = remember(entry.uri) { context.isHistoryUriUsable(entry.uri) }
                HistoryEntryCard(
                    entry = entry,
                    usable = usable,
                    onOpen = { onOpen(entry) },
                    onShare = { onShare(entry) }
                )
            }
        }
    }
}

@Composable
internal fun HistoryEntryCard(
    entry: DownloadHistoryEntry,
    usable: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    SurfaceCard(cornerRadius = MorpheDefaults.CompactCornerRadius) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MorpheDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = entry.appName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.packageName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatHistoryTimestamp(entry.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = buildString {
                    entry.versionName?.let { append(it).append(" · ") }
                    append(entry.sourceName)
                    append(" · ")
                    append(entry.fileName)
                    if (!entry.fileKind.equals("web", ignoreCase = true)) {
                        append(" · ")
                        append(entry.fileKind.uppercase(Locale.US))
                    }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (usable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
                ) {
                    HelperButton(
                        text = "Share",
                        onClick = onShare,
                        icon = Icons.Outlined.Share,
                        modifier = Modifier.weight(1f)
                    )
                    HelperOutlinedButton(
                        text = "Open",
                        onClick = onOpen,
                        icon = Icons.Outlined.FolderOpen,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = "File no longer available (temporary hand-off files are cleaned up after Morphe copies them).",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

internal fun Long.formatBytes(): String {
    if (this <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${toLong()} B" else String.format(Locale.US, "%.1f %s", value, units[unit])
}
