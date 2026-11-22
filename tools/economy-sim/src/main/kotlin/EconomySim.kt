import com.graviton94.notificationcleaner.GameEngine
import com.graviton94.notificationcleaner.ImpactKind
import com.graviton94.notificationcleaner.NoticeApp
import com.graviton94.notificationcleaner.Score
import java.math.BigInteger
import java.math.BigInteger.ZERO
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Economy simulator for the P1 vertical slice (docs/notification-cleaner-p1-design.md).
 *
 * Board, chains, overload waves and score come from the real GameEngine. Each candidate replaces only
 * the parts P1 wants to separate from score: P per swipe, prices and (optionally) the XP curve.
 * Player model: swipe the engine's autoTarget whenever energy allows, at a fixed cadence; buy the next
 * shop app as soon as it is unlocked and affordable, otherwise upgrade the lowest-level owned app.
 *
 *   ./gradlew -p tools/economy-sim run --args="10"      # minutes per run (default 10), 40 seeds
 */

data class Candidate(
    val name: String,
    val pFor: (Score) -> BigInteger,
    val appPrices: List<Long>,
    val upgradePrice: (Int) -> BigInteger,
    /** XP needed to leave level L. null keeps the engine's own leveling. */
    val xpGoal: ((Int) -> Long)? = null,
)

fun big(x: Long): BigInteger = BigInteger.valueOf(x)
fun digits(n: BigInteger) = if (n.signum() <= 0) 1 else n.toString().length
fun isqrt(n: Int) = floor(sqrt(n.toDouble())).toLong()
fun geometric(first: Long, ratio: Double): (Int) -> BigInteger = { big((first * ratio.pow(it - 1)).toLong()) }

val demoGoal: (Int) -> Long = { val d = (it - 1).toLong(); 20 + 6 * d + d * d / 3 }

/** Demo curve up to [knee], then each level costs [ratio] times more. */
fun bentGoal(knee: Int, ratio: Double): (Int) -> Long = { l -> (demoGoal(l) * ratio.pow(maxOf(0, l - knee))).toLong() }

val candidates = listOf(
    Candidate(
        "A 시연판 · P=점수, 앱 1200/4800/16000, 강화 750×9",
        { it.points }, listOf(1_200, 4_800, 16_000), { big(750).multiply(big(9).pow(it - 1)) },
    ),
    Candidate(
        "B 계획서 가안 · P=8+3(자릿수-1)²+5⌊√연쇄⌋, 강화 75×2",
        { s -> val d = digits(s.points) - 1L; big(8 + 3 * d * d + 5 * isqrt(s.combo)) },
        listOf(150, 300, 600), geometric(75, 2.0),
    ),
    Candidate(
        "C v0.1 제안 · P=2+⌊(자릿수-1)/2⌋+2⌊√연쇄⌋, 앱 120/450/1500, 강화 150×1.8, XP Lv8부터 ×1.22",
        { s -> big(2 + (digits(s.points) - 1L) / 2 + 2 * isqrt(s.combo)) },
        listOf(120, 450, 1_500), geometric(150, 1.8), bentGoal(8, 1.22),
    ),
)

private val shop = listOf(NoticeApp.Inbox, NoticeApp.Music, NoticeApp.Payment)
private const val T0 = 1_000_000L
private val MARKS = listOf(1, 3, 5, 10, 20, 30, 60)

class Run {
    var firstSpend: Double? = null
    var firstApp: Double? = null
    val spends = mutableListOf<Double>()
    val levelAt = sortedMapOf<Int, Int>()
    val digitsAt = sortedMapOf<Int, Int>()
    var firstSwipeDigits = 0
    var swipes = 0
    var overloads = 0
    var overloadsIn5 = 0
    var maxDry = 0
    val overloadGaps = mutableListOf<Double>()
}

