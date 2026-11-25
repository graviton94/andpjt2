package com.graviton94.notificationcleaner.core

import kotlinx.serialization.json.Json
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
            appLevels = Catalog.apps.associate { it.id to 50 })
        val w = WebSession(engine, mature, now)
        val purchases = (0..360 step 30).associateWith { day ->
            "GPA.$day-" + "store-purchase-token".repeat(6)
        }
        var views = 0
        for (d in 0..364) {
            val at = now + d * day
            purchases[d]?.let { w.buyData("data_100", it, at) }
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

    @Test fun watchedAdsCanIncreaseOfflinePThroughTheFollowingUtcDay() {
        val tuned = engine
        val reference = Engine(engine.rules.copy(adWatchOfflinePermille = 1_000))
        val shop = Shop(tuned)
        val away = 60_000L
        val s = tuned.newGame(now, 11).copy(earnRate = java.math.BigInteger.valueOf(10_000))
        val watched = assertNotNull(shop.watchAd(s, AdSlot.CreditBox, now))
        val claimedWithPass = assertNotNull(shop.watchAd(shop.buyPass(s, now), AdSlot.CreditBox, now))
        val base = reference.settleOffline(watched, away, now + day).second
        assertEquals(base * java.math.BigInteger.valueOf(17) / java.math.BigInteger.TEN,
            tuned.settleOffline(watched, away, now + day).second)
        for (state in listOf(s, claimedWithPass)) {
            assertEquals(reference.settleOffline(state, away, now + day), tuned.settleOffline(state, away, now + day))
        }
        assertEquals(reference.settleOffline(watched, away, now + 2 * day),
            tuned.settleOffline(watched, away, now + 2 * day))
    }

    @Test fun ultraOfflineCalibrationLeavesOtherEntitlementsAlone() {
        val reference = Engine(engine.rules.copy(ultraOfflinePermille = 1_000))
        val away = 60_000L
        val s = engine.newGame(now, 11).copy(earnRate = java.math.BigInteger.valueOf(10_000))
        val active = s.withAccount { it.copy(speedPass = SpeedPass.Ultra, speedPassUntil = now + day) }
        val before = reference.settleOffline(active, away, now).second
        assertEquals(before * java.math.BigInteger.valueOf(3) / java.math.BigInteger.TWO,
            engine.settleOffline(active, away, now).second)
        val turbo = active.withAccount { it.copy(speedPass = SpeedPass.Turbo) }
        val expired = active.withAccount { it.copy(speedPassUntil = now - 1) }
        for (state in listOf(s, turbo, expired)) {
            assertEquals(reference.settleOffline(state, away, now), engine.settleOffline(state, away, now))
        }
        assertEquals(reference.chargeMs(active, now), engine.chargeMs(active, now))
        assertEquals(reference.prestigePermille(active, now), engine.prestigePermille(active, now))
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
