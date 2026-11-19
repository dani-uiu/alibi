package com.alibi.spoofer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alibi.spoofer.BuildConfig
import com.alibi.spoofer.R
import com.alibi.spoofer.util.CoordinateParser
import com.alibi.spoofer.util.LatLng
import com.alibi.spoofer.util.Waypoint

@Composable
fun WaitTimeDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hoursText by remember { mutableStateOf((initialSeconds / 3600).takeIf { it > 0 }?.toString() ?: "") }
    var minutesText by remember { mutableStateOf(((initialSeconds % 3600) / 60).takeIf { it > 0 }?.toString() ?: "") }

    val hours = hoursText.toIntOrNull() ?: 0
    val minutes = minutesText.toIntOrNull() ?: 0
    val total = hours * 3600 + minutes * 60
    val tooLong = total > Waypoint.MAX_WAIT_SECONDS

    val presets = listOf(0, 60, 300, 900, 1800, 3600, 5400, 10800)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wait_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.wait_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.hours)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (tooLong) {
                    Text(
                        stringResource(R.string.wait_too_long),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { secs ->
                        FilterChip(
                            selected = total == secs,
                            onClick = {
                                hoursText = (secs / 3600).takeIf { it > 0 }?.toString() ?: ""
                                minutesText = ((secs % 3600) / 60).takeIf { it > 0 }?.toString() ?: ""
                            },
                            label = { Text(if (secs == 0) stringResource(R.string.no_wait) else formatWait(secs)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(total) }, enabled = !tooLong) { Text(stringResource(R.string.set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun SaveLocationDialog(
    initial: LatLng?,
    initialName: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, lat: Double, lng: Double) -> Unit
) {
    var name by remember { mutableStateOf(initialName.orEmpty()) }
    var emoji by remember { mutableStateOf("📍") }
    var coordsText by remember { mutableStateOf(initial?.let { formatCoords(it) } ?: "") }

    val coords = CoordinateParser.parse(coordsText)
    val coordsError = coordsText.isNotBlank() && coords == null
    val quickEmojis = listOf("📍", "🏠", "💼", "☕", "🍔", "🏖️", "🎓", "🏋️", "🌲", "🛒")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_place_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.place_name)) },
                    placeholder = { Text(stringResource(R.string.place_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickEmojis.forEach { em ->
                        FilterChip(
                            selected = emoji == em,
                            onClick = { emoji = em },
                            label = { Text(em, fontSize = 18.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = coordsText,
                    onValueChange = { coordsText = it },
                    label = { Text(stringResource(R.string.coordinates)) },
                    placeholder = { Text("19.43260, -99.13320") },
                    supportingText = {
                        Text(stringResource(if (coordsError) R.string.coordinates_invalid else R.string.coordinates_help))
                    },
                    isError = coordsError,
                    singleLine = true,
                    // Text keyboard on purpose: many number pads have no minus sign.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { coords?.let { onSave(name.trim(), emoji, it.latitude, it.longitude) } },
                enabled = name.isNotBlank() && coords != null
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_data), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("github.com/dani-uiu/alibi", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
