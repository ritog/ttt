/**
 * TrackParts — the quieter pieces of the Track screen: the planned-today line, permission hints,
 * the note card, and today's sessions row (SPEC 7.3, 7.4, 9.3, 10.1).
 *
 * Depends on: TrackViewModel (note text), ui/components/TimeFormat.kt, ui/theme.
 * Must not: decide anything; it shows state and forwards taps.
 */
package com.ritoghosh.ttt.ui.track

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.components.formatDuration
import com.ritoghosh.ttt.ui.components.formatTimeRange
import com.ritoghosh.ttt.ui.theme.CornerRadius
import com.ritoghosh.ttt.ui.theme.Spacing

/** At most this many dots in the Today row; the count says the rest. */
private const val MAX_TODAY_DOTS = 8

/** "Planned today 14:00–16:00": information, not a constraint (SPEC 7.4). */
@Composable
fun PlannedTodayLine(state: TrackUiState) {
    val block = state.plannedToday ?: return
    QuietText(stringResource(R.string.timer_planned_today, formatTimeRange(block.startMs, block.endMs, state.zone)))
}

@Composable
fun PermissionHints(notificationsAllowed: Boolean, canScheduleExactAlarms: Boolean, onAllowExactAlarms: () -> Unit) {
    if (!notificationsAllowed) QuietText(stringResource(R.string.timer_hint_notifications))
    if (!canScheduleExactAlarms) {
        Column {
            QuietText(stringResource(R.string.timer_hint_exact_alarms))
            TextButton(onClick = onAllowExactAlarms) { Text(stringResource(R.string.timer_allow_exact_alarms)) }
        }
    }
}

/** "Note for this session": the session is already saved, so Skip loses nothing (SPEC 7.3). */
@Composable
fun NoteCard(viewModel: TrackViewModel) {
    Surface(
        shape = RoundedCornerShape(CornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.note_card_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = viewModel.noteText,
                onValueChange = viewModel::onNoteTextChange,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End)) {
                TextButton(onClick = viewModel::onSkipNote) { Text(stringResource(R.string.note_card_skip)) }
                Button(onClick = viewModel::onSaveNote) { Text(stringResource(R.string.note_card_save)) }
            }
        }
    }
}

/** "● ● ● Today: 3 sessions · 1h 15m". The dots are decoration; the words carry the meaning. */
@OptIn(ExperimentalComposeUiApi::class) // pluralStringResource, in case it is still marked experimental
@Composable
fun TodayRow(sessionCount: Int, workedMs: Long) {
    val dots = "● ".repeat(minOf(sessionCount, MAX_TODAY_DOTS)).trim()
    val words = pluralStringResource(R.plurals.timer_today, sessionCount, sessionCount, formatDuration(workedMs))
    QuietText(if (dots.isEmpty()) words else "$dots  $words")
}

@Composable
private fun QuietText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Opens the system page where the user can allow exact alarms for this app (Android 12+). */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return // always allowed on Android 11
    val packageUri = Uri.fromParts("package", context.packageName, null)
    try {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
    } catch (missingPage: ActivityNotFoundException) {
        // Some phones lack that page; the app's general settings page is the next best place.
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
}
