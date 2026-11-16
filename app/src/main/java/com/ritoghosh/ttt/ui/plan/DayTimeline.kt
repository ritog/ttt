/**
 * DayTimeline — the vertical hour grid of one day with its blocks and the current-time line (SPEC 9.2, 15.3).
 *
 * Depends on: PlanUiState (PlannedBlock), ui/theme, TimeFormat.kt.
 * Must not: load data or decide anything; it draws what it is given and reports block taps.
 *
 * The only custom component in the app (SPEC 15.2). Positions are minutes since local
 * midnight times DP_PER_MINUTE, so a 23- or 25-hour daylight-saving day is simply shorter or taller.
 * Two columns (SPEC 9.2): left the planned blocks, right thin neutral bars for the actual
 * sessions, so plan and actual can be compared at a glance.
 */
package com.ritoghosh.ttt.ui.plan

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.components.formatDuration
import com.ritoghosh.ttt.ui.components.formatTime
import com.ritoghosh.ttt.ui.components.formatTimeRange
import com.ritoghosh.ttt.ui.theme.Spacing
import com.ritoghosh.ttt.ui.theme.TimeLabelTextStyle
import com.ritoghosh.ttt.ui.theme.tagColor
import java.time.ZoneId

/** 72 dp per hour: a 15-minute block (the minimum) is 18 dp tall. */
val DP_PER_MINUTE: Dp = 1.2.dp

private const val MS_PER_MINUTE = 60_000L
private const val MS_PER_HOUR = 3_600_000L
private val HOUR_LABEL_WIDTH = 52.dp
private val BLOCK_CORNER = 6.dp

/** Blocks shorter than this show only their title, not the time range. */
private val SHOW_TIMES_MIN_HEIGHT = 44.dp
private val SESSION_COLUMN_WIDTH = 14.dp
private val SESSION_BAR_CORNER = 3.dp

@Composable
fun DayTimeline(
    state: PlanUiState,
    scrollState: ScrollState,
    onBlockClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalMinutes = ((state.dayEndMs - state.dayStartMs) / MS_PER_MINUTE).toInt()
    Row(
        modifier = modifier
            .verticalScroll(scrollState)
            .height(DP_PER_MINUTE * totalMinutes)
            .padding(end = Spacing.l),
    ) {
        HourLabels(state.dayStartMs, state.dayEndMs, state.zone)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            HourLines(state.dayStartMs, state.dayEndMs)
            state.blocks.forEach { planned ->
                BlockBox(planned, state, onClick = { onBlockClick(planned.block.id) })
            }
            if (state.isToday && state.nowMs in state.dayStartMs until state.dayEndMs) {
                Box(
                    Modifier
                        .offset(y = minutesFromStart(state.nowMs, state.dayStartMs))
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Box(Modifier.width(SESSION_COLUMN_WIDTH).fillMaxHeight()) {
            HourLines(state.dayStartMs, state.dayEndMs)
            state.sessions.forEach { SessionBar(it, state) }
        }
    }
}

/** A session as a thin bar; one that runs past midnight is cut at the end of the day it belongs to. */
@Composable
private fun SessionBar(session: DaySession, state: PlanUiState) {
    val entry = session.entry
    val endMs = minOf(entry.endedAtMs, state.dayEndMs)
    val height = (DP_PER_MINUTE * ((endMs - entry.startedAtMs) / MS_PER_MINUTE).toInt()).coerceAtLeast(2.dp)
    Box(
        Modifier
            .offset(y = minutesFromStart(entry.startedAtMs, state.dayStartMs))
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(SESSION_BAR_CORNER))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
    )
}

/** Distance from the top of the day for an instant. */
fun minutesFromStart(instantMs: Long, dayStartMs: Long): Dp =
    DP_PER_MINUTE * ((instantMs - dayStartMs) / MS_PER_MINUTE).toInt()

@Composable
private fun HourLabels(dayStartMs: Long, dayEndMs: Long, zone: ZoneId) {
    // Grows past HOUR_LABEL_WIDTH at large font scales so "16:00" never wraps onto two lines.
    Box(Modifier.widthIn(min = HOUR_LABEL_WIDTH).fillMaxHeight()) {
        hourMarks(dayStartMs, dayEndMs).forEach { markMs ->
            // Nudged up so the label sits centred on its line; the first one stays at the top.
            val y = (minutesFromStart(markMs, dayStartMs) - 8.dp).coerceAtLeast(0.dp)
            Text(
                text = formatTime(markMs, zone),
                style = TimeLabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(y = y).padding(start = Spacing.m, end = Spacing.xs),
            )
        }
    }
}

@Composable
private fun HourLines(dayStartMs: Long, dayEndMs: Long) {
    hourMarks(dayStartMs, dayEndMs).forEach { markMs ->
        HorizontalDivider(
            modifier = Modifier.offset(y = minutesFromStart(markMs, dayStartMs)),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun BlockBox(planned: PlannedBlock, state: PlanUiState, onClick: () -> Unit) {
    val block = planned.block
    val height = DP_PER_MINUTE * ((block.endMs - block.startMs) / MS_PER_MINUTE).toInt()
    val tint = planned.firstTag?.let { tagColor(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .offset(y = minutesFromStart(block.startMs, state.dayStartMs))
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(BLOCK_CORNER))
            .background(tint)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = 2.dp),
    ) {
        Text(
            planned.taskTitle,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (height >= SHOW_TIMES_MIN_HEIGHT) {
            val range = formatTimeRange(block.startMs, block.endMs, state.zone)
            val worked = planned.workedInBlockMs
            val text = if (worked > 0) {
                stringResource(R.string.plan_block_worked, range, formatDuration(worked))
            } else {
                range
            }
            Text(text, style = TimeLabelTextStyle)
        }
    }
}

/** Every whole hour from the day's start, as instants (so daylight-saving days get 23 or 25 marks). */
private fun hourMarks(dayStartMs: Long, dayEndMs: Long): List<Long> =
    generateSequence(dayStartMs) { it + MS_PER_HOUR }.takeWhile { it < dayEndMs }.toList()
