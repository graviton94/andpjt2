package com.graviton94.notificationcleaner.core

import java.math.BigInteger

/*
 * Prints the fixed-seed examples quoted in docs/core-rules-v3.md.
 *   ./gradlew -p core run
 * CoreRulesTest pins the same numbers, so the document cannot drift from the rules.
 */

val engine = Engine()
const val NOW = 1_000_000L

fun name(r: Row): String = when (r.kind) {
    RowKind.App -> "${Catalog.app(r.app!!).name}${r.count}"
    RowKind.Wild -> "와일드${r.count}"
    RowKind.Spam -> "스팸 문자"
    RowKind.Call -> "스팸 전화"
    RowKind.Event -> r.event!!.label
    RowKind.Alert -> r.alert!!.label
}

fun board(s: GameState): String = s.rows.joinToString(" · ") { name(it) }

fun beats(o: SwipeOutcome): String = o.beats.filter { it !is Beat.Spawned }.joinToString(" → ") { b ->
    when (b) {
        is Beat.Swiped -> "밀기 ${name(b.row)}(+${b.earned})"
        is Beat.Merged -> "합치기 ${name(b.into)}/${b.into.threshold}"
        is Beat.Burst -> "${b.chain}연쇄 폭발(+${b.earned})"
        is Beat.Wave -> "파동${b.index} ${b.cleared}개(+${b.earned})"
        is Beat.FreeUpgrade -> "무료 강화 ${Catalog.app(b.app).name} Lv${b.level}"
        is Beat.LevelUp -> "Lv${b.level}"
        is Beat.Capped -> "과열 보호(−${b.lost})"
        is Beat.Spawn -> "낙하 ${name(b.row)}"
        is Beat.ChainEnd -> "연쇄 종료 ${b.reason}"
        is Beat.Spawned -> ""
    }
}

fun rows(vararg spec: Pair<String, Int>): List<Row> = spec.mapIndexed { i, (app, n) ->
    when (app) {
        "spam" -> Row(100L + i, RowKind.Spam)
        "wild" -> Row(100L + i, RowKind.Wild, count = n, threshold = engine.rules.threshold)
        else -> Row(100L + i, RowKind.App, app, count = n, threshold = engine.rules.threshold)
    }
}

private fun with(rows: List<Row>, f: (GameState) -> GameState = { it }): GameState = f(engine.newGame(NOW, 7).copy(rows = rows))

/** 1: the fixed first board. */
fun firstSwipe(): Pair<GameState, SwipeOutcome> = engine.newGame(NOW, 7).let { it to engine.swipe(it, 4, NOW)!! }

/** A pair with just one notification on each side still bursts in rule B. */
fun touchPair(): Pair<GameState, SwipeOutcome> =
    with(rows("memo" to 1, "weather" to 1, "memo" to 1)).let { it to engine.swipe(it, 101, NOW)!! }

/** 2: a two-step unzip. */
fun doubleChain(): Pair<GameState, SwipeOutcome> =
    with(rows("alarm" to 1, "memo" to 2, "alarm" to 3, "weather" to 1, "alarm" to 1, "memo" to 2, "weather" to 2)).let { it to engine.swipe(it, 103, NOW)!! }

/** 3: the same swipe with just enough charge for overload depth 3 (two Overload assists). */
fun overload(): Pair<GameState, SwipeOutcome> {
    val gauge = 100 - 2 * (engine.rules.gaugePerMerge + engine.rules.gaugePerBurst)
    val s = doubleChain().first.copy(gauge = gauge, assists = mapOf(Assist.Overload to 2))
    return s to engine.swipe(s, 103, NOW)!!
}

/** 4: the same swipe after upgrades: 톡탁톡 Lv10 (milestone ×2), 메일소포 Lv4. */
fun upgraded(): Pair<GameState, SwipeOutcome> =
    overload().first.let { it.copy(appLevels = it.appLevels + ("alarm" to 10) + ("memo" to 4)) }.let { it to engine.swipe(it, 103, NOW)!! }

/**
 * 5: igniter. Deck 시계(1티어) · 메롱(3티어) · 아고다다(6티어). A 시계 burst under a 6-tier deck adds
 * base burst gauge plus 4×5 and raises the chain number, so the 6-tier burst after it pays ×3.
 */
