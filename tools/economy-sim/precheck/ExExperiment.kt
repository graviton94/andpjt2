package com.graviton94.notificationcleaner.core

import java.math.BigInteger

// Compiled only by the isolated precheck runner. Uses the production swipe and cap.
object ExExperiment {
    const val START = 1_000_000L
    const val DAY = 86_400_000L
    const val FULL = (1 shl 9) - 1
    private val prism = 0b000000111
    private val reboot = 0b000111000
    private val studio = 0b111000000
    private val days = listOf(0, 1, 3, 7, 14, 28)
    private val basis = BigInteger.valueOf(10_000)
    val current = ThreadLocal<Run>()

    data class Scenario(val name: String, val owned: Int, val staged: Boolean = false, val alternative: Boolean = false,
        val revised: Boolean = false)
    val scenarios = listOf(
        Scenario("none", 0),
        Scenario("full", FULL),
        Scenario("one", 1),
        Scenario("one_set", prism),
        Scenario("spread_three", 0b001001001),
        Scenario("two_sets", prism or reboot),
        Scenario("staged", 0, staged = true),
        Scenario("offline_cap", FULL, alternative = true),
        Scenario("revised", FULL, revised = true),
    )

    class Sample {
        var swipes = 0L
        var noExPaid = BigInteger.ZERO
        var exPaid = BigInteger.ZERO
        var cappedToZero = 0L
        var paidBp = 0.0
        var nominalBp = 0L
        val activations = LongArray(9)
        val marginalBp = DoubleArray(9)
    }

    class Run(val scenario: Scenario) {
        val samples = Array(2) { Array(days.size) { Sample() } }
        val collects = scenario.name == "full" || scenario.revised
        var shadowCountdown = 1
        var shadowAttempted = 0L
        var validPrestige = false
        var lastValidSyncAt: Long? = null

        fun record(actor: Actor, now: Long, conditionMask: Int, bp: Int, base: BigInteger, added: BigInteger,
            marginal: BigInteger, capBlocked: Boolean) {
            val actorIndex = actor.ordinal
            val dayIndex = days.indexOf(((now - START) / DAY).toInt() + 1)
            val actual = ratio(added, base)
            val marginalRate = ratio(marginal, base)
            fun update(sample: Sample) {
                sample.swipes++
                sample.noExPaid += base
                sample.exPaid += added
                if (capBlocked) sample.cappedToZero++
                sample.paidBp += actual
                sample.nominalBp += bp.toLong()
                var mask = conditionMask
                while (mask != 0) {
                    val bit = Integer.numberOfTrailingZeros(mask)
                    sample.activations[bit]++
                    sample.marginalBp[bit] += marginalRate
                    mask = mask and (mask - 1)
                }
            }
            update(samples[actorIndex][0])
            if (dayIndex > 0) update(samples[actorIndex][dayIndex])
        }

        fun sampleRows(): Sequence<List<Any>> = sequence {
            for (actor in Actor.entries) for ((index, day) in days.withIndex()) {
                val s = samples[actor.ordinal][index]
                yield(listOf(actor.name, day, s.swipes, s.noExPaid, s.exPaid, s.nominalBp,
                    s.paidBp, s.cappedToZero, if (actor == Actor.Auto && day == 0) shadowAttempted else 0L))
            }
        }

        fun conditionRows(): Sequence<List<Any>> = sequence {
            for (actor in Actor.entries) for ((index, day) in days.withIndex()) {
                val s = samples[actor.ordinal][index]
                for (asset in 0..8) {
                    yield(listOf(actor.name, day, asset, s.swipes, s.activations[asset], s.marginalBp[asset]))
                }
            }
        }
    }

    private fun ratio(numerator: BigInteger, denominator: BigInteger): Double {
        if (numerator.signum() == 0 || denominator.signum() == 0) return 0.0
        val shift = maxOf(0, denominator.bitLength() - 900)
        return numerator.shiftRight(shift).toDouble() * 10_000 / denominator.shiftRight(shift).toDouble()
    }

    private fun owned(scenario: Scenario, now: Long): Int {
        if (!scenario.staged) return scenario.owned
        val elapsed = now - START
        // Halfway through the first session on days 3 and 14.
        return when {
            elapsed >= 13 * DAY + 450_000 -> FULL
            elapsed >= 2 * DAY + 450_000 -> prism
            else -> 0
        }
    }

    fun offlineCap(base: Long): Long {
        val scenario = current.get()?.scenario ?: return base
        // +0.20% capped time per set; total conservative income bound is below +10%.
        return if (scenario.alternative) base + base * 60 / 10_000 else base
    }

    fun revisedConditions() = current.get()?.scenario?.revised == true

    private fun lateConditions(run: Run, s: GameState, now: Long, resetLevel: Int,
        directApp: String?, burstApps: List<String>): Int {
        val active = if (run.scenario.revised) {
            val firstCategory = burstApps.firstOrNull()?.let { Catalog.app(it).category }
            listOf(
                run.validPrestige,
                s.level in resetLevel until resetLevel + 10,
                run.lastValidSyncAt?.let { now - it in 0 until 30 * 60_000L } ?: false,
                firstCategory != null && burstApps.any { Catalog.app(it).category != firstCategory },
                directApp?.let { s.account.stars(it) > 0 } ?: false,
                burstApps.any { it in s.pro },
            )
        } else {
            val syncs = s.backup.cloud.syncs
            listOf(
                s.backup.resets > 0 || syncs > 0, s.level >= resetLevel, syncs > 0, s.active.size >= 4,
                s.active.any { s.account.stars(it) > 0 }, s.active.any { it in s.pro },
            )
        }
        return active.foldIndexed(0) { index, mask, met -> if (met) mask or (8 shl index) else mask }
    }

    fun adjust(earned: BigInteger, room: BigInteger, s: GameState, now: Long, actor: Actor,
        chain: Int, overload: Boolean, beats: List<Beat>, resetLevel: Int,
        directApp: String?, burstApps: List<String>): BigInteger {
        val run = current.get() ?: return earned
        val owned = owned(run.scenario, now)
        if (owned == 0) return earned
        var conditions = 0
        if (owned and 1 != 0 && chain >= 2) conditions = conditions or 1
        if (owned and 2 != 0 && overload) conditions = conditions or 2
        if (owned and 4 != 0 && beats.any { it is Beat.Merged }) conditions = conditions or 4
        if (owned and 0b111111000 != 0) {
            conditions = conditions or (lateConditions(run, s, now, resetLevel, directApp, burstApps) and owned)
        }
        val sets = (if (owned and prism == prism) 1 else 0) + (if (owned and reboot == reboot) 1 else 0) +
            (if (owned and studio == studio) 1 else 0)
        val bp = 50 * Integer.bitCount(owned) + 25 * Integer.bitCount(conditions) +
            sets * (if (run.scenario.alternative) 40 else 75) + (if (owned == FULL) 100 else 0)
        val adjusted = earned * BigInteger.valueOf(10_000L + bp) / basis
        if (run.collects) {
            val base = earned.min(room)
            val paid = adjusted.min(room)
            val marginal = if (conditions == 0) BigInteger.ZERO
                else paid - (earned * BigInteger.valueOf(10_000L + bp - 25) / basis).min(room)
            run.record(actor, now, conditions, bp, base, paid - base, marginal, earned >= room && adjusted > earned)
        }
        return adjusted
    }
}
