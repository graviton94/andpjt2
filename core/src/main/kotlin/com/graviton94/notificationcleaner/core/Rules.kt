package com.graviton94.notificationcleaner.core

import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.TEN

private fun big(x: Long): BigInteger = BigInteger.valueOf(x)

/**
 * Every tunable number of the core rules. The engine reads only this; a rules change is a new [version].
 * Economy values are drafts until the economy simulator (economy v0.2) fixes them.
 */
data class RuleSet(
    val version: Int = 3,
    // Board
    val baseRows: Int = 7,
    val maxRows: Int = 12,
    val threshold: Int = 4,
    val countWeights: List<Int> = listOf(1, 2, 1),
    val sandwichPercent: Int = 35,
    /** Extra sandwich chance per active app above four: a bigger deck still gets proposals. */
    val sandwichPerExtraApp: Int = 2,
    /** One extra board row per this many active apps above four. */
    val appsPerExtraRow: Int = 2,
    val sandwichPerAssist: Int = 3,
    val wildPerAssist: Int = 2,
    // Deck: level → slots. A deck must hold at least min(minSlots, owned) apps.
    val maxSlotSteps: List<Pair<Int, Int>> = listOf(1 to 3, 3 to 4, 8 to 5, 20 to 6, 30 to 7, 40 to 8, 50 to 9, 73 to 10),
    val minSlotSteps: List<Pair<Int, Int>> = listOf(1 to 3, 10 to 4, 20 to 5, 30 to 6, 40 to 7, 62 to 8),
    val eventSlotSteps: List<Pair<Int, Int>> = listOf(6 to 1, 14 to 2, 38 to 3, 63 to 4),
    // Synergy: same category / region in the deck → bonus % for those apps
    // Synergy (research A·B plan S3): same category / region in the deck → bonus % for those apps,
    // and every active set (2+ apps) multiplies all values by diversityPermille‰ once more.
    val categorySynergySteps: List<Pair<Int, Int>> = listOf(2 to 25, 3 to 60, 4 to 110, 5 to 180, 6 to 270),
    val regionSynergySteps: List<Pair<Int, Int>> = listOf(2 to 20, 3 to 50, 4 to 95, 5 to 155, 6 to 230),
    val diversityPermille: Int = 1_220,
    /** Bonus % per digit of cumulative notifications cleared in a region / category. */
    val regionDigitPercent: Int = 10,
    val categoryDigitPercent: Int = 12,
    // Battery and auto
    val energyCap: Int = 12,
    val chargeMs: Long = 3_000,
    val autoIntervalMs: Long = 4_500,
    /** AutoClean backup level at which auto starts looking one move ahead. */
    val smartAutoLevel: Int = 5,
    // Overload and igniters
    val gaugePerMerge: Int = 4,
    val gaugePerBurst: Int = 12,
    /** Extra gauge for a burst per tier it sits below the deck's best app: low tiers ignite. */
    val igniterGaugePerTier: Int = 4,
    val waveFactor: Int = 3,
    // Interruptions (base rules, not cards)
    val spamEverySwipes: Int = 11,
    val spamCopyAfterSwipes: Int = 3,
    val callEverySwipes: Int = 23,
    val callUnlockLevel: Int = 2,
    val callPauseMs: Long = 5_000,
    val otpLifeSwipes: Int = 3,
    // Value
    val tierBase: Long = 10,
    val levelMilestones: List<Int> = listOf(10, 25, 50, 100, 200, 300, 400, 500),
    val appPriceBase: Long = 100,
    val appPriceTierRatio: Long = 60,
    val appPriceOrderPermille: List<Int> = listOf(1_000, 1_600, 2_500, 4_000),
    val upgradeBase: Long = 25,
    val upgradeGrowthPermille: Int = 1_300,
    // Progress
    val resetLevel: Int = 25,
    /**
     * Level is the run's cumulative P: reaching level L needs levelPBase × ratio^(L−2), the ratio rising by
     * [levelRatioStepPermille] every level after [levelLateFrom]. Leveling is exponential in P, so backup-file
     * multipliers speed it up directly.
     */
    val levelPBase: Long = 30,
    val levelRatioPermille: Int = 2_500,
    val levelLateFrom: Int = 25,
    val levelRatioStepPermille: Int = 5,
    /** Every backup file ever earned adds this % to all notification values. Compounds across resets. */
    val lifetimeFilePercent: Int = 5,
    /** Passive bonus = (1 + 5% × lifetime files)^0.85: compounding, but tapering (research A3). */
    val lifetimeExponentPermille: Int = 850,
    // Offline settlement
    /** Offline pays the auto-clean rate (≈ this ‰ of the direct-play rate) × efficiency, up to the cap (research E1). */
    val offlineAutoPermille: Int = 400,
    val offlineEfficiencyPercent: Int = 10,
    val offlineCapSteps: List<Pair<Int, Int>> = listOf(1 to 8, 7 to 10, 35 to 12),
    /** Content priced from the level curve: a pro version costs this ‰ of the P its level needs. */
    val proPricePermille: Int = 1_000,
    val cardPricePermille: Int = 500,
    /** Value multiplier of a pro version. */
    val proMultiplier: Long = 3,
    /** Backup files: cube root of (run P ÷ 10^this). */
    val backupPDigits: Int = 12,
    /** Backup files total = ⌊10 × (best run P ÷ 10^12)^0.35⌋; beyond 10^(12+softcap) the exponent halves. Paid as the delta over files already earned (research A2). */
    val backupExponentPermille: Int = 350,
    val backupSoftcapDigits: Int = 12,
) {
    fun step(steps: List<Pair<Int, Int>>, level: Int): Int = steps.lastOrNull { level >= it.first }?.second ?: 0

    fun maxSlots(level: Int, backup: Backup) = step(maxSlotSteps, level) + backup.lv(BackupItem.ExtraSlot)
    fun minSlots(level: Int) = step(minSlotSteps, level)
    fun eventSlots(level: Int, backup: Backup) = step(eventSlotSteps, level) + if (level >= 6) backup.lv(BackupItem.EventSlot) else 0

    /** XP to leave [level], in hundredths of a point. ⌈5 × L^1.4 × 1.09^(L−1)⌉ points. */
    private val levelTable: List<BigInteger> by lazy {
        val t = mutableListOf(BigInteger.ZERO, BigInteger.ZERO) // index = level; level 1 is free
        var q = java.math.BigDecimal(levelPBase)
        var ratio = levelRatioPermille
        for (l in 2..400) {
            t += q.toBigInteger()
            if (l >= levelLateFrom) ratio += levelRatioStepPermille
            q = q * java.math.BigDecimal(ratio) / java.math.BigDecimal(1_000)
        }
        t
    }

    /** Cumulative run P needed to reach [level], before the FastBoot discount. */
    fun levelP(level: Int): BigInteger = levelTable[level.coerceIn(1, levelTable.lastIndex)]

    /** Level for [runEarned]; FastBoot lowers every threshold by 10% per step (multiplicative). */
    fun levelFor(runEarned: BigInteger, fastBoot: Int): Int {
        val boosted = runEarned * big(10).pow(fastBoot) / big(9).pow(fastBoot)
        var l = 1
        while (l + 1 < levelTable.size && boosted >= levelTable[l + 1]) l++
        return l
    }

    fun synergyPercent(count: Int, backup: Backup, steps: List<Pair<Int, Int>>): Int {
        val base = step(steps, count)
        return base * (100 + 10 * backup.lv(BackupItem.Synergy)) / 100
    }

    /** One notification before deck multipliers: 10^(tier−1) × level × (2 + 0.1k)^milestones × 1.25^value. */
    fun baseValue(app: AppDef, level: Int, backup: Backup): Pair<BigInteger, BigInteger> {
        val m = levelMilestones.count { level >= it }
        val k = backup.lv(BackupItem.Milestone).toLong()
        val v = backup.lv(BackupItem.Value)
        val passive = Math.round(Math.pow(1.0 + lifetimeFilePercent / 100.0 * backup.lifetime, lifetimeExponentPermille / 1000.0) * 1e6)
        val num = big(tierBase).pow(app.tier - 1) * big(level.toLong()) * big(20 + k).pow(m) * big(5).pow(v) * big(passive)
        val den = TEN.pow(m) * big(4).pow(v) * big(1_000_000)
        return num to den
    }

    fun appPrice(app: AppDef): BigInteger =
        big(appPriceBase) * big(appPriceTierRatio).pow(app.tier - 1) * big(appPriceOrderPermille[app.order].toLong()) / big(1_000)

    fun upgradePrice(app: AppDef, level: Int): BigInteger {
        val base = big(upgradeBase) * big(appPriceTierRatio).pow(app.tier - 1)
        return (base * big(upgradeGrowthPermille.toLong()).pow(level - 1) / big(1_000).pow(level - 1)).max(ONE)
    }

    fun assistPrice(a: Assist, owned: Int): BigInteger = big(a.base) * big(a.growth).pow(owned)

    fun proPrice(appId: String): BigInteger = (levelP(Catalog.proLevel.getValue(appId)) * big(proPricePermille.toLong()) / big(1_000)).max(ONE)

    fun cardPrice(card: EventCard): BigInteger =
        if (card.price > 0) big(card.price) else (levelP(card.unlockLevel) * big(cardPricePermille.toLong()) / big(1_000)).max(ONE)

    fun backupPrice(item: BackupItem, owned: Int): Long =
        Math.ceil(item.base * Math.pow(item.growthPermille / 1000.0, owned.toDouble())).toLong()

    /** Total backup files ever earnable from a best run of [bestRunP]. */
    fun backupTotal(bestRunP: BigInteger): Long {
        if (bestRunP.signum() <= 0) return 0
        val lx = log10(bestRunP) - backupPDigits
        if (lx < 0) return 0
        val e = backupExponentPermille / 1000.0
        val logB = if (lx <= backupSoftcapDigits) e * lx else e * backupSoftcapDigits + e / 2 * (lx - backupSoftcapDigits)
        return Math.floor(10 * Math.pow(10.0, logB)).toLong()
    }

    /** Backup files a factory reset pays now: only what beating the best run adds (no farming of easy resets). */
    fun backupFiles(runEarned: BigInteger, level: Int, backup: Backup): Long {
        if (level < resetLevel) return 0
        return maxOf(0L, backupTotal(runEarned.max(backup.bestRunP)) - backup.lifetime)
    }

    companion object {
        fun log10(n: BigInteger): Double {
            val str = n.toString()
            val head = str.take(15).toDouble()
            return Math.log10(head) + (str.length - minOf(15, str.length))
        }

        fun cubeRoot(n: BigInteger): BigInteger {
            if (n.signum() <= 0) return BigInteger.ZERO
            var lo = BigInteger.ZERO
            var hi = ONE.shiftLeft(n.bitLength() / 3 + 1)
            while (lo < hi) {
                val mid = (lo + hi + ONE).shiftRight(1)
                if (mid.pow(3) <= n) lo = mid else hi = mid - ONE
            }
            return lo
        }
    }
}
