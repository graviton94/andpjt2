import com.graviton94.notificationcleaner.core.AdSlot
import com.graviton94.notificationcleaner.core.BackupItem
import com.graviton94.notificationcleaner.core.Catalog
import com.graviton94.notificationcleaner.core.CloudItem
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.ExOffers
import com.graviton94.notificationcleaner.core.GameState
import com.graviton94.notificationcleaner.core.GrowthOffer
import com.graviton94.notificationcleaner.core.RuleSet
import com.graviton94.notificationcleaner.core.Shop
import com.graviton94.notificationcleaner.core.SpeedPass
import com.graviton94.notificationcleaner.core.SyncCarry
import com.graviton94.notificationcleaner.core.withAccount
import java.io.File
import java.math.BigInteger
import java.util.concurrent.Executors
import kotlin.math.log10

private const val DAY_MS = 86_400_000L
private const val SESSION = 900_000L
private const val START = 1_728_000_000_000L
private val LEVELS = intArrayOf(12, 25, 60, 124)
private val backupItems = BackupItem.entries.filter { it.max == Int.MAX_VALUE }
private val cloudItems = CloudItem.entries.filter { it.max == Int.MAX_VALUE && it.pack == null }

private data class CadenceProfile(val name: String, val ads: Boolean, val pass: Boolean = false,
    val speed: SpeedPass? = null, val offlineDouble: Boolean = true)

private val profiles = listOf(CadenceProfile("Free", false), CadenceProfile("Ads", true), CadenceProfile("Pass", true, true),
    CadenceProfile("PassVisible", true, true, offlineDouble = false), CadenceProfile("Spend30", true, true, SpeedPass.Turbo),
    CadenceProfile("Spend100", true, true, SpeedPass.Ultra))

private enum class Pattern(val sessions: Int, val duration: Long, val clickFloor: Long) {
    Loop(4, SESSION, 12_000), Quarter(1, SESSION, 0), Light45(1, 45_000, 0), Light3(1, 0, 0),
}

private enum class Economy(val fileTail: Int, val gbYield: Int, val gbPrice: Int, val livePayment: Boolean = false, val equip: Boolean = false, val repeatSync: Boolean = false, val inherit: Boolean = false,
    val priceBudget: Int = 0, val carry: SyncCarry? = null, val returnBoost: Int = 0,
    val fileGrowth: Int = 1_000, val pace: Int = 0, val returnMemory: Boolean = false,
    val inheritedPace: Boolean = false, val fileRecordFloor: Boolean = false, val speedWelcome: Int = 0,
    val carryFileBasis: Boolean = false, val rankPower: Int = 1_200) {
    Reference(200, 500, 1_000),
    Moderate(220, 550, 850),
    Reform(240, 600, 800),
    Merit(240, 600, 800, true),
    Focus(240, 600, 800, true, true),
    Replay(240, 600, 800, true, true, true),
    Inherit(240, 600, 800, true, true, true, true),
    SoftReplay(240, 600, 800, true, true, true, priceBudget = 5),
    Linear35(240, 600, 800, true, true, true, priceBudget = 5, carry = SyncCarry.Linear35),
    Hold65(240, 600, 800, true, true, true, priceBudget = 5, carry = SyncCarry.Hold65),
    Hold90(240, 600, 800, true, true, true, priceBudget = 5, carry = SyncCarry.Hold90),
    Return60(240, 600, 800, true, true, true, priceBudget = 5, returnBoost = 60),
    Return120(240, 600, 800, true, true, true, priceBudget = 5, returnBoost = 120),
    ReturnMemory(240, 600, 800, true, true, true, priceBudget = 5, returnBoost = 160, returnMemory = true),
    ReturnGuard(240, 600, 800, true, true, true, priceBudget = 5, returnBoost = 160, returnMemory = true, speedWelcome = 2_000),
    Paced(240, 600, 800, true, true, true, priceBudget = 5, fileGrowth = 3_000, pace = 250),
    FileGrowth(240, 600, 800, true, true, true, priceBudget = 5, fileGrowth = 3_000),
    PriceFollow(240, 600, 800, true, true, true, priceBudget = 5, pace = 250, inheritedPace = true, fileRecordFloor = true),
    PowerFollow(240, 600, 800, true, true, true, priceBudget = 5, pace = 250, inheritedPace = true, fileRecordFloor = true,
        carryFileBasis = true, rankPower = 1_500),
    FileMemory(240, 600, 800, true, true, true, priceBudget = 5, fileRecordFloor = true, carryFileBasis = true),
}

