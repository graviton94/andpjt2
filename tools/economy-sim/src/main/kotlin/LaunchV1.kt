import com.graviton94.notificationcleaner.core.AdSlot
import com.graviton94.notificationcleaner.core.Alert
import com.graviton94.notificationcleaner.core.Beat
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.EventCard
import com.graviton94.notificationcleaner.core.GameState
import com.graviton94.notificationcleaner.core.RowKind
import com.graviton94.notificationcleaner.core.RuleSet
import com.graviton94.notificationcleaner.core.SwipeOutcome
import java.io.File
import java.math.BigInteger
import java.math.MathContext
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

private val zero = BigInteger.ZERO
private fun ratio(n: Long, d: Long): Double = if (d == 0L) 0.0 else n.toDouble() / d
private fun fixed(n: Double): String = String.format(Locale.ROOT, "%.7f", n)
private fun num(n: Double?): String = n?.let(::fixed) ?: ""

/** Collects actual committed swipes. A wave can raise the scoring chain but is not an additional direct explosion. */
private class LaunchFeel(override val throughDay: Int) : DailyAudit {
    var sessions = 0L
    var opening = 0L
    var late = 0L
    var earlyBlast = 0L
    var lateBlast = 0L
    var chains2 = 0L
    var chains4 = 0L
    var chains9 = 0L
    var waves = 0L
    var firstWeekRecords = 0L
    var laterRecords = 0L
    var laterSessions = 0L
    var cappedSwipes = 0L
    var earned = zero
    var clipped = zero
    var majorEvents = 0L
    var repeatedEvents = 0L
    var spamMax = 0
    var openingUnitRise = 0L
    var openingFirstUnitMs = 0L
    var laterUnitSession = 0L
    var laterUnitSessions = 0L
    val adCounts = mutableMapOf<AdSlot, Long>()
    val adP = mutableMapOf<AdSlot, BigInteger>()
    private var previousEvent: String? = null
    private var day = 0
    private var limit = BigInteger.valueOf(1_000)
    private var newUnit = false
    private var sessionBegan = 0L
    private var sessionOpening = false

    override fun startSession(day: Int, state: GameState, now: Long) {
        this.day = day
        sessions++
        if (day > 7) laterSessions++
        sessionOpening = state.level <= 10
        if (!sessionOpening) laterUnitSessions++
        sessionBegan = now
        newUnit = false
        limit = BigInteger.valueOf(1_000).pow(if (state.wallet.signum() == 0) 1 else RuleSet.log10(state.wallet).toInt() / 3 + 1)
    }

    override fun endSession() {
        if (!sessionOpening && newUnit) laterUnitSession++
    }

    override fun adAward(slot: AdSlot, credited: BigInteger) {
        adCounts.merge(slot, 1, Long::plus)
        adP.merge(slot, credited, BigInteger::add)
    }

    override fun swipe(engine: Engine, before: GameState, outcome: SwipeOutcome, now: Long) {
        var burst = false
        for (beat in outcome.beats) when (beat) {
            is Beat.Burst -> burst = true
            is Beat.Spawned -> {
                val row = beat.row
                val event = when (row.kind) {
                    RowKind.Call -> "call"
                    RowKind.Alert -> row.alert?.takeIf { it == Alert.LowBattery || it == Alert.Crash }?.name
                    RowKind.Event -> row.event?.takeIf { it == EventCard.Drill || it == EventCard.Lotto }?.name
                    else -> null
                }
                if (event != null) {
                    majorEvents++
                    if (previousEvent == event) repeatedEvents++
                    previousEvent = event
                }
            }
            else -> Unit
        }
        if (before.level <= 10) { opening++; if (burst) earlyBlast++ }
        else { late++; if (burst) lateBlast++ }
        if (outcome.chain >= 2) chains2++
        if (outcome.chain >= 4) chains4++
        if (outcome.chain >= 9) chains9++
        if (outcome.overload) waves++
        if (outcome.earned > before.bestSwipe) {
            if (day <= 7) firstWeekRecords++ else laterRecords++
        }
        if (outcome.capped.signum() > 0) cappedSwipes++
        earned += outcome.earned
        clipped += outcome.capped
        if (outcome.state.wallet >= limit) {
            while (outcome.state.wallet >= limit) limit *= BigInteger.valueOf(1_000)
            if (!newUnit && sessionOpening) { openingUnitRise++; openingFirstUnitMs += now - sessionBegan }
            newUnit = true
        }
        spamMax = maxOf(spamMax, outcome.state.rows.count { it.kind == RowKind.Spam })
    }

    override fun prestige(kind: String, before: GameState, after: GameState, now: Long, gain: Double, stalls: Int, sessions: Int) = Unit
    override fun endDay(day: Int, state: GameState) = Unit
}

