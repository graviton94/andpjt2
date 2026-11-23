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
    // Monetization (docs/bible/17-monetization.md)
    val premiumMaxStars: Int = 5,
    /** Each premium star multiplies the app's value by this. */
    val premiumStepPermille: Int = 2_000,
    /** Credits for ★0→★5 of a top-tier app: what 12 weeks of attendance + missions pay a steady free player. */
    val premiumBudget: Long = 6_000,
    /** Share of the full premium cost per star, ‰ (sums to 1000). */
    val premiumStarShare: List<Int> = listOf(50, 100, 170, 280, 400),
    /**
     * 30-day consecutive attendance board (missed day → day 1). Currencies alternate; days 10, 20, 30 are big.
     * About 1,300 data a board, so 12 steady weeks still pay ≈ [premiumBudget] with the daily missions.
     */
    val attendanceBoard: List<AttendReward> = run {
        val B = CreditItem.Battery; val Bo = CreditItem.Boost; val W = CreditItem.Warp; val O = CreditItem.Overclock; val BB = CreditItem.BackupBoost
        fun d(n: Long) = AttendReward(n)
        fun tk(vararg p: Pair<CreditItem, Int>) = AttendReward(0, mapOf(*p))
        // Weekdays alternate currencies and let everyone taste the paid speed-ups (오버클럭 on days 3, 13, 23).
        fun band(a: Long, b: Long, c: Long) = listOf(d(a), tk(B to 2), tk(O to 1), tk(Bo to 1), d(b), tk(B to 2), tk(W to 1), d(c), tk(Bo to 1))
        band(30, 40, 40) + AttendReward(200, mapOf(O to 2, BB to 1)) +
            band(40, 50, 50) + AttendReward(300, mapOf(W to 2), starVouchers = 1) +
            band(50, 60, 60) + AttendReward(600, mapOf(O to 6, BB to 2), starVouchers = 2, title = "알림 개근왕")
    },
    /** 프리미엄 ★ 선택권 raise an app up to this many stars. */
    val voucherMaxStars: Int = 2,
    val missionCredits: Long = 35,
    val adCredits: Long = 10,
    val passInstantCredits: Long = 300,
    val passDailyCredits: Long = 60,
    val boostMinutes: Int = 60,
    val boostPermille: Int = 2_000,
    val warpMinutes: Int = 120,
    val batteryOvercharge: Int = 6,
    val backupBoostPercent: Int = 100,
    /** 오버클럭: game speed while on (battery, auto-clean, offline time). */
    val overclockPermille: Int = 3_000,
    /** Fastest battery charge under any speed-up: direct swipes stay humanly possible. */
    val minChargeMs: Long = 1_000,
    val passOverclockMinutes: Int = 180,
    /** P from an ad or a credit item (×2 offers, 시간 도약) raises the level by at most this. */
    val adLevelCap: Int = 1,
    /** 오프라인 광고: the settlement once more at this percent. */
    val offlineAdPercent: Int = 50,
    val freeUpgradeLevels: Int = 5,
    /** 최고 기록 ×2 is offered from this level. */
    val bestDoubleFrom: Int = 20,
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
    /** Spam stops copying itself at this many spam rows on the board. */
    val spamMax: Int = 3,
    val callEverySwipes: Int = 23,
    val callUnlockLevel: Int = 2,
    val callPauseMs: Long = 5_000,
    val otpLifeSwipes: Int = 3,
    // Interruptions added in rules v3.1 and timed cards
    val lowBatteryLevel: Int = 9,
    val lowBatteryAt: Int = 3,
    val storageLevel: Int = 20,
    val storageEverySwipes: Int = 70,
    val crashLevel: Int = 51,
    val crashEverySwipes: Int = 90,
    val drillMs: Long = 30_000,
    val dndMs: Long = 20_000,
    val dndPerDay: Int = 3,
    // Value
    val tierBase: Long = 6,
    val levelMilestones: List<Int> = listOf(10, 25, 50, 100, 200, 300, 400, 500),
    val appPriceBase: Long = 100,
    val appPriceTierRatio: Long = 60,
    val appPriceOrderPermille: List<Int> = listOf(1_000, 1_600, 2_500, 4_000, 6_300, 10_000),
    val appPriceLevelPermille: Int = 100,
    /** An upgrade L→L+1 costs this ‰ of the app's price × growth^(L−1). */
    val upgradeSharePermille: Int = 250,
    val upgradeBase: Long = 25,
    val upgradeGrowthPermille: Int = 1_300,
    /**
     * Extra upgrade growth per tier above 1, in ‰ (research: low tiers cheap to catch up, high tiers steep).
     * 0 keeps one growth for every tier; turning it on is an economy change and needs a simulator pass.
     */
    val upgradeGrowthPerTierPermille: Int = 0,
    // Progress
    val resetLevel: Int = 25,
    /**
     * Level is the run's cumulative P: reaching level L needs levelPBase × ratio^(L−2), the ratio rising by
     * [levelRatioStepPermille] every level after [levelLateFrom]. Leveling is exponential in P, so backup-file
     * multipliers speed it up directly.
     */
    val levelPBase: Long = 30,
    val levelRatioPermille: Int = 2_330,
    val levelLateFrom: Int = 25,
    val levelRatioStepPermille: Int = 5,
    /** Every backup file ever earned adds this % to all notification values. Compounds across resets. */
    val lifetimeFilePercent: Int = 5,
    /** Passive bonus = (1 + 5% × lifetime files)^0.85: compounding, but tapering (research A3). */
    val lifetimeExponentPermille: Int = 850,
    // Offline settlement
    /** Offline pays the auto-clean rate (≈ this ‰ of the direct-play rate) × efficiency, up to the cap (research E1). */
    val offlineAutoPermille: Int = 400,
    val offlineEfficiencyPercent: Int = 3,
    val offlineCapSteps: List<Pair<Int, Int>> = listOf(1 to 8, 7 to 10, 35 to 12),
    /** Content priced from the level curve: a pro version costs this ‰ of the P its level needs. */
    val proPricePermille: Int = 1_000,
    val cardPricePermille: Int = 500,
    /** Value multiplier of a pro version. */
    val proMultiplier: Long = 2,
    /**
     * Backup files total = ⌊10 × (best run P ÷ P(Lv[resetLevel]))^0.5⌋; beyond softcap digits above that anchor the
     * exponent halves. Paid as the delta over files already earned (research A2). The anchor follows the level
     * curve, so reaching the reset level always pays the first 10 files whatever the value tuning.
     */
    val backupExponentPermille: Int = 400,
    val backupSoftcapDigits: Int = 8,
    /** Backup files paid by a run that just reaches the reset level. */
    val backupBase: Long = 10,
    // Jackpot limits: luck jumps a few steps, never the whole run.
    /** One swipe (chains, waves, 로또, 인증번호 included) raises the level by at most this many steps; the rest is lost. */
    val swipeLevelCap: Int = 3,
    /** The caps apply from this level: the first levels are the tutorial and may jump freely. */
    val jackpotCapFrom: Int = 10,
    /** One offline settlement raises the level by at most this many steps. */
    val offlineLevelCap: Int = 2,
    /** Region × category × diversity synergy together multiply a value by at most this ‰: below one tier step (×[tierBase]). */
    val synergyCapPermille: Int = 4_000,
    // 2nd prestige: 클라우드 동기화
    val syncLevel: Int = 60,
    /** GB total = ⌊cloudBase × (backup files of the best cycle ÷ files of a Lv[syncLevel] run)^exponent⌋, paid as a delta. */
    val cloudBase: Long = 10,
    val cloudExponentPermille: Int = 500,
    /** Every GB ever synced adds this % to backup files paid by factory resets. */
    val cloudFilePercent: Int = 10,
) {
    fun step(steps: List<Pair<Int, Int>>, level: Int): Int = steps.lastOrNull { level >= it.first }?.second ?: 0

    fun maxSlots(level: Int, backup: Backup) = step(maxSlotSteps, level) + backup.lv(BackupItem.ExtraSlot) + backup.cloud.lv(CloudItem.CloudSlot)
    fun minSlots(level: Int) = step(minSlotSteps, level)
    fun eventSlots(level: Int, backup: Backup) = step(eventSlotSteps, level) + if (level >= 6) backup.lv(BackupItem.EventSlot) + backup.cloud.lv(CloudItem.CloudEventSlot) else 0

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

    /** Run P at which [level] is reached with [fastBoot] (the raw threshold the player must earn). */
    fun levelCeil(level: Int, fastBoot: Int): BigInteger =
        (levelP(level) * big(9).pow(fastBoot) + big(10).pow(fastBoot) - ONE) / big(10).pow(fastBoot)

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
        val sync = backup.cloud.lv(CloudItem.SyncValue)
        val ai = backup.cloud.lv(CloudItem.AiBoost)
        val num = big(tierBase).pow(app.tier - 1) * big(level.toLong()) * big(20 + k).pow(m) * big(5).pow(v) * big(passive) * big(2).pow(sync) * big(3).pow(ai)
        val den = TEN.pow(m) * big(4).pow(v) * big(1_000_000)
        return num to den
    }

    /**
     * App price: the tier ladder, but never below [appPriceLevelPermille]‰ of the P its unlock level needs.
     * Unlocks are permanent (Engine.unlocked), so in a later run the price — not the level — sets the order:
     * high tiers stay out of reach at the start of a run and are bought one after another.
     */
    fun appPrice(app: AppDef): BigInteger {
        val ladder = big(appPriceBase) * big(appPriceTierRatio).pow(app.tier - 1) * big(appPriceOrderPermille[app.order].toLong()) / big(1_000)
        return ladder.max(levelP(app.unlockLevel) * big(appPriceLevelPermille.toLong()) / big(1_000))
    }

    fun upgradeGrowth(app: AppDef): Int = upgradeGrowthPermille + upgradeGrowthPerTierPermille * (app.tier - 1)

    fun upgradePrice(app: AppDef, level: Int): BigInteger {
        val base = appPrice(app) * big(upgradeSharePermille.toLong()) / big(1_000)
        val g = upgradeGrowth(app).toLong()
        return (base * big(g).pow(level - 1) / big(1_000).pow(level - 1)).max(ONE)
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
        val lx = log10(bestRunP) - log10(levelP(resetLevel))
        if (lx < 0) return 0
        val e = backupExponentPermille / 1000.0
        val logB = if (lx <= backupSoftcapDigits) e * lx else e * backupSoftcapDigits + e / 2 * (lx - backupSoftcapDigits)
        return minOf(FILE_LIMIT, Math.floor(backupBase * Math.pow(10.0, logB)).toLong())
    }

    /** Cloud multiplier on backup files: 압축 백업 ×1.5 per level × √(1 + [cloudFilePercent]% × GB ever synced). */
    fun fileMultiplier(cloud: Cloud): Double =
        Math.pow(1.5, cloud.lv(CloudItem.Compression).toDouble()) * Math.sqrt(1.0 + cloudFilePercent / 100.0 * cloud.lifetime)

    /** Backup files a factory reset pays now: only what beating the best run adds (no farming of easy resets). */
    fun backupFiles(runEarned: BigInteger, level: Int, backup: Backup): Long {
        if (level < resetLevel) return 0
        val total = minOf(FILE_LIMIT, Math.floor(backupTotal(runEarned.max(backup.bestRunP)) * fileMultiplier(backup.cloud)).toLong())
        return maxOf(0L, total - backup.lifetime)
    }

    /** Total GB ever earnable from a cycle that earned [cycleFiles] backup files. */
    fun cloudTotal(cycleFiles: Long): Long {
        val anchor = backupTotal(levelP(syncLevel))
        if (cycleFiles < anchor || anchor <= 0) return 0
        return Math.floor(cloudBase * Math.pow(cycleFiles.toDouble() / anchor, cloudExponentPermille / 1000.0)).toLong()
    }

    fun cloudPrice(item: CloudItem, owned: Int): Long =
        Math.ceil(item.base * Math.pow(item.growthPermille / 1000.0, owned.toDouble())).toLong()

    companion object {
        /** Backup files and GB saturate here so sums never overflow a Long. */
        const val FILE_LIMIT: Long = 100_000_000_000_000_000L

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