private data class Case(val profile: CadenceProfile, val seed: Int, val owns: Int, val indexed: Boolean) {
    val key = "${profile.name},$seed,$owns,$indexed"
    val filename = key.replace(',', '-')
}

private fun BigInteger.logSafe(): Double {
    if (signum() <= 0) return 0.0
    val digits = toString()
    val prefix = digits.take(15)
    return digits.length - prefix.length + log10(prefix.toDouble())
}

private class Play(val plan: Pattern, val case: Case, val economy: Economy) {
    private val engine = Engine(RuleSet(appRankValuePermille = economy.rankPower, growthOffer = GrowthOffer.Discount,
        frontierPrices = case.indexed, productivePrestige = economy.livePayment, fileTailExponentPermille = economy.fileTail,
        cloudExponentPermille = economy.gbYield, cloudGrowthScalePermille = economy.gbPrice, repeatSync = economy.repeatSync,
        inheritRecordIncome = economy.inherit, frontierBudgetPerDigit = economy.priceBudget, syncCarry = economy.carry,
        exReturnPerSkuPermille = economy.returnBoost, filePriceGrowthScalePermille = economy.fileGrowth,
        appUpgradePacePermille = if (case.indexed) economy.pace else 0, exReturnMemory = economy.returnMemory,
        appUpgradeInheritedPace = economy.inheritedPace, fileShopRecordFloor = economy.fileRecordFloor,
        speedWelcomePermille = economy.speedWelcome, fileShopCarryRecord = economy.carryFileBasis))
    private val shop = Shop(engine)
    private var state = engine.newGame(START, case.seed.toLong()).withAccount {
        it.copy(ownedEx = ExOffers.ids.take(case.owns).toSet())
    }
    private var now = START
    private var activeMs = 0L
    private var runStart = 0L
    private var lastResetMs: Long? = null
    private var lastSyncSession: Int? = null
    private var peak = 1
    private var runId = 1
    private val reached = LongArray(4) { -1L }
    private var sumP = BigInteger.ZERO
    private var sumFiles = BigInteger.ZERO
    private var sumGb = BigInteger.ZERO
    private var offlineMinutes = 0L
    private var exOfflineP = BigInteger.ZERO
    val settles = mutableListOf<String>()
    val runs = mutableListOf<String>()
    val daily = mutableListOf<String>()
    private var day = 0
    private var sessionNo = 0
    private var waits = LongArray(10)

    private fun commit(next: GameState) {
        sumP += (next.runEarned - state.runEarned).max(BigInteger.ZERO)
        state = next
        peak = maxOf(peak, state.level)
        LEVELS.forEachIndexed { n, level ->
            if (reached[n] < 0 && state.level >= level) reached[n] = activeMs - runStart
        }
    }

    private fun ad(slot: AdSlot, offer: BigInteger = BigInteger.ZERO) {
        shop.watchAd(state, slot, now, offer)?.let(::commit)
    }

