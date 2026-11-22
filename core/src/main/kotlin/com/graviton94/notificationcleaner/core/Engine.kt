package com.graviton94.notificationcleaner.core

import java.math.BigInteger
import java.math.BigInteger.ZERO

enum class RowKind { App, Wild, Spam, Call, Event }

/** One notification row on the board. Board counts stay small; value multipliers make P large. */
data class Row(
    val id: Long,
    val kind: RowKind,
    val app: String? = null,
    val event: EventCard? = null,
    val count: Int = 0,
    val threshold: Int = 0,
    /** Spam: swipe number at which it copies itself if still on the board. */
    val copyAt: Int = 0,
    /** Event rows: swipe number at which they leave the board (0 = stays). */
    val expireAt: Int = 0,
) {
    val merges: Boolean get() = kind == RowKind.App || kind == RowKind.Wild
}

/** What survives a factory reset: backup files and what they bought. */
data class Backup(
    val files: Long = 0,
    val items: Map<BackupItem, Int> = emptyMap(),
    val resets: Int = 0,
) {
    fun lv(item: BackupItem): Int = items[item] ?: 0
}

data class GameState(
    val rulesVersion: Int,
    /** Top to bottom. */
    val rows: List<Row>,
    /** Owned apps → upgrade level. */
    val appLevels: Map<String, Int>,
    /** The app deck: owned apps allowed to notify. */
    val active: List<String>,
    val events: Set<EventCard> = emptySet(),
    /** The event deck: owned cards that appear on the board. */
    val equipped: List<EventCard> = emptyList(),
    val assists: Map<Assist, Int> = emptyMap(),
    val gauge: Int = 0,
    val energy: Int,
    val nextChargeAt: Long = 0,
    val wallet: BigInteger = ZERO,
    /** P earned in this run; converts into backup files on a factory reset. */
    val runEarned: BigInteger = ZERO,
    val lastEarned: BigInteger = ZERO,
    val bestSwipe: BigInteger = ZERO,
    val bestChain: Int = 0,
    val level: Int = 1,
    val xpCenti: Long = 0,
    val swipes: Int = 0,
    val autoPausedUntil: Long = 0,
    val regionCleared: Map<Region, BigInteger> = emptyMap(),
    val categoryCleared: Map<Category, BigInteger> = emptyMap(),
    /** Apps queued to arrive next, in order (from @모두 공지). */
    val pattern: List<String> = emptyList(),
    val rng: Long,
    val nextId: Long,
    val backup: Backup = Backup(),
) {
    fun assist(a: Assist): Int = assists[a] ?: 0
}

/** What happened, in order. The UI replays these; the state is already final. */
sealed interface Beat {
    data class Swiped(val row: Row, val earned: BigInteger) : Beat
    data class Merged(val into: Row, val fromId: Long) : Beat
    data class Burst(val row: Row, val chain: Int, val earned: BigInteger) : Beat
    data class Wave(val index: Int, val chain: Int, val cleared: BigInteger, val earned: BigInteger) : Beat
    data class Spawned(val row: Row) : Beat
    data class FreeUpgrade(val app: String, val level: Int) : Beat
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
    private val hundred = BigInteger.valueOf(100)

    // Derived values -------------------------------------------------------------------------

