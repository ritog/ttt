/**
 * MainActivity — the app's single activity; hosts the Compose UI, applies the theme setting,
 * and runs timer recovery on open.
 *
 * Depends on: TttApplication (for the AppContainer), ui/theme, ui/nav.
 * Must not: hold app logic or state; that belongs in ViewModels and repositories.
 *
 * A tap on a timer notification starts (or re-uses, launchMode singleTop) this activity with
 * EXTRA_OPEN_TRACK; the flag is passed to AppNav, which switches to the Track tab.
 */
package com.ritoghosh.ttt

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ritoghosh.ttt.core.settings.ThemeChoice
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
        if ((application as TttApplication).consumeRestoredNotice()) {
            Toast.makeText(this, R.string.restore_done, Toast.LENGTH_LONG).show()
        }
        val themeChoice = container.settingsRepository.observeTheme()
        setContent {
            val theme by themeChoice.collectAsStateWithLifecycle(initialValue = ThemeChoice.SYSTEM)
            val isDark = when (theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme, not only the system's.
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                )
            }
            TttTheme(darkTheme = isDark) {
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

    /** Leaving the app (single activity) is the moment for an auto-backup if one is due (SPEC 8.4). */
    override fun onStop() {
        super.onStop()
        container.autoBackup.runIfDue()
    }

    companion object {
        const val EXTRA_OPEN_TRACK = "com.ritoghosh.ttt.OPEN_TRACK"
    }
}
