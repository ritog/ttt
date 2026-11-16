/**
 * TagFilterHolder — the one global tag filter shared by Todo, Plan, Track and Log (SPEC 4.5, 6.5).
 *
 * Depends on: AndroidX ViewModel, kotlinx.coroutines.
 * Must not: persist the filter. It survives screen rotation (it is an activity-scoped ViewModel)
 * but resets when the app process restarts, as the spec asks.
 */
package com.ritoghosh.ttt.ui.nav

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TagFilterHolder : ViewModel() {

    private val selected = MutableStateFlow<String?>(null)

    /** The selected tag, or null when no filter is active. */
    val selectedTag: StateFlow<String?> = selected.asStateFlow()

    /** Selects [tag], or clears the filter when [tag] is null. */
    fun select(tag: String?) {
        selected.value = tag
    }
}