    private fun visit(begin: Long, slot: Int) {
        sessionNo++
        if (case.profile.pass && !shop.passActive(state, begin)) {
            val speed = case.profile.speed
            commit(if (speed == null) shop.buyPass(state, begin) else shop.buySpeedPass(state, begin, speed))
        }
        if (now < begin) {
            val result = engine.settleOfflineWithBonus(state, begin - now, begin)
            commit(result.state)
            now = begin
            offlineMinutes += (result.eligibleMs - result.baseEligibleMs) / 60_000
            exOfflineP += result.exExtraP
            if (case.profile.ads && case.profile.offlineDouble && result.paid.signum() > 0)
                ad(AdSlot.OfflineDouble, result.paid)
        }
        if (slot == 0) {
            commit(shop.claimPassDaily(shop.claimAttendance(state, now), now))
            if (case.profile.ads) {
                ad(AdSlot.Boost)
                ad(AdSlot.Warp)
                ad(AdSlot.Overclock)
                ad(if (case.profile.pass) AdSlot.FreeUpgrade else AdSlot.CreditBox)
            }
        }
        val end = now + plan.duration
        var moves = 0
        while (true) {
            val clickFloor = if (state.swipes < 8) 0 else plan.clickFloor
            val dt = maxOf(clickFloor, engine.chargeMs(state, now))
            if (plan == Pattern.Light3 && moves == 3 || plan != Pattern.Light3 && now + dt > end) break
            now += dt
            activeMs += dt
            moves++
            val tick = engine.tick(state, now)
            val target = engine.autoTarget(tick, now)
            val result = target?.let { engine.swipe(tick, it, now) }
            commit(result?.state ?: tick)
            if (plan != Pattern.Light3 && (plan != Pattern.Loop || moves % 4 == 1 || result?.beats?.any {
                    it is com.graviton94.notificationcleaner.core.Beat.LevelUp
                } == true)) {
                val app = Catalog.apps.asSequence().filter { engine.canBuy(state, it.id) }.maxByOrNull { it.tier }
                if (app != null) {
                    commit(engine.buy(state, app.id))
                    if (economy.equip) equipHighest()
                }
            }
            val app = state.active.minByOrNull { engine.upgradePrice(state, it) }
            if (app != null) {
                val next = engine.upgrade(state, app)
                if (next !== state) {
                    samplePurchase(app)
                    commit(next)
                }
                if (moves % 4 == 1 || plan == Pattern.Light3) samplePrice(app)
            }
        }
        // Idle within a timed visit counts as active session time, even when a last swipe cannot recharge.
        if (now < end) {
            activeMs += end - now
            now = end
        }
        prestige()
    }

    private fun equipHighest() {
        val proposed = state.appLevels.entries.map { (id, level) ->
            val (numerator, denominator) = engine.rules.baseValue(Catalog.app(id), level, state.backup)
            id to (numerator / denominator)
        }.sortedByDescending { it.second }.take(engine.rules.maxSlots(state.level, state.backup)).map { it.first }
        engine.setDeck(state, proposed)?.let(::commit)
    }

    private fun samplePurchase(app: String) {
        if (state.earnRate.signum() <= 0) return
        val opening = activeMs - runStart <= 600_000
        val i = if (opening) 6 else 8
        val point = engine.upgradePrice(state, app).logSafe() - state.earnRate.logSafe()
        waits[i]++
        if (if (opening) point in log10(.05)..0.0 else point in 0.0..1.0) waits[i + 1]++
    }

    private fun samplePrice(app: String) {
        if (state.earnRate.signum() <= 0) return
        val quote = engine.upgradePrice(state, app)
        val point = quote.logSafe() - state.earnRate.logSafe()
        val opening = activeMs - runStart <= 600_000
        val i = if (opening) 0 else 2
        waits[i]++
        if (if (opening) point in log10(.05)..0.0 else point in 0.0..1.0) waits[i+1]++
        if (point > 1) waits[4]++
        if ((state.frontierPricePermille ?: BigInteger.ZERO) > BigInteger.valueOf(1_000) &&
            (state.appLevels[app] ?: 1) > maxOf(25, state.backup.qualifiedAppPeaks[app] ?: 1)) waits[5]++
    }