    fun energyCap(s: GameState) = rules.energyCap + 2 * s.backup.lv(BackupItem.Battery)
    fun chargeMs(s: GameState) = rules.chargeMs - 200L * s.backup.lv(BackupItem.Battery)
    fun autoIntervalMs(s: GameState) = rules.autoIntervalMs - 300L * s.backup.lv(BackupItem.AutoClean)
    fun overloadDepth(s: GameState) = 1 + s.assist(Assist.Overload) + s.backup.lv(BackupItem.DeepOverload)
    fun rowCount(s: GameState) = minOf(
        rules.maxRows,
        rules.baseRows + maxOf(0, s.active.size - 4) / rules.appsPerExtraRow + s.assist(Assist.Board) + s.backup.lv(BackupItem.Board),
    )
    fun sandwichPercent(s: GameState) = rules.sandwichPercent + rules.sandwichPerExtraApp * maxOf(0, s.active.size - 4) +
        rules.sandwichPerAssist * s.assist(Assist.Sandwich) + 2 * s.backup.lv(BackupItem.Sandwich)
    fun wildPercent(s: GameState) = rules.wildPerAssist * s.assist(Assist.Wild) + s.backup.lv(BackupItem.Wild)
    fun maxSlots(s: GameState) = rules.maxSlots(s.level, s.backup)
    fun minSlots(s: GameState) = minOf(rules.minSlots(s.level), s.appLevels.size)
    fun eventSlots(s: GameState) = rules.eventSlots(s.level, s.backup)
    fun bestTier(s: GameState) = s.active.maxOf { Catalog.app(it).tier }
    fun bestApp(s: GameState) = s.active.maxBy { Catalog.app(it).tier * 1_000 + (s.appLevels[it] ?: 1) }
    fun backupFilesNow(s: GameState) = rules.backupFiles(s.runEarned, s.level)

    /** The deck's synergy bonus % for an app's region and category (0 when alone). */
    fun synergy(s: GameState, appId: String): Pair<Int, Int> {
        val app = Catalog.app(appId)
        val r = s.active.count { Catalog.app(it).region == app.region }
        val c = s.active.count { Catalog.app(it).category == app.category }
        return rules.synergyPercent(r, s.backup) to rules.synergyPercent(c, s.backup)
    }

    private fun digits(n: BigInteger?) = if (n == null || n.signum() <= 0) 1 else n.toString().length

    /** P for [count] notifications of [appId] at chain number [chain]. */
    fun earn(s: GameState, appId: String, count: BigInteger, chain: Int): BigInteger {
        val app = Catalog.app(appId)
        val (num, den) = rules.baseValue(app, s.appLevels[appId] ?: 1, s.backup)
        val (rs, cs) = synergy(s, appId)
        val rd = rules.regionDigitPercent * (digits(s.regionCleared[app.region]) - 1)
        val cd = rules.categoryDigitPercent * (digits(s.categoryCleared[app.category]) - 1)
        val pct = BigInteger.valueOf((100L + rs) * (100L + cs) * (100L + rd) * (100L + cd))
        val chainNum = BigInteger.valueOf(10L + chain * (10L + s.backup.lv(BackupItem.ChainBoost)))
        return count * num * pct * chainNum / (den * hundred.pow(4) * BigInteger.TEN)
    }

    private fun valueApp(s: GameState, row: Row): String = if (row.kind == RowKind.Wild) bestApp(s) else row.app!!

    // Game lifecycle --------------------------------------------------------------------------

    /**
     * First board of a run. Fixed so the first ten seconds teach one thing: the middle row (문앞왔) sits
     * between two 메일소포 rows of 2; removing it makes them touch, reach 4 and burst.
     */
    fun newGame(now: Long, seed: Long, backup: Backup = Backup()): GameState {
        val layout = listOf("chat" to 1, "delivery" to 2, "mail" to 2, "delivery" to 1, "mail" to 2, "chat" to 1, "delivery" to 2)
        val rows = layout.mapIndexed { i, (app, n) -> Row(i + 1L, RowKind.App, app, count = n, threshold = rules.threshold) }
        val remembered = Catalog.apps.filter { it.id !in Catalog.starter }.take(backup.lv(BackupItem.RememberedApps)).map { it.id }
        val s = GameState(
            rulesVersion = rules.version, rows = rows,
            appLevels = (Catalog.starter + remembered).associateWith { 1 }, active = Catalog.starter,
            energy = rules.energyCap + 2 * backup.lv(BackupItem.Battery),
            wallet = BigInteger.valueOf(1_250L * backup.lv(BackupItem.StartFunds)),
            rng = seed, nextId = rows.size + 1L, backup = backup,
        )
        return s
    }

