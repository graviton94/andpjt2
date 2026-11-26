package com.graviton94.notificationcleaner.core

/**
 * How long the player was away, for offline settlement (18 meta-and-ops: time-manipulation defence).
 *
 * - Same boot: the monotonic clock (elapsedRealtime) is the truth; the wall clock is ignored, so moving the
 *   phone's date does nothing.
 * - New boot: only the wall clock is left; trust it up to [rebootCapMs] (a reboot can't be used to skip days).
 * - Wall clock behind the last save (set back, or a future-dated save): 0.
 */
object AwayClock {
    const val rebootCapMs = 10 * 3_600_000L

    fun awayMs(lastWall: Long, nowWall: Long, lastElapsed: Long, nowElapsed: Long, sameBoot: Boolean): Long = when {
        sameBoot && nowElapsed >= lastElapsed -> nowElapsed - lastElapsed
        nowWall <= lastWall -> 0L
        else -> minOf(nowWall - lastWall, rebootCapMs)
    }
}