    /** Measure what this payment alone affords using the unlocked repeating ranks and identical prices. */
    private fun spendFiles(start: GameState, budget: BigInteger, limit: Int): Pair<GameState, Int> {
        var s = start.copy(backup = start.backup.copy(files = budget))
        var count = 0
        while (count < limit) {
            val cheapest = backupItems.filter { engine.backupOpen(s, it) }.minByOrNull { engine.backupPrice(s, it) } ?: break
            val value = BackupItem.Value
            val item = if (limit < 12 && engine.backupOpen(s, value) &&
                engine.backupPrice(s, value) <= engine.backupPrice(s, cheapest) * BigInteger.TWO &&
                engine.backupPrice(s, value) <= s.backup.files) value else cheapest
            val next = engine.buyBackup(s, item)
            if (next === s) break
            s = next
            count++
        }
        return s to count
    }

    private fun spendGb(start: GameState, budget: BigInteger, limit: Int): Pair<GameState, Int> {
        var s = start.copy(backup = start.backup.copy(cloud = start.backup.cloud.copy(gb = budget)))
        var count = 0
        while (count < limit) {
            val cheapest = cloudItems.filter { engine.cloudOpen(s, it) }.minByOrNull { engine.cloudPrice(s, it) } ?: break
            val value = CloudItem.SyncValue
            val item = if (limit < 12 && engine.cloudOpen(s, value) &&
                engine.cloudPrice(s, value) <= engine.cloudPrice(s, cheapest) * BigInteger.TWO &&
                engine.cloudPrice(s, value) <= s.backup.cloud.gb) value else cheapest
            val next = engine.buyCloud(s, item)
            if (next === s) break
            s = next
            count++
        }
        return s to count
    }

    private fun prestige() {
        val old = state
        val sinceSync = sessionNo - (lastSyncSession ?: 0)
        val sinceReset = activeMs - (lastResetMs ?: 0L)
        // A paid reset/sync needs a strictly positive new entitlement in the core.
        // Keeping the best of the current run is a player policy, not an engine lockout.
        val maySync = if (plan == Pattern.Loop) sinceSync >= 2 else sinceSync >= 10 && old.backup.resets >= 4
        if (maySync && engine.canSync(old)) {
            val gb = engine.syncPaid(old, now, true)
            if (gb.signum() > 0) {
                val next = checkNotNull(engine.sync(old, now))
                val affordability = spendGb(next, gb, 12).second
                val deepEnough = !economy.repeatSync || old.level >= engine.rules.syncLevel
                if (plan != Pattern.Loop || affordability >= 2 && deepEnough || sinceSync >= 4) {
                    val previous = lastSyncSession
                    val (new, actual) = spendGb(next, next.backup.cloud.gb, 5)
                    sumGb += gb
                    lastSyncSession = sessionNo
                    settle("sync", new, gb, affordability, actual, previous?.let { sessionNo - it }, null)
                    return
                }
            }
        }
        val mayReset = if (plan == Pattern.Loop) sinceReset >= SESSION else day - (lastResetDay ?: -2) >= 2
        if (mayReset && old.level >= engine.rules.resetLevel) {
            val files = engine.factoryResetPaid(old, now, true)
            if (files.signum() > 0) {
                val next = checkNotNull(engine.factoryReset(old, now))
                val affordability = spendFiles(next, files, 12).second
                val newRecord = old.runEarned > old.backup.bestRunP && old.level >= old.backup.bestLevel
                if (plan != Pattern.Loop || affordability >= 3 && newRecord || sinceReset >= 3 * SESSION) {
                    val previous = lastResetMs
                    val (new, actual) = spendFiles(next, next.backup.files, 8)
                    sumFiles += files
                    lastResetMs = activeMs
                    lastResetDay = day
                    settle("reset", new, files, affordability, actual, null, previous?.let { activeMs - it })
                }
            }
        }
    }

    private var lastResetDay: Int? = null

