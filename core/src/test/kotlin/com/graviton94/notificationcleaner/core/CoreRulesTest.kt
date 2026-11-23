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
        assertEquals(valueOf(11), o.earned)
        assertEquals(16, o.state.gauge)
        assertEquals(7, o.state.rows.size)
    }

    @Test
    fun touchingRowsUnzipOutward() {
        assertEquals(valueOf(26), doubleChain().second.earned)
    }

    @Test
    fun overloadWavesTripleTheBacklog() {
        val o = overload().second
        assertTrue(o.overload)
        assertEquals(listOf(24L, 72L, 216L).map(::valueOf), o.beats.filterIsInstance<Beat.Wave>().map { it.cleared })
        assertEquals(valueOf(2_216), o.earned)
    }

    @Test
    fun upgradesMultiplyTheSameSwipe() {
        assertEquals(valueOf(26_541), upgraded().second.earned)
    }

    @Test
    fun lowTiersIgniteHighTiers() {
        val (s, o) = igniter()
        val bursts = o.beats.filterIsInstance<Beat.Burst>()
        assertEquals(listOf("alarm", "agoda"), bursts.map { it.row.app })
        assertEquals(valueOf(93_312), bursts[1].earned)
        // 60 + merges 4 + 4 + 톡탁톡 burst 12 + 4×5 + 지금방송중 burst 12 = 112 → overload.
        assertTrue(o.overload)
        assertEquals(0, o.state.gauge)
        assertEquals(60, s.gauge)
    }

    @Test
    fun synergyStepsByDeckCount() {
        assertEquals(listOf(144L, 263L, 421L, 553L, 576L).map(::valueOf), synergyTable().map { it.second })
    }

    @Test
    fun wildMergesWithAnyApp() {
        val o = wildMerge().second
        val merged = o.beats.filterIsInstance<Beat.Merged>().single().into
        assertEquals("memo", merged.app)
        assertEquals(RowKind.App, merged.kind)
        assertEquals(4, merged.count)
    }

    @Test
    fun allHandsQueuesADoubleSandwich() {
        val o = allHands().second
        val arrived = o.beats.filterIsInstance<Beat.Spawned>().map { it.row.app }
        assertEquals(listOf("memo"), arrived)
        assertEquals(listOf("alarm", "memo", "alarm", "memo"), o.state.pattern)
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
        val owned = s.copy(level = 20, appLevels = listOf("alarm", "memo", "weather", "recorder", "feed", "calendar").associateWith { 1 })
        assertNull(e.setDeck(owned, listOf("alarm", "memo", "weather", "recorder")), "below the minimum of 5")
        assertNotNull(e.setDeck(owned, listOf("alarm", "memo", "weather", "recorder", "feed")))
        assertNull(e.setDeck(owned, listOf("alarm", "memo", "weather", "recorder", "feed", "calendar", "pay")), "not owned / above max")
    }

    @Test
    fun aRisingMinimumPullsOwnedAppsIn() {
        // One P short of level 10, where the minimum deck grows from 3 to 4.
        val s = e.newGame(now, 1).copy(level = 9, runEarned = e.rules.levelP(10) - BigInteger.ONE,
            appLevels = listOf("alarm", "memo", "weather", "feed").associateWith { 1 })
        val next = e.swipe(s, 4, now)!!.state
        assertEquals(10, next.level)
        assertEquals(listOf("alarm", "memo", "weather", "feed"), next.active)
    }

    @Test
    fun levelIsExponentialInRunP() {
        val r = e.rules
        assertEquals(1, r.levelFor(BigInteger.ZERO, 0))
        assertEquals(2, r.levelFor(valueOf(30), 0))
        // A fixed ratio per level until 25, then the ratio rises a little every level.
        val expected = r.levelP(10) * valueOf(r.levelRatioPermille.toLong()) / valueOf(1_000)
        assertTrue((expected - r.levelP(11)).abs() <= valueOf(1), "ratio per level")
        assertTrue(r.levelP(40) * r.levelP(19) > r.levelP(39) * r.levelP(20))
        // FastBoot 1 lowers every threshold by 10%.
        assertEquals(10, r.levelFor(r.levelP(10) * valueOf(9) / valueOf(10) + valueOf(1), 1))
    }

    @Test
    fun offlinePaysTheRecentRateAtLowEfficiency() {
        val s = e.newGame(now, 1).copy(earnRate = valueOf(1_000))
        val (_, p) = e.settleOffline(s, 3 * 3_600_000L, now + 3 * 3_600_000L)
        assertEquals(valueOf(1_000L * 180 * 5 * 400 / 100_000), p) // 3 h of the auto rate (40%) at 5%
        val withItem = s.copy(backup = Backup(items = mapOf(BackupItem.Offline to 2)))
        assertEquals(valueOf(1_000L * 180 * 9 * 400 / 100_000), e.settleOffline(withItem, 3 * 3_600_000L, now).second)
    }

    @Test
    fun boughtAppsJoinTheDeckOnlyWhenASlotIsFree() {
        val rich = e.newGame(now, 1).copy(wallet = valueOf(1_000_000), level = 8)
        val four = e.buy(rich, "phone")
        assertEquals(4, four.active.size)
        assertEquals(valueOf(1_000_000 - 6_000), four.wallet)
        val five = e.buy(e.buy(four, "sms"), "camera")
        assertEquals(5, five.active.size)
        val six = e.buy(five, "gallery")
        assertEquals(5, six.active.size)
        assertTrue("gallery" in six.appLevels)
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
        assertEquals(10, e.backupFilesNow(run))
        assertEquals(1, next.level)
        assertEquals(Catalog.starter.toSet(), next.appLevels.keys)
        assertEquals(1, next.backup.resets)
        assertEquals(4, next.backup.files)
        assertEquals(10, next.backup.lifetime)
        assertNull(e.factoryReset(e.newGame(now, 1).copy(level = 24), now))
        assertEquals(valueOf(10), RuleSet.cubeRoot(valueOf(1_000)))
        assertEquals(valueOf(9), RuleSet.cubeRoot(valueOf(999)))
        // Total = ⌊10 × (P_max / P(Lv25))^0.4⌋, exponent halves 10⁸ above the anchor.
        val anchor = e.rules.levelP(25)
        assertEquals(10L, e.rules.backupTotal(anchor))
        assertEquals(158L, e.rules.backupTotal(anchor * BigInteger.TEN.pow(3)))
        assertEquals(100_000L, e.rules.backupTotal(anchor * BigInteger.TEN.pow(12)))
        assertEquals(Math.floor(10 * Math.pow(10.0, 6.4)).toLong(), e.rules.backupTotal(anchor * BigInteger.TEN.pow(24)))
    }

    @Test
    fun onlyBeatingTheBestRunPaysAndCounts() {
        val (_, after) = firstReset()
        // A second run that stops below the first run's P pays nothing and does not count as a reset.
        val weak = after.copy(level = 25, runEarned = e.rules.levelP(25) / BigInteger.TEN)
        val again = e.factoryReset(weak, now)!!
        assertEquals(after.backup.lifetime, again.backup.lifetime)
        assertEquals(after.backup.resets, again.backup.resets)
        // Beating it pays only the difference.
        val strong = after.copy(level = 30, runEarned = e.rules.levelP(25) * BigInteger.TEN.pow(3))
        assertEquals(158L - 10L, e.backupFilesNow(strong))
    }

    @Test
    fun unlocksArePermanentAndPriceSetsTheOrder() {
        // A fresh run after a Lv40 best: 카메라켜요 is unlocked but its price keeps it out of reach.
        val veteran = e.newGame(now, 1, Backup(bestLevel = Catalog.app("meet").unlockLevel + 4, resets = 5))
        assertTrue(e.unlocked(veteran, "meet"))
        assertEquals(veteran, e.buy(veteran, "meet"))
        val price = e.rules.appPrice(Catalog.app("meet"))
        assertTrue(price >= e.rules.levelP(Catalog.app("meet").unlockLevel) / valueOf(10))
        assertTrue("meet" in e.buy(veteran.copy(wallet = price), "meet").appLevels)
        // Higher tiers always cost more than lower ones.
        val prices = Catalog.apps.filter { it.id !in Catalog.starter }.map { e.rules.appPrice(it) }
        assertEquals(prices.sorted(), prices)
    }

    @Test
    fun backupItemsShapeTheNextRun() {
        val b = Backup(items = mapOf(BackupItem.RememberedApps to 2, BackupItem.StartFunds to 3, BackupItem.Battery to 1, BackupItem.DeepOverload to 1))
        val s = e.newGame(now, 1, b)
        assertTrue(s.appLevels.keys.containsAll(listOf("flash", "calc")))
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
    fun proVersionDoublesAnAppForItsLevelPrice() {
        val at = Catalog.proLevel.getValue("alarm")
        val s = e.newGame(now, 1).copy(level = at, wallet = e.rules.levelP(at))
        val before = e.earn(s, "alarm", valueOf(1_000), 0)
        val pro = e.buyPro(s, "alarm")
        assertTrue("alarm" in pro.pro)
        assertEquals(BigInteger.ZERO, pro.wallet)
        assertTrue((before * valueOf(2) - e.earn(pro, "alarm", valueOf(1_000), 0)).abs() <= BigInteger.ONE, "×2 up to rounding")
        val early = s.copy(level = at - 1)
        assertEquals(early, e.buyPro(early, "alarm"), "opens at its pro level")
    }

    @Test
    fun lateAppsOpenByBestLevelEver() {
        val late = Catalog.app("agoda")
        val rich = e.newGame(now, 1).copy(level = late.unlockLevel - 1, wallet = BigInteger.TEN.pow(60))
        assertEquals(rich, e.buy(rich, "agoda"), "아고다다 opens at its level")
        val veteran = rich.copy(backup = Backup(bestLevel = late.unlockLevel))
        assertTrue("agoda" in e.buy(veteran, "agoda").appLevels)
    }

    @Test
    fun newCardsChangeTheBoardAndTheShop() {
        fun withCard(c: EventCard) = e.newGame(now, 2).let { it.copy(level = 70, rows = it.rows.toMutableList().also { r -> r[0] = Row(50, RowKind.Event, event = c) }) }
        // 앱스토어 세일: the next app costs half.
        val sale = e.swipe(withCard(EventCard.Sale), 50, now)!!.state.copy(wallet = valueOf(3_000))
        assertTrue("phone" in e.buy(sale, "phone").appLevels)
        // 단체 문자: every app row on the board +1 (rows below the swiped one are untouched by refill).
        val group = withCard(EventCard.GroupText)
        val after = e.swipe(group, 50, now)!!.state
        assertEquals(group.rows.drop(1).map { it.count + 1 }, after.rows.takeLast(6).map { it.count })
        // 펌웨어 업데이트: every deck app +1 level.
        val fw = e.swipe(withCard(EventCard.Firmware), 50, now)!!.state
        assertEquals(mapOf("alarm" to 2, "memo" to 2, "weather" to 2), fw.appLevels)
    }

    @Test
    fun assistCapsAndSlotsGrowWithLevel() {
        assertEquals(6, Assist.Overload.max(8))
        assertEquals(8, Assist.Overload.max(82))
        assertEquals(9, Assist.Overload.max(83))
        assertEquals(10, e.rules.maxSlots(73, Backup()))
        assertEquals(4, e.rules.eventSlots(63, Backup()))
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

    @Test
    fun aJackpotJumpsAFewLevelsNotTheWholeRun() {
        // A 로또-sized swipe at Lv30: the level rises by at most swipeLevelCap and the rest is reported as lost.
        val s = e.newGame(now, 1).copy(level = 30, runEarned = e.rules.levelP(30), lastEarned = e.rules.levelP(60))
        val room = e.jackpotRoom(s, e.rules.swipeLevelCap)
        assertEquals(30 + e.rules.swipeLevelCap, e.rules.levelFor(s.runEarned + room, 0))
        assertEquals(30 + e.rules.swipeLevelCap, e.rules.levelFor(s.runEarned + room + valueOf(1), 0) - 1)
        // Offline settlement obeys the same kind of cap.
        val away = s.copy(earnRate = e.rules.levelP(80))
        val (back, _) = e.settleOffline(away, 8 * 3_600_000L, now + 8 * 3_600_000L)
        assertEquals(30 + e.rules.offlineLevelCap, back.level)
    }

    @Test
    fun luckySynergyStaysBelowOneTierStep() {
        // Six 생활 apps from two regions cannot out-earn the same app one tier up with no synergy at all.
        val ids = listOf("weather", "flash", "food", "ctaxi", "travel", "agoda")
        val base = e.newGame(now, 1).copy(appLevels = ids.associateWith { 1 }, active = listOf("weather"))
        val alone = e.earn(base, "weather", valueOf(1_000_000), 0)
        val stacked = e.earn(base.copy(active = ids), "weather", valueOf(1_000_000), 0)
        assertTrue(stacked > alone, "synergy still pays")
        assertTrue(stacked <= alone * valueOf(e.rules.synergyCapPermille.toLong()) / valueOf(1_000), "capped at ×4")
        assertTrue(stacked < alone * valueOf(e.rules.tierBase), "below one tier step")
    }

    @Test
    fun cloudSyncRestartsTheBackupLayerButKeepsUnlocksAndGb() {
        val r = e.rules
        assertNull(e.sync(e.newGame(now, 1).copy(level = r.syncLevel - 1), now))
        val backup = Backup(files = 500, lifetime = r.backupTotal(r.levelP(r.syncLevel)) * 4, resets = 30, bestLevel = 75,
            bestRunP = r.levelP(r.syncLevel), items = mapOf(BackupItem.Value to 10))
        val run = e.newGame(now, 1, backup).copy(level = r.syncLevel, runEarned = r.levelP(r.syncLevel))
        // Four times the anchor's files → ⌊10 × 4^0.5⌋ = 20 GB.
        assertEquals(20L, e.cloudGbNow(run))
        val after = e.sync(run, now)!!
        assertEquals(0L, after.backup.files)
        assertEquals(0, after.backup.resets)
        assertEquals(0, after.backup.lv(BackupItem.Value))
        assertEquals(75, after.backup.bestLevel)
        assertTrue(e.unlocked(after, "agoda"), "app unlocks survive")
        assertEquals(20L, after.backup.cloud.gb)
        assertEquals(1, after.backup.cloud.syncs)
        // GB buy cloud items; 압축 백업 multiplies the files the next resets pay.
        val bought = e.buyCloud(after, CloudItem.Compression)
        assertEquals(1, bought.backup.cloud.lv(CloudItem.Compression))
        val firstReset = bought.copy(level = 25, runEarned = r.levelP(25))
        assertEquals(Math.floor(10 * 1.5 * Math.sqrt(3.0)).toLong(), e.backupFilesNow(firstReset)) // ×1.5 compression × √(1 + 10% × 20 GB)
    }

    @Test
    fun rosterIsTenTiersOfSixBalancedAcrossRegionsAndCategories() {
        val apps = Catalog.apps
        assertEquals(60, apps.size)
        assertEquals(60, apps.map { it.name }.toSet().size)
        for (t in 1..10) assertEquals((0..5).toList(), apps.filter { it.tier == t }.map { it.order }, "tier $t")
        Region.entries.forEach { r -> assertEquals(12, apps.count { it.region == r }, r.label) }
        Category.entries.forEach { c -> assertEquals(12, apps.count { it.category == c }, c.label) }
        // A higher tier never opens before a lower one, and every pro version comes after its app.
        assertEquals(apps.map { it.unlockLevel }.sorted(), apps.map { it.unlockLevel })
        apps.forEach { assertTrue(Catalog.proLevel.getValue(it.id) > it.unlockLevel, it.id) }
        assertTrue(Catalog.starter.all { Catalog.app(it).tier == 1 })
    }
}
