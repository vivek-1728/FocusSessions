package com.focussessions.app.testutils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Controllable fake clock for time-dependent unit tests.
 */
class FakeClock(var currentTimeMs: Long = System.currentTimeMillis()) {

    fun advanceByMs(ms: Long) {
        currentTimeMs += ms
    }

    fun advanceBySeconds(seconds: Long) {
        currentTimeMs += seconds * 1000L
    }

    fun advanceByMinutes(minutes: Long) {
        currentTimeMs += minutes * 60_000L
    }

    fun advanceByHours(hours: Long) {
        currentTimeMs += hours * 3_600_000L
    }

    fun setTime(epochMs: Long) {
        currentTimeMs = epochMs
    }

    fun now(): Instant = Instant.ofEpochMilli(currentTimeMs)

    fun localDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(now(), zoneId)
}
