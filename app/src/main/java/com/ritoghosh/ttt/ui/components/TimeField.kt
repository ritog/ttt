/**
 * TimeField and DateField — a labelled value that opens the Material time or date picker when tapped.
 *
 * Depends on: Compose material3 pickers, TimeFormat.kt.
 * Must not: round or validate. Callers round times to the 5-minute grid (BlockTimes.roundToGrid).
 *
 * The material3 pickers are still marked experimental; they are stock components (SPEC 15.2).
 */
package com.ritoghosh.ttt.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.theme.TimeLabelTextStyle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var isPicking by remember { mutableStateOf(false) }
    LabelledButton(label, formatTime(time), onClick = { isPicking = true }, modifier)
    if (isPicking) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { isPicking = false },
            confirmButton = {
                TextButton(onClick = {
                    isPicking = false
                    onTimeChange(LocalTime.of(state.hour, state.minute))
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { isPicking = false }) { Text(stringResource(R.string.editor_cancel)) }
            },
            text = { TimePicker(state = state) },
        )
    }
}

/** The date picker works in UTC midnights, so dates are converted with ZoneOffset.UTC on purpose. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onDateChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var isPicking by remember { mutableStateOf(false) }
    LabelledButton(label, formatDayLabel(date), onClick = { isPicking = true }, modifier)
    if (isPicking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { isPicking = false },
            confirmButton = {
                TextButton(onClick = {
                    isPicking = false
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { isPicking = false }) { Text(stringResource(R.string.editor_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun LabelledButton(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onClick) { Text(value, style = TimeLabelTextStyle) }
    }
}
