package com.graviton94.notificationcleaner.core

import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommerceTest {
    private val day = 86_400_000L
    private val now = day * 100 + 3_600_000
    private val engine = Engine()

    private fun session() = WebSession(engine, engine.newGame(now, 11), now)

    @Test fun eachPublishedDataPackQuotesAndGrantsTheSameAmount() {
        val prices = listOf(2200, 5900, 12_000, 33_000, 69_000, 119_000)
        val amounts = listOf(100L, 540L, 1200L, 3700L, 8600L, 16_800L)
        for ((i, amount) in amounts.withIndex()) {
            val w = session()
            val bonus = minOf(1200L, amount)
            val id = "data_$amount"
            val packs = Json.parseToJsonElement(w.bm(now)).jsonObject.getValue("packs").jsonArray
            val quote = packs[i].jsonObject
            assertEquals(id, quote.getValue("id").jsonPrimitive.content)
            assertEquals(prices[i].toLong(), quote.getValue("won").jsonPrimitive.long)
            assertEquals(amount + bonus, quote.getValue("gets").jsonPrimitive.long)
            w.buyData(id, "first", now)
            w.buyData(id, "first", now)
            assertEquals(amount + bonus, w.state.account.credits)
            w.buyData("data_100", "second", now)
            assertEquals(amount + bonus + 100, w.state.account.credits)
        }
    }

    @Test fun anOldReceiptRemainsSettledAcrossSaveAndReset() {
        val rules = engine.rules
        val w = session()
        for (i in 0..501) w.buyData("data_100", "receipt-$i", now)
        val persisted = SaveCodec.decode(w.save()).copy(level = rules.resetLevel, runEarned = rules.levelP(rules.resetLevel))
        val restored = WebSession(engine, persisted, now)
        restored.premium("memo", false, now)
        val account = restored.state.account
        restored.reset(now)
        assertTrue(restored.state.backup.files > 0)
        assertEquals(account.premium, restored.state.account.premium)
        restored.buyData("data_100", "receipt-0", now)
        restored.buyData("data_100", " ", now)
        assertEquals(account.credits, restored.state.account.credits)
    }

    @Test fun aYearOfDailyAdsAndMonthlyPurchasesFitsTheSaveBudget() {
        val rules = engine.rules
        val mature = engine.newGame(now, 11).copy(level = 124, runEarned = rules.levelP(124),
            earnRate = rules.levelP(124) / 1_000_000L.toBigInteger(),
            lastEarned = rules.levelP(124) / 1_000L.toBigInteger(), appLevels = Catalog.apps.associate { it.id to 50 })
        val w = WebSession(engine, mature, now)
        val purchases = (0..360 step 30).associateWith { day ->
            "GPA.$day-" + "store-purchase-token".repeat(6)
        }
        var views = 0
        for (d in 0..364) {
            val at = now + d * day
            purchases[d]?.let { w.buyData("data_100", it, at) }
            w.offline(day, at)
            for (slot in AdSlot.entries) repeat(slot.daily) { sequence ->
                val id = "network-ad-$d-${slot.name}-$sequence-" + "ad-token".repeat(9)
                val before = w.state.account.processedAdClaims.values.flatten()
                w.watchAd(slot.name, id, at)
                val after = w.state.account.processedAdClaims.values.flatten()
                assertTrue(after.any { it !in before })
                val settled = w.state.account
                w.watchAd(slot.name, id, at)
                assertEquals(settled, w.state.account)
                views++
            }
        }
        assertEquals(4_380, views)
        assertEquals(84, w.state.account.processedAdClaims.values.sumOf { it.size })
        assertEquals(purchases.values.toList(), w.state.account.processedClaims)

        val at = now + 364 * day
        val bytes = w.save().toByteArray(Charsets.UTF_8).size
        assertTrue(bytes <= 20_000, "save was $bytes bytes after $views ads")
        println("365-day commerce fixture: $bytes bytes · $views ads · ${purchases.size} purchase receipts")
        val restored = WebSession(engine, SaveCodec.decode(w.save()), at + day)
        val bought = restored.state.account.creditsBought
        restored.buyData("data_100", purchases.getValue(0), at + day)
        assertEquals(bought, restored.state.account.creditsBought)
        val lastAd = "network-ad-364-CreditBox-0-" + "ad-token".repeat(9)
        val credits = restored.state.account.credits
        restored.watchAd("CreditBox", lastAd, at + day)
        assertEquals(credits, restored.state.account.credits)
        restored.snapshot(at + 7 * day)
        assertTrue(restored.state.account.processedAdClaims.isEmpty())
        restored.watchAd("CreditBox", lastAd, at + 7 * day)
        assertEquals(credits + rules.adCredits, restored.state.account.credits)
    }

    @Test fun migratesOnlyKnownLegacyAdIdsAndKeepsPurchaseReplayProtection() {
        val legacy = engine.newGame(now, 11).withAccount {
            it.copy(processedClaims = listOf("fakead-1", "GPA.keep", "untyped-keep"))
        }
        val w = WebSession(engine, SaveCodec.decode(SaveCodec.encode(legacy)), now)
        assertEquals(listOf("GPA.keep", "untyped-keep"), w.state.account.processedClaims)
        assertEquals(1, w.state.account.processedAdClaims.values.sumOf { it.size })
        w.watchAd("CreditBox", "fakead-1", now)
        w.watchAd("CreditBox", " ", now)
        w.buyData("data_100", "GPA.keep", now + 7 * day)
        assertEquals(0L, w.state.account.credits)
        assertTrue(w.state.account.processedAdClaims.isEmpty())
    }

    @Test fun starterUsesOriginalInstallTimeAndGrantsThePublishedContentsOnce() {
        val fresh = session()
        val inside = WebSession(engine, SaveCodec.decode(fresh.save()), now + 2 * day - 1)
        inside.buyStarter("starter", now + 2 * day - 1)
        inside.buyStarter("again", now + 2 * day - 1)
        assertEquals(500L, inside.state.account.credits)
        assertEquals(1, inside.state.account.starVouchers)
        assertEquals(mapOf(CreditItem.Boost to 3, CreditItem.Overclock to 3), inside.state.account.tickets)
        val expired = WebSession(engine, SaveCodec.decode(fresh.save()), now + 2 * day)
        expired.buyStarter("starter", now + 2 * day)
        assertEquals(0L, expired.state.account.credits)
    }

    @Test fun rookieAndAttendanceFollowCalendarBoundariesWithoutDuplicateGrants() {
        val w = session()
        w.buyRookie("rookie", now)
        for (i in 0..7) repeat(2) { w.claimRookie(now + i * day) }
        assertEquals(650L, w.state.account.credits)
        assertEquals(1, w.state.account.starVouchers)
        val expired = session()
        expired.buyRookie("late", now + 7 * day)
        assertEquals(0L, expired.state.account.credits)

        val shop = Shop(engine)
        var state = engine.newGame(now, 11)
        for (i in 0..29) {
            state = shop.claimAttendance(state, now + i * day)
            assertEquals(state, shop.claimAttendance(state, now + i * day))
        }
        assertEquals(30, state.account.streak)
        assertTrue("알림 개근왕" in state.account.titles)
        assertEquals(1, shop.claimAttendance(state, now + 30 * day).account.streak)
        assertEquals(1, shop.claimAttendance(state, now + 31 * day).account.streak)
    }

    @Test fun watchedAdsAndAnActivePassBothGrantOneSharedOfflineReward() {
        val reference = Engine(engine.rules.copy(adWatchOfflinePermille = 1_000))
        val shop = Shop(engine)
        val away = 60_000L
        val s = engine.newGame(now, 11).copy(earnRate = BigInteger.valueOf(10_000))
        val watched = assertNotNull(shop.watchAd(s, AdSlot.CreditBox, now))
        val paid = shop.buyPass(s, now)
        val claimedWithPass = assertNotNull(shop.watchAd(paid, AdSlot.CreditBox, now))
        assertEquals(-1L, claimedWithPass.account.lastAdWatchDay)
        val passAndAd = watched.withAccount { it.copy(passUntil = paid.account.passUntil) }
        for (state in listOf(watched, paid, claimedWithPass, passAndAd)) {
            val base = reference.settleOffline(state, away, now + day).second
            assertEquals(base * BigInteger.valueOf(17) / BigInteger.TEN,
                engine.settleOffline(state, away, now + day).second)
        }
        assertEquals(reference.settleOffline(watched, away, now + 2 * day),
            engine.settleOffline(watched, away, now + 2 * day))
        assertEquals(reference.settleOffline(paid, away, paid.account.passUntil),
            engine.settleOffline(paid, away, paid.account.passUntil))
    }

    @Test fun speedPassesIncludeTheSharedRewardAndOnlyUltraStacksAnExtraBonus() {
        val reference = Engine(engine.rules.copy(ultraOfflinePermille = 1_000, adWatchOfflinePermille = 1_000))
        val away = 60_000L
        val s = engine.newGame(now, 11).copy(earnRate = BigInteger.valueOf(10_000))
        for ((kind, multiplier) in listOf(SpeedPass.Turbo to 1_700L, SpeedPass.Ultra to 2_550L)) {
            val active = Shop(engine).buySpeedPass(s, now - 1, kind)
            val result = engine.settleOfflineWithBonus(active, away, now)
            val base = reference.settleOffline(active, away, now).second
            assertEquals(base, result.basePaid)
            assertEquals(base * BigInteger.valueOf(multiplier) / BigInteger.valueOf(1_000), result.paid)
            assertEquals(reference.chargeMs(active, now), engine.chargeMs(active, now))
            assertEquals(reference.prestigePermille(active, now), engine.prestigePermille(active, now))
            val expiration = active.account.speedPassUntil
            assertEquals(reference.settleOffline(active, away, expiration), engine.settleOffline(active, away, expiration))
        }
    }

    @Test fun offlineBonusIsConsistentInSnapshotShopAndSettlement() {
        val base = engine.newGame(now, 11).copy(earnRate = BigInteger.valueOf(10_000))
        val bought = Shop(engine).buySpeedPass(base, now, SpeedPass.Ultra)
        val watched = bought.withAccount { it.copy(lastAdWatchDay = dayOf(now)) }
        val w = WebSession(engine, watched, now)
        val snapshot = Json.parseToJsonElement(w.snapshot(now)).jsonObject
        val bm = Json.parseToJsonElement(w.bm(now)).jsonObject
        val settlement = Json.parseToJsonElement(w.offline(60_000L, now)).jsonObject
        val bonus = snapshot.getValue("offlineBonus").jsonObject
        assertEquals(bonus, bm.getValue("offlineBonus"))
        assertEquals(bonus, bm.getValue("state").jsonObject.getValue("offlineBonus"))
        assertEquals(bonus, settlement.getValue("offlineBonus"))
        assertTrue(bonus.getValue("adActive").jsonPrimitive.boolean)
        assertTrue(bonus.getValue("passActive").jsonPrimitive.boolean)
        assertTrue(bonus.getValue("ultraActive").jsonPrimitive.boolean)
        assertEquals(47 * 3_600_000L, bonus.getValue("adLeftMs").jsonPrimitive.long)
        assertEquals(30 * day, bonus.getValue("passLeftMs").jsonPrimitive.long)
        assertEquals(30 * day, bonus.getValue("ultraLeftMs").jsonPrimitive.long)
        assertEquals(2_550L, bonus.getValue("multiplierPermille").jsonPrimitive.long)
        assertEquals(2_550L, bonus.getValue("ultraWithPassMultiplierPermille").jsonPrimitive.long)
        fun amount(key: String) = BigInteger(settlement.getValue(key).jsonPrimitive.content)
        assertTrue(amount("bonusPaid").signum() > 0)
        assertEquals(amount("paid"), amount("basePaid") + amount("bonusPaid"))
    }

    @Test fun offlineLevelCapCanReduceTheCreditedBonusToZero() {
        val level = engine.rules.jackpotCapFrom
        val base = engine.newGame(now, 11).copy(level = level, runEarned = engine.rules.levelP(level),
            earnRate = BigInteger.TEN.pow(400))
        val result = engine.settleOfflineWithBonus(Shop(engine).buyPass(base, now), 60_000, now)
        assertEquals(1_700, result.bonus.multiplierPermille)
        assertTrue(result.paid.signum() > 0)
        assertEquals(result.basePaid, result.paid)
    }

    @Test fun subscriptionsDeliverDailyEntitlementsInTheAppSession() {
        for ((kind, extra) in listOf("month" to 0L, "Turbo" to 0L, "Ultra" to 200L)) {
            val w = session()
            w.buyPass(kind, "order", now)
            w.buyPass(kind, "order", now)
            assertEquals(360L + extra, w.state.account.credits)
            assertEquals(now + 3 * 3_600_000L, w.state.account.overclockUntil)
            val tomorrow = now + day
            val restored = WebSession(engine, SaveCodec.decode(w.save()), tomorrow)
            restored.snapshot(tomorrow)
            restored.bm(tomorrow)
            assertEquals(420L + 2 * extra, restored.state.account.credits)
            restored.panel(now + 30 * day)
            assertEquals(420L + 2 * extra, restored.state.account.credits)
        }
    }

    @Test fun oldFormatStillLoadsWithDefaultEconomyFields() {
        val original = SaveCodec.encode(engine.newGame(now, 11))
        val old = original.replace("\"save\":1,", "")
        val state = SaveCodec.decode(old)
        assertEquals(Account(), state.account)
        assertNotNull(state.rows.firstOrNull())
        assertFalse(state.account.installAt >= 0)
    }
}