    private fun settle(kind: String, next: GameState, paid: BigInteger, affordable: Int, actual: Int,
        intervalSessions: Int?, intervalMs: Long?) {
        settles += listOf(case.key, day, sessionNo, kind, state.level, activeMs, intervalMs ?: "",
            intervalSessions ?: "", paid.logSafe(), affordable, actual).joinToString(",")
        appendRun(kind)
        state = next
        runId++
        runStart = activeMs
        reached.fill(-1L)
    }

    private fun appendRun(kind: String) {
        reached.forEachIndexed { i, elapsed ->
            if (elapsed >= 0) runs += listOf(case.key, day, runId, kind, LEVELS[i], elapsed).joinToString(",")
        }
    }

    fun run(days: Int) {
        for (d in 1..days) {
            day = d
            waits = LongArray(10)
            for (slot in 0 until plan.sessions) visit(START + (day - 1) * DAY_MS + slot * 14_400_000, slot)
            daily += (listOf(case.key, day, sumP.logSafe(), state.wallet.logSafe(), sumFiles.logSafe(), sumGb.logSafe(),
                peak, state.level, runId, activeMs, offlineMinutes, exOfflineP.logSafe()) + waits.toList()).joinToString(",")
        }
        appendRun("observed")
    }
}

object CadenceProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args[0])
        val pattern = Pattern.valueOf(args[1])
        val economy = Economy.valueOf(args[2])
        val seeds = args[3].toInt()
        val days = args[4].toInt()
        val threads = args[5].toInt()
        val firstSeed = args.getOrNull(6)?.toInt() ?: 1
        val selected = args.getOrNull(7)?.split(',') ?: profiles.map { it.name }
        require(seeds > 0 && days > 0 && threads > 0 && selected.all { name -> profiles.any { it.name == name } })
        val arms = if (pattern != Pattern.Loop) listOf(0 to true, 3 to true, 9 to true)
            else if (args.getOrNull(8) == "frontier-only") listOf(0 to true)
            else listOf(0 to false, 0 to true)
        val cases = profiles.filter { it.name in selected }.flatMap { profile ->
            (firstSeed until firstSeed + seeds).flatMap { seed -> arms.map { (owns, indexed) -> Case(profile, seed, owns, indexed) } }
        }
        val rooted = File(out, "cases")
        rooted.mkdirs()
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = cases.map { c ->
                pool.submit {
                    val folder = File(rooted, c.filename)
                    if (File(folder, "done").exists()) return@submit
                    folder.mkdirs()
                    val play = Play(pattern, c, economy)
                    play.run(days)
                    mapOf("daily" to play.daily, "prestige" to play.settles, "reaches" to play.runs).forEach { (name, rows) ->
                        File(folder, "$name.csv").writeText(rows.joinToString("\n", postfix = if (rows.isEmpty()) "" else "\n"))
                    }
                    File(folder, "done").writeText("ok\n")
                    println(c.key)
                }
            }
            tasks.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
        val base = "profile,seed,sku,indexed,"
        val tables = mapOf(
            "daily" to "day,log_total_p,log_wallet,log_files,log_gb,peak,level,run,active_ms,extra_offline_min,log_extra_offline_p,open_obs,open_hits,late_obs,late_hits,over10,index_exposed,open_bought,open_bought_in_band,late_bought,late_bought_in_band",
            "prestige" to "day,session,kind,level,active_ms,gap_ms,gap_sessions,log_paid,affordable_until12,bought",
            "reaches" to "day,run,end_kind,level,elapsed_ms",
        )
        tables.forEach { (name, header) ->
            File(out, "$name.csv").bufferedWriter().use { writer ->
                writer.appendLine(base + header)
                cases.forEach { c -> File(rooted, "${c.filename}/$name.csv").bufferedReader().useLines { lines ->
                    lines.forEach(writer::appendLine)
                } }
            }
        }
    }
}
