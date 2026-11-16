/**
 * TttApplication — the process-wide entry point; owns the single AppContainer.
 *
 * Depends on: AppContainer.
 * Must not: do work at startup beyond creating the container lazily (keeps cold start fast).
 */
package com.ritoghosh.ttt

import android.app.Application

class TttApplication : Application() {

    /** Built once, on first use, and kept for the life of the process. */
    val container: AppContainer by lazy { AppContainer(this) }
}
