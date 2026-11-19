package com.alibi.spoofer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alibi.spoofer.R
import com.alibi.spoofer.service.SpoofError
import com.alibi.spoofer.service.SpoofPhase
import com.alibi.spoofer.service.SpooferState
import com.alibi.spoofer.ui.theme.LiveGreen
import com.alibi.spoofer.util.CustomLocation
import com.alibi.spoofer.util.RouteMode

/** Everything the sheet can ask the screen/ViewModel to do. */
class SheetActions(
    val onModeChange: (SpooferMode) -> Unit,
    val onStartSingle: () -> Unit,
    val onStartRoute: () -> Unit,
    val onStop: () -> Unit,
    val onUseMyLocation: () -> Unit,
    val onClearTarget: () -> Unit,
    val onClearRoute: () -> Unit,
    val onRetryRoute: () -> Unit,
    val onSavePlace: () -> Unit,
    val onPickPlace: (CustomLocation) -> Unit,
    val onDeletePlace: (CustomLocation) -> Unit,
    val onGrantPermission: () -> Unit,
    val onOpenDeveloperOptions: () -> Unit,
    val onDismissError: () -> Unit,
    val onWaitClick: (Int) -> Unit,
    val onMoveStop: (Int, Int) -> Unit,
    val onRemoveStop: (Int) -> Unit,
    val onLegMode: (Int, RouteMode) -> Unit,
    val onLegSpeed: (Int, Double) -> Unit,
    val onLegSmart: (Int, Boolean) -> Unit,
    val onAllSpeeds: (Double) -> Unit,
    val onGlobalSmart: (Boolean) -> Unit
)

// ------------------------------------------------------------------- header (always visible)

@Composable
fun SheetHeader(ui: UiState, spoofer: SpooferState, actions: SheetActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Drag handle
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 12.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(ui, spoofer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    statusTitle(ui, spoofer),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    statusSubtitle(ui, spoofer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            PrimaryAction(ui, spoofer, actions)
        }

        if (spoofer.isRunning && spoofer.isRouteMode) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { spoofer.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = LiveGreen,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusIcon(ui: UiState, spoofer: SpooferState) {
    val live = spoofer.isRunning
    val (icon, container, content) = when {
        live -> Triple(
            if (spoofer.isRouteMode) Icons.Rounded.Navigation else Icons.Rounded.Place,
            LiveGreen, Color.White
        )
        ui.mode == SpooferMode.ROUTE -> Triple(
            Icons.Rounded.Route, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
        )
        else -> Triple(
            Icons.Rounded.Place, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
    Box(contentAlignment = Alignment.Center) {
        if (live) PulseRing()
        Surface(shape = CircleShape, color = container, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = content)
            }
        }
    }
}

/** Soft pulsing halo behind the live status icon. Only composed while spoofing. */
@Composable
private fun PulseRing() {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha"
    )
    Box(
        Modifier
            .size(52.dp)
            // Read in the draw phase so the animation doesn't recompose every frame.
            .graphicsLayer { alpha = pulse * 0.35f }
            .background(LiveGreen, CircleShape)
    )
}

@Composable
private fun statusTitle(ui: UiState, spoofer: SpooferState): String = when {
    spoofer.isRunning -> when (spoofer.phase) {
        SpoofPhase.PINNED -> stringResource(R.string.status_pinned)
        SpoofPhase.MOVING -> stringResource(R.string.status_moving, spoofer.speedKmH.toInt())
        SpoofPhase.WAITING -> stringResource(R.string.status_waiting)
        SpoofPhase.ARRIVED -> stringResource(R.string.status_arrived)
        SpoofPhase.IDLE -> stringResource(R.string.status_ready)
    }
    ui.mode == SpooferMode.SINGLE ->
        if (ui.singleTarget != null) ui.singleTargetLabel ?: stringResource(R.string.status_point_selected)
        else stringResource(R.string.status_pick_location)
    ui.waypoints.size < 2 -> stringResource(R.string.status_plan_route)
    ui.isCalculatingRoute -> stringResource(R.string.status_calculating)
    ui.routeError -> stringResource(R.string.status_route_failed)
    else -> "${formatDistance(ui.route.distanceMeters)} · ${formatDuration(ui.route.durationSeconds)}"
}

@Composable
private fun statusSubtitle(ui: UiState, spoofer: SpooferState): String = when {
    spoofer.isRunning -> when (spoofer.phase) {
        SpoofPhase.PINNED -> spoofer.currentLocation?.let { formatCoords(it) } ?: ""
        SpoofPhase.MOVING -> stringResource(
            R.string.status_progress, (spoofer.progress * 100).toInt(), formatDuration(spoofer.secondsRemaining)
        )
        SpoofPhase.WAITING -> stringResource(R.string.status_wait_left, formatClock(spoofer.waitSecondsRemaining))
        SpoofPhase.ARRIVED -> stringResource(R.string.status_holding)
        SpoofPhase.IDLE -> ""
    }
    ui.mode == SpooferMode.SINGLE ->
        ui.singleTarget?.let { formatCoords(it) } ?: stringResource(R.string.status_pick_hint)
    ui.waypoints.size < 2 -> pluralStringResource(R.plurals.status_route_hint, ui.waypoints.size, ui.waypoints.size)
    ui.isCalculatingRoute || ui.routeError -> pluralStringResource(R.plurals.stops_count, ui.waypoints.size, ui.waypoints.size)
    else -> pluralStringResource(R.plurals.stops_count, ui.waypoints.size, ui.waypoints.size) +
        if (ui.isSmartActive) " · " + stringResource(R.string.smart) else ""
}

@Composable
private fun PrimaryAction(ui: UiState, spoofer: SpooferState, actions: SheetActions) {
    val running = spoofer.isRunning
    // While pinned to a point, picking another point offers "Move here" next to Stop.
    val canMove = running && !spoofer.isRouteMode && ui.mode == SpooferMode.SINGLE &&
        ui.singleTarget != null && ui.singleTarget != spoofer.currentLocation

    when {
        canMove -> Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = actions.onStop) {
                Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.stop))
            }
            Spacer(Modifier.width(4.dp))
            Button(onClick = actions.onStartSingle) { Text(stringResource(R.string.move_here)) }
        }
        running -> Button(
            onClick = actions.onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.stop))
        }
        ui.mode == SpooferMode.SINGLE -> Button(onClick = actions.onStartSingle, enabled = ui.singleTarget != null) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.teleport))
        }
        ui.routeError && ui.waypoints.size >= 2 -> FilledTonalButton(onClick = actions.onRetryRoute) {
            Text(stringResource(R.string.retry))
        }
        else -> Button(onClick = actions.onStartRoute, enabled = ui.canStartRoute) {
            if (ui.isCalculatingRoute) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.start))
        }
    }
}

