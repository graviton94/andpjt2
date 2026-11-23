import com.graviton94.notificationcleaner.core.Assist
import com.graviton94.notificationcleaner.core.BackupItem
import com.graviton94.notificationcleaner.core.Beat
import com.graviton94.notificationcleaner.core.Catalog
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.EventCard
import com.graviton94.notificationcleaner.core.GameState
import com.graviton94.notificationcleaner.core.AdSlot
import com.graviton94.notificationcleaner.core.CreditItem
import com.graviton94.notificationcleaner.core.account

/*
 * Economy v0.3: weeks of everyday play (docs/economy-v0.3.md).
 * A day is [sessions] sessions of [sessionMin] minutes of direct play spread over 16 waking hours,
 * offline in between and overnight. Offline income is settled on return. Resets follow [ResetPolicy].
 *
 *   ./gradlew -p tools/economy-sim run --args="daily 56 4"   # days, seeds
 */

private val engine = Engine()
private val shop = com.graviton94.notificationcleaner.core.Shop(engine)

/** Spending profiles (docs/bible/17-monetization.md). */
enum class Payer(val label: String) { Free("무과금·무광고"), Ads("광고 시청"), Pass("월간 패스"), Whale("고과금") }

/** A spending profile; [slots] limits which ad placements it uses (for one-at-a-time ablation). */
data class Profile(val label: String, val payer: Payer, val slots: Set<AdSlot> = AdSlot.entries.toSet())
/** The app with the highest unlock level: "last app" in the reports. */
private val LAST_APP get() = Catalog.apps.maxBy { it.unlockLevel }.id
private const val START = 1_000_000L
private const val DAY = 86_400_000L

data class DayPlan(val sessions: Int = 4, val sessionMin: Int = 15)

class Timeline {
    /** (active minutes, day, label) of every first-time unlock. */
    val unlocks = mutableListOf<Triple<Double, Double, String>>()
    val tierDay = sortedMapOf<Int, Double>()
    var lastAppDay: Double? = null
    val resetDays = mutableListOf<Double>()
    var credits = 0L
    var bought = 0L
    val stars = mutableMapOf<Int, Int>()
    var ads = 0
    val syncDays = mutableListOf<Double>()
    /** Best level when the update arrived (update mode). */
    var updateLevel = 0
    val bestLevelByWeek = sortedMapOf<Int, Int>()
    val digitsByWeek = sortedMapOf<Int, Int>()
    var activeMin = 0.0
    var lifetimeFiles = 0L
    var offlineP = java.math.BigInteger.ZERO
    var activeP = java.math.BigInteger.ZERO
    val lifetimeByWeek = sortedMapOf<Int, Long>()
    val runDigitsByDay = sortedMapOf<Int, Int>()
}

/** Every level that opens something: apps, cards, assists, deck slots, event slots, reset, calls. */
fun unlockLevels(): Map<Int, List<String>> {
    val r = engine.rules
    val m = sortedMapOf<Int, MutableList<String>>()
    fun add(l: Int, s: String) { m.getOrPut(l) { mutableListOf() } += s }
    Catalog.apps.filter { it.unlockLevel > 1 }.forEach { add(it.unlockLevel, "앱 ${it.name}") }
    EventCard.entries.forEach { add(it.unlockLevel, "카드 ${it.label}") }
    Assist.entries.forEach { a -> add(a.unlockLevel, "판 강화 ${a.label}"); a.caps.drop(1).forEach { add(it.first, "${a.label} 최대 ${it.second}") } }
    Catalog.proLevel.forEach { (id, l) -> add(l, "${Catalog.app(id).name} 프로") }
    r.offlineCapSteps.drop(1).forEach { add(it.first, "오프라인 ${it.second}시간") }
    r.maxSlotSteps.drop(1).forEach { add(it.first, "덱 최대 ${it.second}") }
    r.minSlotSteps.drop(1).forEach { add(it.first, "덱 최소 ${it.second}") }
    r.eventSlotSteps.forEach { add(it.first, "이벤트 슬롯 ${it.second}") }
    add(r.resetLevel, "공장 초기화")
    add(r.callUnlockLevel, "스팸 전화")
    return m
}

