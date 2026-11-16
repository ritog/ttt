/**
 * AppBars — the top bar (hamburger or back arrow + title) and the bottom tab bar.
 *
 * Depends on: Destination.kt, ui/theme, Compose material3.
 * Must not: navigate by itself; it only reports clicks to AppNav.
 */
package com.ritoghosh.ttt.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ritoghosh.ttt.R

/** Top bar: hamburger on the main tabs, back arrow on menu screens. */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar is still marked experimental in Material 3.
@Composable
fun AppTopBar(current: Destination, onMenuClick: () -> Unit, onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(current.labelRes), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (current.isMainTab) {
                IconButton(onClick = onMenuClick) {
                    Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.cd_open_menu))
                }
            } else {
                IconButton(onClick = onBackClick) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** Bottom bar with the three main tabs, separated from content by a hairline (no shadow). */
@Composable
fun AppBottomBar(current: Destination, onTabClick: (Destination) -> Unit) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            Destination.mainTabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab == current,
                    onClick = { onTabClick(tab) },
                    icon = {
                        // The label below already names the tab, so the icon needs no description.
                        tab.tabIconRes?.let { Icon(painterResource(it), contentDescription = null) }
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                )
            }
        }
    }
}