// ------------------------------------------------------------------- body (scrolls)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetBody(ui: UiState, spoofer: SpooferState, actions: SheetActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Setup & errors first: nothing works without them.
        spoofer.error?.let { error ->
            NoticeCard(
                icon = Icons.Rounded.ErrorOutline,
                title = stringResource(R.string.error_title),
                body = stringResource(
                    when (error) {
                        SpoofError.MOCK_NOT_ALLOWED -> R.string.error_mock_not_allowed
                        SpoofError.PROVIDER_FAILED -> R.string.error_provider
                        SpoofError.NO_ROUTE -> R.string.error_no_route
                    }
                ),
                action = stringResource(R.string.dismiss),
                onAction = actions.onDismissError
            )
        }
        if (!ui.hasLocationPermission) {
            NoticeCard(
                icon = Icons.Rounded.LocationOff,
                title = stringResource(R.string.setup_permission_title),
                body = stringResource(R.string.setup_permission_body),
                action = stringResource(R.string.grant),
                onAction = actions.onGrantPermission
            )
        }
        if (!ui.isMockAppSet) {
            NoticeCard(
                icon = Icons.Rounded.Warning,
                title = stringResource(R.string.setup_mock_title),
                body = stringResource(R.string.setup_mock_body),
                action = stringResource(R.string.open_developer_options),
                onAction = actions.onOpenDeveloperOptions
            )
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = ui.mode == SpooferMode.SINGLE,
                onClick = { actions.onModeChange(SpooferMode.SINGLE) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(Icons.Rounded.Place, null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.mode_teleport)) }
            )
            SegmentedButton(
                selected = ui.mode == SpooferMode.ROUTE,
                onClick = { actions.onModeChange(SpooferMode.ROUTE) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Icon(Icons.Rounded.Route, null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.mode_route)) }
            )
        }

        when (ui.mode) {
            SpooferMode.SINGLE -> SinglePanel(ui, actions)
            SpooferMode.ROUTE -> RoutePanel(ui, actions)
        }

        SavedPlaces(ui.savedPlaces, actions)
    }
}

@Composable
private fun SinglePanel(ui: UiState, actions: SheetActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = actions.onUseMyLocation, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.use_my_location), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ui.singleTarget != null) {
            OutlinedButton(onClick = actions.onClearTarget) {
                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.clear))
            }
        }
    }
}

@Composable
private fun RoutePanel(ui: UiState, actions: SheetActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ui.waypoints.size >= 2) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.smart_all_title), style = MaterialTheme.typography.labelLarge)
                            Text(
                                stringResource(R.string.smart_all_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = ui.isSmartActive, onCheckedChange = actions.onGlobalSmart)
                    }
                    if (!ui.isSmartActive) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.speed_all_legs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SpeedSlider(
                            value = ui.waypoints.first().speedKmHToNext,
                            range = 1f..150f,
                            enabled = true,
                            onCommit = actions.onAllSpeeds
                        )
                    }
                }
            }
        }

        if (ui.waypoints.isEmpty()) {
            Text(
                stringResource(R.string.route_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            RouteEditor(
                waypoints = ui.waypoints,
                onWaitClick = actions.onWaitClick,
                onMove = actions.onMoveStop,
                onRemove = actions.onRemoveStop,
                onLegMode = actions.onLegMode,
                onLegSpeed = actions.onLegSpeed,
                onLegSmart = actions.onLegSmart
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = actions.onUseMyLocation, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.start_from_my_location), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (ui.waypoints.isNotEmpty()) {
                OutlinedButton(onClick = actions.onClearRoute) {
                    Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.clear))
                }
            }
        }
    }
}

@Composable
private fun SavedPlaces(places: List<CustomLocation>, actions: SheetActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.saved_places), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = actions.onSavePlace) {
                Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.save_place))
            }
        }
        if (places.isEmpty()) {
            Text(
                stringResource(R.string.saved_places_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                places.forEach { place ->
                    InputChip(
                        selected = false,
                        onClick = { actions.onPickPlace(place) },
                        label = { Text("${place.emoji} ${place.name}") },
                        trailingIcon = {
                            IconButton(onClick = { actions.onDeletePlace(place) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_delete_place, place.name),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}
