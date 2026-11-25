import com.graviton94.notificationcleaner.core.Backup
import com.graviton94.notificationcleaner.core.BackupItem
import com.graviton94.notificationcleaner.core.Cloud
import com.graviton94.notificationcleaner.core.CloudItem
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.GameState
import com.graviton94.notificationcleaner.core.RuleSet
import com.graviton94.notificationcleaner.core.Shop
import com.graviton94.notificationcleaner.core.SpeedPass
import com.graviton94.notificationcleaner.core.account
import com.graviton94.notificationcleaner.core.withAccount
import java.io.File
import java.math.BigInteger

private const val MINUTE_MS = 60_000L
private const val AUDIT_NOW = 1_728_000_000_000L
private val RATE = BigInteger.valueOf(100_000L)
private val BPS = BigInteger.valueOf(10_000L)

private data class CapacityCase(val name: String, val level: Int, val backupRank: Int, val cloudRank: Int, val sets: Int)

/**
 * Call the shared settlement with an eligible window. Translate only the overclock expiry so
 * the paid window begins at departure, while pass/ad eligibility still uses the return time.
 */
private fun limitedSettlement(engine: Engine, state: GameState, away: Long, cap: Long): BigInteger {
    val eligible = minOf(away, cap)
    val clipped = away - eligible
    val bounded = state.withAccount { account ->
        if (account.overclockUntil > 0) account.copy(overclockUntil = account.overclockUntil + clipped) else account
    }
    return engine.settleOfflineWithBonus(bounded, eligible, AUDIT_NOW).paid
}

fun offlineUpgradeAudit(output: File) {
    output.mkdirs()
    val rules = RuleSet()
    val engine = Engine(rules)
    val windowEngine = Engine(rules.copy(offlineCapSteps = listOf(1 to 48)))
    val shop = Shop(engine)
    val configurations = listOf(
        CapacityCase("base_8", 1, 0, 0, 0),
        CapacityCase("backup_1", 1, 1, 0, 1),
        CapacityCase("backup_2", 1, 2, 0, 3),
        CapacityCase("mixed_4", 35, 3, 1, 2),
        CapacityCase("max_14", 35, 10, 4, 3),
    )
    val profiles = listOf("free", "pass", "ultra", "overclock_12h")
    val rows = sequence {
        for (plan in configurations) for (profile in profiles) for (awayMinutes in listOf(360, 705, 720, 960, 1680, 2160)) {
            val away = awayMinutes * MINUTE_MS
            val accountState = engine.newGame(AUDIT_NOW, 17, Backup(items = mapOf(BackupItem.Offline to plan.backupRank),
                cloud = Cloud(items = mapOf(CloudItem.CloudOffline to plan.cloudRank))))
                .copy(level = plan.level, runEarned = rules.levelP(plan.level), earnRate = RATE)
            val state = when (profile) {
                "pass" -> shop.buyPass(accountState, AUDIT_NOW - MINUTE_MS)
                "ultra" -> shop.buySpeedPass(accountState, AUDIT_NOW - MINUTE_MS, SpeedPass.Ultra)
                "overclock_12h" -> accountState.withAccount { it.copy(overclockUntil = AUDIT_NOW - away + 720 * MINUTE_MS) }
                else -> accountState
            }
            val cap = engine.offlineCapMs(state)
            val exMinutes = 2 * plan.sets * (plan.backupRank + plan.cloudRank)
            val exCap = cap + exMinutes * MINUTE_MS
            val neutral = limitedSettlement(windowEngine, state, away, cap)
            val current = engine.settleOfflineWithBonus(state, away, AUDIT_NOW).paid
            val enriched = state.copy(earnRate = RATE * BigInteger.valueOf(10_800L) / BPS)
            val exRaw = if (plan.sets == 0) neutral else limitedSettlement(windowEngine, enriched, away, exCap)
            val limit = neutral * BigInteger.valueOf(11_000L) / BPS
            val paid = exRaw.min(limit)
            check(paid >= neutral && paid <= limit)
            check(neutral <= current)
            if (profile != "overclock_12h" || away <= cap) check(neutral == current)
            yield(listOf(plan.name, profile, plan.level, plan.backupRank, plan.cloudRank, plan.sets, awayMinutes,
                cap / MINUTE_MS, exMinutes, exCap / MINUTE_MS, minOf(away, cap) / MINUTE_MS,
                minOf(away, exCap) / MINUTE_MS, state.account.overclockUntil, current, neutral,
                exRaw, paid, paid - neutral, exRaw > limit))
        }
    }
    csv(File(output, "offline-settlements.csv"), listOf("stage", "profile", "level", "backup_rank", "cloud_rank",
        "sets", "away_minutes", "base_cap_minutes", "ex_extra_cap_minutes", "ex_cap_minutes", "base_eligible_minutes",
        "ex_eligible_minutes", "overclock_until", "current_paid_p", "capped_window_base_p", "ex_p_before_budget",
        "ex_p_after_budget", "ex_extra_p", "ex_budget_clipped"), rows)
    val edge = engine.newGame(AUDIT_NOW, 1).copy(level = 35, runEarned = rules.levelP(35))
    val day = 24 * 60 * MINUTE_MS
    check(limitedSettlement(windowEngine, edge, day, day) == BigInteger.ZERO)
    val saturated = edge.copy(earnRate = BigInteger.TEN.pow(50))
    val room = engine.jackpotRoom(edge, rules.offlineLevelCap)
    check(limitedSettlement(windowEngine, saturated, day, engine.offlineCapMs(edge)) == room)
    check(limitedSettlement(windowEngine, saturated, day, day) == room)
    verifyOfflineRanks(engine, File(output, "offline-upgrade-ranks.csv"))
}

