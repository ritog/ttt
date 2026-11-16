/**
 * PlanScreen — the Timeblock tab: tag filter, day navigation, the day timeline, and "+" for a new block (SPEC 9.2).
 *
 * Depends on: PlanViewModel, BlockEditorViewModel, DayTimeline, BlockEditorSheet, ui/nav/ScreenRequests.kt.
 * Must not: keep state beyond scroll position; PlanRoute wires the ViewModels.
 */
package com.ritoghosh.ttt.ui.plan

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.components.TagFilterRow
import com.ritoghosh.ttt.ui.components.formatDayLabel
import com.ritoghosh.ttt.ui.nav.ScreenRequests
import com.ritoghosh.ttt.ui.theme.Spacing

/** On other days the timeline opens at 08:00; on today, an hour before now. */
private const val DEFAULT_SCROLL_HOUR = 8
private const val MS_PER_HOUR = 3_600_000L

@Composable
fun PlanRoute(
    planViewModel: PlanViewModel,
    editorViewModel: BlockEditorViewModel,
    screenRequests: ScreenRequests,
    onStartTimer: (Long) -> Unit,
) {
    val state by planViewModel.uiState.collectAsStateWithLifecycle()
    val editor by editorViewModel.uiState.collectAsStateWithLifecycle()
    val scheduleRequest by screenRequests.scheduleBlockForTask.collectAsStateWithLifecycle()

    // "Schedule a block" from the task editor: open a new block for that task, on the shown day.
    LaunchedEffect(scheduleRequest, state.isLoaded) {
        val taskId = scheduleRequest
        if (taskId != null && state.isLoaded) {
            editorViewModel.openNew(state.date, taskId)
            screenRequests.consumeBlockRequest()
        }
    }
    PlanScreen(
        state = state,
        onPreviousDay = planViewModel::onPreviousDay,
        onNextDay = planViewModel::onNextDay,
        onToday = planViewModel::onToday,
        onTagFilterClick = planViewModel::onTagFilterClick,
        onClearTagFilter = planViewModel::onClearTagFilter,
        onBlockClick = editorViewModel::openExisting,
        onAddBlock = { editorViewModel.openNew(state.date) },
    )
    editor?.let { BlockEditorSheet(state = it, viewModel = editorViewModel, onStartTimer = onStartTimer) }
}

@Composable
fun PlanScreen(
    state: PlanUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onTagFilterClick: (String) -> Unit,
    onClearTagFilter: () -> Unit,
    onBlockClick: (Long) -> Unit,
    onAddBlock: () -> Unit,
) {
    if (!state.isLoaded) return
    val scrollState = rememberScrollState()
    ScrollToStartingHour(state, scrollState)
    Column(Modifier.fillMaxSize()) {
        TagFilterRow(state.tags, state.activeTag, onTagFilterClick, onClearTagFilter)
        DayHeader(state, onPreviousDay, onNextDay, onToday)
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DayTimeline(state, scrollState, onBlockClick, Modifier.fillMaxWidth())
            if (state.blocks.isEmpty()) {
                Text(
                    text = stringResource(R.string.plan_empty_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.l),
                )
            }
            FloatingActionButton(
                onClick = onAddBlock,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp), // no shadows (SPEC 15.2)
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.cd_add_block))
            }
        }
    }
}

@Composable
private fun DayHeader(state: PlanUiState, onPreviousDay: () -> Unit, onNextDay: () -> Unit, onToday: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.cd_previous_day))
        }
        Text(
            text = formatDayLabel(state.date),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNextDay) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.cd_next_day))
        }
        TextButton(onClick = onToday, enabled = !state.isToday) { Text(stringResource(R.string.plan_today)) }
    }
}

/** Scrolls to a useful hour whenever the shown date changes, not on every minute tick. */
@Composable
private fun ScrollToStartingHour(state: PlanUiState, scrollState: ScrollState) {
    val density = LocalDensity.current
    LaunchedEffect(state.date) {
        val targetMs = if (state.isToday) {
            maxOf(state.dayStartMs, state.nowMs - MS_PER_HOUR)
        } else {
            state.dayStartMs + DEFAULT_SCROLL_HOUR * MS_PER_HOUR
        }
        val offsetPx = with(density) { minutesFromStart(targetMs, state.dayStartMs).roundToPx() }
        scrollState.scrollTo(offsetPx)
    }
}