fun igniter(): Pair<GameState, SwipeOutcome> = with(
    rows("agoda" to 2, "alarm" to 2, "music" to 1, "alarm" to 2, "agoda" to 2, "music" to 2, "alarm" to 1),
) { it.copy(appLevels = mapOf("alarm" to 1, "music" to 1, "agoda" to 1), active = listOf("alarm", "music", "agoda"), level = 40, gauge = 100 - 2 * (engine.rules.gaugePerMerge + engine.rules.gaugePerBurst) - 5 * engine.rules.igniterGaugePerTier) }
    .let { it to engine.swipe(it, 102, NOW)!! }

/** 6: the same 4 notifications of 듀오링구 under different decks. Synergy only (서양권 3개 뒤로는 다른 권역 업무 앱). */
fun synergyTable(): List<Pair<String, BigInteger>> {
    val base = engine.newGame(NOW, 7).copy(level = 40)
    fun deck(vararg ids: String) = base.copy(appLevels = ids.associateWith { 1 }, active = ids.toList())
    return listOf(
        "듀오링구 혼자 (업무 1)" to deck("duo", "calc", "weather"),
        "업무 2 (+주움)" to deck("duo", "meet", "weather"),
        "업무 3 (+링크드아웃)" to deck("duo", "meet", "career"),
        "업무 4 (+마마고)" to deck("duo", "meet", "career", "papago"),
        "업무 5 (+글린츠츠)" to deck("duo", "meet", "career", "papago", "glints"),
    ).map { (label, s) -> label to engine.earn(s, "duo", BigInteger.valueOf(4), 0) }
}

/** 7: a wild row merges with anything. */
fun wildMerge(): Pair<GameState, SwipeOutcome> =
    with(rows("alarm" to 1, "memo" to 2, "spam" to 0, "wild" to 2, "spam" to 0, "memo" to 2, "alarm" to 1)).let { it to engine.swipe(it, 104, NOW)!! }

/** 8: @모두 공지 queues a double sandwich A·B·A·B·A. */
fun allHands(): Pair<GameState, SwipeOutcome> = with(
    rows("alarm" to 1, "memo" to 2, "weather" to 1, "alarm" to 2, "memo" to 1, "weather" to 2, "alarm" to 1)
        .toMutableList().also { it[2] = Row(102, RowKind.Event, event = EventCard.AllHands) },
) { it.copy(events = setOf(EventCard.AllHands), equipped = listOf(EventCard.AllHands), level = 14) }.let { it to engine.swipe(it, 102, NOW)!! }

data class DeckRun(val apps: Int, val rows: Int, val swipes: Int, val bursts: Int, val overloads: Int, val chainMax: Int)

/**
 * 9: why a bigger deck is harder and what helps. Ten minutes of the reference player, no purchases, all
 * apps tier 1 so only the deck size differs. [assists] adds Sandwich 5 and Wild 5.
 */
fun deckRun(seed: Long, size: Int, assists: Boolean, rules: RuleSet = engine.rules): DeckRun {
    val game = if (rules === engine.rules) engine else Engine(rules)
    val ids = listOf("alarm", "memo", "weather", "flash", "calc", "recorder", "phone", "sms").take(size)
    var s = game.newGame(NOW, seed).copy(
        level = 45, appLevels = ids.associateWith { 1 }, active = ids,
        assists = if (assists) mapOf(Assist.Sandwich to 5, Assist.Wild to 5) else emptyMap(),
    )
    var t = NOW
    var bursts = 0
    var overloads = 0
    while (t < NOW + 600_000) {
        t += game.rules.chargeMs
        s = game.tick(s, t)
        val target = game.bestTarget(s, t) ?: continue
        val o = game.swipe(s, target, t) ?: continue
        bursts += o.beats.count { it is Beat.Burst }
        if (o.overload) overloads++
        s = o.state.copy(active = ids)
    }
    return DeckRun(size, game.rowCount(s), s.swipes, bursts, overloads, s.bestChain)
}

/** 10: first factory reset. The starting gate's P pays exactly 10 backup files. */
fun firstReset(): Pair<GameState, GameState> {
    val gate = engine.rules.resetLevel
    val run = engine.newGame(NOW, 7).copy(level = gate, runEarned = engine.rules.levelP(gate))
    var next = engine.factoryReset(run, NOW)!!
    next = engine.buyBackup(next, BackupItem.Value)
    next = engine.buyBackup(next, BackupItem.FastBoot)
    next = engine.buyBackup(next, BackupItem.Sandwich)
    next = engine.buyBackup(next, BackupItem.StartFunds)
    return run to next
}