/** [update]: on this day (0-based) the pack is switched on, as a content update reaching a veteran save. */
fun playDays(seed: Long, days: Int, plan: DayPlan, resets: Boolean, spam: Boolean = false, update: Pair<Int, com.graviton94.notificationcleaner.core.ContentPack>? = null, payer: Payer = Payer.Free, slots: Set<AdSlot> = AdSlot.entries.toSet()): Timeline {
    if (Catalog.packs.isNotEmpty()) Catalog.packs = emptySet()  // shared global: only touch it when needed (parallel runs)
    val tl = Timeline()
    var levels = unlockLevels()
    var s = chooseDeck(engine.newGame(START, seed), DeckPolicy.Synergy)
    var t = START
    var everLevel = 1
    var everResets = 0
    var stallSessions = 0
    var sinceReset = 0
    var everSyncs = 0
    val gap = (16 * 60 / plan.sessions) * 60_000L
    fun dayOf(ms: Long) = (ms - START) / DAY.toDouble()
    fun note(label: String) { tl.unlocks += Triple(tl.activeMin, dayOf(t), label) }

    for (day in 0 until days) {
        if (update != null && day == update.first) {
            Catalog.packs = setOf(update.second)
            levels = unlockLevels()
            tl.updateLevel = maxOf(s.backup.bestLevel, s.level)
            tl.lastAppDay = null  // from now on the pack's top app is the last app
        }
        for (session in 0 until plan.sessions) {
            val begin = START + day * DAY + session * gap
            if (t < begin) {
                val (back, got) = engine.settleOffline(s, begin - t, begin)
                s = back
                tl.offlineP += got
                t = begin
                if (payer != Payer.Free && AdSlot.OfflineDouble in slots && got.signum() > 0) shop.watchAd(s, AdSlot.OfflineDouble, t, got)?.let { s = it; tl.offlineP += got; tl.ads++ }
            }
            s = economy(s, t, session, payer, tl, slots)
            s = engine.activateDnd(s, t)  // 방해 금지 at the start of a session
            val levelAtStart = s.level
            val end = t + plan.sessionMin * 60_000L
            while (t < end) {
                t += engine.chargeMs(s)
                tl.activeMin += engine.chargeMs(s) / 60_000.0
                s = engine.tick(s, t)
                val target = engine.bestTarget(s, t) ?: continue
                val best = s.bestSwipe
                val o = engine.swipe(s, target, t) ?: continue
                s = o.state
                // 최고 기록 ×2: offered when a swipe beats the run's best (only worth it once the run is going).
                if (payer != Payer.Free && AdSlot.BestDouble in slots && s.level >= engine.rules.bestDoubleFrom && o.earned > best) shop.watchAd(s, AdSlot.BestDouble, t, o.earned)?.let { s = it; tl.activeP += o.earned; tl.ads++ }
                if (payer != Payer.Free && AdSlot.Battery in slots && s.energy == 0) shop.watchAd(s, AdSlot.Battery, t)?.let { s = it; tl.ads++ }
                if (payer == Payer.Whale && s.energy == 0) shop.buyItem(s, CreditItem.Battery, t)?.let { s = it }
                // 알림훈련: the reference player taps about half the drill (50 taps -> ×1.5).
                if (o.beats.any { it is Beat.Swiped && it.row.event == EventCard.Drill }) repeat(50) { s = engine.tapDrill(s, t) }
                tl.activeP += o.earned
                val (spent, bought) = spend(s, DeckPolicy.Synergy)
                s = spent
                if (bought.isNotEmpty() || o.beats.any { it is Beat.LevelUp }) s = chooseDeck(s, DeckPolicy.Synergy)
                while (s.level > everLevel) {
                    everLevel++
                    levels[everLevel]?.forEach { note(it) }
                }
                val tier = s.appLevels.keys.maxOf { Catalog.app(it).tier }
                tl.tierDay.putIfAbsent(tier, dayOf(t))
                if (tl.lastAppDay == null && LAST_APP in s.appLevels) tl.lastAppDay = dayOf(t)
                // Exploit probe: from week 2, reset the moment Lv25 is reached.
                if (spam && day >= 7 && s.level >= engine.rules.resetLevel && engine.backupFilesNow(s) > 0) {
                    s = chooseDeck(spendBackup(engine.factoryReset(s, t)!!), DeckPolicy.Synergy)
                    tl.resetDays += dayOf(t)
                }
            }
            stallSessions = if (s.level > levelAtStart) 0 else stallSessions + 1
            if (System.getenv("SIM_TRACE") != null && seed == 1L && resets && day in 5..6) println("  d$day s$session lv ${s.level} P ${s.runEarned.toString().length}자리 지갑 ${s.wallet.toString().length} 덱 ${s.active.size} 백업 ${s.backup.files}/${s.backup.lifetime} 초기화 ${s.backup.resets}")
            // Reset when it raises the lifetime-file bonus by 60% or more, or when the run has stalled for 3 sessions.
            val offer = engine.backupFilesNow(s)
            val pct = engine.rules.lifetimeFilePercent
            val gain = (100.0 + pct * (s.backup.lifetime + offer)) / (100.0 + pct * s.backup.lifetime)
            sinceReset++
            // 2nd prestige: sync at the wall, when resets have stopped paying for a while.
            val gb = engine.cloudGbNow(s)
            if (resets && gb > 0 && sinceReset >= 4 && stallSessions >= 2) {
                if (System.getenv("SIM_RESETS") != null && seed == 1L) println("  동기화 d${"%.1f".format(dayOf(t))} Lv${s.level} +$gb GB 평생 ${s.backup.cloud.lifetime + gb} 이번 주기 파일 ${s.backup.lifetime}")
                s = engine.sync(s, t)!!
                tl.syncDays += dayOf(t)
                everSyncs++
                com.graviton94.notificationcleaner.core.CloudItem.entries.filter { it.minSyncs == everSyncs }.forEach { note("클라우드 ${it.label}") }
                s = chooseDeck(spendBackup(spendCloud(s)), DeckPolicy.Synergy)
                stallSessions = 0
                sinceReset = 0
                continue
            }
            if (resets && s.level >= engine.rules.resetLevel && offer > 0 && (gain >= 1.6 || stallSessions >= 3)) {
                if (System.getenv("SIM_RESETS") != null && seed == 1L) println("  초기화 d${"%.1f".format(dayOf(t))} Lv${s.level} P${s.runEarned.toString().length}자리 +$offer 파일 평생 ${s.backup.lifetime + offer} 가치Lv${s.backup.lv(com.graviton94.notificationcleaner.core.BackupItem.Value)} 유효초기화 ${s.backup.resets} 덱 ${s.active.size} 최고티어 ${s.appLevels.keys.maxOf { com.graviton94.notificationcleaner.core.Catalog.app(it).tier }} 정체 $stallSessions")
                if (payer == Payer.Whale) shop.buyItem(s, CreditItem.BackupBoost, t)?.let { s = it }
                s = engine.factoryReset(s, t)!!
                tl.resetDays += dayOf(t)
                everResets++
                if (s.backup.cloud.syncs == 0) BackupItem.entries.filter { it.minResets == everResets }.forEach { note("백업 ${it.label}") }
                s = chooseDeck(spendBackup(spendCloud(s)), DeckPolicy.Synergy)
                stallSessions = 0
                sinceReset = 0
            }
        }
        tl.runDigitsByDay[day + 1] = s.runEarned.toString().length
        val week = day / 7 + 1
        tl.bestLevelByWeek[week] = maxOf(s.backup.bestLevel, s.level, tl.bestLevelByWeek[week] ?: 0)
        tl.digitsByWeek[week] = s.bestSwipe.toString().length
        tl.lifetimeByWeek[week] = s.backup.lifetime
    }
    tl.credits = s.account.creditsFree
    tl.bought = s.account.creditsBought
    s.account.premium.forEach { (id, n) -> tl.stars.merge(Catalog.app(id).tier, n, Int::plus) }
    return tl
}

