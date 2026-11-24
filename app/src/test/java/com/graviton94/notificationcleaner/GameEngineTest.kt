package com.graviton94.notificationcleaner

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.math.BigInteger.ZERO
import java.math.BigInteger.valueOf

class GameEngineTest {
    private val now = 100_000L

    @Test
    fun firstSwipeIgnitesThreeRealMergesAndFourScoringWaves() {
        val initial = GameEngine.initial(now, seed = 19)
        val result = GameEngine.swipe(initial, rowId = 4, now = now)!!
        val state = result.state
        val score = state.lastScore!!
        assertEquals(11, state.energy)
        assertEquals(2, state.level)
        assertEquals(valueOf(117_645), score.points)
        assertEquals(valueOf(93), state.points)
        assertEquals(valueOf(14_967), score.cleared)
        assertEquals(7, state.bestCombo)
        assertEquals(0, state.pressure)
        assertEquals(7, state.rows.size)
        assertTrue(state.rows.any { it.app == NoticeApp.Photo })
        assertEquals(3, result.frames.count { it.kind == ImpactKind.Burst })
        assertEquals(listOf(42L, 210L, 1_470L, 13_230L).map(::valueOf),
            result.frames.filter { it.kind == ImpactKind.Overflow }.map { it.target.count })
        assertEquals(score.points, result.frames.last().earned)
        assertEquals(score.cleared, result.frames.last().cleared)
        assertEquals(4L, GameEngine.autoTarget(initial))
    }

    @Test
    fun uniqueBonusesFollowedByIndependentMultipliersDoNotChangeXp() {
        val initial = GameEngine.initial(now).copy(
            level = 3, appLevels = mapOf(NoticeApp.Chat to 2),
            regions = mapOf(Region.Domestic to valueOf(12), Region.Global to valueOf(8)),
            categories = mapOf(Category.Social to valueOf(20)),
            eventUntil = now + 12_000, eventTaps = 50,
        )
        val state = GameEngine.swipe(initial, 4, now)!!.state
        val report = state.lastScore!!
        // Chat is paid once for the unique app bonus across its physical burst and two overflow waves.
        val chat = report.appScores.single { it.app == NoticeApp.Chat }
        val region = 120L
        val category = 124L
        val expected = listOf(8L * 160 + 300, 42L * 5 * 160, 13_230L * 8 * 160)
            .sum() * region * category * 150 / 100_000_000
        assertEquals(valueOf(expected), chat.points)
        assertEquals(35L, report.xp)
        val claimed = GameEngine.claimReward(state)
        assertEquals(state.points, claimed.points)
        assertEquals(state.bestScore, claimed.bestScore)
        assertEquals(claimed, GameEngine.claimReward(claimed))
    }

    @Test
    fun heavyQueueKeepsScoringAfterLongRangeWithoutLinearReplayTime() {
        val initial = GameEngine.initial(now).copy(level = 300)
        val heavyRows = initial.rows.map { it.copy(count = it.count * BigInteger.TEN.pow(45)) }
        val result = GameEngine.swipe(initial.copy(rows = heavyRows), 4, now)!!
        val score = result.state.lastScore!!
        val waves = result.frames.filter { it.kind == ImpactKind.Overflow }
        assertEquals(107, score.combo)
        assertEquals(9, waves.size)
        assertEquals(104, waves.sumOf { it.steps })
        assertTrue(score.points > valueOf(Long.MAX_VALUE))
        assertTrue(waves.zipWithNext().all { (a, b) -> a.cleared < b.cleared && a.earned < b.earned })
        assertEquals(score.points, result.frames.last().earned)
        assertEquals(score.cleared, result.state.bestCleared)
    }

    @Test
    fun failedSwipeDoesNotSpendEnergyAndResumeRechargesWithinCap() {
        val start = GameEngine.initial(now).copy(energy = 0, nextChargeAt = now + 3000)
        assertNull(GameEngine.swipe(start, 4, now))
        assertNull(GameEngine.swipe(start, 88, now + 3200))
        val resumed = GameEngine.resume(start, now, now + 60_000)
        assertEquals(12, resumed.energy)
        assertEquals(2L, resumed.xp)
        assertEquals(start.rows, resumed.rows)
    }

    @Test
    fun drillTapsAreRateLimitedAndExpireAtBoundary() {
        val start = GameEngine.initial(now).copy(eventUntil = now + 60_000)
        val first = GameEngine.tapEvent(start, now)
        assertEquals(1, first.eventTaps)
        assertEquals(first, GameEngine.tapEvent(first, now + 199))
        assertEquals(2, GameEngine.tapEvent(first, now + 200).eventTaps)
        assertEquals(101, GameEngine.eventPercent(first, now + 59_999))
        assertEquals(100, GameEngine.eventPercent(first, now + 60_000))
        assertEquals(first, GameEngine.tapEvent(first, now + 60_000))
    }

    @Test
    fun purchasedAppIsPrioritizedAndUpgradesKeepCompounding() {
        val fresh = GameEngine.initial(now).copy(level = 2, points = valueOf(2_000))
        val bought = GameEngine.buy(fresh, NoticeApp.Inbox)
        val upgraded = GameEngine.upgrade(bought, NoticeApp.Chat)
        val next = GameEngine.swipe(upgraded, 4, now)!!.state
        assertEquals(valueOf(925), upgraded.points)
        assertEquals(valueOf(160), GameEngine.appPercent(upgraded, NoticeApp.Chat))
        assertEquals(valueOf(100), GameEngine.appPercent(upgraded, NoticeApp.Inbox))
        val advanced = upgraded.copy(appLevels = mapOf(NoticeApp.Chat to 12), points = BigInteger.TEN.pow(22))
        assertTrue(GameEngine.upgrade(advanced, NoticeApp.Chat).points < advanced.points)
        assertTrue(next.rows.any { it.app == NoticeApp.Inbox })
        assertEquals(upgraded, GameEngine.buy(upgraded, NoticeApp.Inbox))
    }

    @Test
    fun automaticSwipesFindRecurringRealOverloadsAndKeepAValidPanel() {
        var state = GameEngine.initial(now, seed = 19).copy(points = ZERO)
        var overloadCount = 0
        for (i in 0 until 80) {
            val target = GameEngine.autoTarget(state)!!
            val result = GameEngine.swipe(state, target, now + i * GameEngine.AUTO_MS)!!
            state = result.state
            if (result.frames.any { it.kind == ImpactKind.Overflow }) overloadCount += 1
            assertEquals(7, state.rows.size)
            assertEquals(7, state.rows.map { it.id }.distinct().size)
        }
        assertTrue("Overloads after 80 swipes: $overloadCount", overloadCount >= 10)
    }
}
