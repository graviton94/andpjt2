import com.graviton94.notificationcleaner.core.AdSlot
import java.io.File

private const val AUDIT_DAYS = 84
private const val AUDIT_SEEDS = 10
private val ranges = mapOf(
    Payer.Free to 66.0..84.0,
    Payer.Ads to 49.0..63.0,
    Payer.Pass to 35.0..49.0,
    Payer.Spend30 to 24.0..32.0,
    Payer.Spend100 to 11.0..17.0,
)

private fun median(days: List<Double?>): Double? {
    val sorted = days.sortedBy { it ?: Double.POSITIVE_INFINITY }
    val lower = sorted[(sorted.size - 1) / 2] ?: return null
    val upper = sorted[sorted.size / 2] ?: return null
    return (lower + upper) / 2.0
}

fun balanceAudit(output: File, profiles: String?, availableRewardsOnly: Boolean) {
    output.mkdirs()
    val payers = profiles?.split(",")?.map(Payer::valueOf) ?: Payer.entries
    val slots = if (availableRewardsOnly) AdSlot.entries.toSet() - AdSlot.OfflineDouble else AdSlot.entries.toSet()
    val jobs = payers.flatMap { payer -> (1L..AUDIT_SEEDS.toLong()).map { seed -> payer to seed } }
    val rows = jobs.parallelStream().map { (payer, seed) ->
        val result = playDays(seed, AUDIT_DAYS, DayPlan(), true, payer = payer, slots = slots, stopAtLv124 = true)
        println("${payer.name},$seed,${result.level124Day ?: "미도달"}")
        Triple(payer, seed, result)
    }.toList()
    csv(File(output, "audit-seeds.csv"), listOf("profile", "seed", "lv124_day", "first_reset_day", "first_sync_day",
        "record_p"), rows.asSequence().map { (payer, seed, trace) ->
        listOf(payer.name, seed, trace.level124Day, trace.resetDays.firstOrNull(), trace.syncDays.firstOrNull(), trace.bestSwipeP)
    })
    csv(File(output, "audit-stalls.csv"), listOf("profile", "seed", "highest_level", "highest_first_day",
        "week10_run_peak", "week11_run_peak", "week12_run_peak", "final_level", "last_reset_day", "last_reset_level",
        "last_sync_day", "last_sync_level"), rows.asSequence().filter { it.third.level124Day == null }.map { (payer, seed, t) ->
        val high = t.levelDays.lastKey()
        listOf(payer.name, seed, high, t.levelDays[high], t.runPeakByWeek[10], t.runPeakByWeek[11], t.runPeakByWeek[12],
            t.finalLevel, t.resetDays.lastOrNull(), t.lastResetLevel, t.syncDays.lastOrNull(), t.lastSyncLevel)
    })
    csv(File(output, "audit-gates.csv"), listOf("profile", "median_days", "in_band", "seeds", "min_day", "max_day",
        "low", "high", "verdict"), payers.asSequence().map { payer ->
        val limits = ranges.getValue(payer)
        val days = rows.filter { it.first == payer }.map { it.third.level124Day }
        val middle = median(days)
        val hits = days.count { it != null && it in limits }
        listOf(payer.name, middle, hits, AUDIT_SEEDS, days.filterNotNull().minOrNull(), days.filterNotNull().maxOrNull(),
            limits.start, limits.endInclusive, if (middle != null && middle in limits && hits * 5 >= AUDIT_SEEDS * 4) "PASS" else "FAIL")
    })
}
