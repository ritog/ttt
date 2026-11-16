/**
 * TestTime — a fake Clock and small helpers that turn readable local times into epoch ms for tests.
 *
 * Depends on: core/time/Clock.kt, java.time.
 * Must not: be used outside tests.
 */
package com.ritoghosh.ttt.core.testing

import com.ritoghosh.ttt.core.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The zone most tests use (India, +05:30, no daylight saving). */
val KOLKATA: ZoneId = ZoneId.of("Asia/Kolkata")

/** A zone with daylight saving, for 23- and 25-hour days. */
val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/** A clock that returns whatever time the test sets. */
class FakeClock(var nowMs: Long, private val zone: ZoneId = KOLKATA) : Clock {
    override fun nowMs(): Long = nowMs

    override fun zone(): ZoneId = zone
}

/** Epoch ms of [date] at local time [time] ("HH:mm") in [zone]. Example: at("2026-11-16", "14:05"). */
fun at(date: String, time: String, zone: ZoneId = KOLKATA): Long =
    LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

/** Minutes as milliseconds. */
fun minutes(count: Int): Long = count * 60_000L

/** Hours as milliseconds. */
fun hours(count: Int): Long = count * 3_600_000L
