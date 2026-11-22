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
    val maxSlotSteps: List<Pair<Int, Int>> = listOf(1 to 3, 3 to 4, 8 to 5, 20 to 6, 30 to 7, 40 to 8),
    val minSlotSteps: List<Pair<Int, Int>> = listOf(1 to 3, 10 to 4, 20 to 5, 30 to 6, 40 to 7),
    val eventSlotSteps: List<Pair<Int, Int>> = listOf(6 to 1, 14 to 2),
    // Synergy: same category / region in the deck → bonus % for those apps
    val synergySteps: List<Pair<Int, Int>> = listOf(2 to 10, 3 to 25, 4 to 50, 5 to 100),
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
    val appPriceBase: Long = 50,
    val appPriceTierRatio: Long = 25,
    val appPriceOrderPermille: List<Int> = listOf(1_000, 1_600, 2_500, 4_000),
    val upgradeBase: Long = 5,
    val upgradeGrowthPermille: Int = 1_150,
    // Progress
    val resetLevel: Int = 25,
) {
    fun step(steps: List<Pair<Int, Int>>, level: Int): Int = steps.lastOrNull { level >= it.first }?.second ?: 0

    fun maxSlots(level: Int, backup: Backup) = step(maxSlotSteps, level) + backup.lv(BackupItem.ExtraSlot)
    fun minSlots(level: Int) = step(minSlotSteps, level)
    fun eventSlots(level: Int, backup: Backup) = step(eventSlotSteps, level) + if (level >= 6) backup.lv(BackupItem.EventSlot) else 0

    /** XP to leave [level], in hundredths of a point. ⌈5 × L^1.4 × 1.09^(L−1)⌉ points. */
    fun xpGoalCenti(level: Int): Long = Math.ceil(5.0 * Math.pow(level.toDouble(), 1.4) * Math.pow(1.09, (level - 1).toDouble())).toLong() * 100

    fun synergyPercent(count: Int, backup: Backup): Int {
        val base = step(synergySteps, count)
        return base * (100 + 10 * backup.lv(BackupItem.Synergy)) / 100
    }

    /** One notification before deck multipliers: 10^(tier−1) × level × (2 + 0.1k)^milestones × 1.1^value. */
    fun baseValue(app: AppDef, level: Int, backup: Backup): Pair<BigInteger, BigInteger> {
        val m = levelMilestones.count { level >= it }
        val k = backup.lv(BackupItem.Milestone).toLong()
        val v = backup.lv(BackupItem.Value)
        val num = big(tierBase).pow(app.tier - 1) * big(level.toLong()) * big(20 + k).pow(m) * big(11).pow(v)
        val den = TEN.pow(m) * TEN.pow(v)
        return num to den
    }

    fun appPrice(app: AppDef): BigInteger =
        big(appPriceBase) * big(appPriceTierRatio).pow(app.tier - 1) * big(appPriceOrderPermille[app.order].toLong()) / big(1_000)

    fun upgradePrice(app: AppDef, level: Int): BigInteger {
        val base = big(upgradeBase) * big(appPriceTierRatio).pow(app.tier - 1)
        return (base * big(upgradeGrowthPermille.toLong()).pow(level - 1) / big(1_000).pow(level - 1)).max(ONE)
    }

    fun assistPrice(a: Assist, owned: Int): BigInteger = big(a.base) * big(a.growth).pow(owned)

    fun backupPrice(item: BackupItem, owned: Int): Long =
        Math.ceil(item.base * Math.pow(item.growthPermille / 1000.0, owned.toDouble())).toLong()

    /** Backup files for a factory reset now: ⌊∛(run P ÷ 10⁶)⌋ + ⌊(level − 20)² ÷ 10⌋. */
    fun backupFiles(runEarned: BigInteger, level: Int): Long {
        if (level < resetLevel) return 0
        val fromP = cubeRoot(runEarned / big(1_000_000)).toLong()
        val d = (level - 20).toLong()
        return fromP + d * d / 10
    }

    companion object {
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
