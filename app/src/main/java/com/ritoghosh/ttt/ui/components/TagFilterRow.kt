/**
 * TagFilterRow — the chip row for the global tag filter, with a "Clear" chip while a filter is active (SPEC 6.5).
 *
 * Depends on: ui/theme, Compose material3.
 * Must not: hold the selection itself; TagFilterHolder does, through the screen's ViewModel.
 *
 * The Clear chip sits outside the scrolling row, so it stays visible however far the tags scroll.
 * A scrolling row is used instead of a sheet for many tags (recorded in DECISIONS.md).
 */
package com.ritoghosh.ttt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.theme.Spacing

/** Shows nothing when there are no tags. [onTagClick] toggles a tag; [onClear] removes the filter. */
@Composable
fun TagFilterRow(
    tags: List<String>,
    activeTag: String?,
    onTagClick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (activeTag != null) {
            AssistChip(
                onClick = onClear,
                label = { Text(stringResource(R.string.todo_filter_clear)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.cd_clear_filter),
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.padding(start = Spacing.l),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            items(tags, key = { it }) { tag ->
                FilterChip(
                    selected = tag == activeTag,
                    onClick = { onTagClick(tag) },
                    label = { Text(tag) },
                )
            }
        }
    }
}
