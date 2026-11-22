package com.graviton94.notificationcleaner.core

import java.math.BigInteger
import java.math.BigInteger.ZERO

enum class RowKind { App, Spam, Call }

/** One notification row on the board. Board counts stay small; value multipliers make P large. */
data class Row(
    val id: Long,
    val kind: RowKind,
    val app: String? = null,
    val count: Int = 0,
    val threshold: Int = 0,
    /** Spam only: swipe number at which it copies itself if still on the board. */
    val copyAt: Int = 0,
)

data class GameState(
    val rulesVersion: Int,
    /** Top to bottom. */
    val rows: List<Row>,
    /** Owned apps → upgrade level (1 = just bought). */
    val appLevels: Map<String, Int>,
    /** Owned apps currently allowed to send notifications, at most RuleSet.activeSlots. */
    val active: List<String>,
    val overloadDepth: Int,
    val gauge: Int = 0,
    val energy: Int,
    val nextChargeAt: Long = 0,
    val wallet: BigInteger = ZERO,
    val bestSwipe: BigInteger = ZERO,
    val bestChain: Int = 0,
    val level: Int = 1,
    val xp: Long = 0,
    val swipes: Int = 0,
    val autoPausedUntil: Long = 0,
    val rng: Long,
    val nextId: Long,
)

/** What happened, in order. The UI replays these; the state is already final. */
sealed interface Beat {
    data class Swiped(val row: Row, val earned: BigInteger) : Beat
    data class Merged(val into: Row, val fromId: Long) : Beat
    data class Burst(val row: Row, val chain: Int, val earned: BigInteger) : Beat
    data class Wave(val index: Int, val chain: Int, val cleared: BigInteger, val earned: BigInteger) : Beat
    data class Spawned(val row: Row) : Beat
    data class LevelUp(val level: Int) : Beat
}

data class SwipeOutcome(
    val state: GameState,
    val beats: List<Beat>,
    val earned: BigInteger,
    /** Bursts + overload waves. */
    val chain: Int,
    val cleared: BigInteger,
    val overload: Boolean,
)

enum class Actor { Player, Auto }

/** SplitMix64. The whole state of the generator is one Long kept in GameState. */
internal class Rng(var seed: Long) {
    fun next(bound: Int): Int {
        seed += -0x61c8864680b583ebL
        var z = seed
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return ((z ushr 1) % bound).toInt()
    }

    fun weighted(weights: List<Int>): Int {
        var r = next(weights.sum())
        weights.forEachIndexed { i, w -> if (r < w) return i; r -= w }
        return weights.lastIndex
    }
}

class Engine(val rules: RuleSet = RuleSet()) {

    /**
     * First board. Fixed so the first ten seconds always teach the same thing: the middle row
     * (문앞왔) sits between two 메일소포 rows of 2; removing it makes them touch, reach 4 and burst.
     */
    fun newGame(now: Long, seed: Long): GameState {
        val layout = listOf("chat" to 1, "delivery" to 2, "mail" to 2, "delivery" to 1, "mail" to 2, "chat" to 1, "delivery" to 2)
        val rows = layout.mapIndexed { i, (app, n) -> Row(i + 1L, RowKind.App, app, n, rules.threshold) }
        return GameState(
            rulesVersion = rules.version, rows = rows,
            appLevels = rules.starter.associateWith { 1 }, active = rules.starter,
            overloadDepth = rules.startOverloadDepth, energy = rules.energyCap,
            rng = seed, nextId = rows.size + 1L,
        )
    }

    fun value(state: GameState, appId: String): BigInteger =
        rules.notificationValue(rules.app(appId), state.appLevels.getValue(appId))

    /** Battery recharge up to [now]. */
    fun tick(state: GameState, now: Long): GameState {
        if (state.energy >= rules.energyCap || state.nextChargeAt == 0L || now < state.nextChargeAt) return state
        val steps = ((now - state.nextChargeAt) / rules.chargeMs + 1).coerceAtMost((rules.energyCap - state.energy).toLong()).toInt()
        val energy = state.energy + steps
        return state.copy(energy = energy, nextChargeAt = if (energy >= rules.energyCap) 0 else state.nextChargeAt + steps * rules.chargeMs)
    }

