import com.graviton94.notificationcleaner.core.Actor
import com.graviton94.notificationcleaner.core.AdSlot
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.ExExperiment
import com.graviton94.notificationcleaner.core.GameState
import com.graviton94.notificationcleaner.core.SwipeOutcome
import java.io.File
import java.math.BigInteger
import java.util.concurrent.Executors

private val observedDays = setOf(7, 14, 28, 56)
private val statuses = listOf("summary", "checkpoints", "prestige", "samples", "conditions")

private data class Policy(val name: String, val payer: Payer, val slots: Set<AdSlot> = AdSlot.entries.toSet())

private class Trace(private val run: ExExperiment.Run, private val through: Int) : DailyAudit {
    override val throughDay = through
    private var files = BigInteger.ZERO
    private var gb = BigInteger.ZERO
    val checkpoints = mutableListOf<List<Any>>()
    val events = mutableListOf<List<Any>>()

    override fun swipe(engine: Engine, before: GameState, outcome: SwipeOutcome, now: Long) {
        if (!run.collects || --run.shadowCountdown != 0) return
        run.shadowCountdown = 64
        run.shadowAttempted++
        // Same state and timestamp, separate Auto target and payout. Discard the side branch.
        engine.autoTarget(before, now)?.let { engine.swipe(before, it, now, Actor.Auto) }
    }

    override fun prestige(kind: String, before: GameState, after: GameState, now: Long, gain: Double, stalls: Int, sessions: Int) {
        val paidFiles = if (kind == "reset") after.backup.files - before.backup.files else 0L
        val paidGb = if (kind == "sync") after.backup.cloud.gb - before.backup.cloud.gb else 0L
        if (paidFiles > 0 || paidGb > 0) run.validPrestige = true
        if (paidGb > 0) run.lastValidSyncAt = now
        files += BigInteger.valueOf(paidFiles)
        gb += BigInteger.valueOf(paidGb)
        events += listOf(kind, (now - ExExperiment.START) / ExExperiment.DAY.toDouble(), before.level,
            logP(before.runEarned), paidFiles, paidGb, gain, stalls, sessions,
            when {
                kind == "sync" -> "sync_gb_and_stalls"
                gain >= 1.6 -> "reset_gain"
                else -> "reset_stalls"
            })
    }

    override fun endDay(day: Int, state: GameState) {
        if (day in observedDays) {
            checkpoints += listOf(day, state.level, maxOf(state.level, state.backup.bestLevel),
                state.runEarned, logP(state.runEarned), files, gb, state.backup.cloud.lifetime)
        }
    }
}

private fun logP(p: BigInteger): Double {
    if (p.signum() <= 0) return 0.0
    val digits = p.toString()
    val prefix = digits.take(15)
    return digits.length - prefix.length + StrictMath.log10(prefix.toDouble())
}

private fun runCase(dir: File, scenario: ExExperiment.Scenario, policy: Policy, seed: Long, through: Int) {
    val case = File(dir, "${policy.name}-${scenario.name}-$seed")
    if (File(case, "done").exists()) return
    case.mkdirs()
    val run = ExExperiment.Run(scenario)
    val trace = Trace(run, through)
    ExExperiment.current.set(run)
    try {
        val data = playDays(seed, 84, DayPlan(), true, payer = policy.payer, slots = policy.slots,
            stopAtLv124 = true, audit = trace)
        val prefix = listOf(policy.name, scenario.name, seed)
        fun write(name: String, cols: List<String>, rows: Sequence<List<Any?>>) =
            csv(File(case, "$name.csv"), listOf("profile", "scenario", "seed") + cols, rows.map { prefix + it })
        write("summary", listOf("lv124_day", "first_reset_day", "first_sync_day", "end_level", "max_level", "best_swipe_p"),
            sequenceOf(listOf(data.level124Day, data.resetDays.firstOrNull(), data.syncDays.firstOrNull(),
                data.finalLevel, data.levelDays.lastKey(), data.bestSwipeP)))
        write("checkpoints", listOf("day", "level", "peak_level", "run_p", "log10_run_p", "files_total", "gb_total", "gb_base_lifetime"),
            trace.checkpoints.asSequence())
        write("prestige", listOf("kind", "day", "level_before", "log10_run_p_before", "files_paid", "gb_paid",
            "gain", "stall_sessions", "sessions_since_prestige", "decision"), trace.events.asSequence())
        write("samples", listOf("actor", "day", "swipes", "same_state_base_p", "ex_extra_p", "nominal_bp_sum", "actual_bp_sum",
            "ex_blocked_by_cap", "auto_attempts"), run.sampleRows().takeIf { run.collects } ?: emptySequence())
        write("conditions", listOf("actor", "day", "asset_index", "swipes", "activated", "actual_marginal_bp_sum"),
            run.conditionRows().takeIf { run.collects } ?: emptySequence())
        File(case, "done").writeText("ok\n")
        println("${policy.name},${scenario.name},$seed,${data.level124Day ?: "censored"}")
    } finally {
        ExExperiment.current.remove()
    }
}

object ExPrecheckCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val output = File(args[0])
        val seeds = args[1].toInt()
        val workers = args[2].toInt()
        val through = args[5].toInt()
        val firstSeed = args[6].toLong()
        val allPolicies = Payer.entries.map { Policy(it.name, it) } +
            Policy("PassVisible", Payer.Pass, AdSlot.entries.toSet() - AdSlot.OfflineDouble)
        val policies = allPolicies.filter { args[3] == "all" || it.name in args[3].split(",") }
        val scenarios = ExExperiment.scenarios.filter { args[4] == "all" || it.name in args[4].split(",") }
        require(seeds > 0 && firstSeed > 0 && workers > 0 && policies.isNotEmpty() && scenarios.isNotEmpty() && through in 0..56)
        val cases = File(output, "cases")
        val jobs = scenarios.flatMap { scenario ->
            (firstSeed until firstSeed + seeds).flatMap { seed -> policies.map { Triple(scenario, it, seed) } }
        }
        val executor = Executors.newFixedThreadPool(workers)
        try {
            executor.invokeAll(jobs.map { (scenario, policy, seed) ->
                java.util.concurrent.Callable { runCase(cases, scenario, policy, seed, through) }
            }).forEach { it.get() }
        } finally {
            executor.shutdown()
        }
        for (name in statuses) {
            File(output, "$name.csv").bufferedWriter().use { writer ->
                jobs.forEachIndexed { i, (scenario, policy, seed) ->
                    val csv = File(cases, "${policy.name}-${scenario.name}-$seed/$name.csv")
                    csv.useLines { lines ->
                        (if (i == 0) lines else lines.drop(1)).forEach { writer.append(it).append('\n') }
                    }
                }
            }
        }
    }
}
