/**
 * AppNav — the app's frame: hamburger drawer, top bar, bottom tabs, and the screen in between.
 *
 * Depends on: Destination.kt, AppBars.kt, AppDrawer.kt, TagFilterHolder.kt, screen composables, AppContainer.
 * Must not: contain screen logic. Each screen gets what it needs and nothing more.
 * ViewModels are built here with `viewModel { ... }` factories that read from AppContainer (SPEC 4.4).
 *
 * Main tabs (Todo, Plan, Track) keep their own back stack state when switching tabs.
 * Menu screens (Log, Settings, About) open on top of the current tab and show a back arrow.
 */
package com.ritoghosh.ttt.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ritoghosh.ttt.AppContainer
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.ui.about.AboutScreen
import com.ritoghosh.ttt.ui.components.EmptyState
import com.ritoghosh.ttt.ui.todo.TaskEditorViewModel
import com.ritoghosh.ttt.ui.todo.TodoRoute
import com.ritoghosh.ttt.ui.todo.TodoViewModel
import kotlinx.coroutines.launch

/** Short functional transitions only (SPEC 15.2). */
private const val SCREEN_TRANSITION_MS = 150

/** Root composable: builds the navigation frame around every screen. */
@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()
    // Created here, outside the NavHost, so it is scoped to the activity and shared by every screen.
    val tagFilter = viewModel { TagFilterHolder() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(backStackEntry?.destination?.route) ?: Destination.Todo

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
            NavHost(
                navController = navController,
                startDestination = Destination.Todo.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_MS)) },
                exitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_MS)) },
            ) {
                composable(Destination.Todo.route) {
                    val todoViewModel = viewModel { TodoViewModel(container.taskRepository, tagFilter) }
                    val editorViewModel = viewModel { TaskEditorViewModel(container.taskRepository) }
                    TodoRoute(todoViewModel, editorViewModel)
                }
                // Placeholders until each screen is built (Plan M3, Track M4, Log/Settings M5).
                composable(Destination.Plan.route) { EmptyState(R.string.placeholder_plan) }
                composable(Destination.Track.route) { EmptyState(R.string.placeholder_track) }
                composable(Destination.Log.route) { EmptyState(R.string.placeholder_log) }
                composable(Destination.Settings.route) { EmptyState(R.string.placeholder_settings) }
                composable(Destination.About.route) { AboutScreen(container.appVersionName) }
            }
        }
    }
}

/**
 * Switches to a main tab the standard way: one copy of each tab on the stack, and each tab
 * remembers its own state (scroll position, etc.) when the user comes back to it.
 */
private fun NavController.navigateToTab(tab: Destination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