    fun swipe(input: GameState, rowId: Long, now: Long, actor: Actor = Actor.Player): SwipeOutcome? {
        val state = tick(input, now)
        if (state.energy <= 0) return null
        if (actor == Actor.Auto && now < state.autoPausedUntil) return null
        val index = state.rows.indexOfFirst { it.id == rowId }
        if (index < 0) return null

        val rows = state.rows.toMutableList()
        val beats = mutableListOf<Beat>()
        val removed = rows.removeAt(index)
        var earned = ZERO
        var cleared = ZERO
        var chain = 0
        var gauge = state.gauge
        var pausedUntil = state.autoPausedUntil

        when (removed.kind) {
            RowKind.App -> {
                val e = BigInteger.valueOf(removed.count.toLong()) * value(state, removed.app!!)
                earned += e
                cleared += BigInteger.valueOf(removed.count.toLong())
                beats += Beat.Swiped(removed, e)
            }
            RowKind.Call -> { pausedUntil = 0; beats += Beat.Swiped(removed, ZERO) }
            RowKind.Spam -> beats += Beat.Swiped(removed, ZERO)
        }

        // Collapse: the two rows that now touch merge if they are the same app; a full merge bursts
        // and the next pair is checked, so one swipe can unzip the board outward.
        val bursts = mutableListOf<Row>()
        var pivot = index
        while (pivot in 1 until rows.size) {
            val a = rows[pivot - 1]
            val b = rows[pivot]
            if (a.kind != RowKind.App || b.kind != RowKind.App || a.app != b.app) break
            val merged = a.copy(count = a.count + b.count, threshold = minOf(a.threshold, b.threshold))
            rows.removeAt(pivot)
            rows[pivot - 1] = merged
            gauge += rules.gaugePerMerge
            beats += Beat.Merged(merged, b.id)
            if (merged.count < merged.threshold) break
            rows.removeAt(--pivot)
            chain++
            gauge += rules.gaugePerBurst
            val count = BigInteger.valueOf(merged.count.toLong())
            val e = count * value(state, merged.app!!) * BigInteger.valueOf(1L + chain)
            earned += e
            cleared += count
            bursts += merged
            beats += Beat.Burst(merged, chain, e)
        }

        // Overload: a full gauge turns the bursts into waves through the hidden backlog.
        val overload = bursts.isNotEmpty() && gauge >= 100
        if (overload) {
            val factor = BigInteger.valueOf(rules.waveFactor.toLong())
            var backlog = bursts.map { BigInteger.valueOf(it.count.toLong()) }
            repeat(state.overloadDepth) { wave ->
                backlog = backlog.map { it * factor }
                chain++
                var waveEarned = ZERO
                var waveCleared = ZERO
                bursts.forEachIndexed { i, row ->
                    waveCleared += backlog[i]
                    waveEarned += backlog[i] * value(state, row.app!!) * BigInteger.valueOf(1L + chain)
                }
                earned += waveEarned
                cleared += waveCleared
                beats += Beat.Wave(wave + 1, chain, waveCleared, waveEarned)
            }
            gauge = 0
        }
        gauge = gauge.coerceAtMost(100)

        // Progress
        var level = state.level
        var xp = state.xp + 1 + bursts.size + if (overload) 2L * state.overloadDepth else 0L
        while (xp >= rules.xpGoal(level)) {
            xp -= rules.xpGoal(level)
            level++
            beats += Beat.LevelUp(level)
        }

        val swipes = state.swipes + 1
        var next = state.copy(
            rows = rows,
            gauge = gauge,
            energy = state.energy - 1,
            nextChargeAt = if (state.energy >= rules.energyCap) now + rules.chargeMs else state.nextChargeAt,
            wallet = state.wallet + earned,
            bestSwipe = state.bestSwipe.max(earned),
            bestChain = maxOf(state.bestChain, chain),
            level = level,
            xp = xp,
            swipes = swipes,
            autoPausedUntil = pausedUntil,
        )
        next = refill(next, now, beats)
        return SwipeOutcome(next, beats, earned, chain, cleared, overload)
    }

