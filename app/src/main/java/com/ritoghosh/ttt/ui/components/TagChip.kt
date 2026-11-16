/**
 * TagChip — a small, read-only, tinted label showing one tag (used in list rows).
 *
 * Depends on: ui/theme (tagColor, Spacing, CornerRadius), Compose material3.
 * Must not: be clickable; filtering uses TagFilterRow and editing uses the editor's chips.
 */
package com.ritoghosh.ttt.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ritoghosh.ttt.ui.theme.CornerRadius
import com.ritoghosh.ttt.ui.theme.Spacing
import com.ritoghosh.ttt.ui.theme.tagColor

@Composable
fun TagChip(name: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius),
        color = tagColor(name),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
}
