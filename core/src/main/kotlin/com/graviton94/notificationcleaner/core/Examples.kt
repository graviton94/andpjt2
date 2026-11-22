package com.graviton94.notificationcleaner.core

import java.math.BigInteger

/*
 * Prints the fixed-seed examples quoted in docs/core-rules-v2.md.
 *   ./gradlew -p core run
 * Tests in ExamplesTest pin the same numbers, so the document cannot drift from the rules.
 */

private val engine = Engine()
private const val NOW = 1_000_000L

fun board(state: GameState): String = state.rows.joinToString(" · ") { r ->
    when (r.kind) {
        RowKind.App -> "${engine.rules.app(r.app!!).name}${r.count}"
        RowKind.Spam -> "스팸"
        RowKind.Call -> "전화"
    }
}

fun beats(o: SwipeOutcome): String = o.beats.filter { it !is Beat.Spawned }.joinToString(" → ") { b ->
    when (b) {
        is Beat.Swiped -> "밀기 ${b.row.app?.let { engine.rules.app(it).name } ?: b.row.kind.name}(+${b.earned})"
        is Beat.Merged -> "합치기 ${engine.rules.app(b.into.app!!).name}${b.into.count}/${b.into.threshold}"
        is Beat.Burst -> "${b.chain}연쇄 폭발 ×${b.chain + 1}(+${b.earned})"
        is Beat.Wave -> "과부하 파동${b.index} ${b.cleared}개 ×${b.chain + 1}(+${b.earned})"
        is Beat.LevelUp -> "Lv${b.level}"
        is Beat.Spawned -> ""
    }
}

fun rows(vararg spec: Pair<String, Int>): List<Row> =
    spec.mapIndexed { i, (app, n) -> if (app == "spam") Row(100L + i, RowKind.Spam) else Row(100L + i, RowKind.App, app, n, 4) }

/** Example 1: the fixed first board. */
fun firstSwipe(): Pair<GameState, SwipeOutcome> {
    val s = engine.newGame(NOW, seed = 7)
    return s to engine.swipe(s, rowId = 4, now = NOW)!!
}

/** Example 2: a two-step unzip. */
fun doubleChain(): Pair<GameState, SwipeOutcome> {
    val s = engine.newGame(NOW, 7).copy(rows = rows("chat" to 1, "mail" to 2, "chat" to 3, "delivery" to 1, "chat" to 1, "mail" to 2, "delivery" to 2))
    return s to engine.swipe(s, 103, NOW)!!
}

/** Example 3: the same swipe with a nearly full gauge and overload depth 3. */
fun overload(): Pair<GameState, SwipeOutcome> {
    val (s, _) = doubleChain()
    val charged = s.copy(gauge = 80, overloadDepth = 3)
    return charged to engine.swipe(charged, 103, NOW)!!
}

/** Example 4: the same swipe after upgrades: 톡탁톡 Lv10 (×10 × milestone ×2), 메일소포 Lv4. */
fun upgraded(): Pair<GameState, SwipeOutcome> {
    val (s, _) = overload()
    val up = s.copy(appLevels = s.appLevels + ("chat" to 10) + ("mail" to 4))
    return up to engine.swipe(up, 103, NOW)!!
}

/** Example 5: spam left on the board copies itself 3 swipes later. */
fun spamCopies(): List<String> {
    var s = engine.newGame(NOW, 11)
    val log = mutableListOf<String>()
    var t = NOW
    repeat(14) {
        t += 3_000
        val target = s.rows.last { it.kind != RowKind.Spam }.id
        s = engine.swipe(s, target, t)!!.state
        log += "${s.swipes}: 스팸 ${s.rows.count { it.kind == RowKind.Spam }}줄"
    }
    return log
}

data class TenMinutes(val swipes: Int, val bursts: Int, val chainMax: Int, val overloads: Int, val wallet: BigInteger, val level: Int)

/** Example 6: ten minutes of auto-clean versus a player taking the best immediate swipe. No purchases. */
fun tenMinutes(seed: Long, player: Boolean): TenMinutes {
    var s = engine.newGame(NOW, seed)
    var t = NOW
    var bursts = 0
    var overloads = 0
    val step = if (player) engine.rules.chargeMs else engine.rules.autoIntervalMs
    while (t < NOW + 600_000) {
        t += step
        s = engine.tick(s, t)
        val target = (if (player) engine.bestTarget(s, t) else engine.autoTarget(s)) ?: continue
        val o = engine.swipe(s, target, t, if (player) Actor.Player else Actor.Auto) ?: continue
        bursts += o.beats.count { it is Beat.Burst }
        if (o.overload) overloads++
        s = o.state
    }
    return TenMinutes(s.swipes, bursts, s.bestChain, overloads, s.wallet, s.level)
}

fun main() {
    val examples = listOf("1 첫 스와이프" to firstSwipe(), "2 두 단계 연쇄" to doubleChain(), "3 과부하 (게이지 80, 깊이 3)" to overload(), "4 강화 후 같은 스와이프" to upgraded())
    for ((title, pair) in examples) {
        val (s, o) = pair
        println("### 예시 $title")
        println("- 판: ${board(s)}")
        println("- 진행: ${beats(o)}")
        println("- 합계: ${o.chain}연쇄 · ${o.cleared}개 처리 · +${o.earned} P · 게이지 ${s.gauge} → ${o.state.gauge}")
        println()
    }
    println("### 예시 5 스팸 복제")
    println(spamCopies().joinToString(" | "))
    println()
    println("### 예시 6 10분 · 구매 없음 · 시드 1~5")
    for (seed in 1L..5L) {
        val a = tenMinutes(seed, player = false)
        val p = tenMinutes(seed, player = true)
        println("시드 $seed | 자동 $a | 직접 $p")
    }
    println()
    println("### 알림 1개의 가치 (1티어)")
    println(listOf(1, 2, 5, 9, 10, 24, 25, 50, 100).joinToString(" · ") { "Lv$it ${engine.rules.notificationValue(engine.rules.apps[0], it)}" })
}