/** Daily claims, passes, timed boosts and premium stars for a [payer] at the start of a session. */
private fun economy(state: GameState, t: Long, session: Int, payer: Payer, tl: Timeline, slots: Set<AdSlot>): GameState {
    var s = shop.claimAttendance(state, t)
    s = shop.claimMissions(s, t)
    if (payer == Payer.Pass || payer == Payer.Whale) {
        if (!shop.passActive(s, t)) s = shop.buyPass(s, t)
        if (payer == Payer.Whale && session == 0 && dayOf(t).toLong() % 30 == 0L) s = shop.credit(s, 7_500, bought = true)  // ₩69,000 a month
        s = shop.claimPassDaily(s, t)
    }
    if (payer != Payer.Free) {
        for (slot in listOf(AdSlot.Boost, AdSlot.Warp, AdSlot.CreditBox, AdSlot.FreeUpgrade).filter { it in slots }) {
            if (session < 3 && !shop.boostActive(s, t) || slot != AdSlot.Boost) shop.watchAd(s, slot, t)?.let { s = it; tl.ads++ }
        }
    }
    if (payer == Payer.Whale) {
        if (!shop.boostActive(s, t)) shop.buyItem(s, CreditItem.Boost, t)?.let { s = it }
        shop.buyItem(s, CreditItem.Warp, t)?.let { s = it }
    }
    // Premium: the next star of the deck's best app, as soon as it is affordable.
    while (true) {
        val id = s.active.filter { shop.premiumOpen(s, it) }.maxByOrNull { Catalog.app(it).tier * 10 - s.account.stars(it) } ?: break
        s = shop.upgradePremium(s, id) ?: break
    }
    return s
}

