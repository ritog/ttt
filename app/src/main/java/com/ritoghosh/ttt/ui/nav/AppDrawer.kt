/**
 * AppDrawer — the hamburger menu: Log, Settings, About (SPEC 9).
 *
 * Depends on: Destination.kt, ui/theme, Compose material3.
 * Must not: navigate by itself or grow extra items (the menu is fixed by the spec).
 */
package com.ritoghosh.ttt.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.theme.Spacing

/** Drawer contents. [onItemClick] receives the chosen menu destination. */
@Composable
fun AppDrawer(onItemClick: (Destination) -> Unit) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        )
        Destination.menuItems.forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.labelRes)) },
                selected = false,
                onClick = { onItemClick(item) },
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
        }
    }
}
