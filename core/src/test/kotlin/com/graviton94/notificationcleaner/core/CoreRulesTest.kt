package com.graviton94.notificationcleaner.core

import java.math.BigInteger
import java.math.BigInteger.ZERO
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
    fun levelThresholdsUseRunPAndMatchThePublishedCurve() {
        val r = e.rules
        assertEquals(listOf(30L, 70L, 163L).map(::valueOf), (2..4).map(r::levelP))
        for (level in listOf(2, 24, 25, 26, 124)) {
            for (fastBoot in listOf(0, 1, 5)) {
                val threshold = r.levelCeil(level, fastBoot)
                assertEquals(level - 1, r.levelFor(threshold - BigInteger.ONE, fastBoot))
                assertEquals(level, r.levelFor(threshold, fastBoot))
            }
        }
    }

    @Test
    fun sharedDeckValuationMatchesIndividualRoundedPayouts() {
        val apps = listOf("alarm", "calc", "duo", "contacts", "photo", "career", "portal", "jobs", "papago", "why")
        val base = e.newGame(now, 11L).copy(
            appLevels = apps.withIndex().associate { (index, app) -> app to index * 11 + 1 },
            whyStacks = 3, trend = Category.Social, pro = setOf("photo"),
        ).withAccount { it.copy(premium = mapOf("duo" to 2), streak = 10) }
        val count = valueOf(1_000)
        val rates = e.deckValuation(base, count)
        val random = kotlin.random.Random(42)
        repeat(100) {
            val deck = apps.shuffled(random).take(2 + it % 8)
            val state = base.copy(active = deck)
            val expected = deck.fold(ZERO) { sum, id -> sum + e.earn(state, id, count, 0) }
            assertEquals(expected, rates(deck))
        }
    }

    @Test
    fun targetPreviewMatchesFullSwipes() {
        val apps = listOf("alarm", "memo", "weather", "ctaxi", "maps", "chat", "reddit", "match", "music")
        val events = listOf(EventCard.GroupText, EventCard.Update, EventCard.Firmware)
        val random = kotlin.random.Random(124)
        repeat(400) { sample ->
            val rows = (1L..11).map { id ->
                when (random.nextInt(8)) {
                    0 -> Row(id, RowKind.Event, event = events.random(random))
                    1 -> Row(id, RowKind.Wild, count = 2, threshold = e.rules.threshold)
                    2 -> Row(id, RowKind.Spam)
                    else -> Row(id, RowKind.App, app = apps.random(random), count = random.nextInt(1, 6), threshold = e.rules.threshold)
                }
            }
            val state = e.newGame(now, sample.toLong() + 1).copy(rows = rows, active = apps,
                appLevels = apps.associateWith { random.nextInt(1, 10) }, gauge = random.nextInt(101), nextId = 12)
            val reference = rows.withIndex().mapNotNull { (index, row) ->
                e.swipe(state, row.id, now)?.let { Triple(index, row, it) }
            }.maxWithOrNull(compareBy({ it.third.chain }, { it.third.overload },
                { it.third.beats.count { beat -> beat is Beat.Merged } }, { it.second.kind == RowKind.Event },
                { it.second.kind == RowKind.Spam }, { it.first }))?.second?.id
            assertEquals(reference, e.bestTarget(state, now), "board $sample")
        }
    }

    @Test
    fun firstSwipeIsOneSmallBurst() {
        val (_, o) = firstSwipe()
        assertEquals(1, o.chain)
        assertEquals(valueOf(11), o.earned)
        assertEquals(e.rules.gaugePerMerge + e.rules.gaugePerBurst, o.state.gauge)
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
        val emissions = o.beats.mapNotNull {
            when (it) {
                is Beat.Swiped -> it.earned
                is Beat.Burst -> it.earned
                is Beat.Wave -> it.earned
                else -> null
            }
        }
        assertEquals(o.earned, emissions.fold(ZERO, BigInteger::add))
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
        val chargingGauge = 2 * (e.rules.gaugePerMerge + e.rules.gaugePerBurst) + 5 * e.rules.igniterGaugePerTier
        assertEquals(100, s.gauge + chargingGauge)
        assertTrue(o.overload)
        assertEquals(0, o.state.gauge)
    }

    @Test
    fun synergyStepsByDeckCount() {
        assertEquals(listOf(144L, 263L, 421L, 571L, 576L).map(::valueOf), synergyTable().map { it.second })
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
        assertEquals(valueOf(6_480L), p) // Three hours at 40% auto × 9% settlement
        val withItem = s.copy(backup = Backup(items = mapOf(BackupItem.Offline to 2)))
        assertEquals(valueOf(9_360L), e.settleOffline(withItem, 3 * 3_600_000L, now).second)
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
        val lowSupport = RuleSet(sandwichPercent = 35, majorEventCooldownMs = 0)
        val three = (1L..3L).sumOf { deckRun(it, 3, false, lowSupport).bursts }
        val six = (1L..3L).sumOf { deckRun(it, 6, false, lowSupport).bursts }
        val sixHelped = (1L..3L).sumOf { deckRun(it, 6, true, lowSupport).bursts }
        // Under the low-support reference, additional app kinds break the same sandwiches more often.
        assertTrue(six < three * 4 / 5, "6 apps $six vs 3 apps $three")
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
        assertNull(e.factoryReset(e.newGame(now, 1).copy(level = e.rules.resetLevel - 1), now))
        assertEquals(valueOf(10), RuleSet.cubeRoot(valueOf(1_000)))
        assertEquals(valueOf(9), RuleSet.cubeRoot(valueOf(999)))
        // 0.45 until the eighth income digit, then 0.225.
        val anchor = e.rules.levelP(e.rules.resetLevel)
        assertEquals(10L, e.rules.backupTotal(anchor))
        assertEquals(223L, e.rules.backupTotal(anchor * BigInteger.TEN.pow(3)))
        assertEquals(316_227L, e.rules.backupTotal(anchor * BigInteger.TEN.pow(12)))
        assertEquals(Math.floor(10 * Math.pow(10.0, 7.2)).toLong(), e.rules.backupTotal(anchor * BigInteger.TEN.pow(24)))
    }

    @Test
    fun onlyBeatingTheBestRunPaysAndCounts() {
        val (_, after) = firstReset()
        // A second run that stops below the first run's P pays nothing and does not count as a reset.
        val weak = after.copy(level = e.rules.resetLevel, runEarned = e.rules.levelP(e.rules.resetLevel) / BigInteger.TEN)
        val again = e.factoryReset(weak, now)!!
        assertEquals(after.backup.lifetime, again.backup.lifetime)
        assertEquals(after.backup.resets, again.backup.resets)
        // Beating it pays only the difference.
        val strong = after.copy(level = 30, runEarned = e.rules.levelP(e.rules.resetLevel) * BigInteger.TEN.pow(3))
        assertEquals(223L - 10L, e.backupFilesNow(strong))
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
        // Each tier's first app is costlier than previous entry points; adjacent tier ladders may overlap.
        val byTier = Catalog.apps.filter { it.id !in Catalog.starter }.groupBy { it.tier }
        val firstPrices = byTier.values.map { e.rules.appPrice(it.first()) }
        assertTrue(firstPrices.zipWithNext().all { (lower, higher) -> lower < higher })
        byTier.values.forEach { tier ->
            val prices = tier.map(e.rules::appPrice)
            assertEquals(prices.sorted(), prices)
        }
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
        fun withCard(c: EventCard) = e.newGame(now, 2).let { it.copy(level = 70, dailyBonusDay = mapOf("weather" to now / 86_400_000L), rows = it.rows.toMutableList().also { r -> r[0] = Row(50, RowKind.Event, event = c) }) }
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
        val firstReset = bought.copy(level = r.resetLevel, runEarned = r.levelP(r.resetLevel))
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
        // The first app of each tier opens in sequence; the tail of a tier may overlap.
        val firstUnlocks = apps.groupBy { it.tier }.values.map { it.first().unlockLevel }
        assertTrue(firstUnlocks.zipWithNext().all { (lower, higher) -> lower < higher })
        apps.groupBy { it.tier }.values.forEach { tier ->
            val unlocks = tier.map { it.unlockLevel }
            assertEquals(unlocks.sorted(), unlocks)
        }
        apps.forEach { assertTrue(Catalog.proLevel.getValue(it.id) > it.unlockLevel, it.id) }
        assertTrue(Catalog.starter.all { Catalog.app(it).tier == 1 })
    }

    @Test
    fun contentPacksAddTiersOnTopWithoutTouchingLaunchContent() {
        val launch = Catalog.apps
        try {
            Catalog.packs = setOf(ContentPack.AiTier)
            assertEquals(66, Catalog.apps.size)
            assertEquals(launch, Catalog.apps.take(60))
            val ai = Catalog.apps.drop(60)
            assertTrue(ai.all { it.tier == 11 && it.unlockLevel > launch.maxOf { a -> a.unlockLevel } })
            assertTrue(e.rules.appPrice(ai.first()) > launch.maxOf { e.rules.appPrice(it) })
            assertEquals(ai.first().unlockLevel + 25, Catalog.proLevel.getValue(ai.first().id))
        } finally {
            Catalog.packs = emptySet()
        }
        assertEquals(60, Catalog.apps.size)
    }

    // Unique effects (docs/bible/15-unique-effects.md) -------------------------------------------

    private fun deckOf(vararg ids: String) = e.newGame(now, 1).copy(appLevels = ids.associateWith { 1 }, active = ids.toList(), level = 60)

    @Test
    fun valueEffectsFollowTheirConditions() {
        val s = deckOf("memo", "calc", "contacts", "phone", "sms")
        val n = valueOf(4)
        assertEquals(e.earn(s, "memo", n, 0) * valueOf(2), e.earn(s, "memo", n, 0, EarnCtx(direct = true)), "메모 direct ×2")
        val calc = Catalog.app("calc")
        assertEquals(1_000L, e.effectPermille(s, calc, n, 0, EarnCtx()))
        assertEquals(1_000L, e.effectPermille(s, calc, valueOf(2), 1, EarnCtx()))
        assertEquals(1_500L, e.effectPermille(s, calc, valueOf(2), 2, EarnCtx()))
        assertEquals(1_000L, e.effectPermille(s, calc, valueOf(2), 2, EarnCtx(wave = true)))
        // 연락처: two other social apps in the deck -> +30%.
        val alone = deckOf("contacts", "memo", "calc")
        assertEquals(e.earn(alone, "contacts", n, 0).toDouble() * 1.3 * (e.earn(s, "contacts", n, 0).toDouble() / (e.earn(alone, "contacts", n, 0).toDouble() * 1.3)),
            e.earn(s, "contacts", n, 0).toDouble(), 1.0)
        assertEquals(1_300L, e.effectPermille(s, Catalog.app("contacts"), n, 0, EarnCtx()))
    }

    @Test
    fun effectsOnlyWorkInTheDeck() {
        val s = deckOf("alarm", "memo", "weather")
        assertEquals(1_000L, e.effectPermille(s, Catalog.app("photo"), valueOf(4), 0, EarnCtx()))
        assertEquals(e.energyCap(s), e.energyCap(deckOf("alarm", "memo", "weather")))
        assertEquals(e.energyCap(s) + 2, e.energyCap(deckOf("alarm", "memo", "grab")), "그래브 battery +2")
    }

    @Test
    fun touchingPairsIgniteAndCountBonusesUnderRuleB() {
        val match = deckOf("match", "memo", "alarm")
            .copy(rows = rows("memo" to 1, "match" to 1, "alarm" to 1, "match" to 1, "memo" to 1))
        val bursts = e.swipe(match, 102, now)!!.beats.filterIsInstance<Beat.Burst>()
        assertEquals(listOf(3, 4), bursts.map { it.chain })
        assertEquals(listOf(2, 2), bursts.map { it.row.count })

        val green = deckOf("green", "memo", "alarm").copy(rows = rows("green" to 1, "memo" to 1, "green" to 1))
        val result = e.swipe(green, 101, now)!!
        assertEquals(2, result.beats.filterIsInstance<Beat.Burst>().single().row.threshold)
        assertEquals(e.rules.gaugePerMerge + e.rules.gaugePerBurst + 10, result.state.gauge)
    }

    @Test
    fun olderSavedRowsAdoptRuleBWithoutChangingCountsOrWallet() {
        val active = e.newGame(now, 11)
        val old = active.copy(rulesVersion = 3, wallet = valueOf(22), rows = active.rows.map { it.copy(threshold = 4) })
        val loaded = e.tick(SaveCodec.decode(SaveCodec.encode(old)), now)
        assertEquals(active.rulesVersion, loaded.rulesVersion)
        assertEquals(active.rows, loaded.rows)
        assertEquals(old.wallet, loaded.wallet)
    }

    @Test
    fun economyEffectsChangePrices() {
        val plain = deckOf("alarm", "memo", "weather").copy(level = 130, wallet = BigInteger.TEN.pow(60))
        val shop = plain.copy(active = plain.active + "shopee", appLevels = plain.appLevels + ("shopee" to 1))
        assertEquals(e.appPrice(plain, "pin") * valueOf(9) / valueOf(10), e.appPrice(shop, "pin"), "쇼피피 −10% on cheaper apps")
        val work = plain.copy(active = plain.active + "glints", appLevels = plain.appLevels + ("glints" to 1))
        assertEquals(e.upgradePrice(plain, "memo") * valueOf(9) / valueOf(10), e.upgradePrice(work, "memo"), "글린츠츠 −10% on work upgrades")
        val store = plain.copy(active = plain.active + "store", appLevels = plain.appLevels + ("store" to 1))
        assertEquals(2, e.buy(store, "pin").appLevels["pin"], "큐글플레이: bought apps start upgraded")
        assertTrue(e.buy(plain.copy(active = plain.active + "travel", appLevels = plain.appLevels + ("travel" to 1)), "pin").boostNext, "트래블로까 arms the next burst")
    }

    @Test
    fun timeEffectsPayOncePerDayAndAfterReturning() {
        val s = deckOf("momo", "memo", "alarm")
        val (back, _) = e.settleOffline(s, 3_600_000L, now + 3_600_000L)
        assertTrue(back.returnBurst)
        assertEquals(3, back.returnSwipes)
        // 모모모: the first burst of a day pays ×3, the second does not.
        val board = s.copy(rows = rows("memo" to 1, "memo" to 2, "alarm" to 1, "memo" to 2, "alarm" to 1, "memo" to 2, "alarm" to 1))
        val first = e.swipe(board, 102, now)!!
        assertEquals(now / 86_400_000L, first.state.dailyBonusDay["momo"])
        val again = e.swipe(board.copy(dailyBonusDay = first.state.dailyBonusDay), 102, now)!!
        assertEquals(first.earned, again.earned * valueOf(3))
    }

    @Test
    fun defenceEffectsHandleInterruptions() {
        val base = deckOf("phone", "memo", "alarm").copy(energy = 5, rows = rows("memo" to 1, "alarm" to 1, "memo" to 1, "alarm" to 1, "memo" to 1, "alarm" to 1, "memo" to 1)
            .toMutableList().also { it[0] = Row(90, RowKind.Call) })
        val o = e.swipe(base, 90, now)!!
        assertEquals(5 - 1 + 2, o.state.energy, "전화: answering a spam call recharges 2")
    }

    @Test
    fun packCloudItemsOpenOneLevelPerThreeResetsAfterTheUpdate() {
        val rich = e.newGame(now, 1, Backup(cloud = Cloud(gb = 1_000_000, syncs = 20)))
        assertEquals(rich, e.buyCloud(rich, CloudItem.AiBoost), "no pack, no item")
        try {
            Catalog.packs = setOf(ContentPack.AiTier)
            assertEquals(rich, e.buyCloud(rich, CloudItem.AiBoost), "banked GB alone does not open it")
            val oneSync = rich.copy(backup = rich.backup.copy(cloud = rich.backup.cloud.copy(packResets = 3)))
            val bought = e.buyCloud(oneSync, CloudItem.AiBoost)
            assertEquals(1, bought.backup.cloud.lv(CloudItem.AiBoost))
            assertEquals(bought, e.buyCloud(bought, CloudItem.AiBoost), "one level per 3 resets")
        } finally {
            Catalog.packs = emptySet()
        }
    }

    @Test
    fun bulkUpgradesSpendLikeSingleOnes() {
        val s = e.newGame(now, 1).copy(wallet = valueOf(10_000))
        val cost = e.upgradeCost(s, "memo", 10)
        val (ten, n) = e.upgradeMany(s.copy(wallet = cost), "memo", 10)
        assertEquals(10, n)
        assertEquals(ZERO, ten.wallet)
        assertEquals(11, ten.appLevels["memo"])
        val (max, m) = e.upgradeMax(s, "memo")
        assertTrue(m > 0 && e.upgradePrice(max, "memo") > max.wallet, "Max stops only when the next level is unaffordable")
        // 모두 강화 buys the cheapest next level first: the deck ends up levelled evenly.
        val (all, k) = e.upgradeAll(s)
        assertTrue(k > 0)
        val levels = s.active.map { all.appLevels.getValue(it) }
        assertTrue(levels.max() - levels.min() <= 3, "even levels $levels")
    }

    @Test
    fun upgradeGrowthCanRiseByTier() {
        val steep = RuleSet(upgradeGrowthPerTierPermille = 20)
        assertEquals(1_300, steep.upgradeGrowth(Catalog.app("memo")))
        assertEquals(1_300 + 20 * 9, steep.upgradeGrowth(Catalog.app("tube")))
        assertEquals(e.rules.upgradeGrowth(Catalog.app("tube")), e.rules.upgradeGrowth(Catalog.app("memo")), "off by default")
    }

    @Test
    fun alertsCostWhileLeftAndHelpWhenSwiped() {
        val s = deckOf("memo", "alarm", "weather").copy(level = 60, energy = 2)
        val low = s.copy(rows = s.rows.toMutableList().also { it[0] = Row(90, RowKind.Alert, alert = Alert.LowBattery) })
        assertEquals(e.chargeMs(s) * 13 / 10, e.chargeMs(low), "배터리 부족 left: charging 30% slower")
        assertEquals(2 - 1 + 4, e.swipe(low, 90, now)!!.state.energy, "swiped: +4")
        val full = s.copy(rows = s.rows.toMutableList().also { it[0] = Row(91, RowKind.Alert, alert = Alert.Storage) })
        assertEquals(e.rowCount(s) - 1, e.rowCount(full), "저장공간 부족 left: one row less")
        val crash = s.copy(rows = s.rows.toMutableList().also { it[0] = Row(92, RowKind.Alert, app = "memo", alert = Alert.Crash) })
        assertEquals("memo", e.swipe(crash, 92, now)!!.state.crashBoost, "앱 충돌 swiped: memo's next burst ×2")
    }

    @Test
    fun timedCardsDrillAndDoNotDisturb() {
        val s = deckOf("memo", "alarm", "weather").copy(events = setOf(EventCard.Drill, EventCard.Dnd), equipped = listOf(EventCard.Drill, EventCard.Dnd))
        val drill = e.swipe(s.copy(rows = s.rows.toMutableList().also { it[0] = Row(93, RowKind.Event, event = EventCard.Drill) }), 93, now)!!.state
        assertEquals(now + e.rules.drillMs, drill.drillUntil)
        var tapped = drill
        repeat(150) { tapped = e.tapDrill(tapped, now + 1_000) }
        assertEquals(100, tapped.drillTaps, "capped at ×2")
        var d = s
        repeat(5) { d = e.activateDnd(d.copy(dndUntil = 0), now) }
        assertEquals(e.rules.dndPerDay, d.dndUses, "three times a day")
        val on = e.activateDnd(s, now)
        assertEquals(e.autoIntervalMs(on) / 2, e.autoIntervalMs(on, now + 1))
    }

    @Test
    fun numbersUseGlobalShortNotation() {
        fun s(x: String) = short(BigInteger(x))
        assertEquals("999", s("999"))
        assertEquals("1K", s("1000"))
        assertEquals("1.23K", s("1234"))
        assertEquals("12.3K", s("12345"))
        assertEquals("123K", s("123456"))
        assertEquals("82M", s("82000000"))
        assertEquals("1.99B", s("1999999999"))
        assertEquals("4.5T", s("4500000000000"))
        assertEquals("1aa", s("1" + "0".repeat(15)))
        assertEquals("1az", s("1" + "0".repeat(15 + 25 * 3)))
        assertEquals("1ba", s("1" + "0".repeat(15 + 26 * 3)))
    }

    @Test
    fun premiumBudgetMatchesTwelveWeeksOfFreeCredits() {
        val r = RuleSet()
        fun full(tier: Int) = (1..5).sumOf { r.premiumCost(Catalog.apps.first { it.tier == tier }, it) }
        // 84 days of unbroken attendance (2 full boards + 24 days) + daily missions
        val board = r.attendanceBoard.map { it.data }
        val free = 2 * board.sum() + board.take(24).sum() + 84 * r.missionCredits
        // one top-tier app to ★5, or two mid-tier, or four tier-1 apps
        assertTrue(full(10) <= free, "top ${full(10)} vs $free")
        assertTrue(2 * full(5) <= free && 2 * full(6) <= free * 11 / 10, "mid ${full(5)} ${full(6)}")
        assertTrue(4 * full(1) <= free, "low ${full(1)}")
        assertEquals(32_000L, r.premiumPermille(5))
    }

    @Test
    fun accountSurvivesResetAndSyncAndPremiumRaisesValue() {
        val e = Engine(); val shop = Shop(e)
        var s = e.newGame(0, 1).copy(level = 30, runEarned = BigInteger.TEN.pow(14))
        s = shop.credit(s, 1_000)
        val before = e.earn(s, "memo", BigInteger.ONE, 0)
        s = shop.upgradePremium(s, "memo")!!
        assertEquals(1, s.account.stars("memo"))
        assertEquals(before * BigInteger.TWO, e.earn(s, "memo", BigInteger.ONE, 0))
        val after = e.factoryReset(s, 1)!!
        assertEquals(1, after.account.stars("memo"))
        assertEquals(s.account.credits, after.account.credits)
        val synced = e.sync(after.copy(level = 60), 2)!!
        assertEquals(1, synced.account.stars("memo"))
    }

    @Test
    fun attendanceAdsAndPassFollowTheirDailyLimits() {
        val e = Engine(); val shop = Shop(e); val day = 86_400_000L
        var s = e.newGame(0, 1)
        repeat(10) { d -> s = shop.claimAttendance(s, d * day); s = shop.claimAttendance(s, d * day + 1) }
        assertEquals(10, s.account.streak)
        assertEquals(RuleSet().attendanceBoard.take(10).sumOf { it.data }, s.account.credits)
        assertEquals(1, s.account.tickets[CreditItem.BackupBoost])
        assertEquals(2 + 1, s.account.tickets[CreditItem.Overclock])
        // a missed day restarts the board
        s = shop.claimAttendance(s, 12 * day)
        assertEquals(1, s.account.streak)
        assertNotNull(shop.useTicket(s, CreditItem.BackupBoost, 12 * day))
        repeat(AdSlot.CreditBox.daily) { s = shop.watchAd(s, AdSlot.CreditBox, 13 * day)!! }
        assertNull(shop.watchAd(s, AdSlot.CreditBox, 13 * day))
        assertNotNull(shop.watchAd(s, AdSlot.CreditBox, 14 * day))
        s = shop.buyPass(s, 10 * day)
        assertTrue(shop.passActive(s, 39 * day) && !shop.passActive(s, 40 * day))
        val boosted = shop.watchAd(s, AdSlot.Boost, 10 * day)!!
        assertTrue(shop.boostActive(boosted, 10 * day + 59 * 60_000) && !shop.boostActive(boosted, 10 * day + 61 * 60_000))
    }

    @Test
    fun spamNeverLocksTheBoard() {
        val e = Engine()
        // A long run that only ever clears spam must still see app rows come back.
        var s = e.newGame(0, 7).copy(level = 30)
        var t = 0L
        var maxSpam = 0
        repeat(400) {
            t += 3_000
            s = e.tick(s, t)
            val target = s.rows.lastOrNull { it.kind == RowKind.Spam }?.id ?: s.rows.last().id
            s = e.swipe(s, target, t)?.state ?: s
            maxSpam = maxOf(maxSpam, s.rows.count { it.kind == RowKind.Spam })
        }
        assertTrue(maxSpam <= RuleSet().spamMax + 1, "spam rows peaked at $maxSpam")
        assertTrue(s.rows.any { it.kind == RowKind.App })
    }

    @Test
    fun wildAndSpamCannotReplayRemovedRowsOrChainPastTheBoard() {
        val e = Engine(RuleSet(gaugePerMerge = 0, gaugePerBurst = 1, overloadMinIntervalMs = 360_000))
        for (seed in 1L..8) {
            var s = e.newGame(0, seed).copy(level = 25, assists = mapOf(Assist.Wild to 5))
            repeat(600) { step ->
                val now = (step + 1) * 3_000L
                s = e.tick(s, now)
                val target = e.bestTarget(s, now) ?: return@repeat
                val move = e.swipe(s, target, now) ?: return@repeat
                val merges = move.beats.filterIsInstance<Beat.Merged>()
                val bursts = move.beats.filterIsInstance<Beat.Burst>()
                assertEquals(merges.size, merges.map { it.fromId }.toSet().size)
                assertTrue(bursts.size <= (s.rows.size - 1) / 2)
                assertTrue(bursts.none { it.row.kind == RowKind.Spam })
                assertTrue(move.state.rows.size <= e.rules.maxRows)
                s = move.state
            }
        }
    }

    @Test
    fun chargedGaugeWaitsForWaveCooldownAndThenFires() {
        val e = Engine(RuleSet(overloadMinIntervalMs = 360_000))
        val board = e.newGame(0, 1).copy(gauge = 100, nextOverloadAt = 200_000)
        val early = e.swipe(board, 4, 199_999)!!
        assertEquals(100, early.state.gauge)
        assertTrue(!early.overload)
        val ready = e.swipe(board, 4, 200_000)!!
        assertTrue(ready.overload)
        assertEquals(560_000, ready.state.nextOverloadAt)
    }

    @Test
    fun majorEventsShareCooldownAndAlternateTypes() {
        val e = Engine(RuleSet(majorEventCooldownMs = 450_000, callEverySwipes = 1))
        val board = e.newGame(0, 1).copy(level = 15, equipped = listOf(EventCard.Drill), swipes = 58)
        val call = e.swipe(board, 4, 10_000)!!.state
        assertEquals("call", call.previousMajorEvent)
        val drillReady = board.copy(swipes = 59, previousMajorEvent = call.previousMajorEvent, nextMajorEventAt = call.nextMajorEventAt)
        val tooSoon = e.swipe(drillReady, 4, 20_000)!!.state
        assertTrue(tooSoon.rows.none { it.event == EventCard.Drill })
        assertTrue(tooSoon.rows.none { it.kind == RowKind.Call })
        val drill = e.swipe(drillReady, 4, 460_000)!!.state
        assertEquals("Drill", drill.previousMajorEvent)
        assertTrue(drill.rows.any { it.event == EventCard.Drill })
        assertTrue(drill.rows.none { it.kind == RowKind.Call })
    }

    @Test
    fun speedPassesSpeedUpTheGameButNotPastAThumb() {
        val e = Engine(); val shop = Shop(e); val day = 86_400_000L
        var s = e.newGame(0, 1)
        assertEquals(3_000L, e.chargeMs(s, 0))
        s = shop.buySpeedPass(s, 0, SpeedPass.Ultra)
        assertEquals(8_000L, e.speedPermille(s, day))
        assertEquals(1_000L, e.chargeMs(s, day))
        assertTrue(shop.passActive(s, day))
        assertEquals(1_000L, e.speedPermille(s, 31 * day))
    }

    @Test
    fun prestigeBonusDoesNotOverflowLateGameFiles() {
        val e = Engine(); val shop = Shop(e)
        var s = e.newGame(0, 1)
        s = shop.buySpeedPass(s, 0, SpeedPass.Ultra).copy(level = 125, runEarned = BigInteger.TEN.pow(110))
        val plain = e.backupFilesNow(s)
        assertTrue(plain > 10_000_000_000_000_000L, "late game pays $plain files")
        val after = e.factoryReset(s, 1)!!
        assertEquals(plain / 1_000 * 4_000 + plain % 1_000 * 4, after.backup.files)
    }

    @Test
    fun phaseTwoEffectsWork() {
        val e = Engine(); val day = 86_400_000L
        fun deck(vararg ids: String, rows: List<Row>) = e.newGame(0, 3).copy(level = 90, appLevels = ids.associateWith { 1 }, active = ids.toList(), rows = rows)
        fun app(id: Long, a: String, n: Int) = Row(id, RowKind.App, a, count = n, threshold = e.rules.threshold)
        // 날씨: first swipe of the day adds one to its rows (once).
        val w = deck("weather", "memo", rows = listOf(app(1, "weather", 1), app(2, "memo", 1), app(3, "memo", 1)))
        val w1 = e.swipe(w, 3, day)!!.state
        assertEquals(2, w1.rows.first { it.id == 1L }.count)
        assertEquals(2, e.swipe(w1, 2, day + 1)!!.state.rows.first { it.id == 1L }.count)
        // 큐글맵스: merges with a different app.
        val m = deck("maps", "memo", rows = listOf(app(1, "maps", 2), app(2, "alarm", 1), app(3, "memo", 2)))
        assertTrue(e.swipe(m, 2, day)!!.beats.any { it is Beat.Merged })
        // 코코아T: a merged taxi row pulls in another taxi row.
        val c = deck("ctaxi", "memo", rows = listOf(app(1, "ctaxi", 2), app(2, "memo", 1), app(3, "ctaxi", 1), app(4, "ctaxi", 1)))
        val c1 = e.swipe(c, 2, day)!!
        assertEquals(4, c1.beats.filterIsInstance<Beat.Burst>().single().row.count)
        assertEquals(listOf(3L, 4L), c1.beats.filterIsInstance<Beat.Merged>().map { it.fromId })
        // 와이: social apps grow with its bursts; 듀오링구 grows with the login streak.
        val y = deck("why", "chat", rows = listOf(app(1, "why", 2), app(2, "memo", 1), app(3, "why", 2)))
        assertEquals(1, e.swipe(y, 2, day)!!.state.whyStacks)
        val duo = e.newGame(0, 1)
        val streak = duo.withAccount { it.copy(streak = 10) }
        assertEquals(e.earn(duo, "duo", BigInteger.ONE, 0) * BigInteger.valueOf(12) / BigInteger.TEN, e.earn(streak, "duo", BigInteger.ONE, 0))
    }

    @Test
    fun saveRoundTripsAPlayedGameAndReadsOldSaves() {
        val e = Engine(); val shop = Shop(e)
        var s = e.newGame(0, 5)
        var t = 0L
        repeat(200) { t += 3_000; s = e.tick(s, t); e.bestTarget(s, t)?.let { id -> e.swipe(s, id, t)?.let { s = it.state } } }
        s = shop.claimAttendance(shop.credit(s, 500), t)
        s = shop.upgradePremium(s, "memo") ?: s
        s = shop.buySpeedPass(s, t, SpeedPass.Turbo)
        val text = SaveCodec.encode(s)
        assertEquals(s, SaveCodec.decode(text))
        // A save from before monetization existed: no account at all.
        val old = text.replace(Regex(""","account":\{.*?"speedPassUntil":\d+[^}]*}"""), "")
        assertEquals(0, SaveCodec.decode(old).let { it.backup.cloud.account.stars("zzz") })
        assertTrue(text.length < 20_000, "save is ${text.length} chars")
    }

    @Test
    fun missionsAndAchievementsTrackPlay() {
        val e = Engine(); val meta = Meta(e); val shop = Shop(e)
        var s = e.newGame(0, 9)
        var t = 0L
        repeat(400) { t += 3_000; s = e.tick(s, t); e.bestTarget(s, t)?.let { id -> e.swipe(s, id, t)?.let { o -> s = meta.record(o.state, o, t) } } }
        assertEquals(3, meta.missionsFor(t).size)
        assertTrue(s.account.stats["Swipes"]!! > 100 && s.account.stats["Bursts"]!! > 0)
        if (meta.missionsDone(s, t)) assertNotNull(meta.claimMissions(shop, s, t)) else assertNull(meta.claimMissions(shop, s, t))
        val reset = meta.reset(s.copy(level = 30), t)!!
        assertTrue("초기화 입문" in reset.account.titles)
        assertEquals(1, reset.account.pieces)
        // tomorrow's progress starts at zero
        assertEquals(0, meta.missionProgress(reset, Mission.Swipes, t + 86_400_000L))
    }

    @Test
    fun claimsArePaidOnce() {
        val e = Engine(); val shop = Shop(e)
        val s = e.newGame(0, 1)
        val once = shop.claimOnce(s, "gpa.1234") { shop.credit(it, 550, bought = true) }!!
        assertEquals(550, once.account.credits)
        assertNull(shop.claimOnce(once, "gpa.1234") { shop.credit(it, 550, bought = true) })
        assertEquals(once, SaveCodec.decode(SaveCodec.encode(once)))
    }

    @Test
    fun dailyChallengeIsTheSameBoardForEveryoneWithTwoKeys() {
        val e = Engine(); val meta = Meta(e); val now = 10 * 86_400_000L
        assertEquals(meta.challengeBoard(now), meta.challengeBoard(now + 3_600_000))
        assertEquals(6, meta.challengeBoard(now).active.size)
        var s = e.newGame(0, 1)
        var board: GameState? = null
        repeat(2) { val (a, b) = meta.startChallenge(s, now)!!; s = a; board = b }
        assertNull(meta.startChallenge(s, now))
        var b = board!!; var t = now
        while (t < now + 60_000) { t += 1_000; b = e.tick(b, t); e.bestTarget(b, t)?.let { id -> e.swipe(b, id, t)?.let { b = it.state } } }
        s = meta.finishChallenge(s, b)
        assertEquals(2, s.account.pieces)
        assertEquals(b.runEarned.toString(), s.account.challengeBest)
        assertNotNull(meta.startChallenge(s, now + 86_400_000L))
    }

    @Test
    fun awayTimeResistsClockTricks() {
        val h = 3_600_000L
        // same boot: moving the date forward a week changes nothing, only real elapsed time counts
        assertEquals(2 * h, AwayClock.awayMs(0, 7 * 24 * h, 1_000, 1_000 + 2 * h, sameBoot = true))
        // new boot: wall clock trusted up to the cap
        assertEquals(3 * h, AwayClock.awayMs(0, 3 * h, 5_000, 10, sameBoot = false))
        assertEquals(AwayClock.rebootCapMs, AwayClock.awayMs(0, 48 * h, 5_000, 10, sameBoot = false))
        // clock set back: nothing
        assertEquals(0, AwayClock.awayMs(10 * h, 5 * h, 5_000, 10, sameBoot = false))
    }

    @Test
    fun fxPlanFollowsTheLadderAndRespectsReducedMotion() {
        val e = Engine()
        var s = e.newGame(0, 4)
        var t = 0L
        var best: SwipeOutcome? = null
        repeat(300) { t += 3_000; s = e.tick(s, t); e.bestTarget(s, t)?.let { id -> e.swipe(s, id, t)?.let { o -> s = o.state; if (o.chain > (best?.chain ?: 0)) best = o } } }
        val o = best!!
        val plan = FxPlan.plan(o)
        assertEquals(o.beats.count { it is Beat.Burst }, plan.count { it.kind == CueKind.Shatter })
        assertTrue(plan.zipWithNext().all { (a, b) -> a.atMs <= b.atMs })
        assertTrue(plan.any { it.kind == CueKind.Payoff })
        assertTrue(plan.filter { it.kind == CueKind.Sound }.all { it.sound != null })
        val calm = FxPlan.plan(o, reducedMotion = true)
        assertTrue(calm.none { it.kind == CueKind.Shake || it.kind == CueKind.ScreenCrack || it.kind == CueKind.Flash })
        assertEquals(80, FxPlan.step(12).hitStopMs)
        // every sound id the planner can name exists as a file
        val ids = plan.mapNotNull { it.sound } + listOf("notify.spam", "phone.buzz.ring", "notify.event", "phone.buzz") +
            Category.entries.map { "notify." + it.name.lowercase() } + "ui.levelup"
        val dir = java.io.File("../assets-src/sfx")
        ids.distinct().forEach { id -> assertTrue(java.io.File(dir, "$id.ogg").exists() || java.io.File(dir, "cc0/$id.ogg").exists(), "missing sound $id") }
    }
}