private fun dayOf(ms: Long) = (ms - START) / DAY.toDouble()

fun monetization(days: Int, seeds: Long, profiles: List<Profile>) {
    val plan = DayPlan()
    println("=== 과금 프로필 ${days}일 · 시드 $seeds · ${profiles.size}개 프로필 병렬")
    fun d(x: Double?) = if (x == null || x.isNaN()) "-" else "%.1f일".format(x)
    // Every (profile, seed) run is independent: spread them over all cores.
    val jobs = profiles.flatMap { p -> (1L..seeds).map { p to it } }
    val done = jobs.parallelStream().map { (p, seed) -> p to playDays(seed, days, plan, true, payer = p.payer, slots = p.slots) }.toList()
    for (p in profiles) {
        val runs = done.filter { it.first == p }.map { it.second }
        val off = p50(runs.map { val tot = it.offlineP + it.activeP; if (tot.signum() == 0) 0.0 else it.offlineP.toBigDecimal().divide(tot.toBigDecimal(), java.math.MathContext.DECIMAL64).toDouble() * 100 })
        println("[${p.label}] 첫 초기화 ${d(p50(runs.mapNotNull { it.resetDays.firstOrNull() }))} · 마지막 앱 ${d(p50(runs.mapNotNull { it.lastAppDay }))} (${runs.count { it.lastAppDay != null }}/$seeds) · 오프라인 ${"%.0f%%".format(off)}" +
            " · 무료 데이터 ${p50(runs.map { it.credits.toDouble() }).toLong()} · 구매 데이터 ${p50(runs.map { it.bought.toDouble() }).toLong()} · 광고/일 ${"%.1f".format(p50(runs.map { it.ads.toDouble() / days }))}")
        println("      주별 최고 레벨 " + runs[0].bestLevelByWeek.keys.joinToString(" · ") { w -> "${w}주 ${p50(runs.map { (it.bestLevelByWeek[w] ?: 0).toDouble() }).toInt()}" })
    }
}