    /** Factory reset: converts this run into backup files. Null below the reset level. */
    fun factoryReset(s: GameState, now: Long): GameState? {
        if (s.level < rules.resetLevel) return null
        val files = backupFilesNow(s)
        return newGame(now, s.rng, s.backup.copy(files = s.backup.files + files, resets = s.backup.resets + 1))
    }

    fun buyBackup(s: GameState, item: BackupItem): GameState {
        val owned = s.backup.lv(item)
        val price = rules.backupPrice(item, owned)
        if (owned >= item.max || s.backup.files < price) return s
        return s.copy(backup = s.backup.copy(files = s.backup.files - price, items = s.backup.items + (item to owned + 1)))
    }

    /** Battery recharge up to [now]. */
    fun tick(s: GameState, now: Long): GameState {
        val cap = energyCap(s)
        if (s.energy >= cap || s.nextChargeAt == 0L || now < s.nextChargeAt) return s
        val ms = chargeMs(s)
        val steps = ((now - s.nextChargeAt) / ms + 1).coerceAtMost((cap - s.energy).toLong()).toInt()
        val energy = s.energy + steps
        return s.copy(energy = energy, nextChargeAt = if (energy >= cap) 0 else s.nextChargeAt + steps * ms)
    }

    // Swipe ----------------------------------------------------------------------------------

    private fun canMerge(a: Row, b: Row) = a.merges && b.merges && (a.app == b.app || a.kind == RowKind.Wild || b.kind == RowKind.Wild)

