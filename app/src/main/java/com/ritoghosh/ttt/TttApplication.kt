/**
 * TttApplication — the process-wide entry point; owns the single AppContainer.
 *
 * Depends on: AppContainer.
 * Must not: do slow work at startup. Creating notification channels is a quick system call;
 * everything else in the container is created lazily.
 */
package com.ritoghosh.ttt

import android.app.Application

class TttApplication : Application() {

    /** Built once, on first use, and kept for the life of the process. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Channels must exist before the first notification, including one posted by the alarm receiver.
        container.notifications.createChannels()
    }
}
