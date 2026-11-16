/**
 * Clock — the only source of "now" and of the device time zone (SPEC 4.3).
 *
 * Depends on: java.time only.
 * Must not: be bypassed. Nothing outside DeviceClock may call System.currentTimeMillis()
 * or ZoneId.systemDefault(); everything else receives a Clock, so tests can use a fake one.
 */
package com.ritoghosh.ttt.core.time

import java.time.ZoneId

interface Clock {
    /** Current instant as epoch milliseconds (UTC). */
    fun nowMs(): Long

    /** The device's current time zone, used to decide local days and weeks. */
    fun zone(): ZoneId
}

/**
 * The production clock. The zone is read on every call (not cached) because the user may
 * change time zones while the app is running.
 */
class DeviceClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
