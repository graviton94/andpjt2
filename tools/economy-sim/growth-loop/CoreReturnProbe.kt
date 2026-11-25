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
import com.graviton94.notificationcleaner.core.withAccount
import java.io.File
import java.math.BigInteger
import java.util.concurrent.Executors

private const val DAY = 86_400_000L
private const val FIRST_DAY = 1_728_000_000_000L
private val markers = setOf(7, 14, 28, 56, 84, 112, 140, 168)

private enum class Route { Reset, Sync }

private data class Usage(val name: String, val rewards: Boolean, val pass: Boolean,
    val speed: SpeedPass? = null, val double: Boolean = true)

private val users = listOf(Usage("Free", false, false), Usage("Ads", true, false),
    Usage("Pass", true, true), Usage("PassVisible", true, true, double = false),
    Usage("Spend30", true, true, SpeedPass.Turbo), Usage("Spend100", true, true, SpeedPass.Ultra))

private data class Result(val rows: List<String>, val route: Map<Int, Route>)

private fun digits(p: BigInteger): Double {
    if (p.signum() == 0) return 0.0
    val s = p.toString()
    val prefix = s.take(15)
    return s.length - prefix.length + StrictMath.log10(prefix.toDouble())
}

private fun oneLoginPerDay(user: Usage, seed: Long, offer: GrowthOffer, owns: Int, days: Int, activeMs: Long,
    fixed: Map<Int, Route>? = null, telemetry: Boolean = false, indexPrices: Boolean = false): Result {
    val engine = Engine(RuleSet(appRankValuePermille = 1_200, growthOffer = offer, indexUpgradePricesOnReset = indexPrices))
    val shop = Shop(engine)
    var state = engine.newGame(FIRST_DAY, seed).withAccount { it.copy(ownedEx = ExOffers.ids.take(owns).toSet()) }
    var lastActive = FIRST_DAY
    var lastReset = -2
    var lastSync = -10
    var totalP = BigInteger.ZERO
    var files = BigInteger.ZERO
    var gb = BigInteger.ZERO
    var peak = 1
    var skipped = 0
    var runActiveMs = 0L
    val results = mutableListOf<String>()
    val route = mutableMapOf<Int, Route>()

    fun step(next: GameState) {
        totalP += (next.runEarned - state.runEarned).max(BigInteger.ZERO)
        peak = maxOf(peak, next.level)
        state = next
    }
    fun reward(slot: AdSlot, now: Long, p: BigInteger = BigInteger.ZERO) {
        shop.watchAd(state, slot, now, p)?.let { step(it) }
    }

    for (day in 1..days) {
        var now = FIRST_DAY + (day - 1) * DAY
        val priceTimes = LongArray(5)
        fun observePrice(app: String) {
            if (state.earnRate.signum() == 0) return
            val wait = digits(engine.upgradePrice(state, app)) - digits(state.earnRate)
            val opening = runActiveMs <= 600_000L
            val index = if (opening) 0 else 2
            priceTimes[index]++
            if (if (opening) wait in StrictMath.log10(.05)..0.0 else wait in 0.0..1.0) priceTimes[index + 1]++
            if (wait > 1.0) priceTimes[4]++
        }
        if (user.pass && !shop.passActive(state, now)) {
            val speed = user.speed
            step(if (speed == null) shop.buyPass(state, now) else shop.buySpeedPass(state, now, speed))
        }
        if (day > 1) {
            val offline = engine.settleOfflineWithBonus(state, now - lastActive, now)
            step(offline.state)
            if (user.rewards && user.double) reward(AdSlot.OfflineDouble, now, offline.paid)
        }
        step(shop.claimPassDaily(shop.claimAttendance(state, now), now))
        if (user.rewards) {
            reward(AdSlot.Boost, now)
            reward(AdSlot.Warp, now)
            reward(AdSlot.Overclock, now)
            reward(if (user.pass) AdSlot.FreeUpgrade else AdSlot.CreditBox, now)
        }
        // Actual engine swipes for an offline-heavy return pattern.
        val end = now + activeMs
        var moves = 0
        while (if (activeMs == 0L) moves < 3 else now + engine.chargeMs(state, now) <= end) {
            val elapsed = engine.chargeMs(state, now)
            runActiveMs += elapsed
            now += elapsed
            moves++
            val tick = engine.tick(state, now)
            val target = engine.autoTarget(tick, now)
            val result = target?.let { engine.swipe(tick, it, now) }
            if (result != null) step(result.state) else step(tick)
            if (activeMs > 0) {
                val app = Catalog.apps.filter { engine.canBuy(state, it.id) }.maxByOrNull { it.tier }
                if (app != null) step(engine.buy(state, app.id))
            }
            val cheapest = state.active.minByOrNull { engine.upgradePrice(state, it) }
            if (cheapest != null) {
                step(engine.upgrade(state, cheapest))
                if (telemetry) observePrice(cheapest)
            }
        }
        lastActive = now
        val fileEntitlement = engine.backupFilesNow(state)
        val gbEntitlement = engine.cloudGbNow(state)
        val candidate = if (fixed != null) fixed[day] else when {
            gbEntitlement.signum() > 0 && state.backup.resets >= 4 && day - lastSync >= 10 -> Route.Sync
            fileEntitlement.signum() > 0 && day - lastReset >= 2 -> Route.Reset
            else -> null
        }
        if (candidate == Route.Reset && fileEntitlement.signum() > 0) {
            val before = state.backup.files
            state = checkNotNull(engine.factoryReset(state, now))
            files += state.backup.files - before
            route[day] = Route.Reset
            runActiveMs = 0
            lastReset = day
            repeat(4) {
                val item = listOf(BackupItem.Value, BackupItem.Offline, BackupItem.ChainBoost)
                    .filter { engine.backupOpen(state, it) }.minByOrNull { engine.backupPrice(state, it) }
                if (item != null) state = engine.buyBackup(state, item)
            }
        } else if (candidate == Route.Sync && gbEntitlement.signum() > 0) {
            val before = state.backup.cloud.gb
            state = checkNotNull(engine.sync(state, now))
            gb += state.backup.cloud.gb - before
            route[day] = Route.Sync
            runActiveMs = 0
            lastSync = day
            repeat(4) {
                val item = listOf(CloudItem.SyncValue, CloudItem.Compression, CloudItem.CloudOffline)
                    .filter { engine.cloudOpen(state, it) }.minByOrNull { engine.cloudPrice(state, it) }
                if (item != null) state = engine.buyCloud(state, item)
            }
        } else if (candidate != null) {
            skipped++
        }
        if (telemetry || day in markers) {
            val core = listOf(day, digits(totalP), digits(state.wallet), digits(files), digits(gb), peak, route.size + 1, skipped)
            results += (if (telemetry) core + priceTimes.toList() else core).joinToString(",")
        }
    }
    return Result(results, route)
}

object CoreReturnProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val output = File(args[0])
        val seeds = args.getOrNull(1)?.toInt() ?: 30
        val days = args.getOrNull(2)?.toInt() ?: 168
        val pool = Executors.newFixedThreadPool(args.getOrNull(3)?.toInt() ?: 4)
        val activeMs = (args.getOrNull(4)?.toLong() ?: 45) * 1_000
        val firstSeed = args.getOrNull(5)?.toInt() ?: 1
        val telemetry = args.getOrNull(6)?.toBooleanStrict() ?: false
        val indexPrices = args.getOrNull(7)?.toBooleanStrict() ?: false
        require(seeds > 0 && days > 0 && activeMs >= 0 && firstSeed > 0)
        val paths = users.flatMap { user -> (firstSeed until firstSeed + seeds).map { seed -> user to seed } }
        try {
            val futures = paths.map { (user, seed) ->
                pool.submit<List<String>> {
                    val before = oneLoginPerDay(user, seed.toLong(), GrowthOffer.Prestige, 0, days, activeMs,
                        telemetry = telemetry, indexPrices = indexPrices)
                    val base = before.rows.map { "${user.name},${seed},natural,none,0,$it" }
                    base + listOf("natural", "fixed").flatMap { policy ->
                        GrowthOffer.entries.flatMap { offer ->
                            listOf(3, 9).flatMap { owned ->
                                oneLoginPerDay(user, seed.toLong(), offer, owned, days, activeMs,
                                    if (policy == "fixed") before.route else null, telemetry, indexPrices).rows.map {
                                    "${user.name},${seed},$policy,${offer.name.lowercase()},$owned,$it"
                                }
                            }
                        }
                    }
                }
            }
            output.parentFile?.mkdirs()
            output.bufferedWriter().use { w ->
                val extras = if (telemetry) ",p_opening_samples,p_opening_in_range,p_late_samples,p_late_in_range,p_over_10min" else ""
                w.appendLine("profile,seed,policy,model,sku,day,log_total_p,log_wallet_p,log_total_files,log_total_gb,peak_lv,run,skipped_prestige$extras")
                futures.forEachIndexed { i, future ->
                    future.get().forEach { w.appendLine(it) }
                    if ((i+1) % seeds == 0) println(paths[i].first.name)
                }
            }
        } finally {
            pool.shutdown()
        }
    }
}
