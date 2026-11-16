/**
 * MainActivity — the app's single activity; hosts the Compose UI and runs timer recovery on open.
 *
 * Depends on: TttApplication (for the AppContainer), ui/theme, ui/nav.
 * Must not: hold app logic or state; that belongs in ViewModels and repositories.
 *
 * A tap on a timer notification starts (or re-uses, launchMode singleTop) this activity with
 * EXTRA_OPEN_TRACK; the flag is passed to AppNav, which switches to the Track tab.
 */
package com.ritoghosh.ttt

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.ritoghosh.ttt.ui.nav.AppNav
import com.ritoghosh.ttt.ui.theme.TttTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val openTrackRequested = mutableStateOf(false)

    private val container: AppContainer
        get() = (application as TttApplication).container // the manifest names TttApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTrackRequested.value = intent.getBooleanExtra(EXTRA_OPEN_TRACK, false)
        setContent {
            TttTheme {
                AppNav(
                    container = container,
                    openTrackRequested = openTrackRequested.value,
                    onOpenTrackHandled = { openTrackRequested.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TRACK, false)) openTrackRequested.value = true
    }

    /** Every time the app comes to the front: end an expired break, re-schedule a lost alarm (SPEC 10.2). */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { container.timerRepository.recoverOnAppStart() }
    }

    companion object {
        const val EXTRA_OPEN_TRACK = "com.ritoghosh.ttt.OPEN_TRACK"
    }
}
