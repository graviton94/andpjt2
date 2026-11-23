package com.graviton94.notificationcleaner.core

import java.math.BigInteger
import java.math.BigInteger.ZERO

enum class RowKind { App, Wild, Spam, Call, Event, Alert }

/** System alerts (rules v3.1): they never merge; leaving them costs something, swiping them helps. */
enum class Alert(val label: String) { LowBattery("배터리 부족"), Storage("저장공간 부족"), Crash("앱 충돌") }

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
    /** Alert rows: which alert. For 앱 충돌, [app] is the app that stopped. */
    val alert: Alert? = null,
) {
    val merges: Boolean get() = kind == RowKind.App || kind == RowKind.Wild
}

/** What survives a factory reset: backup files and what they bought. */
data class Backup(
    val files: Long = 0,
    val items: Map<BackupItem, Int> = emptyMap(),
    val resets: Int = 0,
    /** All backup files ever earned. Each adds RuleSet.lifetimeFilePercent to every notification value. */
    val lifetime: Long = 0,
    /** Deepest level any run reached. */
    val bestLevel: Int = 0,
    /** Best run P ever: backup files pay only for beating it. */
    val bestRunP: BigInteger = BigInteger.ZERO,
    /** 2nd prestige: survives a sync, which wipes everything else here except [bestLevel]. */
    val cloud: Cloud = Cloud(),
) {
    fun lv(item: BackupItem): Int = items[item] ?: 0
}

/** What survives a 클라우드 동기화: GB and what they bought. */
data class Cloud(
    val gb: Long = 0,
    val items: Map<CloudItem, Int> = emptyMap(),
    /** Syncs that paid GB. */
    val syncs: Int = 0,
    val lifetime: Long = 0,
    /** Most backup files one cycle earned: GB pay only for beating it. */
    val bestCycleFiles: Long = 0,
    /** Paying factory resets made while a content pack was on: pack items open one level per 3 of them. */
    val packResets: Int = 0,
    /** Credits, premium stars, ads and passes: permanent, survives everything (Monetization.kt). */
    val account: Account = Account(),
) {
    fun lv(item: CloudItem): Int = items[item] ?: 0
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
    val swipes: Int = 0,
    val autoPausedUntil: Long = 0,
    val regionCleared: Map<Region, BigInteger> = emptyMap(),
    val categoryCleared: Map<Category, BigInteger> = emptyMap(),
    /** Apps queued to arrive next, in order (from @모두 공지). */
    val pattern: List<String> = emptyList(),
    val rng: Long,
    val nextId: Long,
    val backup: Backup = Backup(),
    /** Recent income, for offline settlement: P per minute (moving average over swipes). */
    val earnRate: BigInteger = ZERO,
    /** Apps whose pro version was bought this run. */
    val pro: Set<String> = emptySet(),
    /** 앱스토어 세일: the next app bought costs half. */
    val saleNext: Boolean = false,
    val lastSwipeAt: Long = 0,
    // Unique-effect state (docs/bible/15-unique-effects.md)
    /** 배다른민족: swipes left that cost no battery. */
    val freeSwipes: Int = 0,
    /** 트래블로까: the next swipe with a burst after buying an app pays ×3. */
    val boostNext: Boolean = false,
    /** 아고다다: the first burst after an offline settlement pays ×3. */
    val returnBurst: Boolean = false,
    /** 넷플러그: swipes left after returning that pay ×2. */
    val returnSwipes: Int = 0,
    /** 모모모 · 에어방앤방: day number of each app's last daily bonus. */
    val dailyBonusDay: Map<String, Long> = emptyMap(),
    /** 메이버: the trending category (×2) for the current 10 swipes. */
    val trend: Category? = null,
    // Timed cards and alerts (rules v3.1)
    /** 생일 축하: day number it last arrived. */
    val birthdayDay: Long = -1,
    /** 알림훈련: running until this time; taps so far (1% each, up to 100). */
    val drillUntil: Long = 0,
    val drillTaps: Int = 0,
    /** 방해 금지: active until this time; uses today. */
    val dndUntil: Long = 0,
    val dndDay: Long = -1,
    val dndUses: Int = 0,
    /** 앱 충돌: the restarted app's next burst pays ×2. */
    val crashBoost: String? = null,
) {
    fun assist(a: Assist): Int = assists[a] ?: 0
}

/** How a notification was cleared, for value effects. */
data class EarnCtx(
    val direct: Boolean = false,
    val wave: Boolean = false,
    val auto: Boolean = false,
    /** 로빈훗훗: the swipe's gamble multiplier in ‰. */
    val gamblePermille: Int = 1_000,
    /** 핀트레스트: rows of this app on the board. */
    val pile: Int = 0,
    /** 수입 부스트 is running. */
    val boosted: Boolean = false,
)

/** What happened, in order. The UI replays these; the state is already final. */
sealed interface Beat {
    data class Swiped(val row: Row, val earned: BigInteger) : Beat
    data class Merged(val into: Row, val fromId: Long) : Beat
    data class Burst(val row: Row, val chain: Int, val earned: BigInteger) : Beat
    data class Wave(val index: Int, val chain: Int, val cleared: BigInteger, val earned: BigInteger) : Beat
    data class Spawned(val row: Row) : Beat
    data class FreeUpgrade(val app: String, val level: Int) : Beat
    data class LevelUp(val level: Int) : Beat
    /** 과열 보호: P past the per-swipe level cap was lost. */
    data class Capped(val lost: BigInteger) : Beat
}

