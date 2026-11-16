/**
 * AppNav — the app's frame: hamburger drawer, top bar, bottom tabs, and the screen in between.
 *
 * Depends on: Destination.kt, AppBars.kt, AppDrawer.kt, AppNavHost.kt, TagFilterHolder.kt,
 * ScreenRequests.kt, AppContainer.
 * Must not: contain screen logic. Which screen shows what is in AppNavHost.kt.
 *
 * Main tabs (Todo, Plan, Track) keep their own back stack state when switching tabs.
 * Menu screens (Log, Settings, About) open on top of the current tab and show a back arrow.
 */
package com.ritoghosh.ttt.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ritoghosh.ttt.AppContainer
import kotlinx.coroutines.launch

/** Root composable: builds the navigation frame around every screen. */
@Composable
fun AppNav(container: AppContainer, openTrackRequested: Boolean, onOpenTrackHandled: () -> Unit) {
    val navController = rememberNavController()
    // Created here, outside the NavHost, so they are scoped to the activity and shared by every screen.
    val tagFilter = viewModel { TagFilterHolder() }
    val screenRequests = viewModel { ScreenRequests() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(backStackEntry?.destination?.route) ?: Destination.Todo

    // A tap on a timer notification brings the user to the Track tab.
    LaunchedEffect(openTrackRequested) {
        if (openTrackRequested) {
            navController.navigateToTab(Destination.Track)
            onOpenTrackHandled()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // The swipe-to-open gesture only makes sense where the hamburger is shown.
        gesturesEnabled = current.isMainTab || drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                onItemClick = { item ->
                    scope.launch { drawerState.close() }
                    navController.navigate(item.route) { launchSingleTop = true }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    current = current,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBackClick = { navController.popBackStack() },
                )
            },
            bottomBar = {
                if (current.isMainTab) {
                    AppBottomBar(current = current, onTabClick = { navController.navigateToTab(it) })
                }
            },
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                container = container,
                tagFilter = tagFilter,
                screenRequests = screenRequests,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/**
 * Switches to a main tab the standard way: one copy of each tab on the stack, and each tab
 * remembers its own state (scroll position, etc.) when the user comes back to it.
 */
fun NavController.navigateToTab(tab: Destination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
