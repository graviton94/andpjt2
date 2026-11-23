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
    RowKind.Spam -> "스팸"
    RowKind.Call -> "전화"
    RowKind.Event -> r.event!!.label
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
        is Beat.Spawned -> ""
    }
}

fun rows(vararg spec: Pair<String, Int>): List<Row> = spec.mapIndexed { i, (app, n) ->
    when (app) {
        "spam" -> Row(100L + i, RowKind.Spam)
        "wild" -> Row(100L + i, RowKind.Wild, count = n, threshold = 4)
        else -> Row(100L + i, RowKind.App, app, count = n, threshold = 4)
    }
}

private fun with(rows: List<Row>, f: (GameState) -> GameState = { it }): GameState = f(engine.newGame(NOW, 7).copy(rows = rows))

/** 1: the fixed first board. */
fun firstSwipe(): Pair<GameState, SwipeOutcome> = engine.newGame(NOW, 7).let { it to engine.swipe(it, 4, NOW)!! }

/** 2: a two-step unzip. */
fun doubleChain(): Pair<GameState, SwipeOutcome> =
    with(rows("alarm" to 1, "memo" to 2, "alarm" to 3, "weather" to 1, "alarm" to 1, "memo" to 2, "weather" to 2)).let { it to engine.swipe(it, 103, NOW)!! }

/** 3: the same swipe with gauge 80 and overload depth 3 (two Overload assists). */
fun overload(): Pair<GameState, SwipeOutcome> =
    doubleChain().first.copy(gauge = 80, assists = mapOf(Assist.Overload to 2)).let { it to engine.swipe(it, 103, NOW)!! }

/** 4: the same swipe after upgrades: 톡탁톡 Lv10 (milestone ×2), 메일소포 Lv4. */
fun upgraded(): Pair<GameState, SwipeOutcome> =
    overload().first.let { it.copy(appLevels = it.appLevels + ("alarm" to 10) + ("memo" to 4)) }.let { it to engine.swipe(it, 103, NOW)!! }

/**
 * 5: igniter. Deck 오분만(1티어) · 메롱(3티어) · 아고다다(6티어). A 오분만 burst under a 6-tier deck adds
 * 12 + 4×5 = 32 gauge and raises the chain number, so the 6-tier burst after it pays ×3.
 */
fun igniter(): Pair<GameState, SwipeOutcome> = with(
    rows("agoda" to 2, "alarm" to 2, "music" to 1, "alarm" to 2, "agoda" to 2, "music" to 2, "alarm" to 1),
) { it.copy(appLevels = mapOf("alarm" to 1, "music" to 1, "agoda" to 1), active = listOf("alarm", "music", "agoda"), level = 40, gauge = 60) }
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
    with(rows("alarm" to 1, "memo" to 2, "spam" to 0, "wild" to 2, "weather" to 1, "memo" to 2, "alarm" to 1)).let { it to engine.swipe(it, 104, NOW)!! }

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
fun deckRun(seed: Long, size: Int, assists: Boolean): DeckRun {
    val ids = listOf("alarm", "memo", "weather", "flash", "calc", "recorder", "phone", "sms").take(size)
    var s = engine.newGame(NOW, seed).copy(
        level = 45, appLevels = ids.associateWith { 1 }, active = ids,
        assists = if (assists) mapOf(Assist.Sandwich to 5, Assist.Wild to 5) else emptyMap(),
    )
    var t = NOW
    var bursts = 0
    var overloads = 0
    while (t < NOW + 600_000) {
        t += engine.rules.chargeMs
        s = engine.tick(s, t)
        val target = engine.bestTarget(s, t) ?: continue
        val o = engine.swipe(s, target, t) ?: continue
        bursts += o.beats.count { it is Beat.Burst }
        if (o.overload) overloads++
        s = o.state.copy(active = ids)
    }
    return DeckRun(size, engine.rowCount(s), s.swipes, bursts, overloads, s.bestChain)
}

/** 10: first factory reset: exactly the Lv25 P → 10 backup files. */
fun firstReset(): Pair<GameState, GameState> {
    val run = engine.newGame(NOW, 7).copy(level = 25, runEarned = engine.rules.levelP(25))
    var next = engine.factoryReset(run, NOW)!!
    next = engine.buyBackup(next, BackupItem.Value)
    next = engine.buyBackup(next, BackupItem.FastBoot)
    next = engine.buyBackup(next, BackupItem.Sandwich)
    next = engine.buyBackup(next, BackupItem.StartFunds)
    return run to next
}

fun main(args: Array<String> = emptyArray()) {
    if (args.firstOrNull() == "content") return print(contentDoc())
    for ((title, pair) in listOf("1 첫 스와이프" to firstSwipe(), "2 두 단계 연쇄" to doubleChain(), "3 과부하 (게이지 80, 깊이 3)" to overload(),
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
