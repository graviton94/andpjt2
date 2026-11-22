package com.graviton94.notificationcleaner.core

import java.math.BigInteger
import java.math.BigInteger.valueOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pins the examples in docs/core-rules-v3.md. If a number here changes, the document changes too. */
class CoreRulesTest {
    private val e = Engine()
    private val now = 1_000_000L

    @Test
    fun firstSwipeIsOneSmallBurst() {
        val (_, o) = firstSwipe()
        assertEquals(1, o.chain)
        assertEquals(valueOf(9), o.earned)
        assertEquals(16, o.state.gauge)
        assertEquals(7, o.state.rows.size)
    }

    @Test
    fun touchingRowsUnzipOutward() {
        assertEquals(valueOf(22), doubleChain().second.earned)
    }

    @Test
    fun overloadWavesTripleTheBacklog() {
        val o = overload().second
        assertTrue(o.overload)
        assertEquals(listOf(24L, 72L, 216L).map(::valueOf), o.beats.filterIsInstance<Beat.Wave>().map { it.cleared })
        assertEquals(valueOf(1_948), o.earned)
    }

    @Test
    fun upgradesMultiplyTheSameSwipe() {
        assertEquals(valueOf(24_769), upgraded().second.earned)
    }

    @Test
    fun lowTiersIgniteHighTiers() {
        val (s, o) = igniter()
        val bursts = o.beats.filterIsInstance<Beat.Burst>()
        assertEquals(listOf("chat", "live"), bursts.map { it.row.app })
        assertEquals(valueOf(1_440_000), bursts[1].earned)
        // 60 + merges 4 + 4 + 톡탁톡 burst 12 + 4×5 + 지금방송중 burst 12 = 112 → overload.
        assertTrue(o.overload)
        assertEquals(0, o.state.gauge)
        assertEquals(60, s.gauge)
    }

    @Test
    fun synergyStepsByDeckCount() {
        assertEquals(listOf(40L, 57L, 90L, 120L, 180L).map(::valueOf), synergyTable().map { it.second })
    }

    @Test
    fun wildMergesWithAnyApp() {
        val o = wildMerge().second
        val merged = o.beats.filterIsInstance<Beat.Merged>().single().into
        assertEquals("mail", merged.app)
        assertEquals(RowKind.App, merged.kind)
        assertEquals(4, merged.count)
    }

    @Test
    fun allHandsQueuesADoubleSandwich() {
        val o = allHands().second
        val arrived = o.beats.filterIsInstance<Beat.Spawned>().map { it.row.app }
        assertEquals(listOf("mail"), arrived)
        assertEquals(listOf("chat", "mail", "chat", "mail"), o.state.pattern)
    }

    @Test
    fun updateCardUpgradesForFree() {
        val s = e.newGame(now, 3).let { it.copy(rows = it.rows.toMutableList().also { r -> r[3] = Row(50, RowKind.Event, event = EventCard.Update) }) }
        val o = e.swipe(s, 50, now)!!
        val up = o.beats.filterIsInstance<Beat.FreeUpgrade>().single()
        assertEquals(2, o.state.appLevels[up.app])
    }

    @Test
    fun otpPaysTenTimesTheLastSwipeThenExpires() {
        val s = e.newGame(now, 3).let { it.copy(lastEarned = valueOf(500), rows = it.rows.toMutableList().also { r -> r[0] = Row(50, RowKind.Event, event = EventCard.Otp, expireAt = 2) }) }
        assertEquals(valueOf(5_000), e.swipe(s, 50, now)!!.earned)
        val later = e.swipe(s.copy(swipes = 1), 7, now)!!.state
        assertTrue(later.rows.none { it.event == EventCard.Otp })
    }

    @Test
    fun deckSlotsFollowLevel() {
        val s = e.newGame(now, 1)
        assertEquals(3, e.maxSlots(s))
        assertEquals(5 to 6, e.rules.minSlots(20) to e.rules.maxSlots(20, Backup()))
        assertEquals(7 to 8, e.rules.minSlots(40) to e.rules.maxSlots(40, Backup()))
        val owned = s.copy(level = 20, appLevels = listOf("chat", "mail", "delivery", "steps", "feed", "calendar").associateWith { 1 })
        assertNull(e.setDeck(owned, listOf("chat", "mail", "delivery", "steps")), "below the minimum of 5")
        assertNotNull(e.setDeck(owned, listOf("chat", "mail", "delivery", "steps", "feed")))
        assertNull(e.setDeck(owned, listOf("chat", "mail", "delivery", "steps", "feed", "calendar", "pay")), "not owned / above max")
    }

    @Test
    fun aRisingMinimumPullsOwnedAppsIn() {
        // One P short of level 10, where the minimum deck grows from 3 to 4.
        val s = e.newGame(now, 1).copy(level = 9, runEarned = e.rules.levelP(10) - BigInteger.ONE,
            appLevels = listOf("chat", "mail", "delivery", "feed").associateWith { 1 })
        val next = e.swipe(s, 4, now)!!.state
        assertEquals(10, next.level)
        assertEquals(listOf("chat", "mail", "delivery", "feed"), next.active)
    }

    @Test
    fun levelIsExponentialInRunP() {
        val r = e.rules
        assertEquals(1, r.levelFor(BigInteger.ZERO, 0))
        assertEquals(2, r.levelFor(valueOf(30), 0))
        // ×2.5 per level until 25, then the ratio rises by 0.005 a level.
        assertEquals(r.levelP(10) * valueOf(5) / valueOf(2), r.levelP(11))
        assertTrue(r.levelP(40) * r.levelP(19) > r.levelP(39) * r.levelP(20))
        // FastBoot 1 lowers every threshold by 10%.
        assertEquals(10, r.levelFor(r.levelP(10) * valueOf(9) / valueOf(10) + valueOf(1), 1))
    }

