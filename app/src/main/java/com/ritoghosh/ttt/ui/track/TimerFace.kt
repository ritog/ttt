/**
 * TimerFace and TimerButtons — the running timer: phase label, task title, big time, thin progress
 * line, and the buttons for the current state (SPEC 9.3, 15.3).
 *
 * Depends on: TrackUiState, core/timer (TimerMath), ui/components/TimeFormat.kt, ui/theme.
 * Must not: compute time itself beyond formatting what TimerMath returns.
 *
 * Lots of empty space; one prominent button; secondary actions small and quiet.
 * The accent colour marks a running timer and overtime.
 */
package com.ritoghosh.ttt.ui.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.model.TimerPhase
import com.ritoghosh.ttt.core.model.TimerStatus
import com.ritoghosh.ttt.core.timer.elapsedMs
import com.ritoghosh.ttt.core.timer.isBreak
import com.ritoghosh.ttt.core.timer.isInOvertime
import com.ritoghosh.ttt.core.timer.overtimeMs
import com.ritoghosh.ttt.core.timer.remainingMs
import com.ritoghosh.ttt.ui.components.formatClock
import com.ritoghosh.ttt.ui.theme.Spacing
import com.ritoghosh.ttt.ui.theme.TimerTextStyle

@Composable
fun TimerFace(state: TrackUiState) {
    val timer = state.timer
    val now = state.nowMs
    val inOvertime = timer.isInOvertime(now)
    val timeText = when {
        timer.status == TimerStatus.AWAITING_START -> formatClock(timer.phaseLengthMs, roundUp = true)
        inOvertime -> "+" + formatClock(timer.overtimeMs(now))
        else -> formatClock(timer.remainingMs(now), roundUp = true)
    }
    val progress = if (timer.status == TimerStatus.AWAITING_START || timer.phaseLengthMs <= 0) {
        0f
    } else {
        (timer.elapsedMs(now).toFloat() / timer.phaseLengthMs).coerceIn(0f, 1f)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = phaseLine(state).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.task?.title.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        Text(
            text = timeText,
            style = TimerTextStyle,
            color = if (timer.status == TimerStatus.RUNNING) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(vertical = Spacing.l),
        )
        ProgressLine(progress)
    }
}

/** A thin line, full once the planned length is reached. */
@Composable
private fun ProgressLine(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(MaterialTheme.colorScheme.primary))
    }
}

/**
 * Primary button by state (SPEC 9.3): Pause/Resume while counting down, Finish during overtime,
 * Start next when waiting. Secondary: Pause or Resume during overtime, Skip break, Stop.
 */
@Composable
fun TimerButtons(state: TrackUiState, viewModel: TrackViewModel) {
    val timer = state.timer
    val inOvertime = timer.isInOvertime(state.nowMs)
    val primary: Pair<Int, () -> Unit> = when {
        timer.status == TimerStatus.AWAITING_START -> {
            val label = if (timer.phase == TimerPhase.WORK) R.string.timer_start_work else R.string.timer_start_break
            label to viewModel::onStartNext
        }
        inOvertime -> R.string.timer_finish to viewModel::onFinishPressed
        timer.status == TimerStatus.RUNNING -> R.string.timer_pause to viewModel::onPause
        else -> R.string.timer_resume to viewModel::onResume
    }
    Button(onClick = primary.second, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(primary.first))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        if (inOvertime && timer.status == TimerStatus.RUNNING) {
            TextButton(onClick = viewModel::onPause) { Text(stringResource(R.string.timer_pause)) }
        }
        if (inOvertime && timer.status == TimerStatus.PAUSED) {
            TextButton(onClick = viewModel::onResume) { Text(stringResource(R.string.timer_resume)) }
        }
        if (timer.status == TimerStatus.AWAITING_START && timer.isBreak) {
            TextButton(onClick = viewModel::onSkipBreak) { Text(stringResource(R.string.timer_skip_break)) }
        }
        TextButton(onClick = viewModel::onStopPressed) { Text(stringResource(R.string.timer_stop)) }
    }
}

/** "Work", "Work · paused", "Next: Short break". */
@Composable
private fun phaseLine(state: TrackUiState): String {
    val phase = phaseName(state.timer.phase)
    return when (state.timer.status) {
        TimerStatus.AWAITING_START -> stringResource(R.string.timer_next, phase)
        TimerStatus.PAUSED -> stringResource(R.string.timer_phase_paused, phase)
        else -> phase
    }
}

@Composable
private fun phaseName(phase: TimerPhase): String = when (phase) {
    TimerPhase.WORK -> stringResource(R.string.timer_phase_work)
    TimerPhase.SHORT_BREAK -> stringResource(R.string.timer_phase_short_break)
    TimerPhase.LONG_BREAK -> stringResource(R.string.timer_phase_long_break)
    TimerPhase.NONE -> ""
}
