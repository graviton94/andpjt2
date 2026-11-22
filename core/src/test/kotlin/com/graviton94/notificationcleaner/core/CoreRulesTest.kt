package com.graviton94.notificationcleaner.core

import java.math.BigInteger.valueOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pins the examples in docs/core-rules-v2.md. If a number here changes, the document changes too. */
class CoreRulesTest {
    private val engine = Engine()
    private val now = 1_000_000L

    @Test
    fun firstSwipeIsOneSmallBurst() {
        val (_, o) = firstSwipe()
        assertEquals(1, o.chain)
        assertEquals(valueOf(9), o.earned)
        assertEquals(valueOf(5), o.cleared)
        assertEquals(16, o.state.gauge)
        assertEquals(11, o.state.energy)
        assertEquals(7, o.state.rows.size)
    }

    @Test
    fun touchingRowsUnzipOutward() {
        val (_, o) = doubleChain()
        assertEquals(2, o.chain)
        assertEquals(valueOf(21), o.earned)
        assertEquals(listOf(2, 3), o.beats.filterIsInstance<Beat.Burst>().map { it.chain + 1 })
    }

    @Test
    fun overloadWavesTripleTheBurstBacklog() {
        val (_, o) = overload()
        assertTrue(o.overload)
        assertEquals(5, o.chain)
        assertEquals(listOf(24L, 72L, 216L).map(::valueOf), o.beats.filterIsInstance<Beat.Wave>().map { it.cleared })
        assertEquals(valueOf(1_773), o.earned)
        assertEquals(0, o.state.gauge)
    }

    @Test
    fun upgradesMultiplyTheSameSwipe() {
        val (_, o) = upgraded()
        assertEquals(valueOf(21_233), o.earned)
        assertEquals(valueOf(20), engine.rules.notificationValue(engine.rules.app("chat"), 10))
    }

    @Test
    fun sameSeedSameGame() {
        assertEquals(tenMinutes(3, player = false), tenMinutes(3, player = false))
        assertEquals(tenMinutes(3, player = true), tenMinutes(3, player = true))
    }

    @Test
    fun aPlayerOutEarnsAutoClean() {
        (1L..5L).forEach { seed ->
            val auto = tenMinutes(seed, player = false)
            val player = tenMinutes(seed, player = true)
            assertTrue(player.wallet > auto.wallet * valueOf(2), "seed $seed: $player vs $auto")
        }
    }

    @Test
    fun spamLeftAloneCopiesItself() {
        val log = spamCopies()
        assertEquals("11: 스팸 1줄", log[10])
        assertEquals("14: 스팸 2줄", log[13])
    }

    @Test
    fun emptyBatteryBlocksAndRecharges() {
        val s = engine.newGame(now, 1).copy(energy = 0, nextChargeAt = now + 3_000)
        assertNull(engine.swipe(s, 4, now))
        assertEquals(1, engine.tick(s, now + 3_000).energy)
        assertEquals(12, engine.tick(s, now + 60_000).energy)
    }

    @Test
    fun callPausesAutoUntilSwiped() {
        val s = engine.newGame(now, 1).copy(autoPausedUntil = now + 5_000)
        assertNull(engine.swipe(s, 4, now, Actor.Auto))
        val o = engine.swipe(s.copy(rows = s.rows + Row(99, RowKind.Call)), 99, now, Actor.Player)!!
        assertEquals(0L, o.state.autoPausedUntil)
    }

    @Test
    fun buyingFillsAnEmptySlotThenReplacesTheLowestTier() {
        val rich = engine.newGame(now, 1).copy(wallet = valueOf(1_000_000), level = 10)
        val four = engine.buy(rich, "feed")
        assertEquals(listOf("chat", "mail", "delivery", "feed"), four.active)
        val five = engine.buy(four, "calendar")
        assertEquals(listOf("mail", "delivery", "feed", "calendar"), five.active)
        assertEquals(valueOf(1_000_000 - 1_250 - 1_250), five.wallet)
    }
}
