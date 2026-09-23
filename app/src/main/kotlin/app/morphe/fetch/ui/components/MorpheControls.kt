package app.morphe.fetch

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Text colour inside Morphe-style dialogs. */
internal val LocalDialogTextColor = compositionLocalOf { Color.Unspecified }

/** Expandable surface with a header icon, title and collapsible content. */
@Composable
internal fun MorpheExpandableSurface(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    initialExpanded: Boolean = false,
    headerTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MorpheDefaults.ANIMATION_DURATION),
        label = "morphe_expand_rotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MorpheDefaults.CompactCornerRadius)),
        shape = RoundedCornerShape(MorpheDefaults.CompactCornerRadius),
        color = headerTint.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (icon != null) {
                        ThemedIcon(
                            icon = icon,
                            size = MorpheDefaults.IconSizeSmall,
                            tint = headerTint
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = headerTint
                    )
                }

                ThemedIcon(
                    icon = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotationAngle),
                    size = MorpheDefaults.IconSizeSmall,
                    tint = headerTint.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = MorpheAnimations.expandFadeEnter,
                exit = MorpheAnimations.shrinkFadeExit
            ) {
                content()
            }
        }
    }
}

/** Switch with check/close icons in the thumb, as the manager draws them. */
@Composable
internal fun MorpheToggleSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
}

/** One card of a [MorpheSelectorRow]. */
internal data class MorpheSelectorOption(
    val label: String,
    val icon: ImageVector,
    /** Glyph rotation, e.g. 180° so a sort glyph reads as descending. */
    val iconRotation: Float = 0f,
    /** Announced in place of the label when an option is icon-only. */
    val contentDescription: String? = null
)

/**
 * Row of equally wide cards for switching between a few modes  the manager's
 * `CardSelectorRow`. The selected mode is carried by the fill and a stronger border.
 */
@Composable
internal fun MorpheSelectorRow(
    options: List<MorpheSelectorOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(index) }
                    ),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    Color.Transparent
                },
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 0.5.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemedIcon(
                        icon = option.icon,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        },
                        contentDescription = option.contentDescription
                            ?.takeIf { option.label.isBlank() },
                        modifier = Modifier.rotate(option.iconRotation)
                    )
                    // Rendered even when the label is blank here so an icon-only option keeps
                    // the same height as its siblings.
                    Text(
                        text = option.label,
                        style = labelStyle,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Pill-shaped action button with an icon and optional label  the manager's `ActionPillButton`,
 * shared by the compact card actions.
 */
@Composable
internal fun MorphePillButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    tall: Boolean = false,
    tone: SemanticTone = SemanticTone.Neutral
) {
    val interactionSource = remember { MutableInteractionSource() }
    val height = if (tall) MorpheDefaults.PillHeightLarge else MorpheDefaults.PillHeight
    val iconSize = if (tall) 20.dp else 18.dp
    // Without a label the pill has nothing to stretch for, so it stays a circle. A
    // filled row here would otherwise take every pixel left in the row it sits in and
    // push whatever follows it off the edge.
    val iconOnly = label == null

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MorpheDefaults.PillShape,
        color = if (enabled) tone.container else tone.container.copy(alpha = 0.4f),
        contentColor = if (enabled) tone.content else tone.content.copy(alpha = 0.5f),
        interactionSource = interactionSource,
        modifier = modifier
            .height(height)
            .then(if (iconOnly) Modifier.width(height) else Modifier)
            .pressScale(interactionSource = interactionSource, enabled = enabled)
            .semantics { role = Role.Button }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = if (iconOnly) {
                    Modifier
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MorpheDefaults.ContentPadding)
                },
                // Centred rather than start-aligned: these pills stretch to fill a card
                // row, and a start-aligned icon+label would sit against the left edge with
                // dead space beside it.
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize)
                )
                label?.let {
                    Text(
                        text = it,
                        style = if (tall) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


/**
 * Filter chip for the lists that narrow down  the manager's `AppFilterChip`. Carries a fill of
 * its own rather than the platform's transparent one, which would show the raised surface back
 * and leave only a hairline to say a button is there.
 */
@Composable
internal fun MorpheFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = Icons.Outlined.Done
) {
    val scheme = MaterialTheme.colorScheme

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier,
        leadingIcon = if (selected) {
            { Icon(selectedIcon, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surfaceColorAtElevation(2.dp),
            labelColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.onPrimaryContainer,
            selectedLeadingIconColor = scheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = scheme.outline.copy(alpha = 0.5f),
            selectedBorderColor = scheme.primary,
            selectedBorderWidth = 1.dp
        )
    )
}

/**
 * Inline status marker, sized to its content.
 *
 * @param text Badge label, or null for a badge that is only its [icon]  dropping the label is
 *   for markers sharing a row with badges that need the room for their own words.
 * @param tone Semantic colour role
 * @param containerColor Background override, for badges drawn over custom artwork
 * @param contentColor Content override, paired with [containerColor]
 * @param onClick Makes the badge act as a control
 */
@Composable
internal fun MorpheStatusBadge(
    text: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: SemanticTone = SemanticTone.Neutral,
    containerColor: Color = tone.container,
    contentColor: Color = tone.content,
    onClick: (() -> Unit)? = null
) {
    // Zero-width spaces so long tokens break at "/" and "." instead of overflowing the pill.
    val breakableText = remember(text) {
        text?.replace("/", "/\u200B")?.replace(".", ".\u200B")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                horizontal = BadgeDefaults.HorizontalPadding,
                vertical = BadgeDefaults.VerticalPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(BadgeDefaults.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            ThemedIcon(icon = it, tint = contentColor, size = BadgeDefaults.IconSize)
        }
        breakableText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Badges on a line of their own, wrapping onto the next one when they run out of room.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MorpheStatusBadgeRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BadgeDefaults.ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(BadgeDefaults.ItemSpacing),
        content = content
    )
}

/**
 * Semi-transparent dialog action button (the manager's `AppDialogButton` family), shared by
 * every confirmation surface in the helper.
 */
@Composable
internal fun MorpheDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    filled: Boolean = true,
    textSuffix: String? = null
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val isDark = !textColor.isDarkColor()
    val primaryColor = MaterialTheme.colorScheme.primary
    val destructiveDark = Color(0xFFFF6B6B)
    val destructiveLight = Color(0xFFD32F2F)

    val containerColor = when {
        isDestructive && filled -> Color.Red.copy(alpha = if (isDark) 0.25f else 0.2f)
        isDestructive -> Color.Transparent
        filled -> primaryColor.copy(alpha = if (isDark) 0.3f else 0.25f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isDestructive -> if (isDark) destructiveDark else destructiveLight
        filled -> textColor
        else -> textColor.copy(alpha = 0.85f)
    }
    val borderColor = when {
        isDestructive -> Color.Red.copy(alpha = if (isDark) 0.4f else 0.35f)
        filled -> primaryColor.copy(alpha = if (isDark) 0.5f else 0.4f)
        else -> primaryColor.copy(alpha = if (isDark) 0.3f else 0.25f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val buttonModifier = modifier
        .height(MorpheDefaults.DialogButtonHeight)
        .pressScale(interactionSource = interactionSource, enabled = enabled)
    val shape = RoundedCornerShape(MorpheDefaults.CardCornerRadius)
    val border = BorderStroke(1.dp, borderColor)
    val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(MorpheDefaults.IconSizeSmall)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = if (textSuffix == null) TextOverflow.Ellipsis else TextOverflow.Clip
        )
        if (textSuffix != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = textSuffix,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }

    if (filled) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = 0.5f),
                disabledContentColor = contentColor.copy(alpha = 0.5f)
            ),
            border = border,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = contentColor.copy(alpha = 0.4f)
            ),
            border = border,
            contentPadding = contentPadding,
            content = content
        )
    }
}