/** Standard profiles, or "ablate": no ads, each ad placement alone, all ads. */
fun profilesFor(arg: String?): List<Profile> = when (arg) {
    null, "all" -> Payer.entries.map { Profile(it.label, it) }
    "ablate" -> listOf(Profile("광고 없음", Payer.Ads, emptySet())) + AdSlot.entries.map { Profile("${it.label}만", Payer.Ads, setOf(it)) } + Profile("광고 전부", Payer.Ads)
    else -> arg.split(",").map { Payer.valueOf(it) }.map { Profile(it.label, it) }
}

fun daily(days: Int, seeds: Long) {
    val plan = DayPlan()
    println("=== 일상 플레이 ${days}일 · 하루 ${plan.sessions}회 × ${plan.sessionMin}분 직접 + 오프라인 · 시드 $seeds · 시너지 덱")
    val withReset = (1L..seeds).map { playDays(it, days, plan, true) }
    val noReset = (1L..seeds).map { playDays(it, minOf(days, 30), plan, false) }
    fun d(x: Double?) = if (x == null || x.isNaN()) "-" else "%.1f일".format(x)
    println("[환생] 첫 초기화 ${d(p50(withReset.mapNotNull { it.resetDays.firstOrNull() }))} · 초기화 횟수 ${p50(withReset.map { it.resetDays.size.toDouble() }).toInt()}" +
        " · " + (2..10).joinToString(" · ") { tr -> "${tr}티어 ${d(p50(withReset.mapNotNull { it.tierDay[tr] }))}" } +
        " · 마지막 앱 ${d(p50(withReset.mapNotNull { it.lastAppDay }))} (${withReset.count { it.lastAppDay != null }}/$seeds)")
    println("       주별 최고 레벨 " + withReset[0].bestLevelByWeek.keys.joinToString(" · ") { w -> "${w}주 ${p50(withReset.map { (it.bestLevelByWeek[w] ?: 0).toDouble() }).toInt()}" })
    println("       주별 최고점 자릿수 " + withReset[0].digitsByWeek.keys.joinToString(" · ") { w -> "${w}주 ${p50(withReset.map { (it.digitsByWeek[w] ?: 0).toDouble() }).toInt()}" })
    println("[환생 없음 30일] 최고 레벨 ${p50(noReset.map { (it.bestLevelByWeek.values.maxOrNull() ?: 0).toDouble() }).toInt()} · " +
        (2..10).joinToString(" · ") { tr -> "${tr}티어 ${d(p50(noReset.mapNotNull { it.tierDay[tr] }))}" } + " · 마지막 앱 ${noReset.count { it.lastAppDay != null }}/$seeds")
    // Unlock density: gaps between first-time unlocks.
    val gapsMin = withReset.flatMap { tl -> tl.unlocks.zipWithNext { a, b -> b.first - a.first } }
    val gapsDay = withReset.flatMap { tl -> tl.unlocks.zipWithNext { a, b -> b.second - a.second } }
    println("[오프라인 몫] P 중 오프라인 비율 중앙값 " + "%.0f%%".format(p50(withReset.map { val tot = it.offlineP + it.activeP; if (tot.signum() == 0) 0.0 else it.offlineP.toBigDecimal().divide(tot.toBigDecimal(), java.math.MathContext.DECIMAL64).toDouble() * 100 })))
    println("[환생 없음] 회차 누적 P 자릿수 " + listOf(1, 2, 3, 5, 7, 10, 14, 21, 30).joinToString(" · ") { d -> "${d}일 ${p50(noReset.mapNotNull { it.runDigitsByDay[d]?.toDouble() }).toInt()}" })
    println("[해금 밀도] 해금 ${p50(withReset.map { it.unlocks.size.toDouble() }).toInt()}개 · 간격(직접 플레이) p50 ${fmtMin(pct(gapsMin, .5))} p90 ${fmtMin(pct(gapsMin, .9))} 최대 ${fmtMin(gapsMin.maxOrNull() ?: 0.0)} · 달력 최대 공백 ${d(gapsDay.maxOrNull())}")
    if (System.getenv("SIM_SPAM") != null) {
        val spamRuns = (1L..seeds).map { playDays(it, days, plan, true, spam = true) }
        println("[단타 탐지] 평생 백업파일 주별 — 일반: " + withReset[0].lifetimeByWeek.keys.joinToString(" · ") { w -> "${w}주 ${p50(withReset.map { (it.lifetimeByWeek[w] ?: 0).toDouble() }).toLong()}" })
        println("            단타(2주차부터 Lv25 즉시 초기화): " + spamRuns[0].lifetimeByWeek.keys.joinToString(" · ") { w -> "${w}주 ${p50(spamRuns.map { (it.lifetimeByWeek[w] ?: 0).toDouble() }).toLong()}" })
        println("            단타 최고 레벨: " + spamRuns[0].bestLevelByWeek.keys.joinToString(" · ") { w -> "${w}주 ${p50(spamRuns.map { (it.bestLevelByWeek[w] ?: 0).toDouble() }).toInt()}" } + " · 초기화 ${p50(spamRuns.map { it.resetDays.size.toDouble() }).toInt()}회 · 마지막 앱 ${spamRuns.count { it.lastAppDay != null }}/$seeds")
    }
    val tl = withReset[0]
    println("  시드1 해금 순서: " + tl.unlocks.joinToString(" | ") { "${d(it.second)} ${it.third}" })
    println("  시드1 초기화: " + tl.resetDays.joinToString(" ") { d(it) })
    println("[동기화] 첫 동기화 ${d(p50(withReset.mapNotNull { it.syncDays.firstOrNull() }))} · 횟수 ${p50(withReset.map { it.syncDays.size.toDouble() }).toInt()} · 시드1 " + tl.syncDays.joinToString(" ") { d(it) })
}