    /** New rows arrive at the top until the board is full again. */
    private fun refill(state: GameState, now: Long, beats: MutableList<Beat>): GameState {
        val rng = Rng(state.rng)
        val rows = state.rows.toMutableList()
        var id = state.nextId
        var pausedUntil = state.autoPausedUntil
        val s = state.swipes

        // Spam left on the board copies itself; the copy takes the next arriving slot.
        val overdue = rows.indexOfFirst { it.kind == RowKind.Spam && it.copyAt in 1..s }
        if (overdue >= 0) rows[overdue] = rows[overdue].copy(copyAt = 0)
        var spamToPlace = overdue >= 0 || (s % rules.spamEverySwipes == 0)
        var callToPlace = s % rules.callEverySwipes == 0 && rows.none { it.kind == RowKind.Call }

        while (rows.size < rules.rowCount) {
            val row = when {
                callToPlace -> {
                    callToPlace = false
                    pausedUntil = now + rules.callPauseMs
                    Row(id++, RowKind.Call)
                }
                spamToPlace -> {
                    spamToPlace = false
                    Row(id++, RowKind.Spam, copyAt = s + rules.spamCopyAfterSwipes)
                }
                else -> spawnApp(rows, rng, id++, state.active)
            }
            rows.add(0, row)
            beats += Beat.Spawned(row)
        }
        return state.copy(rows = rows, rng = rng.seed, nextId = id, autoPausedUntil = pausedUntil)
    }

    private fun spawnApp(rows: List<Row>, rng: Rng, id: Long, active: List<String>): Row {
        val top = rows.firstOrNull()
        val second = rows.getOrNull(1)
        val options = active.filter { it != top?.app }
        val sandwich = second?.kind == RowKind.App && second.app != top?.app && second.app in options
        val app = if (sandwich && rng.next(100) < rules.sandwichPercent) second!!.app!! else options[rng.next(options.size)]
        val count = rng.weighted(rules.countWeights) + 1
        return Row(id, RowKind.App, app, count, rules.threshold)
    }

    /** Rows whose removal makes two same-app rows touch. */
    fun mergeTargets(state: GameState): List<Row> = state.rows.indices.filter { i ->
        val a = state.rows.getOrNull(i - 1)
        val b = state.rows.getOrNull(i + 1)
        a != null && b != null && a.kind == RowKind.App && b.kind == RowKind.App && a.app == b.app
    }.map { state.rows[it] }

    /**
     * Auto-clean is deliberately simple: the lowest row that makes a merge, else spam, else the
     * bottom row. It never looks at chain length or the gauge, so a player can beat it.
     */
    fun autoTarget(state: GameState): Long? =
        mergeTargets(state).lastOrNull()?.id
            ?: state.rows.lastOrNull { it.kind == RowKind.Spam }?.id
            ?: state.rows.lastOrNull()?.id

    /**
     * Reference "good player" for simulators: tries every row and keeps the best immediate result
     * (chain, overload, merges, then clearing spam). It never spends a big row for its own P. Ties go to the lower row, which keeps the board moving.
     */
    fun bestTarget(state: GameState, now: Long): Long? {
        var best: Long? = null
        var bestKey = listOf(-1L, -1L, -1L, -1L)
        state.rows.forEach { row ->
            val o = swipe(state, row.id, now) ?: return@forEach
            val key = listOf(o.chain.toLong(), if (o.overload) 1L else 0L, o.beats.count { it is Beat.Merged }.toLong(), if (row.kind == RowKind.Spam) 1L else 0L)
            val cmp = key.indices.firstNotNullOfOrNull { i -> key[i].compareTo(bestKey[i]).takeIf { it != 0 } } ?: 0
            if (cmp >= 0) { best = row.id; bestKey = key }
        }
        return best
    }

    // Shop -----------------------------------------------------------------------------------

    fun canBuy(state: GameState, appId: String): Boolean {
        val app = rules.app(appId)
        return appId !in state.appLevels && state.level >= app.unlockLevel && state.wallet >= rules.appPrice(app)
    }

    /** Buying an app also lets it notify, replacing the lowest-tier active app when slots are full. */
    fun buy(state: GameState, appId: String): GameState {
        if (!canBuy(state, appId)) return state
        val app = rules.app(appId)
        val active = if (state.active.size < rules.activeSlots) state.active + appId
        else state.active.minus(state.active.minBy { rules.app(it).tier }) + appId
        return state.copy(wallet = state.wallet - rules.appPrice(app), appLevels = state.appLevels + (appId to 1), active = active)
    }

    fun upgrade(state: GameState, appId: String): GameState {
        val level = state.appLevels[appId] ?: return state
        val price = rules.upgradePrice(rules.app(appId), level)
        if (state.wallet < price) return state
        return state.copy(wallet = state.wallet - price, appLevels = state.appLevels + (appId to level + 1))
    }

    /** Toggle which owned apps may notify. Takes effect on the next arriving row. */
    fun setActive(state: GameState, active: List<String>): GameState {
        if (active.isEmpty() || active.size > rules.activeSlots || active.size < 2 || !state.appLevels.keys.containsAll(active)) return state
        return state.copy(active = active.distinct())
    }
}