    fun swipe(input: GameState, rowId: Long, now: Long, actor: Actor = Actor.Player): SwipeOutcome? {
        val state = tick(input, now)
        if (state.energy <= 0) return null
        if (actor == Actor.Auto && now < state.autoPausedUntil) return null
        val index = state.rows.indexOfFirst { it.id == rowId }
        if (index < 0) return null

        val rng = Rng(state.rng)
        val rows = state.rows.toMutableList()
        val beats = mutableListOf<Beat>()
        val removed = rows.removeAt(index)
        var s = state
        var earned = ZERO
        var cleared = ZERO
        var chain = 0
        var gauge = state.gauge
        var pausedUntil = state.autoPausedUntil
        var pattern = state.pattern
        val regionAdd = mutableMapOf<Region, BigInteger>()
        val categoryAdd = mutableMapOf<Category, BigInteger>()

        fun track(row: Row, n: BigInteger) {
            if (row.kind != RowKind.App) return
            val app = Catalog.app(row.app!!)
            regionAdd.merge(app.region, n, BigInteger::add)
            categoryAdd.merge(app.category, n, BigInteger::add)
        }

        when (removed.kind) {
            RowKind.App, RowKind.Wild -> {
                val n = BigInteger.valueOf(removed.count.toLong())
                val e = earn(state, valueApp(state, removed), n, 0)
                earned += e
                cleared += n
                track(removed, n)
                beats += Beat.Swiped(removed, e)
            }
            RowKind.Call -> { pausedUntil = 0; beats += Beat.Swiped(removed, ZERO) }
            RowKind.Spam -> beats += Beat.Swiped(removed, ZERO)
            RowKind.Event -> {
                var e = ZERO
                when (removed.event!!) {
                    EventCard.Update -> {
                        val app = state.active[rng.next(state.active.size)]
                        val lv = (s.appLevels[app] ?: 1) + 1
                        s = s.copy(appLevels = s.appLevels + (app to lv))
                        beats += Beat.FreeUpgrade(app, lv)
                    }
                    EventCard.AllHands -> {
                        val a = state.active[rng.next(state.active.size)]
                        val others = state.active - a
                        val b = others[rng.next(others.size)]
                        pattern = listOf(a, b, a, b, a)
                    }
                    EventCard.Otp -> e = state.lastEarned * BigInteger.TEN
                }
                earned += e
                beats += Beat.Swiped(removed, e)
            }
        }

        // Collapse: the rows that now touch merge if they are the same app (or one is wild). A full merge
        // bursts and the next pair is checked, so one swipe can unzip the board outward.
        val bursts = mutableListOf<Row>()
        val bestTier = bestTier(s)
        var pivot = index
        while (pivot in 1 until rows.size) {
            val a = rows[pivot - 1]
            val b = rows[pivot]
            if (!canMerge(a, b)) break
            val wild = a.kind == RowKind.Wild && b.kind == RowKind.Wild
            val merged = a.copy(
                kind = if (wild) RowKind.Wild else RowKind.App,
                app = a.app ?: b.app,
                count = a.count + b.count,
                threshold = minOf(a.threshold, b.threshold),
            )
            rows.removeAt(pivot)
            rows[pivot - 1] = merged
            gauge += rules.gaugePerMerge
            beats += Beat.Merged(merged, b.id)
            if (merged.count < merged.threshold) break
            rows.removeAt(--pivot)
            chain++
            val tier = Catalog.app(valueApp(s, merged)).tier
            gauge += rules.gaugePerBurst + rules.igniterGaugePerTier * (bestTier - tier)
            val n = BigInteger.valueOf(merged.count.toLong())
            val e = earn(s, valueApp(s, merged), n, chain)
            earned += e
            cleared += n
            track(merged, n)
            bursts += merged
            beats += Beat.Burst(merged, chain, e)
        }

        // Overload: a full gauge turns the bursts into waves through the hidden backlog.
        val overload = bursts.isNotEmpty() && gauge >= 100
        val depth = overloadDepth(s)
        if (overload) {
            val factor = BigInteger.valueOf(rules.waveFactor.toLong())
            var backlog = bursts.map { BigInteger.valueOf(it.count.toLong()) }
            repeat(depth) { wave ->
                backlog = backlog.map { it * factor }
                chain++
                var waveEarned = ZERO
                var waveCleared = ZERO
                bursts.forEachIndexed { i, row ->
                    waveCleared += backlog[i]
                    waveEarned += earn(s, valueApp(s, row), backlog[i], chain)
                    track(row, backlog[i])
                }
                earned += waveEarned
                cleared += waveCleared
                beats += Beat.Wave(wave + 1, chain, waveCleared, waveEarned)
            }
            gauge = 0
        }
        gauge = gauge.coerceAtMost(100)

        // Progress
        // XP grows with the tiers that burst, so a run that climbs tiers also climbs levels.
        val burstTiers = bursts.sumOf { Catalog.app(valueApp(s, it)).tier.toLong() }
        val xpPoints = 1L + burstTiers + if (overload) 2L * depth * bursts.maxOf { Catalog.app(valueApp(s, it)).tier } else 0L
        var xp = s.xpCenti + xpPoints * (100 + 25 * s.backup.lv(BackupItem.FastBoot))
        var level = s.level
        while (xp >= rules.xpGoalCenti(level)) {
            xp -= rules.xpGoalCenti(level)
            level++
            beats += Beat.LevelUp(level)
        }

        val cap = energyCap(s)
        var next = s.copy(
            rows = rows,
            gauge = gauge,
            energy = state.energy - 1,
            nextChargeAt = if (state.energy >= cap) now + chargeMs(s) else state.nextChargeAt,
            wallet = s.wallet + earned,
            runEarned = s.runEarned + earned,
            lastEarned = earned,
            bestSwipe = s.bestSwipe.max(earned),
            bestChain = maxOf(s.bestChain, chain),
            level = level,
            xpCenti = xp,
            swipes = s.swipes + 1,
            autoPausedUntil = pausedUntil,
            pattern = pattern,
            regionCleared = s.regionCleared.toMutableMap().also { m -> regionAdd.forEach { (k, v) -> m.merge(k, v, BigInteger::add) } },
            categoryCleared = s.categoryCleared.toMutableMap().also { m -> categoryAdd.forEach { (k, v) -> m.merge(k, v, BigInteger::add) } },
            rng = rng.seed,
        )
        next = fillDeck(next)
        next = refill(next, now, beats)
        return SwipeOutcome(next, beats, earned, chain, cleared, overload)
    }