fun simulate(seed: Long, c: Candidate, everyMs: Long, minutes: Int): Run {
    var now = T0
    var s = GameEngine.initial(now, seed)
    var wallet = ZERO
    var level = 1
    var xp = 0L
    var best = ZERO
    var dry = 0
    var bought = 0
    var lastOverload = T0
    var mark = 0
    val out = Run()

    // With a custom curve the candidate owns level; the engine only sees the resulting level.
    fun gain(value: Long) {
        val goal = c.xpGoal ?: return
        xp += value
        while (xp >= goal(level)) { xp -= goal(level); level++ }
        val photo = level >= 2 && NoticeApp.Photo !in s.owned
        s = s.copy(
            level = level, xp = 0,
            owned = if (photo) s.owned + NoticeApp.Photo else s.owned,
            pendingApps = if (photo) s.pendingApps + NoticeApp.Photo else s.pendingApps,
        )
    }

    while (now < T0 + minutes * 60_000L) {
        now += everyMs
        while (mark < MARKS.size && MARKS[mark] <= minutes && now - T0 >= MARKS[mark] * 60_000L) {
            out.levelAt[MARKS[mark]] = level
            out.digitsAt[MARKS[mark]] = digits(best)
            mark++
        }
        val before = s
        s = GameEngine.advance(s, now)
        if (c.xpGoal == null) level = s.level
        else if (s.level != before.level || s.xp != before.xp) {
            gain(if (s.level == before.level) s.xp - before.xp else 1L)
        }
        if (s.energy == 0) continue
        val target = GameEngine.autoTarget(s) ?: continue
        val result = GameEngine.swipe(s, target, now) ?: continue
        val score = result.state.lastScore!!
        s = result.state.copy(points = ZERO, rewardOffer = ZERO)
        if (c.xpGoal == null) level = s.level else { s = s.copy(level = level, xp = 0); gain(score.xp) }

        out.swipes++
        if (out.swipes == 1) out.firstSwipeDigits = digits(score.points)
        if (result.frames.none { it.kind == ImpactKind.Burst }) { dry++; out.maxDry = maxOf(out.maxDry, dry) } else dry = 0
        if (result.frames.any { it.kind == ImpactKind.Overflow }) {
            out.overloads++
            if (now - T0 <= 5 * 60_000L) out.overloadsIn5++
            out.overloadGaps += (now - lastOverload) / 1000.0
            lastOverload = now
        }
        best = best.max(score.points)
        wallet += c.pFor(score)

        val t = (now - T0) / 1000.0
        while (true) {
            val next = shop.firstOrNull { it !in s.owned }
            if (next != null && level >= next.requiredLevel && bought < c.appPrices.size && wallet >= big(c.appPrices[bought])) {
                wallet -= big(c.appPrices[bought])
                bought++
                s = s.copy(owned = s.owned + next, pendingApps = s.pendingApps + next)
                if (out.firstApp == null) out.firstApp = t
            } else {
                val app = s.owned.filter { !it.isEvent }.minByOrNull { s.appLevels.getOrDefault(it, 1) } ?: break
                val lv = s.appLevels.getOrDefault(app, 1)
                val price = c.upgradePrice(lv)
                if (wallet < price) break
                wallet -= price
                s = s.copy(appLevels = s.appLevels + (app to lv + 1))
            }
            if (out.firstSpend == null) out.firstSpend = t
            out.spends += t
        }
    }
    return out
}

fun pct(xs: List<Double>, p: Double) = if (xs.isEmpty()) Double.NaN else xs.sorted()[((xs.size - 1) * p).toInt()]
fun p50(xs: List<Number>) = pct(xs.map { it.toDouble() }, .5)
fun f0(x: Double) = if (x.isNaN()) "-" else "%.0f".format(x)
fun mark(ok: Boolean) = if (ok) "통과" else "미달"

fun main(args: Array<String>) {
    val minutes = args.getOrNull(0)?.toInt() ?: 10
    val seeds = 1L..40L
    for ((label, every) in listOf("수동 · 행동력이 있으면 1.2초마다" to 1_200L, "자동 · 3.2초마다" to 3_200L)) {
        println("\n=== $label · ${minutes}분 · 시드 ${seeds.count()}개")
        for (c in candidates) {
            val runs = seeds.map { simulate(it, c, every, minutes) }
            val first = runs.mapNotNull { it.firstSpend }
            val third = runs.mapNotNull { it.spends.getOrNull(2) }
            val gaps = runs.flatMap { it.overloadGaps }
            println("[${c.name}]")
            println("  첫 지출 p50 ${f0(pct(first, .5))}s p90 ${f0(pct(first, .9))}s · 첫 앱 구매 p50 ${f0(pct(runs.mapNotNull { it.firstApp }, .5))}s" +
                " · 3번째 지출 p50 ${f0(pct(third, .5))}s · 총 지출 p50 ${f0(p50(runs.map { it.spends.size }))}")
            println("  " + runs[0].levelAt.keys.joinToString(" | ") { m ->
                "${m}분 Lv${f0(p50(runs.map { it.levelAt[m]!! }))} 최고 ${f0(p50(runs.map { it.digitsAt[m]!! }))}자리"
            })
            println("  스와이프 ${f0(p50(runs.map { it.swipes }))} · 과부하 ${f0(p50(runs.map { it.overloads }))}회" +
                " · 간격 p50 ${f0(pct(gaps, .5))}s p90 ${f0(pct(gaps, .9))}s · 최장 무폭발 ${runs.maxOf { it.maxDry }}")
            if (minutes >= 5) {
                val firstOk = pct(first, .5) in 40.0..90.0
                val grow = p50(runs.map { it.digitsAt[5]!! - it.firstSwipeDigits })
                println("  기준: 첫 지출 40~90초 ${mark(firstOk)} · 5분 과부하 3회 이상 ${mark(runs.all { it.overloadsIn5 >= 3 })}" +
                    " · 5분 최고점이 첫 스와이프보다 3자리 이상 ${mark(grow >= 3)}(+${f0(grow)}) · 무폭발 8회 이하 ${mark(runs.all { it.maxDry <= 8 })}")
            }
        }
    }
}
