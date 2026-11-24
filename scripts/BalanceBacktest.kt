package notificationcleaner.report

import com.graviton94.notificationcleaner.*
import java.io.File
import java.math.BigInteger.ZERO
import java.math.BigInteger.valueOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.util.SplittableRandom
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private const val DAY = 86_400_000L
private const val HOUR = DAY / 24
private val start = LocalDate.of(2027, 1, 1).atTime(18, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
private val apps = NoticeApp.entries.filterNot { it.isEvent }
private val tiers = listOf(1, 3, 5, 8, 12)
private val arms = listOf("free", "ad", "small", "large", "mastery", "theme", "pass", "starter", "pass+mastery+small")
private data class Session(val start: Long, val end: Long)
private data class Result(val growth: List<String>, val details: List<String>, val mastery: List<String>)

private class AppRun {
    var unlock: Long? = null
    var affordable: Long? = null
    var buy: Long? = null
    var seen: Long? = null
    var pending = false
    var slots = 0
    var late = 0
    var censored = 0
    var spend = ZERO
    var measuredCost = ZERO
    var delta = ZERO
    val firstTier = mutableMapOf<Int, Long>()
    var maxTier = 0
}

private fun playSessions(calendar: Boolean, horizon: Long): Iterator<Session> {
    if (!calendar) return generateSequence(0L) { it + 30 * 60_000L }
        .takeWhile { it < horizon }.map { Session(it, minOf(it + 10 * 60_000, horizon)) }.iterator()
    return (1..2).asSequence().flatMap { month ->
        val m = YearMonth.of(2027, month)
        (0 until 20).map { index -> 1 + index * (m.lengthOfMonth() - 1) / 19 }.asSequence()
            .map { day -> (m.atDay(day).toEpochDay() - LocalDate.of(2027, 1, 1).toEpochDay()) * DAY }
    }.map { Session(it, it + HOUR) }.iterator()
}

private fun run(seed: Long, auto: Boolean, calendar: Boolean, arm: String, auditMastery: Boolean = false): Result {
    val checkpoints = if (calendar) listOf(744 * HOUR, 1416 * HOUR) else listOf(600_000L, 10 * HOUR, 100 * HOUR, 300 * HOUR)
    val intervals = playSessions(calendar, checkpoints.last())
    var session = intervals.next()
    var next = if (intervals.hasNext()) intervals.next() else null
    var state = GameEngine.initial(start, seed)
    val moves = SplittableRandom(seed)
    val appRuns = apps.associateWith { AppRun() }
    val mode = if (calendar) "calendar" else "regular"
    val policy = if (auto) "auto" else "random"
    val growth = mutableListOf<String>()
    val details = mutableListOf<String>()
    val mastery = mutableListOf<String>()
    val masteredMax = mutableMapOf<NoticeApp, java.math.BigInteger>()
    val masteryHits = mutableMapOf<NoticeApp, Long>()
    var firstSpend: Long? = null
    var firstPrestige: Long? = null
    var firstRecovery: Long? = null
    var prestiges = 0
    var clock = 0L
    var pEarned = ZERO
    var pBase = ZERO
    var pTokens = ZERO
    var pComparison = ZERO
    var nextComparisonAd = 0L
    var charged = 0L
    var refilled = 0L
    var loginP = ZERO
    var pSpent = ZERO
    var pReset = ZERO
    var energyWait = 0L
    var swipes = 0L
    var overloads = 0L
    var ads = 0L
    var consumed = 0L
    val bonusHits = mutableMapOf<String, Long>()
    var maxBase = ZERO
    var availCredits = 0L
    var earnedActive = 0L
    var lastUpgrade = 0L
    var maxGap = 0L
    val trackedThresholds = listOf(30, 60, 70, 90)
    val milestone = mutableMapOf<Int, Long>()

    fun login(at: Long) {
        val before = state.points
        state = EconomyRules.login(state, start + at)
        loginP += state.points - before
    }

    fun purchase(p: Product, app: NoticeApp? = null) {
        val whenAt = start + clock
        val receipt = "purchase-${p.name}-${state.account.won}"
        val applied = EconomyRules.purchase(state, p, receipt, whenAt, app)
        check(applied !== state)
        check(EconomyRules.purchase(applied, p, receipt, whenAt, app) == applied)
        availCredits += applied.account.credits - state.account.credits
        state = applied
    }

    fun record(at: Long) {
        for (level in trackedThresholds) if (state.level >= level) milestone.putIfAbsent(level, at)
        for ((app, data) in appRuns) {
            if (state.level >= app.requiredLevel && data.unlock == null) data.unlock = at
            if (state.level >= app.requiredLevel && state.points >= valueOf(app.price.toLong()) && data.affordable == null) data.affordable = at
            if (app !in state.owned) continue
            val level = state.appLevels[app] ?: 1
            if (data.buy == null && app.price == 0) data.buy = at
            if (data.seen == null && app.price == 0 && state.rows.any { it.app == app }) data.seen = at
            data.maxTier = maxOf(data.maxTier, level)
            for (tier in tiers) if (level >= tier) data.firstTier.putIfAbsent(tier, at)
        }
    }

    login(0)
    for (name in arm.split("+")) {
        when (name) {
            "small" -> purchase(Product.SMALL)
            "large" -> purchase(Product.LARGE)
            "mastery" -> purchase(Product.MASTERY, NoticeApp.Chat)
            "theme" -> purchase(Product.THEME)
            "pass", "pass-renew" -> purchase(Product.PASS)
            "trial" -> {
                purchase(Product.SMALL)
                purchase(Product.SMALL)
                state = EconomyRules.redeem(state, true)
            }
            "starter" -> purchase(Product.STARTER, NoticeApp.Chat)
        }
    }
    record(0)

    for (end in checkpoints) {
        while (true) {
            val canPlay = if (auto) maxOf(clock, state.pauseUntil - start) else clock
            val ready = if (state.energy == 0) maxOf(canPlay, state.nextChargeAt - start) else canPlay
            val passive = state.nextPassiveAt - start
            if (passive >= 0 && passive < minOf(ready, session.end, end)) {
                val energyBefore = state.energy
                state = GameEngine.advance(state, start + passive)
                charged += state.energy - energyBefore
                record(passive)
                continue
            }
            if (ready >= session.end && next != null && next.start < end) {
                energyWait += (session.end - canPlay).coerceAtLeast(0)
                earnedActive += session.end - session.start
                val energyBefore = state.energy
                state = GameEngine.resume(state, start + session.end, start + next.start)
                charged += state.energy - energyBefore
                session = next
                clock = session.start
                login(clock)
                if (arm == "pass-renew" && clock == 31 * DAY) purchase(Product.PASS)
                record(clock)
                next = if (intervals.hasNext()) intervals.next() else null
                continue
            }
            if (ready >= minOf(session.end, end)) {
                val active = earnedActive + (minOf(session.end, end) - session.start)
                check(state.points == pEarned + loginP - pSpent - pReset)
                check(pEarned == pBase + pTokens + pComparison)
                check(GameEngine.ENERGY_CAP + charged + refilled - swipes == state.energy.toLong())
                val fields = listOf(mode, policy, seed, arm, end, active, swipes, overloads, state.level, state.xp,
                    firstSpend, firstPrestige, firstRecovery, prestiges, state.account.tokens, state.points, pEarned, loginP,
                    pSpent, pReset, energyWait + (minOf(end, ready, session.end) - canPlay).coerceAtLeast(0), maxBase,
                    state.account.personalBest, state.account.won, availCredits, state.account.credits, ads, consumed,
                    state.account.boosts, maxOf(maxGap, active - lastUpgrade)) +
                    trackedThresholds.map { milestone[it] } + listOf(pBase, pTokens, charged, refilled, state.energy, pComparison) +
                    listOf("마스터리", "증폭기", "중계 알림", "꾸미기").map { bonusHits[it] ?: 0 }
                growth += fields.joinToString(",") { it?.toString().orEmpty() }
                for ((app, data) in appRuns) details += (listOf(mode, policy, seed, arm, end, app.name, data.unlock,
                    data.affordable, data.buy, data.seen, data.late, data.censored + if (data.pending) 1 else 0,
                    data.maxTier, data.spend, data.measuredCost, data.delta) + tiers.map { data.firstTier[it] })
                    .joinToString(",") { it?.toString().orEmpty() }
                if (auditMastery) for (app in apps) {
                    mastery += listOf(policy, seed, end, app.name, state.account.personalBest,
                        masteredMax.getOrDefault(app, ZERO), masteryHits.getOrDefault(app, 0L),
                        Product.MASTERY.won).joinToString(",")
                }
                break
            }

            val energyBefore = state.energy
            state = GameEngine.advance(state, start + ready)
            charged += state.energy - energyBefore
            // Equip and spend by a reproducible policy independent of the future board.
            if (state.account.decor > 0 && !state.account.equipped) state = state.copy(account = state.account.copy(equipped = true))
            if (state.account.credits >= 100 && state.account.boosts == 0) state = EconomyRules.redeem(state, false)
            if (state.account.boosts > 0 && !state.account.armed) state = state.copy(account = state.account.copy(armed = true))
            if (state.account.trials > 0 && state.account.trialUses == 0) {
                state = if (NoticeApp.Chat in state.account.masteries) EconomyRules.exchange(state)
                    else EconomyRules.equip(state, NoticeApp.Chat, true)
            }
            val before = state
            val id = if (auto || before.swipes == 0) checkNotNull(GameEngine.autoTarget(before))
                else before.rows[moves.nextInt(before.rows.size)].id
            val swipe = checkNotNull(GameEngine.swipe(before, id, start + ready))
            state = swipe.state
            val score = checkNotNull(state.lastScore)
            score.bonuses.keys.forEach { source -> bonusHits[source] = (bonusHits[source] ?: 0) + 1 }
            if (auditMastery) {
                val freeBonus = score.bonuses.values.fold(ZERO, java.math.BigInteger::add)
                val other = freeBonus - score.bonuses.getOrDefault("마스터리", ZERO)
                for (app in apps) {
                    val available = appRuns.getValue(app).buy != null
                    val contribution = if (available) score.appScores.firstOrNull { it.app == app }?.points ?: ZERO else ZERO
                    val addition = EconomyRules.masteryBonus(contribution)
                    if (addition > ZERO) masteryHits[app] = masteryHits.getOrDefault(app, 0L) + 1
                    val personal = if (available) EconomyRules.personalScore(score.points, other + addition) else score.personal
                    if (personal > masteredMax.getOrDefault(app, ZERO)) masteredMax[app] = personal
                }
            }
            if (prestiges == 1 && firstRecovery == null && score.points >= state.account.recovery) {
                firstRecovery = ready - checkNotNull(firstPrestige)
            }
            maxBase = maxOf(maxBase, score.points)
            check(score.personal >= score.points && score.personal * valueOf(100) <= score.points * valueOf(108))
            val awardedP = state.points - before.points
            val basicP = EconomyRules.p(score)
            pEarned += awardedP
            pBase += basicP
            pTokens += awardedP - basicP
            val comparisonAd = arm == "compare-ad-p" && before.level >= 35 && ready >= nextComparisonAd &&
                ready + 30_000 < session.end && swipe.frames.any { it.kind == ImpactKind.Overflow }
            val modelP = when {
                comparisonAd -> basicP
                arm == "compare-paid-p" -> basicP / valueOf(10)
                else -> ZERO
            }
            if (modelP > ZERO) {
                state = state.copy(points = state.points + modelP)
                pEarned += modelP
                pComparison += modelP
            }
            if (comparisonAd) {
                ads++
                nextComparisonAd = ready + 600_000
            }
            consumed += before.account.boosts - state.account.boosts
            energyWait += ready - canPlay
            swipes++
            if (swipe.frames.any { it.kind == ImpactKind.Overflow } && ready < 300_000) overloads++
            if (appRuns.values.any { it.pending }) {
                for (row in state.rows.filter { !it.app.isEvent && it.id >= before.nextId }.sortedBy { it.id }) {
                    for ((app, appRun) in appRuns) if (appRun.pending) {
                        appRun.slots++
                        if (row.app == app) {
                            if (appRun.seen == null) appRun.seen = ready
                            if (appRun.slots > 3) appRun.late++
                            appRun.pending = false
                        }
                    }
                }
            }
            record(ready)
            val available = apps.firstOrNull { it !in state.owned && it.price > 0 &&
                state.level >= it.requiredLevel && state.points >= valueOf(it.price.toLong()) }
            val pBefore = state.points
            if (available != null) {
                state = GameEngine.buy(state, available)
                val appRun = appRuns.getValue(available)
                if (appRun.buy == null) appRun.buy = ready
                appRun.pending = true
                appRun.slots = 0
            } else {
                val upgraded = state.owned.minWithOrNull(compareBy<NoticeApp> { GameEngine.upgradePrice(state, it) }.thenBy { it.ordinal })!!
                val old = state
                state = GameEngine.upgrade(state, upgraded)
                if (state != old) {
                    val active = earnedActive + ready - session.start
                    maxGap = maxOf(maxGap, active - lastUpgrade)
                    lastUpgrade = active
                    val data = appRuns.getValue(upgraded)
                    val cost = pBefore - state.points
                    data.spend += cost
                    val oldContribution = score.appScores.firstOrNull { it.app == upgraded }?.points
                    if (oldContribution != null) {
                        // Re-evaluate this exact input with only the chosen app's tier changed.
                        val hypothetical = before.copy(appLevels = before.appLevels +
                            (upgraded to state.appLevels.getValue(upgraded)))
                        val upgradedScore = GameEngine.swipe(hypothetical, id, start + ready)?.state?.lastScore
                        val nextContribution = upgradedScore?.appScores?.firstOrNull { it.app == upgraded }?.points
                        if (nextContribution != null) {
                            data.measuredCost += cost
                            data.delta += nextContribution - oldContribution
                        }
                    }
                }
            }
            if (state.points < pBefore) {
                pSpent += pBefore - state.points
                if (firstSpend == null) firstSpend = ready
            }
            record(ready)
            if (EconomyRules.canRebirth(state) && (arm != "late" || state.level >= 90)) {
                if (firstPrestige == null) firstPrestige = ready
                pReset += state.points
                for (data in appRuns.values) if (data.pending) {
                    data.censored++
                    data.pending = false
                }
                val energyBeforeReset = state.energy
                state = EconomyRules.rebirth(state, start + ready)
                refilled += state.energy - energyBeforeReset
                prestiges++
            }
            clock = ready + if (arm == "fast") 700 else GameEngine.AUTO_MS
            if (comparisonAd) clock += 30_000
            if (arm == "ad" && ready + 30_000 < session.end && swipe.frames.any { it.kind == ImpactKind.Overflow }) {
                val finish = start + ready + 30_000
                val awarded = EconomyRules.ad(state, "ad-$ready", finish)
                if (awarded != state) {
                    check(EconomyRules.ad(awarded, "ad-$ready", finish) == awarded)
                    state = awarded
                    ads++
                    clock += 30_000
                }
            }
        }
    }
    return Result(growth, details, mastery)
}

private fun opening(seedStart: Long, count: Int) {
    val prices = listOf(450, 475, 500, 525, 550)
    println("policy,seed,first_app_p,spend_ms")
    for (auto in listOf(true, false)) for (seed in seedStart until seedStart + count) {
        val first = mutableMapOf<Int, Long>()
        val moves = SplittableRandom(seed)
        var state = GameEngine.initial(start, seed)
        var at = 0L
        while (at <= 150_000 && first.size < prices.size) {
            if (auto) at = maxOf(at, state.pauseUntil - start)
            val row = if (auto || state.swipes == 0) checkNotNull(GameEngine.autoTarget(state))
                else state.rows[moves.nextInt(state.rows.size)].id
            state = checkNotNull(GameEngine.swipe(state, row, start + at)).state
            for (price in prices) {
                if (state.level >= 2 && state.points >= valueOf(price.toLong()) || state.points >= valueOf(600)) {
                    first.putIfAbsent(price, at)
                }
            }
            at += GameEngine.AUTO_MS
        }
        for (price in prices) println("${if (auto) "auto" else "random"},$seed,$price,${first[price] ?: ""}")
    }
}

fun main(args: Array<String>) {
    if (args[0] == "--opening") {
        opening(args[1].toLong(), args[2].toInt())
        return
    }
    val commerce = args[0] == "--commerce"
    val comparison = args[0] == "--comparison"
    val replay = args[0] == "--replay"
    val masteryAudit = args[0] == "--mastery-audit"
    val options = if (commerce || comparison || replay || masteryAudit) args.drop(1) else args.toList()
    val output = File(options[0]).apply { mkdirs() }
    val seedStart = options.getOrNull(1)?.toLong() ?: 161
    val count = if (replay) 1 else options.getOrNull(2)?.toInt() ?: 30
    require(count > 0)
    val pool = Executors.newFixedThreadPool(minOf(4, Runtime.getRuntime().availableProcessors()))
    try {
        File(output, "catalog.csv").bufferedWriter().use { out ->
            out.appendLine("app,unlock_level,unlock_p,level,applied_percent,next_upgrade_p,cumulative_upgrade_p")
            val initial = GameEngine.initial(start)
            for (app in apps) {
                var cumulative = ZERO
                for (level in 1..12) {
                    val state = initial.copy(appLevels = mapOf(app to level))
                    val cost = GameEngine.upgradePrice(state, app)
                    out.appendLine("${app.name},${app.requiredLevel},${app.price},$level,${GameEngine.appPercent(state, app)},$cost,$cumulative")
                    cumulative += cost
                }
            }
        }
        val jobs = if (replay) listOf(Callable {
            require(options[2] in listOf("regular", "calendar") && options[3] in listOf("auto", "random"))
            run(seedStart, options[3] == "auto", options[2] == "calendar", options[4])
        }) else (seedStart until seedStart + count).flatMap { seed ->
            listOf(true, false).flatMap { auto ->
                if (masteryAudit) {
                    listOf(Callable { run(seed, auto, true, "free", auditMastery = true) })
                } else if (comparison) {
                    listOf("compare-paid-p", "compare-ad-p").map { arm -> Callable { run(seed, auto, true, arm) } }
                } else if (commerce) {
                    (arms + listOf("trial", "pass-renew")).map { arm -> Callable { run(seed, auto, true, arm) } }
                } else {
                    listOf(Callable { run(seed, auto, false, "free") }, Callable { run(seed, auto, false, "late") }) +
                        (if (auto) emptyList() else listOf(Callable { run(seed, false, false, "fast") })) +
                        arms.map { arm -> Callable { run(seed, auto, true, arm) } }
                }
            }
        }
        val results = pool.invokeAll(jobs)
        if (masteryAudit) File(output, "mastery.csv").bufferedWriter().use { writer ->
            writer.appendLine("policy,seed,elapsed_ms,app,free_best,mastery_best,hits,test_won")
            for (result in results) result.get().mastery.forEach(writer::appendLine)
        }
        File(output, "growth.csv").bufferedWriter().use { growth ->
            File(output, "apps.csv").bufferedWriter().use { apps ->
                growth.appendLine("mode,policy,seed,arm,elapsed_ms,active_ms,swipes,first_5min_overloads,level,xp,first_spend_ms,first_prestige_ms,first_recovery_ms,prestiges,tokens,p_balance,p_swipes,p_attendance,p_spent,p_reset,energy_wait_ms,base_best,personal_best,won,credits_granted,credits_left,ad_views,boosts_used,boosts_left,max_upgrade_gap_ms,level_30_ms,level_60_ms,level_70_ms,level_90_ms,p_base,p_tokens,energy_charged,energy_refilled,energy_balance,p_model_bm,mastery_hits,boost_hits,special_hits,decor_hits")
                apps.appendLine("mode,policy,seed,arm,elapsed_ms,app,unlock_ms,affordable_ms,buy_ms,seen_ms,late,censored,max_tier,upgrade_p,measured_p,score_delta,tier1_ms,tier3_ms,tier5_ms,tier8_ms,tier12_ms")
                for (result in results) {
                    result.get().growth.forEach(growth::appendLine)
                    result.get().details.forEach(apps::appendLine)
                }
            }
        }
    } finally {
        pool.shutdown()
    }
}