    /** A rising minimum slot count pulls the best owned apps into the deck. */
    private fun fillDeck(s: GameState): GameState {
        val need = minSlots(s) - s.active.size
        if (need <= 0) return s
        val extra = s.appLevels.keys.filter { it !in s.active }.sortedByDescending { Catalog.app(it).tier * 10 + Catalog.app(it).order }.take(need)
        return s.copy(active = s.active + extra)
    }

    /** New rows arrive at the top until the board is full again. */
    private fun refill(state: GameState, now: Long, beats: MutableList<Beat>): GameState {
        val rng = Rng(state.rng)
        val n = state.swipes
        val rows = state.rows.filterNot { it.kind == RowKind.Event && it.expireAt in 1..n }.toMutableList()
        var id = state.nextId
        var pausedUntil = state.autoPausedUntil
        val pattern = state.pattern.toMutableList()

        val overdue = rows.indexOfFirst { it.kind == RowKind.Spam && it.copyAt in 1..n }
        if (overdue >= 0) rows[overdue] = rows[overdue].copy(copyAt = 0)
        var spam = overdue >= 0 || n % rules.spamEverySwipes == 0
        var call = state.level >= rules.callUnlockLevel && n % rules.callEverySwipes == 0 && rows.none { it.kind == RowKind.Call }
        val cards = state.equipped.filter { c -> n % c.everySwipes == 0 && rows.none { it.event == c } }.toMutableList()

        while (rows.size < rowCount(state)) {
            val row = when {
                call -> { call = false; pausedUntil = now + rules.callPauseMs; Row(id++, RowKind.Call) }
                spam -> { spam = false; Row(id++, RowKind.Spam, copyAt = n + rules.spamCopyAfterSwipes) }
                cards.isNotEmpty() -> {
                    val c = cards.removeAt(0)
                    Row(id++, RowKind.Event, event = c, expireAt = if (c == EventCard.Otp) n + rules.otpLifeSwipes else 0)
                }
                pattern.isNotEmpty() -> Row(id++, RowKind.App, pattern.removeAt(0), count = rng.weighted(rules.countWeights) + 1, threshold = rules.threshold)
                else -> spawnApp(state, rows, rng, id++)
            }
            rows.add(0, row)
            beats += Beat.Spawned(row)
        }
        return state.copy(rows = rows, rng = rng.seed, nextId = id, autoPausedUntil = pausedUntil, pattern = pattern)
    }

    private fun spawnApp(s: GameState, rows: List<Row>, rng: Rng, id: Long): Row {
        val count = { rng.weighted(rules.countWeights) + 1 }
        if (rng.next(100) < wildPercent(s)) return Row(id, RowKind.Wild, count = count(), threshold = rules.threshold)
        val top = rows.firstOrNull()
        val second = rows.getOrNull(1)
        val options = s.active.filter { top?.kind != RowKind.App || it != top.app }
        val sandwich = second?.kind == RowKind.App && second.app != top?.app && second.app in options
        val app = if (sandwich && rng.next(100) < sandwichPercent(s)) second!!.app!! else options[rng.next(options.size)]
        return Row(id, RowKind.App, app, count = count(), threshold = rules.threshold)
    }

    // Targeting ------------------------------------------------------------------------------

    /** Rows whose removal makes two mergeable rows touch. */
    fun mergeTargets(s: GameState): List<Row> = s.rows.indices.filter { i ->
        val a = s.rows.getOrNull(i - 1)
        val b = s.rows.getOrNull(i + 1)
        a != null && b != null && canMerge(a, b)
    }.map { s.rows[it] }

