package com.graviton94.notificationcleaner.core

import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StableBoardTest {
    private val engine = Engine()
    private val start = 1_000_000L

    private fun app(id: Long, app: String) = Row(id, RowKind.App, app, count = 2, threshold = 2)

    /** Independent check of the three pair rules, including the deck-gated cross-region maps effect. */
    private fun stable(s: GameState, input: String) {
        for ((a, b) in s.rows.zipWithNext()) {
            if (!a.merges || !b.merges) continue
            val maps = "maps" in s.active && (a.app == "maps" || b.app == "maps")
            val matches = a.kind == RowKind.Wild || b.kind == RowKind.Wild || a.app == b.app ||
                (maps && Catalog.app(a.app!!).region != Catalog.app(b.app!!).region)
            assertTrue(!matches, "$input left ${a.id}:${a.kind}:${a.app} beside ${b.id}:${b.kind}:${b.app}")
        }
    }

    @Test
    fun expiryAndFinalPatternCannotLeavePairsAfterTheWave() {
        val e = Engine(RuleSet(fallMatchPermille = 1_000))
        val base = e.newGame(start, 11)
        val rows = listOf(app(10, "memo"), Row(11, RowKind.Event, event = EventCard.Otp, expireAt = 1),
            app(12, "memo"), app(13, "alarm"), app(14, "weather"), app(15, "alarm"), app(16, "weather"))
        val s = base.copy(rows = rows, gauge = 100, pattern = listOf("weather", "weather", "weather"), nextId = 17)
        val result = assertNotNull(e.swipe(s, 14, start))
        val end = result.beats.filterIsInstance<Beat.ChainEnd>().single()
        assertEquals(ChainEndReason.Wave, end.reason)
        assertEquals(1, end.bursts)
        assertTrue(result.beats.indexOfLast { it is Beat.Wave } < result.beats.indexOf(end))
        assertTrue(result.beats.drop(result.beats.indexOf(end)).none { it is Beat.Burst || it is Beat.Wave })
        stable(result.state, "wave/OTP/pattern")
    }

    @Test
    fun oldSaveSettlesWithoutCreditAndEnablingMapsCannotLeavePairs() {
        val base = engine.newGame(start, 11)
        val stopped = base.copy(rulesVersion = 4, energy = 8, gauge = 64, wallet = BigInteger.TEN,
            nextId = 21, nextChargeAt = start + 100_000,
            rows = listOf(app(18, "alarm"), app(19, "alarm"), Row(20, RowKind.Wild, count = 2, threshold = 2),
                app(17, "weather"), Row(16, RowKind.Alert, alert = Alert.Storage)))
        val saved = SaveCodec.decode(SaveCodec.encode(stopped))
        val session = WebSession(engine, saved, start)
        val loaded = session.state
        stable(loaded, "loaded 0.6.0 board")
        assertEquals(stopped.wallet, loaded.wallet)
        assertEquals(stopped.runEarned, loaded.runEarned)
        assertEquals(stopped.energy, loaded.energy)
        assertEquals(stopped.gauge, loaded.gauge)
        assertEquals(stopped.swipes, loaded.swipes)
        assertEquals(stopped.rng, loaded.rng)
        assertEquals(stopped.rows.last(), loaded.rows.last())
        assertEquals(loaded, engine.tick(loaded, start))

        val ownsMaps = base.copy(appLevels = base.appLevels + ("maps" to 1),
            rows = listOf(app(1, "maps"), app(2, "memo"), app(3, "alarm")))
        stable(ownsMaps, "maps outside deck")
        stable(assertNotNull(engine.setDeck(ownsMaps.copy(level = 8), listOf("alarm", "weather", "memo", "maps"))),
            "maps equipped")
    }

    @Test
    fun mixedActionsNeverReturnAnUnresolvedPair() {
        var manual = 0
        var auto = 0
        var offline = 0
        var reload = 0
        var eventRowsSwiped = 0
        var alertRowsSwiped = 0
        var wildSeen = 0
        val endings = mutableSetOf<ChainEndReason>()
        val cards = listOf(EventCard.Update, EventCard.Birthday, EventCard.AllHands, EventCard.Otp, EventCard.Drill)
        val decks = listOf(Catalog.starter, Catalog.starter + listOf("maps", "mega", "mail", "match"))
        for (seed in 11L..22) {
            val random = Random(seed)
            val deck = decks[if (seed < 17) 0 else 1]
            val level = if (seed < 17) 7 else 95
            var s = engine.newGame(start, seed).copy(level = level, active = deck,
                appLevels = deck.associateWith { 1 }, runEarned = engine.rules.levelP(level),
                events = cards.toSet(), equipped = if (level == 7) cards.take(2) else cards,
                assists = if (level == 7) emptyMap() else mapOf(Assist.Wild to 8, Assist.Board to 3))
            var now = start
            repeat(1_000) { step ->
                now += 3_000
                when (random.nextInt(10)) {
                    in 0..6 -> {
                        val isAuto = step % 3 == 0
                        val actor = if (isAuto) Actor.Auto else Actor.Player
                        s = engine.tick(s, now)
                        val id = if (isAuto) engine.autoTarget(s, now) else s.rows.random(random).id
                        if (isAuto) auto++ else manual++
                        val result = id?.let { engine.swipe(s, it, now, actor) }
                        if (result != null) {
                            val swiped = result.beats.filterIsInstance<Beat.Swiped>().single().row
                            if (swiped.kind == RowKind.Event) eventRowsSwiped++
                            if (swiped.kind == RowKind.Alert) alertRowsSwiped++
                            wildSeen += result.beats.filterIsInstance<Beat.Spawn>().count { it.row.kind == RowKind.Wild }
                            result.beats.filterIsInstance<Beat.ChainEnd>().forEach { endings.add(it.reason) }
                            s = result.state
                        }
                    }
                    in 7..8 -> {
                        val away = random.nextLong(60_000, 15 * 3_600_000L)
                        now += away
                        offline++
                        s = engine.settleOffline(s, away, now).first
                    }
                    else -> {
                        reload++
                        s = WebSession(engine, SaveCodec.decode(SaveCodec.encode(s)), now).state
                    }
                }
                stable(s, "seed $seed input ${step + 1}")
                assertEquals(s.rows.size, s.rows.distinctBy(Row::id).size)
            }
        }
        println("stable-board mix: manual=$manual auto=$auto offline=$offline reload=$reload events=$eventRowsSwiped alerts=$alertRowsSwiped fallingWild=$wildSeen endings=$endings")
        assertEquals(12_000, manual + auto + offline + reload)
        assertTrue(listOf(manual, auto, offline, reload, eventRowsSwiped, alertRowsSwiped, wildSeen).all { it > 0 })
        assertTrue(endings.containsAll(listOf(ChainEndReason.Natural, ChainEndReason.Spam, ChainEndReason.Wave)))
    }
}
