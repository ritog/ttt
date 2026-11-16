/**
 * AppNavHost — which composable each route shows, and how each screen's ViewModels are built.
 *
 * Depends on: AppContainer, Destination.kt, TagFilterHolder.kt, ScreenRequests.kt, the screens.
 * Must not: contain screen logic. ViewModels are built with `viewModel { ... }` factories that
 * read from AppContainer (SPEC 4.4): no reflection, no DI framework.
 */
package com.ritoghosh.ttt.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ritoghosh.ttt.AppContainer
import com.ritoghosh.ttt.ui.about.AboutScreen
import com.ritoghosh.ttt.ui.log.LogRoute
import com.ritoghosh.ttt.ui.log.LogViewModel
import com.ritoghosh.ttt.ui.plan.BlockEditorViewModel
import com.ritoghosh.ttt.ui.plan.PlanRoute
import com.ritoghosh.ttt.ui.plan.PlanViewModel
import com.ritoghosh.ttt.ui.settings.SettingsRoute
import com.ritoghosh.ttt.ui.settings.SettingsViewModel
import com.ritoghosh.ttt.ui.todo.TaskEditorViewModel
import com.ritoghosh.ttt.ui.todo.TodoRoute
import com.ritoghosh.ttt.ui.todo.TodoViewModel
import com.ritoghosh.ttt.ui.track.TrackRoute
import com.ritoghosh.ttt.ui.track.TrackViewModel

/** Short functional transitions only (SPEC 15.2). */
private const val SCREEN_TRANSITION_MS = 150

@Composable
fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    tagFilter: TagFilterHolder,
    screenRequests: ScreenRequests,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Todo.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_MS)) },
    ) {
        composable(Destination.Todo.route) {
            val todoViewModel = viewModel {
                TodoViewModel(
                    container.taskRepository,
                    container.blockRepository,
                    container.entryRepository,
                    container.clock,
                    tagFilter,
                )
            }
            val editorViewModel = viewModel { TaskEditorViewModel(container.taskRepository) }
            TodoRoute(
                todoViewModel = todoViewModel,
                editorViewModel = editorViewModel,
                onScheduleBlock = { taskId ->
                    screenRequests.requestBlockFor(taskId)
                    navController.navigateToTab(Destination.Plan)
                },
                onStartTimer = { taskId -> openTrackFor(taskId, screenRequests, navController) },
                restoreService = container.restoreService,
            )
        }
        composable(Destination.Plan.route) {
            val planViewModel = viewModel {
                PlanViewModel(
                    container.blockRepository,
                    container.entryRepository,
                    container.taskRepository,
                    container.clock,
                    tagFilter,
                )
            }
            val editorViewModel = viewModel {
                BlockEditorViewModel(container.blockRepository, container.taskRepository, container.clock)
            }
            PlanRoute(
                planViewModel = planViewModel,
                editorViewModel = editorViewModel,
                screenRequests = screenRequests,
                onStartTimer = { taskId -> openTrackFor(taskId, screenRequests, navController) },
            )
        }
        composable(Destination.Track.route) {
            val trackViewModel = viewModel {
                TrackViewModel(
                    timerRepository = container.timerRepository,
                    taskRepository = container.taskRepository,
                    blockRepository = container.blockRepository,
                    entryRepository = container.entryRepository,
                    clock = container.clock,
                    tagFilter = tagFilter,
                )
            }
            TrackRoute(trackViewModel, screenRequests)
        }
        composable(Destination.Log.route) {
            val logViewModel = viewModel {
                LogViewModel(container.entryRepository, container.taskRepository, container.clock, tagFilter)
            }
            LogRoute(logViewModel)
        }
        composable(Destination.Settings.route) {
            SettingsRoute(
                viewModel {
                    SettingsViewModel(
                        container.settingsRepository,
                        container.backupService,
                        container.reportService,
                        container.clock,
                    )
                },
                container.restoreService,
            )
        }
        composable(Destination.About.route) { AboutScreen(container.appVersionName) }
    }
}

/** "Start timer" from a task or a block: open Track with that task selected (it does not start by itself). */
private fun openTrackFor(taskId: Long, screenRequests: ScreenRequests, navController: NavHostController) {
    screenRequests.requestTimerFor(taskId)
    navController.navigateToTab(Destination.Track)
}
