package com.graviton94.notificationcleaner

import java.math.BigInteger.ZERO
import java.math.BigInteger.valueOf
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class EconomyTest {
    private val now = Instant.parse("2027-01-01T09:00:00Z").toEpochMilli()

    @Test
    fun directPurchaseExchangeAndBoostSettleExactlyOnce() {
        val start = GameEngine.initial(now, 19)
        var state = EconomyRules.purchase(start, Product.STARTER, "purchase", now, NoticeApp.Chat)
        assertEquals(300L, state.account.credits)
        assertEquals(3900L, state.account.won)
        assertEquals(state, EconomyRules.purchase(state, Product.SMALL, "purchase", now))
        assertEquals(state, EconomyRules.purchase(state, Product.MASTERY, "duplicate-app", now, NoticeApp.Chat))
        state = EconomyRules.redeem(state, true)
        state = EconomyRules.exchange(state)
        assertEquals(100L, state.account.credits)
        assertEquals(2, state.account.boosts)
        state = state.copy(account = state.account.copy(armed = true, decor = 100, equipped = true))
        val free = GameEngine.swipe(start, 4, now)!!.state
        val paid = GameEngine.swipe(state, 4, now)!!.state
        assertEquals(free.points, paid.points)
        assertEquals(free.xp, paid.xp)
        assertEquals(free.bestScore, paid.bestScore)
        assertTrue(paid.lastScore!!.personal > paid.bestScore)
        assertTrue(paid.lastScore.personal * valueOf(100) <= paid.bestScore * valueOf(108))
        assertEquals(1, paid.account.boosts)
        assertFalse(paid.account.armed)
    }

    @Test
    fun twentyClaimedDatesGrantExactRewardsAndMonthResets() {
        var state = GameEngine.initial(now)
        repeat(20) { day ->
            val at = now + day * 86_400_000L
            state = EconomyRules.login(state, at)
            assertEquals(state, EconomyRules.login(state, at + 1))
        }
        assertEquals(20, state.account.attendance)
        assertEquals(valueOf(360), state.points)
        assertEquals(12, state.account.boosts)
        assertEquals(3, state.account.trials)
        assertEquals(2, state.account.previews)
        val next = EconomyRules.login(state, now + 31 * 86_400_000L)
        assertEquals(1, next.account.attendance)
        assertEquals(state.points, next.points)
        assertEquals(next, EconomyRules.login(next, now))
    }

    @Test
    fun expiryPreservesClaimedRewardsAndEnteredBoard() {
        val state = GameEngine.initial(now).copy(level = 35, account = Account(overloads = 15))
        val pass = EconomyRules.purchase(state, Product.PASS, "pass", now)
        assertEquals(2, pass.account.passClaims)
        assertEquals(80, pass.account.decor)
        val entered = EconomyRules.enterChallenge(pass, now)
        val expiry = pass.account.passUntil
        val done = EconomyRules.playChallenge(entered, 4, expiry)
        assertFalse(done.account.challengeOpen)
        assertTrue(done.account.challengeBest > ZERO)
        assertEquals(entered.points, done.points)
        assertEquals(done, EconomyRules.enterChallenge(done, expiry))
        val ad = EconomyRules.ad(done, "ad", expiry)
        assertEquals(1, ad.account.boosts)
        assertEquals(ad, EconomyRules.ad(ad, "other-ad", expiry))
    }

    @Test
    fun prestigeKeepsEntitlementsAndWaitsForRunRecovery() {
        val first = GameEngine.initial(now).copy(level = 90, points = valueOf(600), bestScore = valueOf(50_000),
            account = Account(credits = 600, boosts = 2, personalBest = valueOf(52_000)))
        val second = EconomyRules.rebirth(first, now)
        assertEquals(45, second.level)
        assertEquals(ZERO, second.points)
        assertEquals(3L, second.account.tokens)
        assertEquals(600L, second.account.credits)
        assertEquals(2, second.account.boosts)
        assertEquals(first.bestScore, second.account.recovery)
        assertEquals(first.account.personalBest, second.account.personalBest)
        assertEquals(second.copy(level = 70), EconomyRules.rebirth(second.copy(level = 70), now))
    }

    @Test
    fun compressedWavesMatchAnExplicitIntegerReference() {
        val initial = GameEngine.initial(now).copy(level = 300)
        val result = GameEngine.swipe(initial, 4, now)!!
        var ordinal = 0
        var backlog = result.frames.filter { it.kind == ImpactKind.Burst }.fold(ZERO) { sum, frame -> sum + frame.target.count }
        ordinal += result.frames.count { it.kind == ImpactKind.Burst }
        result.frames.filter { it.kind == ImpactKind.Overflow }.forEachIndexed { index, frame ->
            var weighted = ZERO
            repeat(frame.steps) {
                backlog *= valueOf((3 + index * 2).toLong())
                ordinal++
                weighted += backlog * valueOf(ordinal + 1L)
            }
            assertEquals(weighted, frame.weighted)
        }
    }
}