/**
 * Update check: [launchDays] of play on launch content, then [pack] switches on and play continues for
 * [extraDays]. Reports whether growth restarts and when the pack's top app opens.
 */
fun updateCheck(launchDays: Int, extraDays: Int, seeds: Long, pack: com.graviton94.notificationcleaner.core.ContentPack) {
    val plan = DayPlan()
    println("=== 업데이트 검증 · ${pack.label} · 출시 ${launchDays}일 뒤 적용, ${extraDays}일 더 · 시드 $seeds")
    val runs = (1L..seeds).map { playDays(it, launchDays + extraDays, plan, true, update = launchDays to pack) }
    Catalog.packs = setOf(pack)
    val top = Catalog.apps.maxBy { it.unlockLevel }
    if (Catalog.packs.isNotEmpty()) Catalog.packs = emptySet()  // shared global: only touch it when needed (parallel runs)
    fun d(x: Double?) = if (x == null || x.isNaN()) "-" else "%.1f일".format(x)
    println("적용 시점 최고 레벨 ${p50(runs.map { it.updateLevel.toDouble() }).toInt()} · 11티어 첫 앱 ${d(p50(runs.mapNotNull { it.tierDay[11] }))} · 팩 마지막 앱(${top.name} Lv${top.unlockLevel}) ${d(p50(runs.mapNotNull { it.lastAppDay }))} (${runs.count { it.lastAppDay != null }}/$seeds)")
    println("주별 최고 레벨 " + runs[0].bestLevelByWeek.keys.joinToString(" · ") { w -> "${w}주 ${p50(runs.map { (it.bestLevelByWeek[w] ?: 0).toDouble() }).toInt()}" })
    println("동기화 횟수 ${p50(runs.map { it.syncDays.size.toDouble() }).toInt()} · 적용 후 동기화 ${p50(runs.map { tl -> tl.syncDays.count { it >= launchDays }.toDouble() }).toInt()}")
}
