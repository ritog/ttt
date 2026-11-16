/**
 * EmptyState — one quiet, centred sentence for a screen with nothing to show (SPEC 15.2: "Empty states are one sentence").
 *
 * Depends on: ui/theme, Compose material3.
 * Must not: add illustrations, buttons or extra lines.
 */
package com.ritoghosh.ttt.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.ritoghosh.ttt.ui.theme.Spacing

/** Shows the string [messageRes] centred in the available space. */
@Composable
fun EmptyState(@StringRes messageRes: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
