import com.graviton94.notificationcleaner.core.Backup
import com.graviton94.notificationcleaner.core.BackupItem
import com.graviton94.notificationcleaner.core.Cloud
import com.graviton94.notificationcleaner.core.CloudItem
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.RuleSet
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.TEN
import java.math.BigInteger.ZERO

private fun exactPrice(base: Long, growth: Int, owned: Int): BigInteger {
    val denominator = BigInteger.valueOf(1_000).pow(owned)
    val numerator = BigInteger.valueOf(base) * BigInteger.valueOf(growth.toLong()).pow(owned)
    return (numerator + denominator - ONE) / denominator
}

private fun integerRoot(n: BigInteger, degree: Int): BigInteger {
    var low = ZERO
    var high = ONE.shiftLeft((n.bitLength() + degree - 1) / degree)
    while (low < high) {
        val middle = (low + high + ONE).shiftRight(1)
        if (middle.pow(degree) <= n) low = middle else high = middle - ONE
    }
    return low
}

/** Exact extension for the default reset exponents (2/5 early, 1/5 late). */
private fun uncappedFiles(rules: RuleSet, p: BigInteger): BigInteger {
    check(rules.backupExponentPermille == 400)
    val anchor = rules.levelP(rules.resetLevel)
    if (p < anchor) return ZERO
    val transition = TEN.pow(rules.backupSoftcapDigits)
    val base = BigInteger.valueOf(rules.backupBase).pow(5)
    val fifthPower = if (p <= anchor * transition) base * p.pow(2) / anchor.pow(2)
        else base * transition * p / anchor
    return integerRoot(fifthPower, 5)
}

private fun checkLevelTail(rules: RuleSet, output: File) {
    val marks = setOf(124, 399, 400, 401, 450, 500, 1_000)
    var threshold = BigDecimal.valueOf(rules.levelPBase)
    var ratio = rules.levelRatioPermille
    var previous = ZERO
    val rows = sequence {
        for (level in 2..1_000) {
            val exact = threshold.toBigInteger()
            check(exact > previous)
            if (level <= 400) check(exact == rules.levelP(level))
            if (level in marks) yield(listOf(level, rules.levelFor(exact, 0), rules.levelP(level).toString().length,
                exact.toString().length, exact))
            previous = exact
            if (level >= rules.levelLateFrom) ratio += rules.levelRatioStepPermille
            threshold = threshold * BigDecimal.valueOf(ratio.toLong()) / BigDecimal.valueOf(1_000)
        }
    }
    csv(output, listOf("requested_level", "current_level_for_tail_p", "current_threshold_digits", "tail_threshold_digits",
        "tail_threshold_p"), rows)
}

fun growthTailAudit(output: File) {
    output.mkdirs()
    val engine = Engine()
    val rules = engine.rules
    checkLevelTail(rules, File(output, "level-tail.csv"))
    val hardEnd = engine.newGame(1_000_000, 17).copy(level = 400, runEarned = rules.levelP(400))
    check(engine.jackpotRoom(hardEnd, rules.swipeLevelCap).signum() == 0)
    for ((base, growth) in listOf(BackupItem.Value.base to BackupItem.Value.growthPermille,
        CloudItem.SyncValue.base to CloudItem.SyncValue.growthPermille,
        BackupItem.Offline.base to BackupItem.Offline.growthPermille,
        CloudItem.CloudOffline.base to CloudItem.CloudOffline.growthPermille)) {
        var previous = ZERO
        for (level in 0..1_000) {
            val cost = exactPrice(base, growth, level)
            check(cost > previous)
            previous = cost
        }
    }
    val priceRows = sequence {
        for (level in listOf(0, 30, 50, 100, 200, 250, 500, 1_000)) {
            val backup = exactPrice(BackupItem.Value.base, BackupItem.Value.growthPermille, level)
            val cloud = exactPrice(CloudItem.SyncValue.base, CloudItem.SyncValue.growthPermille, level)
            val hoursBackup = exactPrice(BackupItem.Offline.base, BackupItem.Offline.growthPermille, level)
            val hoursCloud = exactPrice(CloudItem.CloudOffline.base, CloudItem.CloudOffline.growthPermille, level)
            val state = hardEnd.copy(backup = Backup(items = mapOf(BackupItem.Value to level)))
            yield(listOf(level, rules.backupPrice(BackupItem.Value, level), engine.backupPrice(state, BackupItem.Value),
                backup, rules.cloudPrice(CloudItem.SyncValue, level), cloud, hoursBackup, hoursCloud, 8 + level.toLong()))
        }
    }
    csv(File(output, "upgrade-tail-prices.csv"), listOf("owned_level", "current_backup_rule_price",
        "current_backup_checkout_price", "exact_backup_tail_price", "current_sync_value_price",
        "exact_sync_value_tail_price", "exact_offline_backup_price", "exact_offline_cloud_price",
        "example_offline_hours_one_rank_track"), priceRows)

    val rewards = sequence {
        check(rules.cloudExponentPermille == 500)
        val anchor = uncappedFiles(rules, rules.levelP(rules.syncLevel))
        val squaredBase = BigInteger.valueOf(rules.cloudBase).pow(2)
        var previous = ZERO
        for (pExponent in listOf(30, 100, 150, 250, 400, 1_000, 2_000)) {
            val p = TEN.pow(pExponent)
            val files = uncappedFiles(rules, p)
            check(files > previous)
            previous = files
            val gb = (squaredBase * files / anchor).sqrt()
            yield(listOf(pExponent, rules.backupTotal(p), files, gb, files.toString().length, gb.toString().length))
        }
    }
    csv(File(output, "prestige-tail.csv"), listOf("run_p_exponent", "current_backup_file_total", "uncapped_files",
        "uncapped_gb_for_cycle_files", "uncapped_file_digits", "uncapped_gb_digits"), rewards)

    val starter = sequence {
        val t0 = 1_000_000L
        for (cloudRank in listOf(0, 1, 2, 4, 8, 16, 30, 60)) for (seed in 1L..5L) {
            var s = engine.newGame(t0, seed, Backup(cloud = Cloud(items = mapOf(CloudItem.SyncValue to cloudRank))))
            var t = t0
            while (true) {
                t += engine.chargeMs(s, t)
                if (t > t0 + 60_000) break
                val row = engine.bestTarget(s, t) ?: continue
                s = engine.swipe(s, row, t)?.state ?: continue
                s = engine.upgradeMax(s, "memo").first
            }
            yield(listOf(cloudRank, seed, BigInteger.TWO.pow(cloudRank), s.appLevels.getValue("memo"),
                s.level, s.runEarned, s.swipes))
        }
    }
    csv(File(output, "starter-reinvestment.csv"), listOf("inherited_cloud_value_rank", "seed", "value_multiplier",
        "memo_level_after_60s", "run_level_after_60s", "run_p_after_60s", "swipes"), starter)
}
