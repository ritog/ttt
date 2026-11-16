/**
 * Destination — every screen the app can show, with its route, label and (for tabs) icon.
 *
 * Depends on: app resources (R) only.
 * Must not: hold screen state or navigation logic; AppNav.kt does the navigating.
 *
 * Routes are plain strings on purpose: type-safe routes would need the kotlinx-serialization
 * plugin, which is not an allowed dependency (SPEC 3).
 */
package com.ritoghosh.ttt.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ritoghosh.ttt.R

enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    /** Icon for the bottom bar; null for screens reached from the menu. */
    @DrawableRes val tabIconRes: Int?,
) {
    Todo("todo", R.string.tab_todo, R.drawable.ic_tab_todo),
    Plan("plan", R.string.tab_plan, R.drawable.ic_tab_plan),
    Track("track", R.string.tab_track, R.drawable.ic_tab_track),
    Log("log", R.string.menu_log, null),
    Settings("settings", R.string.menu_settings, null),
    About("about", R.string.menu_about, null),
    ;

    /** True for the three bottom-bar tabs, which show the hamburger menu (SPEC 9). */
    val isMainTab: Boolean
        get() = tabIconRes != null

    companion object {
        /** Bottom-bar tabs, in display order. */
        val mainTabs: List<Destination> = entries.filter { it.isMainTab }

        /** Items in the hamburger menu, in display order. */
        val menuItems: List<Destination> = listOf(Log, Settings, About)

        /** The destination for a navigation route, or null if the route is unknown. */
        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}