    /**
     * Auto-clean is deliberately simple: the lowest row that makes a merge, else an event card, else spam,
     * else the bottom row. From AutoClean backup level 5 it looks one move ahead like [bestTarget].
     */
    fun autoTarget(s: GameState, now: Long): Long? {
        if (s.backup.lv(BackupItem.AutoClean) >= rules.smartAutoLevel) return bestTarget(s, now)
        return mergeTargets(s).lastOrNull()?.id
            ?: s.rows.lastOrNull { it.kind == RowKind.Event }?.id
            ?: s.rows.lastOrNull { it.kind == RowKind.Spam }?.id
            ?: s.rows.lastOrNull()?.id
    }

    /**
     * Reference "good player" for simulators: tries every row and keeps the best immediate result
     * (chain, overload, merges, then event cards and spam). Ties go to the lower row.
     */
    fun bestTarget(s: GameState, now: Long): Long? {
        var best: Long? = null
        var bestKey = listOf(-1L, -1L, -1L, -1L, -1L)
        s.rows.forEach { row ->
            val o = swipe(s, row.id, now) ?: return@forEach
            val key = listOf(
                o.chain.toLong(), if (o.overload) 1L else 0L, o.beats.count { it is Beat.Merged }.toLong(),
                if (row.kind == RowKind.Event) 1L else 0L, if (row.kind == RowKind.Spam) 1L else 0L,
            )
            val cmp = key.indices.firstNotNullOfOrNull { i -> key[i].compareTo(bestKey[i]).takeIf { it != 0 } } ?: 0
            if (cmp >= 0) { best = row.id; bestKey = key }
        }
        return best
    }

    // Shop and deck --------------------------------------------------------------------------

    fun canBuy(s: GameState, appId: String): Boolean {
        val app = Catalog.app(appId)
        return appId !in s.appLevels && s.level >= app.unlockLevel && s.wallet >= rules.appPrice(app)
    }

    /** A bought app joins the deck if a slot is free; otherwise the player swaps it in. */
    fun buy(s: GameState, appId: String): GameState {
        if (!canBuy(s, appId)) return s
        val app = Catalog.app(appId)
        val active = if (s.active.size < maxSlots(s)) s.active + appId else s.active
        return s.copy(wallet = s.wallet - rules.appPrice(app), appLevels = s.appLevels + (appId to 1), active = active)
    }

    fun upgrade(s: GameState, appId: String): GameState {
        val level = s.appLevels[appId] ?: return s
        val price = rules.upgradePrice(Catalog.app(appId), level)
        if (s.wallet < price) return s
        return s.copy(wallet = s.wallet - price, appLevels = s.appLevels + (appId to level + 1))
    }

    fun buyEvent(s: GameState, card: EventCard): GameState {
        val price = BigInteger.valueOf(card.price)
        if (card in s.events || s.level < card.unlockLevel || s.wallet < price) return s
        val equipped = if (s.equipped.size < eventSlots(s)) s.equipped + card else s.equipped
        return s.copy(wallet = s.wallet - price, events = s.events + card, equipped = equipped)
    }

    fun buyAssist(s: GameState, a: Assist): GameState {
        val owned = s.assist(a)
        val price = rules.assistPrice(a, owned)
        if (s.level < a.unlockLevel || owned >= a.max || s.wallet < price) return s
        return s.copy(wallet = s.wallet - price, assists = s.assists + (a to owned + 1))
    }

    /** Set both decks. Apps: owned, distinct, between the level's minimum and maximum. Takes effect on new rows. */
    fun setDeck(s: GameState, apps: List<String>, cards: List<EventCard> = s.equipped): GameState? {
        val ok = apps.distinct().size == apps.size && apps.size >= maxOf(2, minSlots(s)) && apps.size <= maxSlots(s) &&
            s.appLevels.keys.containsAll(apps) && cards.distinct().size == cards.size && cards.size <= eventSlots(s) &&
            s.events.containsAll(cards)
        return if (ok) s.copy(active = apps, equipped = cards) else null
    }
}
