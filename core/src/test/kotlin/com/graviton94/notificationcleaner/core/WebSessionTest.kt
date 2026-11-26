package com.graviton94.notificationcleaner.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSessionTest {
    private val t0 = 1_700_000_000_000L
    private fun parse(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun snapshotHasTheBoardAndHud() {
        val w = WebSession(state = Engine().newGame(t0, 11), now = t0)
        val s = parse(w.snapshot(t0))
        assertEquals(7, s.getValue("rows").jsonArray.size)
        assertEquals("0", s.getValue("wallet").jsonPrimitive.content)
        assertTrue(s.getValue("rows").jsonArray.all { it.jsonObject.getValue("threshold").jsonPrimitive.content == "2" })
        assertTrue(s.getValue("deckApps").jsonArray.isNotEmpty())
    }

    @Test fun hintedSwipeReplaysAsBeatsAndRefillsTheBoard() {
        val w = WebSession(state = Engine().newGame(t0, 11), now = t0)
        val id = parse(w.hint(t0)).getValue("id").jsonPrimitive.long
        val o = parse(w.swipe(id, t0 + 1_000))
        assertEquals("true", o.getValue("ok").jsonPrimitive.content)
        val kinds = o.getValue("beats").jsonArray.map { it.jsonObject.getValue("t").jsonPrimitive.content }
        assertEquals("swiped", kinds.first())
        assertTrue("spawned" in kinds)
        assertEquals(7, o.getValue("state").jsonObject.getValue("rows").jsonArray.size)
        // the save round-trips the session
        assertEquals(w.state, SaveCodec.decode(w.save()))
    }

    @Test fun burstAndWaveExposeActualPresentationMetadata() {
        val e = Engine()
        val s = e.newGame(t0, 11).copy(
            rows = listOf(
                Row(1, RowKind.App, "green", count = 1), Row(2, RowKind.App, "memo", count = 1),
                Row(3, RowKind.App, "green", count = 1), Row(4, RowKind.App, "weather", count = 1),
                Row(5, RowKind.App, "alarm", count = 1),
            ),
            active = listOf("green", "memo", "weather", "alarm", "maps"),
            appLevels = listOf("green", "memo", "weather", "alarm", "maps").associateWith { 1 },
            gauge = 100,
        )
        val w = WebSession(e, s, t0)
        val bits = parse(w.swipe(2, t0)).getValue("beats").jsonArray.map { it.jsonObject }
        val burst = bits.first { it["t"]?.jsonPrimitive?.content == "burst" }
        val wave = bits.first { it["t"]?.jsonPrimitive?.content == "wave" }
        assertEquals(Catalog.app("green").region.id, burst.getValue("row").jsonObject.getValue("region").jsonPrimitive.content)
        assertEquals(Catalog.app("green").tier.toString(), burst.getValue("tierMax").jsonPrimitive.content)
        assertTrue(burst.getValue("effect").jsonArray.any { it.jsonPrimitive.content == "green" })
        assertEquals("region", burst.getValue("synergy").jsonObject.getValue("kind").jsonPrimitive.content)
        assertEquals(wave.getValue("index"), wave.getValue("waveIndex"))
    }

    @Test fun offlineDoubleCreditsOneQuotedSettlementAndNeverConsumesAnEmptyOffer() {
        val e = Engine()
        val w = WebSession(e, e.newGame(t0, 4).copy(earnRate = java.math.BigInteger.valueOf(50_000)), t0)
        assertEquals("false", parse(w.watchAd("OfflineDouble", "ad-empty", t0)).getValue("ok").jsonPrimitive.content)
        val away = 15 * 3_600_000L
        val result = parse(w.offline(away, t0))
        val paid = result.getValue("paid").jsonPrimitive.content.toBigInteger()
        val cap = result.getValue("offlineCapMs").jsonPrimitive.long
        assertTrue(paid.signum() > 0)
        assertEquals(away, result.getValue("awayMs").jsonPrimitive.long)
        assertTrue(cap < away)
        assertEquals(result.getValue("offlineCapMs"), result.getValue("baseOfflineCapMs"))
        val before = w.state.wallet
        val room = e.jackpotRoom(w.state, e.rules.adLevelCap)
        assertEquals("true", parse(w.watchAd("OfflineDouble", "ad-paid", t0)).getValue("ok").jsonPrimitive.content)
        assertEquals(paid.min(room), w.state.wallet - before)
        assertEquals("false", parse(w.watchAd("OfflineDouble", "ad-paid", t0)).getValue("ok").jsonPrimitive.content)
        assertEquals("false", parse(w.watchAd("OfflineDouble", "ad-other", t0)).getValue("ok").jsonPrimitive.content)
    }

    @Test fun refusedSwipeSaysWhy() {
        val w = WebSession(state = Engine().newGame(t0, 11), now = t0)
        val o = parse(w.swipe(9_999, t0))
        assertEquals("false", o.getValue("ok").jsonPrimitive.content)
        assertEquals("gone", o.getValue("reason").jsonPrimitive.content)
    }

    @Test fun dailyChallengeSwapsBoardsAndSavesTheMainGame() {
        val w = WebSession(state = Engine().newGame(t0, 11), now = t0)
        val mainSave = w.save()
        val m = parse(w.meta(t0))
        assertEquals(2, m.getValue("keys").jsonPrimitive.content.toInt())
        assertEquals(9, m.getValue("achievements").jsonArray.size)
        assertEquals("true", parse(w.challengeStart(t0)).getValue("ok").jsonPrimitive.content)
        assertEquals("active", parse(w.challengeStart(t0)).getValue("reason").jsonPrimitive.content)
        assertEquals(40, w.state.level)                     // the shared challenge board
        assertEquals(1, SaveCodec.decode(w.save()).level)   // a save mid-run still writes the main game
        val id = parse(w.snapshot(t0 + 1000)).getValue("rows").jsonArray.first().jsonObject.getValue("id").jsonPrimitive.long
        w.swipe(id, t0 + 1000)
        val e = parse(w.challengeEnd(t0 + 60_000))
        assertEquals("true", e.getValue("ok").jsonPrimitive.content)
        assertEquals(1, w.state.level)
        assertTrue(w.state.account.pieces >= 1)
        assertEquals(1, parse(w.meta(t0 + 60_000)).getValue("keys").jsonPrimitive.content.toInt())
        assertTrue(mainSave.isNotEmpty())
    }
}
