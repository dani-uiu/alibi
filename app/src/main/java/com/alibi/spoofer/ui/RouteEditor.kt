package com.alibi.spoofer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alibi.spoofer.R
import com.alibi.spoofer.util.RouteMode
import com.alibi.spoofer.util.Waypoint
import kotlin.math.roundToInt

/** Vertical list of stops with the leg settings between them. */
@Composable
fun RouteEditor(
    waypoints: List<Waypoint>,
    onWaitClick: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onLegMode: (Int, RouteMode) -> Unit,
    onLegSpeed: (Int, Double) -> Unit,
    onLegSmart: (Int, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        waypoints.forEachIndexed { index, wp ->
            StopRow(
                index = index,
                count = waypoints.size,
                waypoint = wp,
                onWaitClick = { onWaitClick(index) },
                onMoveUp = { onMove(index, index - 1) },
                onMoveDown = { onMove(index, index + 1) },
                onRemove = { onRemove(index) }
            )
            if (index < waypoints.lastIndex) {
                LegRow(
                    waypoint = wp,
                    onMode = { onLegMode(index, it) },
                    onSpeed = { onLegSpeed(index, it) },
                    onSmart = { onLegSmart(index, it) }
                )
            }
        }
    }
}

private fun stopLetter(index: Int): String =
    if (index < 26) ('A' + index).toString() else (index + 1).toString()

@Composable
private fun StopRow(
    index: Int,
    count: Int,
    waypoint: Waypoint,
    onWaitClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val isFirst = index == 0
    val isLast = index == count - 1
    val badgeColor = when {
        isFirst -> Color(0xFF1E9E5A)
        isLast -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val title = waypoint.label ?: when {
        isFirst -> stringResource(R.string.stop_start)
        isLast -> stringResource(R.string.stop_destination)
        else -> stringResource(R.string.stop_n, index + 1)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(badgeColor, CircleShape)
        ) {
            Text(stopLetter(index), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                formatCoords(waypoint.location),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // The destination's wait doesn't apply: playback holds there until you stop.
        if (!isLast) {
            AssistChip(
                onClick = onWaitClick,
                label = {
                    Text(
                        if (waypoint.waitTimeSeconds > 0) formatWait(waypoint.waitTimeSeconds)
                        else stringResource(R.string.wait)
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Timer, null, Modifier.size(16.dp)) },
                colors = if (waypoint.waitTimeSeconds > 0) AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) else AssistChipDefaults.assistChipColors()
            )
        }
        StopMenu(
            canMoveUp = index > 0,
            canMoveDown = index < count - 1,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onRemove = onRemove
        )
    }
}

@Composable
private fun StopMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_stop_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_up)) },
                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) },
                enabled = canMoveUp,
                onClick = { open = false; onMoveUp() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_down)) },
                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) },
                enabled = canMoveDown,
                onClick = { open = false; onMoveDown() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove)) },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                onClick = { open = false; onRemove() }
            )
        }
    }
}

@Composable
private fun RouteMode.icon(): ImageVector = when (this) {
    RouteMode.CAR_RIDE -> Icons.Rounded.DirectionsCar
    RouteMode.WALKING -> Icons.AutoMirrored.Rounded.DirectionsWalk
    RouteMode.SIMPLE_LINE -> Icons.Rounded.LinearScale
}

@Composable
fun RouteMode.label(): String = stringResource(
    when (this) {
        RouteMode.CAR_RIDE -> R.string.mode_car
        RouteMode.WALKING -> R.string.mode_walk
        RouteMode.SIMPLE_LINE -> R.string.mode_line
    }
)

/** Collapsed: "🚗 Car · 50 km/h". Expanded: mode, speed (applied on release), smart toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegRow(
    waypoint: Waypoint,
    onMode: (RouteMode) -> Unit,
    onSpeed: (Double) -> Unit,
    onSmart: (Boolean) -> Unit
) {
    var expanded by rememberSaveable(waypoint.id) { mutableStateOf(false) }
    val mode = waypoint.modeToNext

    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            // Timeline connector between the stop badges, as tall as the leg card.
            .drawBehind {
                val x = 14.dp.toPx()
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
    ) {
        Spacer(Modifier.width(40.dp))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
                .animateContentSize()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(mode.icon(), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = mode.label() + " · " + if (waypoint.isSmartDrive) stringResource(R.string.smart)
                        else "${waypoint.speedKmHToNext.roundToInt()} km/h",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (waypoint.isSmartDrive) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand)
                    )
                }

                AnimatedVisibility(expanded) {
                    Column(
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            RouteMode.entries.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = m == mode,
                                    onClick = { onMode(m) },
                                    shape = SegmentedButtonDefaults.itemShape(i, RouteMode.entries.size),
                                    icon = { Icon(m.icon(), null, Modifier.size(16.dp)) },
                                    label = { Text(m.label(), maxLines = 1) }
                                )
                            }
                        }

                        SpeedSlider(
                            value = waypoint.speedKmHToNext,
                            range = mode.minSpeedKmH..mode.maxSpeedKmH,
                            enabled = !waypoint.isSmartDrive,
                            onCommit = onSpeed
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.smart_title), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    stringResource(
                                        when (mode) {
                                            RouteMode.WALKING -> R.string.smart_desc_walk
                                            RouteMode.CAR_RIDE -> R.string.smart_desc_car
                                            RouteMode.SIMPLE_LINE -> R.string.smart_desc_line
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = waypoint.isSmartDrive, onCheckedChange = onSmart)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Slider that shows its value live while dragging but only reports it when released,
 * so dragging doesn't trigger dozens of route recalculations.
 */
@Composable
fun SpeedSlider(
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value.toFloat()) }
    val shown = if (dragging) local else value.toFloat().coerceIn(range)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                onCommit(local.roundToInt().toDouble())
            },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${shown.roundToInt()} km/h",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
    }
}
