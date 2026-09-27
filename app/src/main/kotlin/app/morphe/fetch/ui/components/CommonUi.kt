package app.morphe.fetch

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import app.morphe.fetch.updater.UpdateInfo
import app.morphe.fetch.updater.UpdateState
import java.io.File
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun HelperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
) {
    MorpheDialogButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        filled = true,
        contentPadding = contentPadding
    )
}

@Composable
internal fun HelperOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
) {
    MorpheDialogButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        filled = false,
        contentPadding = contentPadding
    )
}

/**
 * Square glass button used by the header row and other single-icon actions,
 * matching the manager's glass-button family (16dp shape, 48dp touch target).
 */
@Composable
internal fun HelperIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val contentColor = tint ?: if (selected) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(MorpheDefaults.GlassButtonHeight)
            .clip(RoundedCornerShape(MorpheDefaults.CardCornerRadius))
            .pressScale(interactionSource),
        shape = RoundedCornerShape(MorpheDefaults.CardCornerRadius),
        color = if (selected) primary.copy(alpha = 0.28f) else Color.Transparent,
        contentColor = contentColor,
        border = BorderStroke(1.dp, primary.copy(alpha = if (selected) 0.6f else 0.32f)),
        interactionSource = interactionSource
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(MorpheDefaults.IconSizeSmall)
            )
        }
    }
}

