package com.graviton94.notificationcleaner

import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    private val now = 100_000L

    @Test
    fun tutorialDoubleBurstCostsOneChargeAndIntroducesPhotoWithoutResettingInbox() {
        val start = GameEngine.initial(now, seed = 19)
        val result = GameEngine.swipe(start, rowId = 4, now = now)!!
        val state = result.state
        assertEquals(11, state.energy)
        assertEquals(2, state.level)
        assertEquals(21L, state.points)
        assertEquals(2, state.bestCombo)
        assertEquals(7, state.rows.size)
        assertEquals(2, state.rows.single { it.id == 1L }.count)
        assertTrue(state.rows.any { it.app == NoticeApp.Photo })
        assertEquals(listOf(1, 2), result.frames.mapNotNull { it.burst.takeIf { n -> n > 0 } })
        assertEquals(4L, GameEngine.autoTarget(start))
    }

    @Test
    fun enhancedChainUsesAdditiveBonusesBeforeMultipliersWithoutChangingXp() {
        val start = GameEngine.initial(now).copy(
            level = 3, appLevels = mapOf(NoticeApp.Chat to 2),
            regions = mapOf(Region.Domestic to 12, Region.Global to 8),
            categories = mapOf(Category.Social to 20),
            eventUntil = now + 12_000, eventTaps = 50,
        )
        val state = GameEngine.swipe(start, 4, now)!!.state
        assertEquals(67L, state.bestScore)
        assertEquals(21L, state.lastScore!!.xp)
        assertEquals(listOf(35L, 31L), state.lastScore!!.appScores.map { it.points })
        val claimed = GameEngine.claimReward(state)
        assertEquals(134L, claimed.points)
        assertEquals(67L, claimed.bestScore)
        assertEquals(claimed, GameEngine.claimReward(claimed))
    }

    @Test
    fun uniqueBonusIsPaidOnceWhenSameAppBurstsTwice() {
        val rows = listOf(
            Notice(1, NoticeApp.Chat, 2), Notice(2, NoticeApp.Mail, 2),
            Notice(3, NoticeApp.Chat, 2), Notice(4, NoticeApp.Delivery),
            Notice(5, NoticeApp.Chat, 2), Notice(6, NoticeApp.Mail, 2),
            Notice(7, NoticeApp.Chat, 2),
        )
        val start = GameEngine.initial(now).copy(level = 3, rows = rows)
        val report = GameEngine.swipe(start, 4, now)!!.state.lastScore!!
        assertEquals(3, report.combo)
        assertEquals(27L, report.appScores.single { it.app == NoticeApp.Chat }.points)
        assertEquals(37L, report.xp)
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
    fun purchasedAppIsPrioritizedAndUpgradesAreIndependent() {
        val fresh = GameEngine.initial(now).copy(level = 2, points = 100)
        val bought = GameEngine.buy(fresh, NoticeApp.Inbox)
        val upgraded = GameEngine.upgrade(bought, NoticeApp.Chat)
        val next = GameEngine.swipe(upgraded, 4, now)!!.state
        assertEquals(40L, upgraded.points)
        assertEquals(125, GameEngine.appPercent(upgraded, NoticeApp.Chat))
        assertEquals(100, GameEngine.appPercent(upgraded, NoticeApp.Inbox))
        assertTrue(next.rows.any { it.app == NoticeApp.Inbox })
        assertEquals(upgraded, GameEngine.buy(upgraded, NoticeApp.Inbox))
    }
}