fun main(args: Array<String> = emptyArray()) {
    if (args.firstOrNull() == "content") return print(contentDoc())
    if (args.firstOrNull() == "bench") return bench()
    if (args.firstOrNull() == "android-balance-fixture") {
        val level = engine.rules.resetLevel
        val state = engine.newGame(0, 11)
        val deck = Catalog.starter + listOf("flash", "calc", "recorder")
        val fixture = state.copy(level = level, runEarned = engine.rules.levelP(level),
            appLevels = deck.associateWith { 1 }, active = deck.dropLast(1))
        require(engine.backupFilesNow(fixture) > 0)
        java.io.File(args[1]).writeText(SaveCodec.encode(fixture))
        return
    }
    if (args.firstOrNull() == "serve") return serve(args.getOrNull(1)?.toInt() ?: 8765, java.io.File(args.getOrNull(2) ?: "../app/src/main/assets/game"), args.getOrNull(3)?.let { java.io.File(it) })
    for ((title, pair) in listOf("1 첫 스와이프" to firstSwipe(), "규칙 B의 1+1 즉시 폭발" to touchPair(), "2 두 단계 연쇄" to doubleChain(), "3 과부하 (과부하 직전 게이지, 깊이 3)" to overload(),
        "4 강화 후 같은 스와이프" to upgraded(), "5 점화제" to igniter(), "7 와일드" to wildMerge(), "8 @모두 공지" to allHands())) {
        val (s, o) = pair
        println("### 예시 $title")
        println("- 판: ${board(s)}")
        println("- 진행: ${beats(o)}")
        println("- 합계: ${o.chain}연쇄 · ${o.cleared}개 · +${o.earned} P · 게이지 ${s.gauge} → ${o.state.gauge} · 다음 줄 예약 ${o.state.pattern.map { Catalog.app(it).name }}")
        println()
    }
    println("### 예시 6 시너지 (일정찍 4개, 강화 1)")
    synergyTable().forEach { (l, p) -> println("- $l: $p P") }
    println()
    println("### 예시 9 덱 크기 (10분, 시드 1~3 합)")
    for (assists in listOf(false, true)) for (size in listOf(3, 4, 5, 6, 8)) {
        val r = (1L..3L).map { deckRun(it, size, assists) }
        println("- 앱 ${size}개 ${if (assists) "+ 보조" else ""}: 판 ${r[0].rows}줄 · 스와이프 ${r.sumOf { it.swipes }} · 폭발 ${r.sumOf { it.bursts }} · 과부하 ${r.sumOf { it.overloads }} · 최대 연쇄 ${r.maxOf { it.chainMax }}")
    }
    println()
    val (run, next) = firstReset()
    println("### 예시 10 첫 공장 초기화")
    println("- 백업파일 ${engine.backupFilesNow(run)} · 구매 후 남은 ${next.backup.files} · ${next.backup.items}")
}

/** Swipe and save cost on the JVM (22 greybox budget: a swipe ≤ 2 ms). `./gradlew -p core run --args=bench` */
fun bench() {
    val e = Engine()
    var s = e.newGame(0, 11)
    var t = 0L
    val swipeNs = mutableListOf<Long>(); val planNs = mutableListOf<Long>(); val saveNs = mutableListOf<Long>()
    repeat(3_000) { i ->
        t += 3_000
        s = e.tick(s, t)
        val target = s.rows.last().id
        val a = System.nanoTime()
        val o = e.swipe(s, target, t) ?: return@repeat
        val b = System.nanoTime()
        FxPlan.plan(o)
        val c = System.nanoTime()
        val text = SaveCodec.encode(o.state)
        val d = System.nanoTime()
        s = o.state
        if (i > 500) { swipeNs += b - a; planNs += c - b; saveNs += d - c }
        if (i % 200 == 0) s = s.copy(level = s.level + 4, appLevels = s.appLevels.mapValues { it.value + 20 })
        if (i == 2_999) println("save size ${text.length} chars")
    }
    fun p(xs: List<Long>, q: Double) = xs.sorted()[((xs.size - 1) * q).toInt()] / 1_000.0
    println("swipe  p50 ${p(swipeNs, .5)} µs  p99 ${p(swipeNs, .99)} µs")
    println("fxplan p50 ${p(planNs, .5)} µs  p99 ${p(planNs, .99)} µs")
    println("save   p50 ${p(saveNs, .5)} µs  p99 ${p(saveNs, .99)} µs")
}