@Composable
internal fun HelperHeaderIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HelperIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
internal fun ReuseOfferDialog(
    options: List<ReuseOption>,
    onUseExisting: (ReuseOption) -> Unit,
    onDownloadNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDownloadNew,
        title = {
            Text(
                text = "Use an existing APK?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
            ) {
                Text(
                    text = "A previous download for this exact version is still available. Pick one to return to Morphe without downloading again.",
                    style = MaterialTheme.typography.bodyMedium
                )
                options.forEach { option ->
                    val entry = option.entry
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MorpheDefaults.CompactCornerRadius))
                            .clickable { onUseExisting(option) },
                        shape = RoundedCornerShape(MorpheDefaults.CompactCornerRadius),
                        color = sourceCardFill(),
                        border = BorderStroke(1.dp, sourceCardBorder())
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.sourceName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                entry.versionName?.let {
                                    Text(
                                        text = "· $it",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (option.sizeBytes > 0L) {
                                    Text(
                                        text = "· ${option.sizeBytes.formatBytes()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = entry.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                HelperButton(
                    text = "Download new",
                    onClick = onDownloadNew,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {}
    )
}

internal data class SourceBrand(
    val resId: Int
)

internal fun DownloadSource.brand(): SourceBrand = when (this) {
    DownloadSource.AURORA -> SourceBrand(resId = R.drawable.ic_src_play)
    DownloadSource.APK_PURE -> SourceBrand(resId = R.drawable.ic_src_apkpure)
    DownloadSource.APK_COMBO -> SourceBrand(resId = R.drawable.ic_src_apkcombo)
    DownloadSource.UPTODOWN -> SourceBrand(resId = R.drawable.ic_src_uptodown)
    DownloadSource.APK_MIRROR -> SourceBrand(resId = R.drawable.ic_src_apkmirror)
}

@Composable
internal fun SourceAvatar(source: DownloadSource, size: androidx.compose.ui.unit.Dp = 40.dp) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(source.brand().resId),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
    )
}

@Composable
internal fun RadioDot(selected: Boolean) {
    Icon(
        imageVector = if (selected) {
            Icons.Outlined.RadioButtonChecked
        } else {
            Icons.Outlined.RadioButtonUnchecked
        },
        contentDescription = if (selected) "Selected" else null,
        tint = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        modifier = Modifier.size(18.dp)
    )
}

internal val sourceCategories: List<Pair<String, List<DownloadSource>>> = listOf(
    "Direct downloads" to listOf(
        DownloadSource.APK_PURE,
        DownloadSource.APK_COMBO
    ),
    "Archive / Web verification" to listOf(
        DownloadSource.UPTODOWN,
        DownloadSource.APK_MIRROR
    )
)

@Composable
internal fun InfoCard(text: String) {
    InfoBox(
        title = text,
        titleColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {}
}

@Composable
internal fun sourceCardFill(): Color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

@Composable
internal fun sourceCardBorder(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
internal fun HelperThemeButton(
    dark: Boolean,
    onToggle: () -> Unit
) {
    HelperIconButton(
        icon = if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
        contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme",
        onClick = onToggle
    )
}

@Composable
internal fun AppAvatar(
    packageName: String,
    initial: Char,
    isInstalled: Boolean? = null,
    iconUrl: String? = null,
    apkUri: String? = null,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(MorpheDefaults.CompactCornerRadius)
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName, iconUrl, apkUri) {
        mutableStateOf<ImageBitmap?>(AppIconResolver.getCached(packageName))
    }

    LaunchedEffect(packageName, iconUrl, apkUri, isInstalled) {
        if (iconBitmap == null) {
            val resolved = AppIconResolver.resolveIcon(
                context = context,
                packageName = packageName,
                iconUrl = iconUrl,
                apkUri = apkUri,
                isInstalled = isInstalled
            )
            if (resolved != null) {
                iconBitmap = resolved
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape)
        )
    } else {
        val colors = listOf(
            Color(0xFF1A73E8),
            Color(0xFF4C8DFF),
            Color(0xFFFBBC04),
            Color(0xFFEA4335),
            Color(0xFF4285F4),
            Color(0xFFF25C1B)
        )
        val color = colors[initial.code % colors.size]
        val tile = MonochromeThemeDefaults.accentColor(color)
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(tile.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial.toString(),
                color = MonochromeThemeDefaults.iconTint(Color.White),
                style = if (size < 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun LazyListScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val info = listState.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    if (total <= 0 || visible !in 1 until total) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier = modifier.width(20.dp)) {
        val containerPx = with(density) { maxHeight.toPx() }
        val fraction = visible.toFloat() / total.toFloat()
        val thumbPx = (containerPx * fraction).coerceIn(44f, containerPx)
        val travelPx = (containerPx - thumbPx).coerceAtLeast(0f)
        val scrollable = (total - visible).coerceAtLeast(1)
        val pos = listState.firstVisibleItemIndex.toFloat() / scrollable.toFloat()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(total, visible, travelPx, scrollable) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragStartY = offset.y
                            dragStartIndex = listState.firstVisibleItemIndex
                        },
                        onVerticalDrag = { change, _ ->
                            val delta = change.position.y - dragStartY
                            val target = dragStartIndex +
                                (delta / travelPx * scrollable).toInt()
                            scope.launch {
                                listState.scrollToItem(target.coerceIn(0, total - 1))
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(3.dp)
                    .height(with(density) { thumbPx.toDp() })
                    .offset { IntOffset(0, (travelPx * pos).toInt()) }
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
internal fun ScrollStateScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val maxPx = scrollState.maxValue
    if (maxPx <= 0) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragStartVal by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier = modifier.width(20.dp)) {
        val containerPx = with(density) { maxHeight.toPx() }
        val thumbFraction =
            (containerPx / (containerPx + maxPx.toFloat())).coerceIn(0.1f, 1f)
        val thumbPx = (containerPx * thumbFraction).coerceIn(44f, containerPx)
        val travelPx = (containerPx - thumbPx).coerceAtLeast(0f)
        val pos = scrollState.value.toFloat() / maxPx.toFloat()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(maxPx, travelPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragStartY = offset.y
                            dragStartVal = scrollState.value
                        },
                        onVerticalDrag = { change, _ ->
                            val delta = change.position.y - dragStartY
                            val target = dragStartVal + (delta / travelPx * maxPx).toInt()
                            scope.launch {
                                scrollState.scrollTo(target.coerceIn(0, maxPx))
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(3.dp)
                    .height(with(density) { thumbPx.toDp() })
                    .offset { IntOffset(0, (travelPx * pos).toInt()) }
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
internal fun AnimatedExpand(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = androidx.compose.animation.expandVertically(
            animationSpec = androidx.compose.animation.core.tween(MorpheDefaults.ANIMATION_DURATION),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(MorpheDefaults.ANIMATION_DURATION)),
        exit = androidx.compose.animation.shrinkVertically(
            animationSpec = androidx.compose.animation.core.tween(MorpheDefaults.ANIMATION_DURATION_SHORT),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(MorpheDefaults.ANIMATION_DURATION_SHORT))
    ) {
        content()
    }
}

@Composable
internal fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun AppUpdateDialog(
    updateState: UpdateState,
    onDownloadUpdate: (UpdateInfo) -> Unit,
    onInstallUpdate: (File) -> Unit,
    onDismiss: () -> Unit
) {
    when (updateState) {
        is UpdateState.Available -> {
            val info = updateState.info
            val sizeMb = if (info.apkSize > 0) {
                "%.1f MB".format(Locale.US, info.apkSize / (1024f * 1024f))
            } else ""

            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update Available",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Morphe Fetch ${info.tagName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (sizeMb.isNotEmpty()) {
                                Text(
                                    text = sizeMb,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Current: v${BuildConfig.VERSION_NAME} → Latest: ${info.tagName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!info.changelog.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = info.changelog.trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    HelperButton(
                        text = "Update Now",
                        onClick = { onDownloadUpdate(info) },
                        icon = Icons.Outlined.Download
                    )
                },
                dismissButton = {
                    HelperOutlinedButton(
                        text = "Later",
                        onClick = onDismiss
                    )
                }
            )
        }
        is UpdateState.Downloading -> {
            val progress = updateState.progress
            val copiedMb = "%.1f".format(Locale.US, updateState.downloadedBytes / (1024f * 1024f))
            val totalMb = "%.1f".format(Locale.US, updateState.totalBytes / (1024f * 1024f))
            AlertDialog(
                onDismissRequest = { /* Keep active during download */ },
                title = {
                    Text(
                        text = "Downloading Update...",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${updateState.info.tagName} • $copiedMb of $totalMb MB (${(progress * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                },
                confirmButton = {}
            )
        }
        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update Ready",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "${updateState.info.tagName} has been downloaded and is ready to install.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    HelperButton(
                        text = "Install Now",
                        onClick = { onInstallUpdate(updateState.apkFile) },
                        icon = Icons.Outlined.Download
                    )
                },
                dismissButton = {
                    HelperOutlinedButton(
                        text = "Later",
                        onClick = onDismiss
                    )
                }
            )
        }
        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update Failed",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = updateState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                confirmButton = {
                    HelperButton(
                        text = "Dismiss",
                        onClick = onDismiss
                    )
                }
            )
        }
        else -> {}
    }
}



