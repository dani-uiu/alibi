package com.alibi.spoofer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alibi.spoofer.R
import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.PlaceKind
import com.alibi.spoofer.util.PlaceResult
import com.alibi.spoofer.util.RouteCalculator

@Composable
fun SearchPanel(
    state: SearchUiState,
    mapCenter: LatLng?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPick: (PlaceResult) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val active = focused || state.isOpen

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                if (active) {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onClose()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_close_search))
                    }
                } else {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                    )
                }
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            stringResource(R.string.search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focused = it.isFocused }
                )
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                } else {
                    OverflowMenu(onOpenDeveloperOptions, onOpenAbout)
                }
            }
        }

        AnimatedVisibility(
            visible = state.isOpen && state.query.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (state.isLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                    when {
                        state.error != null && state.results.isEmpty() -> MessageRow(
                            icon = Icons.Rounded.CloudOff,
                            text = stringResource(state.error.message()),
                            action = stringResource(R.string.retry),
                            onAction = onSubmit
                        )
                        state.results.isNotEmpty() -> LazyColumn(Modifier.heightIn(max = 340.dp)) {
                            items(state.results) { result ->
                                ResultRow(result, mapCenter) {
                                    focusManager.clearFocus()
                                    onPick(result)
                                }
                                HorizontalDivider(
                                    Modifier.padding(start = 64.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            if (!state.submitted) {
                                item {
                                    TextButton(
                                        onClick = onSubmit,
                                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                                    ) { Text(stringResource(R.string.search_more)) }
                                }
                            }
                        }
                        state.isLoading -> MessageRow(Icons.Rounded.Search, stringResource(R.string.searching))
                        state.submitted -> MessageRow(Icons.Rounded.SearchOff, stringResource(R.string.search_no_results))
                        else -> MessageRow(
                            icon = Icons.Rounded.Search,
                            text = stringResource(R.string.search_press_enter),
                            action = stringResource(R.string.search_action),
                            onAction = onSubmit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(onOpenDeveloperOptions: () -> Unit, onOpenAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_developer_options)) },
                leadingIcon = { Icon(Icons.Rounded.DeveloperMode, null) },
                onClick = { open = false; onOpenDeveloperOptions() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_about)) },
                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                onClick = { open = false; onOpenAbout() }
            )
        }
    }
}

@Composable
private fun ResultRow(result: PlaceResult, mapCenter: LatLng?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    result.kind.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (result.kind == PlaceKind.COORDINATE) stringResource(R.string.go_to_coordinates) else result.primaryText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val secondary = if (result.kind == PlaceKind.COORDINATE) result.primaryText else result.secondaryText
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (mapCenter != null && result.kind != PlaceKind.COORDINATE) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDistance(RouteCalculator.computeDistanceMeters(mapCenter, result.latLng)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageRow(icon: ImageVector, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

private fun PlaceKind.icon(): ImageVector = when (this) {
    PlaceKind.COORDINATE -> Icons.Rounded.MyLocation
    PlaceKind.ADDRESS -> Icons.Rounded.Home
    PlaceKind.STREET -> Icons.Rounded.Signpost
    PlaceKind.CITY -> Icons.Rounded.Apartment
    PlaceKind.REGION -> Icons.Rounded.Map
    PlaceKind.POI -> Icons.Rounded.Place
}