private fun storeCompletedCase(file: File, line: String) {
    val dir = file.parentFile.toPath()
    val temporary = Files.createTempFile(dir, file.name, ".tmp")
    try {
        Files.writeString(temporary, line)
        Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private data class LaunchCase(val payer: Payer, val seed: Int, val input: InputPolicy, val resets: Boolean) {
    val label = if (resets) input.name else "NoReset"
    val key = "${payer.name},$seed,$label"
}

fun launchV1(out: File, days: Int, seeds: Int, profiles: List<Profile>, modes: Set<String>) {
    require(days > 0 && seeds > 0 && modes.all { it in setOf("Best", "Random", "Auto", "NoReset") })
    val signature = "launch-v1;days=$days;seeds=$seeds;profiles=${profiles.map { it.payer }};modes=${modes.sorted()};rules=${System.getenv("SIM_RULES").orEmpty()}"
    out.mkdirs()
    val manifest = File(out, "parameters.txt")
    if (manifest.isFile) require(manifest.readText().trim() == signature) { "Results directory already has different parameters" }
    else manifest.writeText(signature + "\n")
    val path = File(out, "cases").apply { mkdirs() }
    val cases = profiles.flatMap { profile -> (1..seeds).flatMap { seed ->
        modes.mapNotNull { name ->
            if (name != "Best" && profile.payer != Payer.Free) return@mapNotNull null
            LaunchCase(profile.payer, seed, if (name == "NoReset") InputPolicy.Best else InputPolicy.valueOf(name), name != "NoReset")
        }
    } }
    val metrics = "profile,seed,input,days,reset_day,tier5_day,tier6_day,sync_day,lv124_day,last_app_day,peak," +
        "stalled_weeks,unlock_gap_days,offline_share,opening_swipes,late_swipes,opening_burst,late_burst,chain2,chain4,chain9," +
        "overloads_per_session,first_week_records_per_day,late_records_per_session,opening_first_unit_minutes,late_unit_sessions_share," +
        "events_per_session,repeat_events,clipped_swipe_share,clipped_p_share,max_spam,ads_per_day,weekly_peaks"
    val ads = "profile,seed,input,slot,claims,credited_p"
    cases.parallelStream().forEach { case ->
        val filename = case.key.replace(',', '-')
        val main = File(path, "$filename.metrics")
        val ad = File(path, "$filename.ads")
        if (main.isFile && ad.isFile) return@forEach
        val horizon = if (case.resets) days else minOf(30, days)
        val feel = LaunchFeel(horizon)
        val tl = playDays(case.seed.toLong(), horizon, DayPlan(), case.resets, payer = case.payer,
            audit = feel, input = case.input)
        val daysUntilLastUnlock = tl.unlocks.map { it.second }.filter { it <= (tl.lastAppDay ?: horizon.toDouble()) }
        val end = tl.lastAppDay ?: horizon.toDouble()
        val gap = (listOf(0.0) + daysUntilLastUnlock + end).zipWithNext { a,b -> b - a }.maxOrNull() ?: 0.0
        val weeks = tl.bestLevelByWeek.values.toList()
        val swipes = feel.opening + feel.late
        fun pct(a: BigInteger, b: BigInteger): String = if (b.signum() <= 0) "0" else a.toBigDecimal().divide(b.toBigDecimal(), MathContext.DECIMAL64).toPlainString()
        val record = listOf(case.key, horizon, num(tl.resetDays.firstOrNull()), num(tl.tierDay[5]), num(tl.tierDay[6]),
            num(tl.syncDays.firstOrNull()), num(tl.level124Day), num(tl.lastAppDay), weeks.maxOrNull() ?: tl.finalLevel,
            weeks.zipWithNext().count { (a,b) -> b <= a }, fixed(gap), pct(tl.offlineP, tl.offlineP + tl.activeP),
            feel.opening, feel.late, fixed(ratio(feel.earlyBlast, feel.opening)), fixed(ratio(feel.lateBlast, feel.late)),
            fixed(ratio(feel.chains2, swipes)), fixed(ratio(feel.chains4, swipes)), fixed(ratio(feel.chains9, swipes)),
            fixed(ratio(feel.waves, feel.sessions)), fixed(ratio(feel.firstWeekRecords, minOf(horizon, 7).toLong())),
            fixed(ratio(feel.laterRecords, feel.laterSessions)), num(if (feel.openingUnitRise == 0L) null else ratio(feel.openingFirstUnitMs, feel.openingUnitRise) / 60_000),
            fixed(ratio(feel.laterUnitSession, feel.laterUnitSessions)), fixed(ratio(feel.majorEvents, feel.sessions)), feel.repeatedEvents,
            fixed(ratio(feel.cappedSwipes, swipes)), pct(feel.clipped, feel.earned + feel.clipped), feel.spamMax,
            fixed(tl.ads.toDouble() / horizon), weeks.joinToString("|")).joinToString(",")
        ad.writeText(AdSlot.entries.joinToString("\n", postfix = "\n") { "${case.key},${it.name},${feel.adCounts[it] ?: 0},${feel.adP[it] ?: zero}" })
        storeCompletedCase(main, record + "\n")
        println(record)
    }
    for ((file, header, extension) in listOf(Triple("seeds.csv",metrics,"metrics"), Triple("ad-slots.csv",ads,"ads"))) {
        File(out,file).bufferedWriter().use { writer ->
            writer.appendLine(header)
            for (case in cases) writer.write(File(path,"${case.key.replace(',', '-')}.$extension").readText())
        }
    }
}
