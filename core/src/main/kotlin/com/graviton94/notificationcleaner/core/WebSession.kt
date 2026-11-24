package com.graviton94.notificationcleaner.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.math.BigInteger

/**
 * The game screen is a web page (the approved FX demo, 22 §0) driven by this session: every call runs the core engine
 * and answers JSON the page can replay. The Android bridge and the desktop dev server both sit on top of it, so the
 * page behaves the same in a browser as on the phone. Persistence and clocks are the host's job.
 */
class WebSession(val engine: Engine = Engine(), state: GameState? = null, now: Long = System.currentTimeMillis()) {
    private val meta = Meta(engine)
    val shop = Shop(engine)
    var state: GameState = state ?: engine.newGame(now, now)
        private set

    /** Board, HUD and shop data. */
    fun snapshot(now: Long): String { state = engine.tick(state, now); return snap(state, now).toString() }

    /** A player swipe (or the auto-cleaner's). Null outcome = refused (no battery, row gone). */
    fun swipe(rowId: Long, now: Long, auto: Boolean = false): String {
        val o = engine.swipe(state, rowId, now, if (auto) Actor.Auto else Actor.Player)
            ?: return buildJsonObject { put("ok", false); put("reason", if (engine.tick(state, now).energy <= 0) "battery" else "gone"); put("state", snap(engine.tick(state, now), now)) }.toString()
        state = meta.record(o.state, o, now)
        return outcome(o, now).toString()
    }

    /** The auto-cleaner's move if it is due, else just the refreshed state. */
    fun auto(now: Long): String {
        state = engine.tick(state, now)
        if (now < state.autoPausedUntil) return buildJsonObject { put("ok", false); put("state", snap(state, now)) }.toString()
        val target = engine.autoTarget(state, now) ?: return buildJsonObject { put("ok", false); put("state", snap(state, now)) }.toString()
        return swipe(target, now, auto = true)
    }

    /** The row the auto-cleaner would pick: the tutorial glove points at it. */
    fun hint(now: Long): String = buildJsonObject { put("id", engine.autoTarget(engine.tick(state, now), now)) }.toString()

    fun autoIntervalMs(now: Long): Long = engine.autoIntervalMs(state, now)

    /** Away time → offline pay (the host measures the time with [AwayClock]). */
    fun offline(awayMs: Long, now: Long): String {
        val (next, paid) = engine.settleOffline(state, awayMs, now)
        state = shop.claimAttendance(next, now)
        return buildJsonObject { put("paid", paid.toString()); put("paidShort", short(paid)); put("state", snap(state, now)) }.toString()
    }

    fun buy(appId: String, now: Long): String = act(now) { engine.buy(it, appId) }
    fun upgrade(appId: String, now: Long): String = act(now) { s -> engine.upgrade(s, appId).let { n -> if (n !== s) meta.upgraded(n, 1, now) else n } }
    fun reset(now: Long): String = act(now) { meta.reset(it, now) ?: it }

    fun save(): String = SaveCodec.encode(state)

    private fun act(now: Long, f: (GameState) -> GameState): String {
        val before = state
        state = f(engine.tick(state, now))
        return buildJsonObject { put("ok", state !== before); put("state", snap(state, now)) }.toString()
    }

    // ---- JSON ------------------------------------------------------------------------------------------------

    private fun big(n: BigInteger): JsonElement = JsonPrimitive(n.toString())

    fun row(r: Row): JsonObject = buildJsonObject {
        put("id", r.id)
        put("kind", r.kind.name)
        put("count", r.count)
        put("threshold", r.threshold)
        put("merges", r.merges)
        r.app?.let { id ->
            val a = Catalog.app(id)
            put("app", id); put("name", a.name); put("cat", a.category.name); put("tier", a.tier)
        }
        r.event?.let { put("event", it.name); put("label", it.label) }
        r.alert?.let { put("alert", it.name); put("label", it.label) }
    }

    private fun snap(s: GameState, now: Long): JsonObject = buildJsonObject {
        put("wallet", big(s.wallet))
        put("level", s.level)
        put("gauge", s.gauge)
        put("energy", s.energy)
        put("energyCap", engine.energyCap(s))
        put("nextChargeAt", s.nextChargeAt)
        put("now", now)
        put("autoPausedUntil", s.autoPausedUntil)
        put("autoIntervalMs", engine.autoIntervalMs(s, now))
        put("swipes", s.swipes)
        put("bestSwipe", big(s.bestSwipe))
        put("deck", s.active.size)
        put("slots", engine.rules.maxSlots(s.level, s.backup))
        put("resetLevel", engine.rules.resetLevel)
        put("backupFilesNow", engine.backupFilesNow(s))
        put("files", s.backup.files)
        putJsonArray("rows") { s.rows.forEach { add(row(it)) } }
        putJsonArray("deckApps") {
            s.active.forEach { id ->
                add(buildJsonObject {
                    val a = Catalog.app(id)
                    put("id", id); put("name", a.name); put("tier", a.tier); put("level", s.appLevels[id] ?: 1)
                    put("upgradePrice", big(engine.upgradePrice(s, id)))
                })
            }
        }
        putJsonArray("shopApps") {
            Catalog.apps.filter { it.id !in s.appLevels && engine.unlocked(s, it.id) }
                .sortedBy { engine.appPrice(s, it.id) }.take(6).forEach { a ->
                    add(buildJsonObject { put("id", a.id); put("name", a.name); put("tier", a.tier); put("price", big(engine.appPrice(s, a.id))) })
                }
        }
    }

    private fun outcome(o: SwipeOutcome, now: Long): JsonObject = buildJsonObject {
        put("ok", true)
        put("earned", big(o.earned))
        put("chain", o.chain)
        put("overload", o.overload)
        put("capped", big(o.capped))
        put("beats", JsonArray(o.beats.map { beat(it) }))
        put("state", snap(state, now))
    }

    private fun beat(b: Beat): JsonObject = when (b) {
        is Beat.Swiped -> buildJsonObject { put("t", "swiped"); put("id", b.row.id); put("earned", big(b.earned)) }
        is Beat.Merged -> buildJsonObject { put("t", "merged"); put("into", row(b.into)); put("from", b.fromId) }
        is Beat.Burst -> buildJsonObject {
            put("t", "burst"); put("row", row(b.row)); put("chain", b.chain); put("earned", big(b.earned))
            // chips × mult: the engine pays count × value × (1 + chain); the plate shows the two factors.
            put("chips", big(b.earned / BigInteger.valueOf(1L + b.chain)))
        }
        is Beat.Wave -> buildJsonObject { put("t", "wave"); put("index", b.index); put("chain", b.chain); put("earned", big(b.earned)) }
        is Beat.Spawned -> buildJsonObject { put("t", "spawned"); put("row", row(b.row)) }
        is Beat.FreeUpgrade -> buildJsonObject { put("t", "freeUpgrade"); put("app", b.app); put("name", Catalog.app(b.app).name); put("level", b.level) }
        is Beat.LevelUp -> buildJsonObject { put("t", "levelUp"); put("level", b.level) }
        is Beat.Capped -> buildJsonObject { put("t", "capped"); put("lost", big(b.lost)) }
    }
}

