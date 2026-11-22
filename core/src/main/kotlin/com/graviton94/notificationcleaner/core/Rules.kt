package com.graviton94.notificationcleaner.core

import java.math.BigInteger

/** Virtual market of an app. Grows its own multiplier in the economy layer (v0.2). */
enum class Region { Domestic, Global, NorthAmerica }

/** App category. Grows its own multiplier in the economy layer (v0.2). */
enum class Category { Social, Work, Daily, Media }

/**
 * One fictional app. [tier] sets the value of a single notification (tierBase^(tier-1)).
 * [unlockLevel] is the player level at which it appears in the shop.
 */
data class AppDef(
    val id: String,
    val name: String,
    val tier: Int,
    val region: Region,
    val category: Category,
    val unlockLevel: Int,
)

/**
 * Every tunable number of the core rules. The engine reads only this; a rules change is a new
 * [version]. Economy values marked v0.2 are placeholders until the economy simulator fixes them.
 */
data class RuleSet(
    val version: Int = 2,
    // Board
    val rowCount: Int = 7,
    val threshold: Int = 4,
    /** Weights for a new row holding 1, 2 or 3 notifications. */
    val countWeights: List<Int> = listOf(1, 2, 1),
    /** Chance (%) that a new top row repeats the app two rows below it: A · X · A. */
    val sandwichPercent: Int = 35,
    /** Apps allowed to send notifications at once (the active deck). */
    val activeSlots: Int = 4,
    // Battery (action energy)
    val energyCap: Int = 12,
    val chargeMs: Long = 3_000,
    val autoIntervalMs: Long = 4_500,
    // Overload
    val gaugePerMerge: Int = 4,
    val gaugePerBurst: Int = 12,
    val waveFactor: Int = 3,
    val startOverloadDepth: Int = 1,
    // Interruptions
    val spamEverySwipes: Int = 11,
    val spamCopyAfterSwipes: Int = 3,
    val callEverySwipes: Int = 23,
    val callPauseMs: Long = 5_000,
    // Value (economy v0.2 placeholders)
    val tierBase: Long = 10,
    val levelMilestones: List<Int> = listOf(10, 25, 50, 100, 200, 300, 400, 500),
    val appPriceBase: Long = 50,
    val appPriceTierRatio: Long = 25,
    val upgradeBase: Long = 5,
    val upgradeGrowthPermille: Int = 1_150,
    // Progress
    val xpGoalBase: Double = 10.0,
    val xpGoalRatio: Double = 1.25,
    val apps: List<AppDef> = DEFAULT_APPS,
    val starter: List<String> = listOf("chat", "mail", "delivery"),
) {
    fun app(id: String): AppDef = apps.first { it.id == id }

    fun xpGoal(level: Int): Long = Math.ceil(xpGoalBase * Math.pow(xpGoalRatio, (level - 1).toDouble())).toLong()

    /** Value of one notification of [app] at upgrade level [level]: tier value × level × 2^milestones. */
    fun notificationValue(app: AppDef, level: Int): BigInteger {
        val milestones = levelMilestones.count { level >= it }
        return BigInteger.valueOf(tierBase).pow(app.tier - 1) *
            BigInteger.valueOf(level.toLong()) * BigInteger.TWO.pow(milestones)
    }

    fun appPrice(app: AppDef): BigInteger =
        BigInteger.valueOf(appPriceBase) * BigInteger.valueOf(appPriceTierRatio).pow(app.tier - 1)

    /** Price to go from [level] to level + 1. Grows by upgradeGrowthPermille/1000 per level. */
    fun upgradePrice(app: AppDef, level: Int): BigInteger {
        val base = BigInteger.valueOf(upgradeBase) * BigInteger.valueOf(appPriceTierRatio).pow(app.tier - 1)
        val num = BigInteger.valueOf(upgradeGrowthPermille.toLong()).pow(level - 1)
        val den = BigInteger.valueOf(1_000).pow(level - 1)
        return (base * num / den).max(BigInteger.ONE)
    }

    companion object {
        val DEFAULT_APPS = listOf(
            AppDef("chat", "톡탁톡", 1, Region.Domestic, Category.Social, 1),
            AppDef("mail", "메일소포", 1, Region.Global, Category.Work, 1),
            AppDef("delivery", "문앞왔", 1, Region.Domestic, Category.Daily, 1),
            AppDef("feed", "피드또봐", 2, Region.Global, Category.Media, 3),
            AppDef("calendar", "일정찍", 2, Region.Global, Category.Work, 4),
            AppDef("pay", "페이한칸", 2, Region.NorthAmerica, Category.Daily, 5),
            AppDef("music", "비트서랍", 3, Region.NorthAmerica, Category.Media, 8),
            AppDef("inbox", "밀린메일", 3, Region.Domestic, Category.Work, 10),
            AppDef("map", "길막지도", 3, Region.Domestic, Category.Daily, 12),
            AppDef("reward", "접속보상", 4, Region.Global, Category.Media, 15),
            AppDef("bank", "잔액알림", 4, Region.Domestic, Category.Daily, 18),
            AppDef("cloud", "용량부족", 4, Region.Global, Category.Work, 21),
        )
    }
}
