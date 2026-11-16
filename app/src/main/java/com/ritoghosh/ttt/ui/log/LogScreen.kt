/**
 * LogScreen — the Log (from the menu): tag filter, range chips, time per tag, then sessions
 * grouped by day with day totals. Tap a session to edit its note or delete it (SPEC 9.4).
 *
 * Depends on: LogViewModel, LogDialogs, ui/components, ui/theme.
 * Must not: compute totals; LogUiState has them.
 */
package com.ritoghosh.ttt.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.stats.TagTotal
import com.ritoghosh.ttt.ui.components.TagFilterRow
import com.ritoghosh.ttt.ui.components.formatDayLabel
import com.ritoghosh.ttt.ui.components.formatDuration
import com.ritoghosh.ttt.ui.components.formatTimeRange
import com.ritoghosh.ttt.ui.theme.ListRowHeight
import com.ritoghosh.ttt.ui.theme.Spacing
import com.ritoghosh.ttt.ui.theme.TimeLabelTextStyle
import java.time.ZoneId

/** Room for "14:00–14:25" in tabular figures, so titles line up. */
private val TIME_COLUMN_WIDTH = 96.dp

@Composable
fun LogRoute(viewModel: LogViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.isLoaded) return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        item(key = "filter") {
            TagFilterRow(state.tags, state.activeTag, viewModel::onTagFilterClick, viewModel::onClearTagFilter)
        }
        item(key = "range") { RangeChips(state.range, viewModel::onRangeSelected) }
        item(key = "totals") { TagTotalsSection(state.tagTotals) }
        state.days.forEach { day ->
            item(key = "day-${day.date}") { DayHeader(formatDayLabel(day.date), formatDuration(day.totalMs)) }
            items(day.entries, key = { it.entry.id }) { row ->
                EntryRow(row, state.zone, onClick = { viewModel.onEntryClick(row) })
            }
        }
    }
    LogDialogs(state, viewModel)
}

@Composable
private fun RangeChips(selected: LogRange, onSelect: (LogRange) -> Unit) {
    Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        LogRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(rangeLabel(range)) },
            )
        }
    }
}

/** "Time per tag": one row per tag; a note explains that a two-tag task counts in both. */
@Composable
private fun TagTotalsSection(totals: List<TagTotal>) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(stringResource(R.string.log_time_per_tag), style = MaterialTheme.typography.titleMedium)
        if (totals.isEmpty()) {
            QuietText(stringResource(R.string.log_empty))
            return@Column
        }
        totals.forEach { total ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = total.tag ?: stringResource(R.string.log_no_tag),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(total.workedMs), style = TimeLabelTextStyle)
            }
        }
        if (totals.count { it.tag != null } > 1) QuietText(stringResource(R.string.log_tag_overlap_note))
    }
}

@Composable
private fun DayHeader(label: String, total: String) {
    Column(Modifier.padding(top = Spacing.l)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(total, style = TimeLabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "14:00–14:25  Write outline  25m", with the note underneath when there is one. */
@Composable
private fun EntryRow(row: LogEntryRow, zone: ZoneId, onClick: () -> Unit) {
    val entry = row.entry
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(
                text = formatTimeRange(entry.startedAtMs, entry.endedAtMs, zone),
                style = TimeLabelTextStyle,
                modifier = Modifier.width(TIME_COLUMN_WIDTH),
            )
            Text(row.taskTitle, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(formatDuration(entry.workedMs), style = TimeLabelTextStyle)
        }
        if (entry.note.isNotBlank()) {
            Text(
                entry.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TIME_COLUMN_WIDTH + Spacing.m),
            )
        }
    }
}

@Composable
private fun QuietText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun rangeLabel(range: LogRange): String = when (range) {
    LogRange.TODAY -> stringResource(R.string.log_range_today)
    LogRange.THIS_WEEK -> stringResource(R.string.log_range_week)
    LogRange.THIS_MONTH -> stringResource(R.string.log_range_month)
}