private fun verifyOfflineRanks(engine: Engine, output: File) {
    val rules = engine.rules
    var state = engine.newGame(AUDIT_NOW, 17, Backup(files = 1_000_000, resets = 1,
        cloud = Cloud(syncs = 4, gb = 1_000_000))).copy(level = 35, runEarned = rules.levelP(35))
    fun row(action: String, cost: Long = 0) = listOf(action, cost, state.level, state.backup.lv(BackupItem.Offline),
        state.backup.cloud.lv(CloudItem.CloudOffline), engine.offlineCapMs(state) / MINUTE_MS, engine.offlinePercent(state))
    val rows = sequence {
        yield(row("start"))
        repeat(BackupItem.Offline.max) {
            val cap = engine.offlineCapMs(state)
            val left = state.backup.files
            state = engine.buyBackup(state, BackupItem.Offline)
            check(engine.offlineCapMs(state) - cap == 60 * MINUTE_MS)
            yield(row("backup_upgrade", left - state.backup.files))
        }
        repeat(CloudItem.CloudOffline.max) {
            val cap = engine.offlineCapMs(state)
            val left = state.backup.cloud.gb
            state = engine.buyCloud(state, CloudItem.CloudOffline)
            check(engine.offlineCapMs(state) - cap == 60 * MINUTE_MS)
            yield(row("cloud_upgrade", left - state.backup.cloud.gb))
        }
        check(engine.offlineCapMs(state) == 26 * 60 * MINUTE_MS)
        state = checkNotNull(engine.factoryReset(state, AUDIT_NOW))
        check(state.backup.lv(BackupItem.Offline) == BackupItem.Offline.max)
        check(state.backup.cloud.lv(CloudItem.CloudOffline) == CloudItem.CloudOffline.max)
        yield(row("factory_reset"))
        state = checkNotNull(engine.sync(state.copy(level = rules.syncLevel, runEarned = rules.levelP(rules.syncLevel)), AUDIT_NOW))
        check(state.backup.lv(BackupItem.Offline) == 0)
        check(state.backup.cloud.lv(CloudItem.CloudOffline) == CloudItem.CloudOffline.max)
        yield(row("sync"))
    }
    csv(output, listOf("action", "cost_in_own_currency", "level", "backup_rank", "cloud_rank", "cap_minutes",
        "efficiency_percent"), rows)
}