data class SwipeOutcome(
    val state: GameState,
    val beats: List<Beat>,
    val earned: BigInteger,
    /** Bursts + overload waves. */
    val chain: Int,
    val cleared: BigInteger,
    val overload: Boolean,
    /** P lost to the jackpot cap (RuleSet.swipeLevelCap). */
    val capped: BigInteger = ZERO,
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

    /** SplitMix64 finaliser: effect rolls that must not touch the board's random sequence. */
    private fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Unique effects work only while the app is in the deck. */
    fun on(s: GameState, appId: String) = appId in s.active

    fun energyCap(s: GameState) = rules.energyCap + 2 * s.backup.lv(BackupItem.Battery) + if (on(s, "grab")) 2 else 0
    fun chargeMs(s: GameState) = (rules.chargeMs - 200L * s.backup.lv(BackupItem.Battery)).let {
        if (s.rows.any { r -> r.alert == Alert.LowBattery }) it * 13 / 10 else it  // 배터리 부족 left on the board
    }
    fun autoIntervalMs(s: GameState) = rules.autoIntervalMs - 300L * s.backup.lv(BackupItem.AutoClean) - 500L * s.backup.cloud.lv(CloudItem.CloudAuto) -
        (if (on(s, "settings")) 300L else 0L) - (if (on(s, "ai")) 500L else 0L)
    fun overloadDepth(s: GameState) = 1 + s.assist(Assist.Overload) + s.backup.lv(BackupItem.DeepOverload)
    fun rowCount(s: GameState) = minOf(
        rules.maxRows,
        rules.baseRows + maxOf(0, s.active.size - 4) / rules.appsPerExtraRow + s.assist(Assist.Board) + s.backup.lv(BackupItem.Board),
    ) - if (s.rows.any { it.alert == Alert.Storage }) 1 else 0  // 저장공간 부족 left on the board

    /** 방해 금지 doubles auto speed while it runs. */
    fun autoIntervalMs(s: GameState, now: Long) = autoIntervalMs(s).let { if (now < s.dndUntil) it / 2 else it }
    fun sandwichPercent(s: GameState) = rules.sandwichPercent + rules.sandwichPerExtraApp * maxOf(0, s.active.size - 4) +
        rules.sandwichPerAssist * s.assist(Assist.Sandwich) + 2 * s.backup.lv(BackupItem.Sandwich) + if (on(s, "feed")) 10 else 0
    fun wildPercent(s: GameState) = rules.wildPerAssist * s.assist(Assist.Wild) + s.backup.lv(BackupItem.Wild)
    fun maxSlots(s: GameState) = rules.maxSlots(s.level, s.backup)
    fun minSlots(s: GameState) = minOf(rules.minSlots(s.level), s.appLevels.size)
    fun eventSlots(s: GameState) = rules.eventSlots(s.level, s.backup)
    fun bestTier(s: GameState) = s.active.maxOf { Catalog.app(it).tier }
    fun bestApp(s: GameState) = s.active.maxBy { Catalog.app(it).tier * 1_000 + (s.appLevels[it] ?: 1) }
    fun backupFilesNow(s: GameState) = rules.backupFiles(s.runEarned, s.level, s.backup)

    /** The deck's synergy bonus % for an app's region and category (0 when alone). */
    fun synergy(s: GameState, appId: String): Pair<Int, Int> {
        val app = Catalog.app(appId)
        // Stock apps have no home market: category synergy only.
        val r = if (app.region == Region.Stock) 0 else s.active.count { Catalog.app(it).region == app.region }
        val c = s.active.count { Catalog.app(it).category == app.category }
        val rs = rules.synergyPercent(r, s.backup, rules.regionSynergySteps).let { if (on(s, "papago")) it * 110 / 100 else it }
        return rs to rules.synergyPercent(c, s.backup, rules.categorySynergySteps)
    }

    /** Regions and categories with 2+ apps in the deck: each multiplies every value by RuleSet.diversityPermille. */
    fun activeSets(s: GameState): Int {
        val apps = s.active.map { Catalog.app(it) }
        return apps.filter { it.region != Region.Stock }.groupingBy { it.region }.eachCount().count { it.value >= 2 } +
            apps.groupingBy { it.category }.eachCount().count { it.value >= 2 }
    }

    private fun digits(n: BigInteger?) = if (n == null || n.signum() <= 0) 1 else n.toString().length

    /** P for [count] notifications of [appId] at chain number [chain]. */
    fun earn(s: GameState, appId: String, count: BigInteger, chain: Int, ctx: EarnCtx = EarnCtx()): BigInteger {
        val app = Catalog.app(appId)
        val (num, den) = rules.baseValue(app, s.appLevels[appId] ?: 1, s.backup)
        val (rs, cs) = synergy(s, appId)
        val rd = rules.regionDigitPercent * (digits(s.regionCleared[app.region]) - 1)
        val cd = rules.categoryDigitPercent * (digits(s.categoryCleared[app.category]) - 1)
        val digitPct = BigInteger.valueOf((100L + rd) * (100L + cd))
        val link = if (chain > 0 && on(s, "tube")) chain + 1 else chain  // 너튜브: deck-wide chain +1
        val chainNum = BigInteger.valueOf(10L + link * (10L + s.backup.lv(BackupItem.ChainBoost) + 2 * s.backup.cloud.lv(CloudItem.CloudChain)))
        val pro = if (appId in s.pro) BigInteger.valueOf(rules.proMultiplier) else BigInteger.ONE
        // Deck synergy (region × category × diversity) is capped below one tier step.
        val sets = maxOf(0, activeSets(s) - 1)
        var comboNum = BigInteger.valueOf((100L + rs) * (100L + cs)) * BigInteger.valueOf(rules.diversityPermille.toLong()).pow(sets)
        var comboDen = hundred.pow(2) * BigInteger.valueOf(1_000).pow(sets)
        val cap = BigInteger.valueOf(rules.synergyCapPermille.toLong())
        if (comboNum * BigInteger.valueOf(1_000) > cap * comboDen) { comboNum = cap; comboDen = BigInteger.valueOf(1_000) }
        val fx = BigInteger.valueOf(effectPermille(s, app, count, chain, ctx))
        val premium = BigInteger.valueOf(rules.premiumPermille(s.backup.cloud.account.stars(appId)) * (if (ctx.boosted) rules.boostPermille.toLong() else 1_000L))
        return count * num * comboNum * digitPct * chainNum * pro * fx * premium / (den * comboDen * hundred.pow(2) * BigInteger.TEN * BigInteger.valueOf(1_000_000_000))
    }

    /** Value effects of the deck on one clear of [app], in ‰ (1000 = none). */
    fun effectPermille(s: GameState, app: AppDef, count: BigInteger, chain: Int, ctx: EarnCtx): Long {
        var m = 1_000L
        fun times(p: Long) { m = m * p / 1_000 }
        val id = app.id
        val deck = s.active.map { Catalog.app(it) }
        if (id == "memo" && ctx.direct) times(2_000)
        if (id == "calc" && count.signum() > 0 && count.mod(BigInteger.valueOf(4)).signum() == 0) times(1_500)
        if (id == "pin" && ctx.pile >= 3) times(1_200)
        if (id == "contacts") times(1_000L + 150L * deck.count { it.id != id && it.category == Category.Social })
        if (id == "photo") times(1_000L + 150L * deck.count { it.id != id && it.category == Category.Media })
        if (id == "career") times(1_000L + 100L * deck.map { it.category }.distinct().size)
        if (on(s, "jobs") && app.category == Category.Work && deck.count { it.category == Category.Work } >= 3) times(1_200)
        if (id == "joox" && ctx.wave) times(1_500)
        if (id == "beat" && ctx.auto) times(1_300)
        if (id == "mail" && chain >= 2) times(1_500)
        if (id == "robin") times(ctx.gamblePermille.toLong())
        if (on(s, "portal") && s.trend == app.category) times(2_000)
        return m
    }

    /** Most P this run can still gain in one go without rising more than [steps] levels. */
    fun jackpotRoom(s: GameState, steps: Int): BigInteger =
        if (s.level < rules.jackpotCapFrom) BigInteger.TEN.pow(400) else (rules.levelCeil(s.level + steps + 1, s.backup.lv(BackupItem.FastBoot)) - BigInteger.ONE - s.runEarned).max(ZERO)

    private fun valueApp(s: GameState, row: Row): String = if (row.kind == RowKind.Wild) bestApp(s) else row.app!!

    // Game lifecycle --------------------------------------------------------------------------

    /**
     * First board of a run. Fixed so the first ten seconds teach one thing: the middle row (날씨) sits
     * between two 메모 rows of 2; removing it makes them touch, reach 4 and burst.
     */
    fun newGame(now: Long, seed: Long, backup: Backup = Backup()): GameState {
        val layout = listOf("alarm" to 1, "weather" to 2, "memo" to 2, "weather" to 1, "memo" to 2, "alarm" to 1, "weather" to 2)
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
        val files = backupFilesNow(s).let { f -> if (s.backup.cloud.account.backupBoost) f * (100 + rules.backupBoostPercent) / 100 else f }
        // Only a reset that pays files counts toward reset-gated content; empty resets cannot farm the count.
        return newGame(now, s.rng, s.backup.copy(
            files = s.backup.files + files, resets = s.backup.resets + if (files > 0) 1 else 0,
            lifetime = s.backup.lifetime + files, bestLevel = maxOf(s.backup.bestLevel, s.level),
            bestRunP = s.backup.bestRunP.max(s.runEarned),
            cloud = s.backup.cloud.let { c -> if (files > 0 && Catalog.packs.isNotEmpty()) c.copy(packResets = c.packResets + 1) else c }
                .let { c -> c.copy(account = c.account.copy(backupBoost = false)) },
        ))
    }

    /** Backup shop price after 클라우드 할인. */
    fun backupPrice(s: GameState, item: BackupItem): Long =
        maxOf(1L, rules.backupPrice(item, s.backup.lv(item)) * (10 - s.backup.cloud.lv(CloudItem.Discount)) / 10)

    /** Reset count the backup shop gates on: real resets plus 백업 상점 단계 유지. */
    fun shopStage(s: GameState) = s.backup.resets + 3 * s.backup.cloud.lv(CloudItem.KeepShop)

    /** Max level of a backup item; 알림 가치 한도 raises 알림 가치's. */
    fun backupMax(s: GameState, item: BackupItem) = item.max + if (item == BackupItem.Value) 10 * s.backup.cloud.lv(CloudItem.ValueCap) else 0

    fun backupOpen(s: GameState, item: BackupItem) = s.backup.lv(item) < backupMax(s, item) && shopStage(s) >= item.minResets

    fun buyBackup(s: GameState, item: BackupItem): GameState {
        val owned = s.backup.lv(item)
        val price = backupPrice(s, item)
        if (!backupOpen(s, item) || s.backup.files < price) return s
        return s.copy(backup = s.backup.copy(files = s.backup.files - price, items = s.backup.items + (item to owned + 1)))
    }

    /** GB a sync pays now: only what beating the best cycle adds. Includes this run's pending backup files. */
    fun cloudGbNow(s: GameState): Long {
        if (s.level < rules.syncLevel) return 0
        val cycle = s.backup.lifetime + backupFilesNow(s)
        return maxOf(0L, rules.cloudTotal(maxOf(cycle, s.backup.cloud.bestCycleFiles)) - s.backup.cloud.lifetime)
    }

    /**
     * 클라우드 동기화 (2nd prestige): the run and the whole backup layer (files, shop, resets, best run) restart;
     * GB, the cloud shop and app unlocks (best level) stay. Null below [RuleSet.syncLevel].
     */
    fun sync(s: GameState, now: Long): GameState? {
        if (s.level < rules.syncLevel) return null
        val gb = cloudGbNow(s)
        val c = s.backup.cloud
        val cloud = c.copy(
            gb = c.gb + gb, syncs = c.syncs + if (gb > 0) 1 else 0, lifetime = c.lifetime + gb,
            bestCycleFiles = maxOf(c.bestCycleFiles, s.backup.lifetime + backupFilesNow(s)),
        )
        return newGame(now, s.rng, Backup(bestLevel = maxOf(s.backup.bestLevel, s.level), cloud = cloud))
    }

    /** A cloud item can be bought now: below max, enough syncs; pack items open one level per 3 resets after the update. */
    fun cloudOpen(s: GameState, item: CloudItem): Boolean {
        val c = s.backup.cloud
        val owned = c.lv(item)
        if (owned >= item.max || c.syncs < item.minSyncs) return false
        if (item.pack != null) return item.pack in Catalog.packs && owned < c.packResets / 3
        return true
    }

    fun buyCloud(s: GameState, item: CloudItem): GameState {
        val c = s.backup.cloud
        val owned = c.lv(item)
        val price = rules.cloudPrice(item, owned)
        if (!cloudOpen(s, item) || c.gb < price) return s
        return s.copy(backup = s.backup.copy(cloud = c.copy(gb = c.gb - price, items = c.items + (item to owned + 1))))
    }

    /** Battery recharge up to [now]. */
    fun tick(s: GameState, now: Long): GameState {
        val cap = energyCap(s)
        if (s.energy >= cap) return s.copy(nextChargeAt = 0)
        if (s.nextChargeAt == 0L) return s.copy(nextChargeAt = now + chargeMs(s))
        if (now < s.nextChargeAt) return s
        val ms = chargeMs(s)
        val steps = ((now - s.nextChargeAt) / ms + 1).coerceAtMost((cap - s.energy).toLong()).toInt()
        val energy = s.energy + steps
        return s.copy(energy = energy, nextChargeAt = if (energy >= cap) 0 else s.nextChargeAt + steps * ms)
    }

    // Swipe ----------------------------------------------------------------------------------

    private fun canMerge(a: Row, b: Row) = a.merges && b.merges && (a.app == b.app || a.kind == RowKind.Wild || b.kind == RowKind.Wild)

    fun swipe(input: GameState, rowId: Long, now: Long, actor: Actor = Actor.Player): SwipeOutcome? {
        val state = tick(input, now)
        val auto = actor == Actor.Auto
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
        var energyGain = 0
        var crashBoost = state.crashBoost
        var drillUntil = state.drillUntil
        var drillTaps = state.drillTaps
        var freeSwipes = maxOf(0, state.freeSwipes - if (state.freeSwipes > 0) 1 else 0)
        val usesBattery = state.freeSwipes <= 0
        // 로빈훗훗: one gamble roll per swipe, ×0.5 to ×3 (mean about ×1.4). Hashed from the state so effects never
        // shift the board's random sequence for decks without the app.
        val roll = mix(state.rng xor state.swipes.toLong())
        val gamble = 500 + Math.floorMod(roll, 2_501L).toInt().let { if (it > 1_800) (it - 1_800) / 2 + 1_800 else it }
        fun pileOf(app: String?) = rows.count { it.app == app }
        fun ctx(wave: Boolean = false, direct: Boolean = false, app: String? = null) =
            EarnCtx(direct = direct, wave = wave, auto = auto, gamblePermille = gamble, pile = pileOf(app), boosted = now < state.backup.cloud.account.boostUntil)

        fun track(row: Row, n: BigInteger) {
            if (row.kind != RowKind.App) return
            val app = Catalog.app(row.app!!)
            regionAdd.merge(app.region, n, BigInteger::add)
            categoryAdd.merge(app.category, n, BigInteger::add)
        }

        when (removed.kind) {
            RowKind.App, RowKind.Wild -> {
                val n = BigInteger.valueOf(removed.count.toLong())
                val e = earn(state, valueApp(state, removed), n, 0, ctx(direct = !auto, app = removed.app))
                earned += e
                cleared += n
                track(removed, n)
                beats += Beat.Swiped(removed, e)
            }
            RowKind.Call -> {
                pausedUntil = 0
                if (on(state, "phone")) energyGain += 2  // 전화: answering recharges
                val e = if (on(state, "meet")) state.earnRate else ZERO  // 주움: joining the call pays a minute of income
                earned += e
                beats += Beat.Swiped(removed, e)
            }
            RowKind.Spam -> beats += Beat.Swiped(removed, ZERO)
            RowKind.Alert -> {
                when (removed.alert!!) {
                    Alert.LowBattery -> energyGain += 4  // 절전 모드
                    Alert.Storage -> {}
                    Alert.Crash -> crashBoost = removed.app
                }
                beats += Beat.Swiped(removed, ZERO)
            }
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
                    EventCard.Sale -> s = s.copy(saleNext = true)
                    EventCard.GroupText -> for (i in rows.indices) if (rows[i].kind == RowKind.App) rows[i] = rows[i].copy(count = rows[i].count + 1)
                    EventCard.Firmware -> s = s.copy(appLevels = s.appLevels.mapValues { (id, lv) -> if (id in s.active) lv + 1 else lv })
                    EventCard.Lotto -> if (rng.next(2) == 1) e = state.lastEarned * BigInteger.valueOf(1L + rng.next(60))
                    EventCard.Birthday -> e = state.earnRate * BigInteger.TEN
                    EventCard.Drill -> { drillUntil = now + rules.drillMs; drillTaps = 0 }
                    EventCard.Dnd -> {}
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
        var chainBonus = 0  // 메롱: later bursts in this swipe count one more chain
        var waveFactor = rules.waveFactor
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
            val matched = a.app == "match" && b.app == "match"  // 튄더: two rows touching burst at once
            if (merged.count < merged.threshold && !matched) break
            rows.removeAt(--pivot)
            chain++
            val tier = Catalog.app(valueApp(s, merged)).tier
            gauge += rules.gaugePerBurst + rules.igniterGaugePerTier * (bestTier - tier)
            val n = BigInteger.valueOf(merged.count.toLong())
            val burstApp = valueApp(s, merged)
            val e = earn(s, burstApp, n, chain + chainBonus, ctx(app = burstApp))
            earned += e
            cleared += n
            track(merged, n)
            bursts += merged
            beats += Beat.Burst(merged, chain, e)
            // Burst effects of the app that burst.
            when (burstApp) {
                "music" -> chainBonus++
                "alarm" -> pattern = pattern + "alarm"
                "flash" -> rows.indexOfFirst { it.kind == RowKind.Spam }.takeIf { it >= 0 }?.let { rows.removeAt(it); if (it < pivot) pivot-- }
                "sms" -> rows.getOrNull(pivot - 1)?.takeIf { it.kind == RowKind.App }?.let { rows[pivot - 1] = it.copy(count = it.count + 1) }
                "chat" -> rows.getOrNull(pivot - 1)?.takeIf { it.app == "chat" }?.let { rows[pivot - 1] = it.copy(count = it.count + 1) }
                "michat" -> rows.getOrNull(pivot)?.takeIf { it.kind == RowKind.App }?.let { rows[pivot] = it.copy(count = it.count + 1) }
                "reddit" -> for (i in rows.indices) rows[i].app?.takeIf { it != "reddit" && Catalog.app(it).category == Category.Social }
                    ?.let { rows[i] = rows[i].copy(count = rows[i].count + 1) }
                "pay" -> energyGain++
                "food" -> freeSwipes = 3
                "zalo" -> gauge += 2 * s.active.count { Catalog.app(it).region == Region.SoutheastAsia }
                "line" -> gauge += 2 * s.active.count { Catalog.app(it).region == Region.Asia }
                "bigo" -> waveFactor = 4
                "superapp" -> Category.entries.forEach { c -> if (c != Catalog.app("superapp").category) categoryAdd.merge(c, n, BigInteger::add) }
                "mega" -> earned += rules.appPrice(Catalog.app("mega")) * BigInteger.TWO / BigInteger.valueOf(1_000)
            }
        }

        // Overload: a full gauge turns the bursts into waves through the hidden backlog.
        val overload = bursts.isNotEmpty() && gauge >= 100
        val depth = overloadDepth(s)
        if (overload) {
            val factor = BigInteger.valueOf(waveFactor.toLong())
            var backlog = bursts.map { BigInteger.valueOf(it.count.toLong()) }
            repeat(depth) { wave ->
                backlog = backlog.map { it * factor }
                chain++
                var waveEarned = ZERO
                var waveCleared = ZERO
                bursts.forEachIndexed { i, row ->
                    waveCleared += backlog[i]
                    waveEarned += earn(s, valueApp(s, row), backlog[i], chain + chainBonus, ctx(wave = true, app = valueApp(s, row)))
                    track(row, backlog[i])
                }
                earned += waveEarned
                cleared += waveCleared
                beats += Beat.Wave(wave + 1, chain, waveCleared, waveEarned)
            }
            gauge = 0
        }
        gauge = gauge.coerceAtMost(100)

        // Swipe-level effects: daily, return, after-purchase and binge bonuses.
        var dailyBonusDay = s.dailyBonusDay
        var boostNext = s.boostNext
        var returnBurst = s.returnBurst
        var returnSwipes = s.returnSwipes
        val today = now / 86_400_000L
        if (bursts.isNotEmpty()) {
            for ((app, mult) in listOf("momo" to 3L, "airbnb" to 5L)) {
                if (on(s, app) && (dailyBonusDay[app] ?: -1L) < today) { earned *= BigInteger.valueOf(mult); dailyBonusDay = dailyBonusDay + (app to today) }
            }
            if (boostNext && on(s, "travel")) earned *= BigInteger.valueOf(3)
            boostNext = false
            if (returnBurst && on(s, "agoda")) earned *= BigInteger.valueOf(3)
            returnBurst = false
        }
        if (returnSwipes > 0) { if (on(s, "ott")) earned *= BigInteger.TWO; returnSwipes-- }
        // 앱 충돌 restarted: that app's next burst ×2. 알림훈련: taps raise income while the drill runs.
        if (crashBoost != null && bursts.any { valueApp(s, it) == crashBoost }) { earned *= BigInteger.TWO; crashBoost = null }
        if (now < state.drillUntil) earned = earned * BigInteger.valueOf(100L + minOf(100, state.drillTaps)) / hundred
        // 메이버: a new trending category every 10 swipes.
        val trend = if (on(s, "portal") && s.swipes % 10 == 0) Category.entries[Math.floorMod(mix(roll), Category.entries.size.toLong()).toInt()] else s.trend

        // Jackpot cap: luck may jump a few levels, never the whole run.
        val room = jackpotRoom(s, rules.swipeLevelCap)
        val capped = (earned - room).max(ZERO)
        if (capped.signum() > 0) { earned = room; beats += Beat.Capped(capped) }

        // Progress: level follows this run's cumulative P.
        val runEarned = s.runEarned + earned
        val level = maxOf(s.level, rules.levelFor(runEarned, s.backup.lv(BackupItem.FastBoot)))
        for (l in s.level + 1..level) beats += Beat.LevelUp(l)

        val cap = energyCap(s)
        val dt = (if (s.lastSwipeAt == 0L) chargeMs(s) else now - s.lastSwipeAt).coerceIn(chargeMs(s), 60_000L)
        var next = s.copy(
            rows = rows,
            gauge = gauge,
            energy = minOf(energyCap(s), state.energy - (if (usesBattery) 1 else 0) + energyGain),
            // Any swipe below a full battery keeps a charge scheduled, even if the cap grew meanwhile.
            nextChargeAt = if (state.nextChargeAt == 0L) now + chargeMs(s) else state.nextChargeAt,
            wallet = s.wallet + earned,
            runEarned = runEarned,
            lastEarned = earned,
            bestSwipe = s.bestSwipe.max(earned),
            bestChain = maxOf(s.bestChain, chain),
            level = level,
            swipes = s.swipes + 1,
            autoPausedUntil = pausedUntil,
            pattern = pattern,
            regionCleared = s.regionCleared.toMutableMap().also { m -> regionAdd.forEach { (k, v) -> m.merge(k, v, BigInteger::add) } },
            categoryCleared = s.categoryCleared.toMutableMap().also { m -> categoryAdd.forEach { (k, v) -> m.merge(k, v, BigInteger::add) } },
            rng = rng.seed,
            earnRate = (s.earnRate * BigInteger.valueOf(9) + earned * BigInteger.valueOf(60_000 / dt)) / BigInteger.TEN,
            lastSwipeAt = now,
            freeSwipes = freeSwipes,
            boostNext = boostNext,
            returnBurst = returnBurst,
            returnSwipes = returnSwipes,
            dailyBonusDay = dailyBonusDay,
            trend = trend,
            crashBoost = crashBoost,
            drillUntil = drillUntil,
            drillTaps = drillTaps,
        )
        next = fillDeck(next)
        next = refill(next, now, beats)
        return SwipeOutcome(next, beats, earned, chain, cleared, overload, capped)
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

        val overdue = if (on(state, "secret")) -1 else rows.indexOfFirst { it.kind == RowKind.Spam && it.copyAt in 1..n }  // 텔레그림
        if (overdue >= 0) rows[overdue] = rows[overdue].copy(copyAt = 0)
        val quiet = now < state.dndUntil  // 방해 금지: no interruptions
        var spam = !quiet && (overdue >= 0 || n % rules.spamEverySwipes == 0)
        var call = !quiet && state.level >= rules.callUnlockLevel && n % rules.callEverySwipes == 0 && rows.none { it.kind == RowKind.Call }
        val cards = state.equipped.filter { c -> c.everySwipes > 0 && n % c.everySwipes == 0 && rows.none { it.event == c } }.toMutableList()
        val today = now / 86_400_000L
        var birthdayDay = state.birthdayDay
        if (EventCard.Birthday in state.equipped && today > birthdayDay && rows.none { it.event == EventCard.Birthday }) { cards += EventCard.Birthday; birthdayDay = today }
        fun noAlert(a: Alert) = rows.none { it.alert == a }
        val alerts = mutableListOf<Row>()
        if (!quiet) {
            if (state.level >= rules.lowBatteryLevel && state.energy <= rules.lowBatteryAt && noAlert(Alert.LowBattery)) alerts += Row(0, RowKind.Alert, alert = Alert.LowBattery)
            if (state.level >= rules.storageLevel && n % rules.storageEverySwipes == rules.storageEverySwipes / 2 && noAlert(Alert.Storage)) alerts += Row(0, RowKind.Alert, alert = Alert.Storage)
            if (state.level >= rules.crashLevel && n % rules.crashEverySwipes == rules.crashEverySwipes / 2 && noAlert(Alert.Crash) && state.active.size >= 2)
                alerts += Row(0, RowKind.Alert, app = state.active[rng.next(state.active.size)], alert = Alert.Crash)
        }

        while (rows.size < rowCount(state)) {
            val row = when {
                call -> { call = false; pausedUntil = now + rules.callPauseMs / (if (on(state, "secret")) 2 else 1); Row(id++, RowKind.Call) }
                spam -> { spam = false; Row(id++, RowKind.Spam, copyAt = n + rules.spamCopyAfterSwipes) }
                alerts.isNotEmpty() -> alerts.removeAt(0).copy(id = id++)
                cards.isNotEmpty() -> {
                    val c = cards.removeAt(0)
                    Row(id++, RowKind.Event, event = c, expireAt = if (c == EventCard.Otp) n + rules.otpLifeSwipes else 0)
                }
                pattern.isNotEmpty() -> pattern.removeAt(0).let { a -> Row(id++, RowKind.App, a, count = rng.weighted(rules.countWeights) + 1, threshold = thresholdOf(a)) }
                else -> spawnApp(state, rows, rng, id++)
            }
            rows.add(0, row)
            beats += Beat.Spawned(row)
        }
        return state.copy(rows = rows, rng = rng.seed, nextId = id, autoPausedUntil = pausedUntil, pattern = pattern, birthdayDay = birthdayDay)
    }

    private fun spawnApp(s: GameState, rows: List<Row>, rng: Rng, id: Long): Row {
        val count = { rng.weighted(rules.countWeights) + 1 }
        if (rng.next(100) < wildPercent(s)) return Row(id, RowKind.Wild, count = count(), threshold = rules.threshold)
        val top = rows.firstOrNull()
        val second = rows.getOrNull(1)
        val crashed = rows.filter { it.alert == Alert.Crash }.mapNotNull { it.app }  // 앱 충돌: that app posts nothing until restarted
        val options = s.active.filter { (top?.kind != RowKind.App || it != top.app) && it !in crashed }.ifEmpty { s.active }
        val sandwich = second?.kind == RowKind.App && second.app != top?.app && second.app in options
        val app = if (sandwich && rng.next(100) < sandwichPercent(s)) second!!.app!! else options[rng.next(options.size)]
        return Row(id, RowKind.App, app, count = count(), threshold = thresholdOf(app))
    }

    /** 왔썹 rows burst at 3. */
    fun thresholdOf(app: String) = if (app == "green") 3 else rules.threshold

    // Offline --------------------------------------------------------------------------------

    fun offlineCapMs(s: GameState): Long =
        (rules.step(rules.offlineCapSteps, s.level) + s.backup.lv(BackupItem.Offline) + s.backup.cloud.lv(CloudItem.CloudOffline)) * 3_600_000L
    fun offlinePercent(s: GameState): Int = rules.offlineEfficiencyPercent + 2 * s.backup.lv(BackupItem.Offline)

    /**
     * Returning after [awayMs]: pays the recent income rate for the capped time at the offline efficiency,
     * in P; the level follows. The battery refills through [tick] as usual.
     */
    fun settleOffline(s: GameState, awayMs: Long, now: Long): Pair<GameState, BigInteger> {
        val minutes = BigInteger.valueOf(minOf(awayMs, offlineCapMs(s)) / 60_000)
        val pct = BigInteger.valueOf(offlinePercent(s).toLong())
        val p = (s.earnRate * minutes * pct * BigInteger.valueOf(rules.offlineAutoPermille.toLong()) / BigInteger.valueOf(100_000))
            .min(jackpotRoom(s, rules.offlineLevelCap))
        val runEarned = s.runEarned + p
        val level = maxOf(s.level, rules.levelFor(runEarned, s.backup.lv(BackupItem.FastBoot)))
        val next = fillDeck(tick(s, now).copy(wallet = s.wallet + p, runEarned = runEarned, level = level, lastSwipeAt = 0,
            returnBurst = true, returnSwipes = 3))
        return next to p
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
        if (s.backup.lv(BackupItem.AutoClean) >= rules.smartAutoLevel || on(s, "search") || on(s, "ai")) return bestTarget(s, now)
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

    /** App price now: half after 앱스토어 세일. */
    fun appPrice(s: GameState, appId: String): BigInteger {
        var p = rules.appPrice(Catalog.app(appId))
        if (on(s, "shopee") && p < rules.appPrice(Catalog.app("shopee"))) p = p * BigInteger.valueOf(9) / BigInteger.TEN  // 쇼피피
        return if (s.saleNext) p / BigInteger.TWO else p
    }

    /** Upgrade price after 글린츠츠 (업무 −10%) and 페이팔팔 (금융 −10%). */
    fun upgradePrice(s: GameState, appId: String): BigInteger {
        val app = Catalog.app(appId)
        var p = rules.upgradePrice(app, s.appLevels[appId] ?: 1)
        if ((on(s, "glints") && app.category == Category.Work) || (on(s, "paypal") && app.category == Category.Money)) p = p * BigInteger.valueOf(9) / BigInteger.TEN
        return p
    }

    /** Unlocks are permanent: the deepest level any run reached (and the reset count) opens apps for good. */
    fun unlocked(s: GameState, appId: String): Boolean {
        val app = Catalog.app(appId)
        return maxOf(s.level, s.backup.bestLevel) >= app.unlockLevel && s.backup.resets >= app.minResets
    }

    fun canBuy(s: GameState, appId: String): Boolean =
        appId !in s.appLevels && unlocked(s, appId) && s.wallet >= appPrice(s, appId)

    /** A bought app joins the deck if a slot is free; otherwise the player swaps it in. */
    fun buy(s: GameState, appId: String): GameState {
        if (!canBuy(s, appId)) return s
        val active = if (s.active.size < maxSlots(s)) s.active + appId else s.active
        val start = if (on(s, "store")) 2 else 1  // 큐글플레이: new apps arrive upgraded once
        return s.copy(wallet = s.wallet - appPrice(s, appId), appLevels = s.appLevels + (appId to start), active = active, saleNext = false,
            boostNext = on(s, "travel") || s.boostNext)
    }

    /** Pro version: ×3 value for an owned app, from its pro level; priced from the level curve. */
    fun buyPro(s: GameState, appId: String): GameState {
        val price = rules.proPrice(appId)
        if (appId !in s.appLevels || appId in s.pro || s.level < Catalog.proLevel.getValue(appId) || s.wallet < price) return s
        return s.copy(wallet = s.wallet - price, pro = s.pro + appId)
    }

    fun upgrade(s: GameState, appId: String): GameState {
        val level = s.appLevels[appId] ?: return s
        val price = upgradePrice(s, appId)
        if (s.wallet < price) return s
        return s.copy(wallet = s.wallet - price, appLevels = s.appLevels + (appId to level + 1))
    }

    /** Bulk upgrade: up to [times] levels (x10 button), stopping when the wallet runs out. Returns the state and levels bought. */
    fun upgradeMany(s: GameState, appId: String, times: Int): Pair<GameState, Int> {
        var cur = s
        var n = 0
        while (n < times) {
            val next = upgrade(cur, appId)
            if (next === cur || next == cur) break
            cur = next; n++
        }
        return cur to n
    }

    /** Max button: as many levels of one app as the wallet allows. */
    fun upgradeMax(s: GameState, appId: String): Pair<GameState, Int> = upgradeMany(s, appId, Int.MAX_VALUE)

    /** Total price of the next [times] levels of an app (what the x10 button shows). */
    fun upgradeCost(s: GameState, appId: String, times: Int): BigInteger {
        var total = ZERO
        var cur = s
        repeat(times) {
            val p = upgradePrice(cur, appId)
            total += p
            cur = cur.copy(appLevels = cur.appLevels + (appId to (cur.appLevels[appId] ?: 1) + 1))
        }
        return total
    }

    /**
     * 모두 강화: spends the wallet on the deck, always buying the cheapest next level among deck apps, so low
     * tiers catch up first. Returns the state and how many levels were bought.
     */
    fun upgradeAll(s: GameState): Pair<GameState, Int> {
        var cur = s
        var n = 0
        while (true) {
            val id = cur.active.minByOrNull { upgradePrice(cur, it) } ?: break
            val next = upgrade(cur, id)
            if (next == cur) break
            cur = next; n++
        }
        return cur to n
    }

    /** 알림훈련: one tap on the drill panel while it runs (+1% income, up to +100%). */
    fun tapDrill(s: GameState, now: Long): GameState =
        if (now < s.drillUntil && s.drillTaps < 100) s.copy(drillTaps = s.drillTaps + 1) else s

    /** 방해 금지: switch on if equipped and used fewer than [RuleSet.dndPerDay] times today. */
    fun activateDnd(s: GameState, now: Long): GameState {
        val today = now / 86_400_000L
        val uses = if (s.dndDay == today) s.dndUses else 0
        if (EventCard.Dnd !in s.equipped || uses >= rules.dndPerDay || now < s.dndUntil) return s
        return s.copy(dndUntil = now + rules.dndMs, dndDay = today, dndUses = uses + 1)
    }

    fun buyEvent(s: GameState, card: EventCard): GameState {
        val price = rules.cardPrice(card)
        if (card in s.events || s.level < card.unlockLevel || s.wallet < price) return s
        val equipped = if (s.equipped.size < eventSlots(s)) s.equipped + card else s.equipped
        return s.copy(wallet = s.wallet - price, events = s.events + card, equipped = equipped)
    }

    fun buyAssist(s: GameState, a: Assist): GameState {
        val owned = s.assist(a)
        val price = rules.assistPrice(a, owned)
        if (s.level < a.unlockLevel || owned >= a.max(s.level) || s.wallet < price) return s
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