/** Approximate luminance test for picking destructive accents. */
private fun Color.isDarkColor(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

/** Dialog content padding matching the manager's dialog chrome. */
internal val MorpheDialogContentPadding = PaddingValues(
    horizontal = MorpheDefaults.ContentPaddingMedium,
    vertical = MorpheDefaults.ContentPadding
)

/**
 * Shared dialog properties: dismiss on back/outside, sized by the caller's own surface.
 */
internal val MorpheDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

/**
 * Dialog chrome shared by the helper's dialogs: manager surface, title, content and optional
 * actions, so call sites stop hand-assembling their own dialog layout.
 */
@Composable
internal fun MorpheDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Dialogs arrive with the manager's scale-fade rather than snapping in. The flag only
    // flips on the way in: callers dismiss by dropping the dialog from composition, which
    // takes the window with it, so the exit spec is here for symmetry and for callers that
    // animate the dialog out before removing it.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(onDismissRequest = onDismiss, properties = MorpheDialogProperties) {
        AnimatedVisibility(
            visible = visible,
            enter = MorpheAnimations.dialogEnter,
            exit = MorpheAnimations.dialogExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            MorpheDialogSurface(modifier = modifier.fillMaxWidth(0.92f)) {
                Column(
                    modifier = Modifier.padding(MorpheDialogContentPadding),
                    verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPadding)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalDialogTextColor.current
                    )
                    content()
                    actions?.let { actions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing),
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating button that pops in and out with the manager's floating-button motion, so it
 * reads as arriving rather than appearing.
 */
@Composable
internal fun MorpheFab(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = MorpheAnimations.fabEnter,
        exit = MorpheAnimations.fabExit
    ) {
        content()
    }
}

/**
 * Opaque full-screen holder for a screen that is pushed over another one, so whatever sits
 * underneath never shows through mid-slide.
 *
 * The tap handler swallows taps the pushed screen itself did not use, which keeps them from
 * reaching the still-composed screen below.
 */
@Composable
internal fun MorphePushedScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        content()
    }
}

/** Wraps dialog content so nested rows resolve their text colour from the surface. */
@Composable
internal fun MorpheDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MorpheDefaults.SectionCornerRadius),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    ) {
        CompositionLocalProvider(
            LocalDialogTextColor provides MaterialTheme.colorScheme.onSurface
        ) {
            Column(content = content)
        }
    }
}
