/**
 * MainActivity — the app's single activity; hosts the Compose UI.
 *
 * Depends on: TttApplication (for the AppContainer), ui/theme, ui/nav.
 * Must not: hold app logic or state; that belongs in ViewModels and repositories.
 */
package com.ritoghosh.ttt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ritoghosh.ttt.ui.nav.AppNav
import com.ritoghosh.ttt.ui.theme.TttTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The manifest names TttApplication, so this cast always holds.
        val container = (application as TttApplication).container
        setContent {
            TttTheme {
                AppNav(container)
            }
        }
    }
}