    @Test
    fun offlinePaysTheRecentRateAtLowEfficiency() {
        val s = e.newGame(now, 1).copy(earnRate = valueOf(1_000))
        val (_, p) = e.settleOffline(s, 3 * 3_600_000L, now + 3 * 3_600_000L)
        assertEquals(valueOf(1_000L * 60 * 10 / 100), p) // capped at 1 hour, 10%
        val withItem = s.copy(backup = Backup(items = mapOf(BackupItem.Offline to 2)))
        assertEquals(valueOf(1_000L * 180 * 20 / 100), e.settleOffline(withItem, 3 * 3_600_000L, now).second)
    }

    @Test
    fun boughtAppsJoinTheDeckOnlyWhenASlotIsFree() {
        val rich = e.newGame(now, 1).copy(wallet = valueOf(1_000_000), level = 8)
        val four = e.buy(rich, "feed")
        assertEquals(4, four.active.size)
        assertEquals(valueOf(1_000_000 - 6_000), four.wallet)
        val five = e.buy(e.buy(four, "calendar"), "music")
        assertEquals(5, five.active.size)
        val six = e.buy(five, "pay")
        assertEquals(5, six.active.size)
        assertTrue("pay" in six.appLevels)
    }

    @Test
    fun eventCardsAndAssistsRespectLevelAndSlots() {
        val s = e.newGame(now, 1).copy(wallet = valueOf(10_000_000), level = 14)
        val one = e.buyEvent(e.buyEvent(s, EventCard.Update), EventCard.AllHands)
        assertEquals(listOf(EventCard.Update, EventCard.AllHands), one.equipped)
        assertEquals(one, e.buyEvent(one, EventCard.Otp), "인증번호 opens at level 16")
        val a = e.buyAssist(e.buyAssist(one, Assist.Overload), Assist.Overload)
        assertEquals(3, e.overloadDepth(a))
        assertEquals(a, e.buyAssist(a, Assist.Wild), "wild opens at level 20")
    }

    @Test
    fun biggerDecksAreHarderAndAssistsHelp() {
        val three = (1L..3L).sumOf { deckRun(it, 3, false).bursts }
        val six = (1L..3L).sumOf { deckRun(it, 6, false).bursts }
        val sixHelped = (1L..3L).sumOf { deckRun(it, 6, true).bursts }
        assertTrue(six < three * 3 / 4, "6 apps $six vs 3 apps $three")
        assertTrue(sixHelped > six * 5 / 4, "assists $sixHelped vs $six")
    }

    @Test
    fun factoryResetTurnsTheRunIntoBackupFiles() {
        val (run, next) = firstReset()
        assertEquals(15, e.backupFilesNow(run))
        assertEquals(1, next.level)
        assertEquals(Catalog.starter.toSet(), next.appLevels.keys)
        assertEquals(1, next.backup.resets)
        assertEquals(9, next.backup.files)
        assertEquals(15, next.backup.lifetime)
        assertNull(e.factoryReset(e.newGame(now, 1).copy(level = 24), now))
        assertEquals(valueOf(10), RuleSet.cubeRoot(valueOf(1_000)))
        assertEquals(valueOf(9), RuleSet.cubeRoot(valueOf(999)))
        assertEquals(10_000L + 30, e.rules.backupFiles(BigInteger.TEN.pow(21), 50))
    }

    @Test
    fun backupItemsShapeTheNextRun() {
        val b = Backup(items = mapOf(BackupItem.RememberedApps to 2, BackupItem.StartFunds to 3, BackupItem.Battery to 1, BackupItem.DeepOverload to 1))
        val s = e.newGame(now, 1, b)
        assertTrue(s.appLevels.keys.containsAll(listOf("steps", "feed")))
        assertEquals(valueOf(3_750), s.wallet)
        assertEquals(14, s.energy)
        assertEquals(2, e.overloadDepth(s))
    }

    @Test
    fun sameSeedSameGame() {
        assertEquals(deckRun(5, 5, true), deckRun(5, 5, true))
    }

    @Test
    fun emptyBatteryBlocksAndRecharges() {
        val s = e.newGame(now, 1).copy(energy = 0, nextChargeAt = now + 3_000)
        assertNull(e.swipe(s, 4, now))
        assertEquals(12, e.tick(s, now + 60_000).energy)
    }

    @Test
    fun aBiggerBatteryKeepsCharging() {
        // Battery bought after the run started: cap 14, energy 12, no charge scheduled yet.
        val s = e.newGame(now, 1).copy(backup = Backup(items = mapOf(BackupItem.Battery to 1)))
        val first = e.tick(s, now)
        assertEquals(12, first.energy)
        assertEquals(14, e.tick(first, now + 10_000).energy)
        var t = now
        var drained = s
        repeat(12) { t += 100; drained = e.swipe(drained, drained.rows.last().id, t)!!.state }
        assertEquals(0, drained.energy)
        assertEquals(14, e.tick(drained, t + 60_000).energy)
    }

    @Test
    fun callPausesAutoUntilSwiped() {
        val s = e.newGame(now, 1).copy(autoPausedUntil = now + 5_000)
        assertNull(e.swipe(s, 4, now, Actor.Auto))
        assertEquals(0L, e.swipe(s.copy(rows = s.rows + Row(99, RowKind.Call)), 99, now)!!.state.autoPausedUntil)
    }
}
