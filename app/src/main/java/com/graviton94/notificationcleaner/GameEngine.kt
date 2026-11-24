package com.graviton94.notificationcleaner

import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import java.math.BigInteger.valueOf
import kotlin.math.abs

enum class Region(val label: String) {
    Domestic("국내권"),
    Global("글로벌"),
    NorthAmerica("북미권"),
}

enum class Category(val label: String) {
    Social("소통"),
    Daily("생활"),
    Media("미디어"),
}

enum class NoticeApp(
    val label: String,
    val message: String,
    val category: Category?,
    val region: Region?,
    val requiredLevel: Int = 1,
    val price: Int = 0,
    val uniqueBonus: Int = 0,
) {
    Calendar("일정찍", "끝나는 시간이 또 미정이에요.", Category.Daily, Region.Global),
    Mail("메일소포", "중요 표시한 메일이 더 왔어요.", Category.Social, Region.Global, uniqueBonus = 2),
    Chat("톡탁톡", "조용한 단톡방에 메시지가 쌓였어요.", Category.Social, Region.Domestic, uniqueBonus = 3),
    Delivery("문앞왔", "도착 2분 전이라고 한 지 8분 됐어요.", Category.Daily, Region.Domestic),
    Photo("피드또봐", "이미 본 피드를 다시 추천합니다.", Category.Media, Region.Global, 2, uniqueBonus = 2),
    Inbox("밀린메일", "전부 읽기까지 1,024통 남았어요.", Category.Social, Region.Domestic, 2, 475, 2),
    Music("비트서랍", "한 곡만 더 듣는 중이에요.", Category.Media, Region.NorthAmerica, 3, 1_800, 3),
    Payment("페이한칸", "결제 기억은 나중에 떠오릅니다.", Category.Daily, Region.NorthAmerica, 4, 6_000, 4),
    Spam("스팸", "광고 알림 · 다른 알림을 두 번 밀면 복제", null, null),
    Call("전화", "발신번호 표시 제한 · 자동 정리 잠시 대기", null, null),
    Drill("모의 재난문자", "훈련 알림 · 60초 동안 탭해서 배율을 올리세요.", null, null);

    val isEvent: Boolean get() = category == null
}

data class Notice(
    val id: Long,
    val app: NoticeApp,
    val count: BigInteger = ONE,
    val threshold: BigInteger = valueOf(4),
    val arrivedAt: Long = 0,
    val copyAfter: Int = 0,
)

data class AppScore(val app: NoticeApp, val points: BigInteger)

data class Score(
    val points: BigInteger,
    val xp: Long,
    val combo: Int,
    val cleared: BigInteger,
    val appScores: List<AppScore>,
    val eventPercent: Int,
    val personal: BigInteger = points,
    val bonuses: Map<String, BigInteger> = emptyMap(),
)

data class GameState(
    val rows: List<Notice>,
    val owned: Set<NoticeApp>,
    val appLevels: Map<NoticeApp, Int> = emptyMap(),
    val regions: Map<Region, BigInteger> = emptyMap(),
    val categories: Map<Category, BigInteger> = emptyMap(),
    val pendingApps: List<NoticeApp> = emptyList(),
    val level: Int = 1,
    val xp: Long = 0,
    val points: BigInteger = ZERO,
    val bestScore: BigInteger = ZERO,
    val bestCombo: Int = 0,
    val bestCleared: BigInteger = ZERO,
    val pressure: Int = 75,
    val lastScore: Score? = null,
    val rewardOffer: BigInteger = ZERO,
    val energy: Int = GameEngine.ENERGY_CAP,
    val nextChargeAt: Long = 0,
    val nextPassiveAt: Long = 0,
    val auto: Boolean = false,
    val swipes: Int = 0,
    val pauseUntil: Long = 0,
    val eventUntil: Long = 0,
    val eventTaps: Int = 0,
    val lastTapAt: Long = 0,
    val nextId: Long = 8,
    val random: Long = 471,
    val account: Account = Account(),
)

enum class ImpactKind { Swipe, Merge, Burst, Overflow, Finish }

data class ChainFrame(
    val rows: List<Notice>,
    val kind: ImpactKind,
    val target: Notice,
    val partnerId: Long? = null,
    val steps: Int = 1,
    val combo: Int = 0,
    val earned: BigInteger = ZERO,
    val cleared: BigInteger = ZERO,
    val weighted: BigInteger? = null,
)

data class SwipeResult(val state: GameState, val frames: List<ChainFrame>)

object GameEngine {
    const val ENERGY_CAP = 12
    const val CHARGE_MS = 3_000L
    const val AUTO_MS = 3_200L
    const val EVENT_MS = 60_000L
    private const val ROW_COUNT = 7
    private const val PASSIVE_MS = 6_000L
    private val starter = setOf(NoticeApp.Calendar, NoticeApp.Mail, NoticeApp.Chat, NoticeApp.Delivery)
    private val three = valueOf(3)
    private val hundred = valueOf(100)
    private val pointDivisor = valueOf(100_000_000)

    private data class Collapse(val rows: List<Notice>, val frames: List<ChainFrame>) {
        val breaks = frames.filter { it.kind == ImpactKind.Burst }
    }

    fun initial(now: Long, seed: Long = now): GameState {
        val rows = listOf(
            NoticeApp.Calendar to 3, NoticeApp.Mail to 2, NoticeApp.Chat to 2,
            NoticeApp.Delivery to 1, NoticeApp.Chat to 2, NoticeApp.Mail to 2, NoticeApp.Calendar to 3,
        ).mapIndexed { i, (app, count) ->
            Notice(i + 1L, app, valueOf(count.toLong()), arrivedAt = now - (i + 1L) * 27_000)
        }
        return GameState(rows = rows, owned = starter, nextPassiveAt = now + PASSIVE_MS, random = seed)
    }

    fun weight(level: Int): BigInteger = three.pow(((level - 1) / 3).coerceAtLeast(0))

    fun goal(level: Int): Long {
        val depth = (level - 1).toLong()
        return 20 + 6 * depth + depth * depth / 3
    }

    fun appPercent(state: GameState, app: NoticeApp): BigInteger {
        val steps = (state.appLevels.getOrDefault(app, 1) - 1).coerceAtLeast(0)
        return hundred * valueOf(8).pow(steps) / valueOf(5).pow(steps)
    }

    fun upgradePrice(state: GameState, app: NoticeApp): BigInteger =
        valueOf(600) * three.pow(2 * (state.appLevels.getOrDefault(app, 1) - 1))

    fun regionPercent(total: BigInteger): Int = 100 + ((total.bitLength() - 2).coerceAtLeast(0) * 10)

    fun categoryPercent(total: BigInteger): Int = 100 + ((total.bitLength() - 3).coerceAtLeast(0) * 12)

    fun eventPercent(state: GameState, now: Long): Int = if (now < state.eventUntil) 100 + state.eventTaps else 100

    fun advance(state: GameState, now: Long): GameState {
        val recharged = recharge(state, now)
        if (now < recharged.nextPassiveAt || recharged.nextPassiveAt == 0L) return recharged
        val ticks = ((now - recharged.nextPassiveAt) / PASSIVE_MS + 1).coerceAtMost(300)
        return gainXp(recharged.copy(nextPassiveAt = now + PASSIVE_MS), ticks)
    }

    fun resume(state: GameState, savedAt: Long, now: Long): GameState {
        val away = (now - savedAt).coerceIn(0L, 30 * 60_000L)
        val adjusted = if (state.nextChargeAt > now + CHARGE_MS) state.copy(nextChargeAt = now + CHARGE_MS) else state
        return gainXp(recharge(adjusted, now).copy(nextPassiveAt = now + PASSIVE_MS), away / 24_000L)
    }

    private fun recharge(state: GameState, now: Long): GameState {
        if (state.energy == ENERGY_CAP || state.nextChargeAt == 0L || now < state.nextChargeAt) return state
        val steps = ((now - state.nextChargeAt) / CHARGE_MS + 1).coerceAtMost((ENERGY_CAP - state.energy).toLong())
        val energy = state.energy + steps.toInt()
        return state.copy(energy = energy, nextChargeAt = if (energy == ENERGY_CAP) 0 else state.nextChargeAt + steps * CHARGE_MS)
    }

    private fun gainXp(state: GameState, value: Long): GameState {
        var xp = state.xp + value
        var level = state.level
        while (xp >= goal(level)) {
            xp -= goal(level)
            level += 1
        }
        return if (level >= 2 && NoticeApp.Photo !in state.owned) {
            state.copy(xp = xp, level = level, owned = state.owned + NoticeApp.Photo, pendingApps = state.pendingApps + NoticeApp.Photo)
        } else {
            state.copy(xp = xp, level = level)
        }
    }

    private fun collapse(input: List<Notice>, index: Int): Collapse {
        val rows = input.toMutableList()
        var pivot = index
        val removed = rows.removeAt(index)
        val frames = mutableListOf(ChainFrame(rows.toList(), ImpactKind.Swipe, removed))
        while (pivot in 1 until rows.size) {
            val a = rows[pivot - 1]
            val b = rows[pivot]
            if (a.app.isEvent || a.app != b.app) break
            val merged = a.copy(count = a.count + b.count, threshold = minOf(a.threshold, b.threshold))
            rows.removeAt(pivot)
            rows[pivot - 1] = merged
            frames += ChainFrame(rows.toList(), ImpactKind.Merge, merged, partnerId = b.id)
            if (merged.count < merged.threshold) break
            rows.removeAt(--pivot)
            frames += ChainFrame(rows.toList(), ImpactKind.Burst, merged, partnerId = b.id)
        }
        return Collapse(rows, frames)
    }

    fun autoTarget(state: GameState): Long? {
        val middle = state.rows.size / 2
        return state.rows.indices.maxWithOrNull(
            compareBy<Int> { if (state.rows[it].app == NoticeApp.Spam) 1 else 0 }
                .thenBy { collapse(state.rows, it).breaks.size }
                .thenBy { -abs(it - middle) },
        )?.let { state.rows[it].id }
    }

    fun swipe(state: GameState, rowId: Long, now: Long): SwipeResult? {
        val current = advance(state, now)
        if (current.energy == 0) return null
        val index = current.rows.indexOfFirst { it.id == rowId }
        if (index < 0) return null
        val removed = current.rows[index]
        val collapsed = collapse(current.rows, index)
        val breaks = collapsed.breaks
        val pressure = (current.pressure + 21 + breaks.size * 11).coerceAtMost(100)
        val overload = breaks.isNotEmpty() && (pressure == 100 || breaks.size >= 3)
        val depth = 4 + current.level / 3 + breaks.size - 3
        val overflow = if (overload) overflow(collapsed, depth.coerceAtLeast(3)) else emptyList()
        val (report, frames) = score(current, removed, collapsed.frames + overflow, now)
        val regions = current.regions.toMutableMap()
        val categories = current.categories.toMutableMap()
        (breaks + overflow).forEach { frame ->
            val notice = frame.target
            notice.app.region?.let { regions[it] = regions.getOrDefault(it, ZERO) + notice.count }
            notice.app.category?.let { categories[it] = categories.getOrDefault(it, ZERO) + notice.count }
        }
        val progressed = gainXp(
            current.copy(
                rows = collapsed.rows,
                energy = current.energy - 1,
                nextChargeAt = if (current.nextChargeAt == 0L) now + CHARGE_MS else current.nextChargeAt,
                points = current.points + EconomyRules.p(report) * valueOf(10 + minOf(10, current.account.tokens)) / valueOf(10),
                lastScore = report,
                bestScore = maxOf(current.bestScore, report.points),
                bestCombo = maxOf(current.bestCombo, report.combo),
                bestCleared = maxOf(current.bestCleared, report.cleared),
                pressure = if (overload) 0 else pressure,
                rewardOffer = ZERO,
                swipes = current.swipes + 1,
                pauseUntil = if (removed.app == NoticeApp.Call) 0 else current.pauseUntil,
                regions = regions,
                categories = categories,
            ), report.xp,
        )
        val filled = fill(progressed, now)
        val (account, personal) = EconomyRules.settle(current, filled, report, frames, now)
        return SwipeResult(filled.copy(account = account, lastScore = personal), frames)
    }

    private fun overflow(collapse: Collapse, depth: Int): List<ChainFrame> {
        // Each burst drains a larger wave of the same apps' queued notifications.
        val bursts = collapse.breaks.map { it.target }
        var backlog = bursts.fold(ZERO) { total, notice -> total + notice.count }
        val beats = minOf(depth, 9)
        var preceding = bursts.size
        return List(beats) { i ->
            val steps = depth / beats + if (i >= beats - depth % beats) 1 else 0
            val growth = valueOf(3L + 2L * i)
            val factor = growth.pow(steps)
            // A beat can cover several consecutive waves at high levels. Drain their full geometric sum.
            val cleared = backlog * growth * (factor - ONE) / (growth - ONE)
            val delta = growth - ONE
            val weighted = cleared * valueOf(preceding + 1L) +
                backlog * growth * (ONE + factor * (valueOf(steps.toLong()) * delta - ONE)) / delta.pow(2)
            preceding += steps
            backlog *= factor
            val app = bursts[i % bursts.size]
            ChainFrame(collapse.rows, ImpactKind.Overflow, app.copy(count = cleared), steps = steps, weighted = weighted)
        }
    }

    private fun score(
        state: GameState, removed: Notice, frames: List<ChainFrame>, now: Long,
    ): Pair<Score, List<ChainFrame>> {
        val appScores = linkedMapOf<NoticeApp, BigInteger>()
        val numerators = mutableMapOf<NoticeApp, BigInteger>()
        val event = eventPercent(state, now)
        var earned = removed.count
        var cleared = removed.count
        var combo = 0
        val scored = frames.map { frame ->
            if (frame.kind == ImpactKind.Burst || frame.kind == ImpactKind.Overflow) {
                val (app, count) = frame.target.let { it.app to it.count }
                combo += frame.steps
                val bonus = if (state.level >= 3 && app !in numerators) {
                    weight(state.level) * valueOf(app.uniqueBonus.toLong()) * hundred
                } else ZERO
                val base = (frame.weighted ?: (count * valueOf(combo + 1L))) * appPercent(state, app) + bonus
                val region = regionPercent(state.regions[app.region] ?: ZERO)
                val category = categoryPercent(state.categories[app.category] ?: ZERO)
                val numerator = numerators.getOrDefault(app, ZERO) +
                    base * valueOf(region.toLong()) * valueOf(category.toLong()) * valueOf(event.toLong())
                numerators[app] = numerator
                val points = numerator / pointDivisor
                earned += points - appScores.getOrDefault(app, ZERO)
                appScores[app] = points
                cleared += count
            }
            frame.copy(combo = combo, earned = earned, cleared = cleared)
        }
        val xp = 2L + removed.count.bitLength().coerceAtMost(12) + combo.coerceAtMost(15) * 4L
        return Score(earned, xp, combo, cleared, appScores.map { AppScore(it.key, it.value) }, event) to scored
    }

    private fun fill(state: GameState, now: Long): GameState {
        val rows = state.rows.toMutableList()
        val pending = state.pendingApps.toMutableList()
        var seed = state.random
        var id = state.nextId
        var pause = state.pauseUntil
        var event = state.eventUntil
        var taps = state.eventTaps
        var lastTap = state.lastTapAt

        fun pick(bound: Int): Int {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) % bound).toInt()
        }

        fun eventRow(app: NoticeApp, copyAfter: Int = 0) =
            Notice(id++, app, threshold = ZERO, arrivedAt = now, copyAfter = copyAfter)

        val extras = rows.count { it.app.isEvent }
        if (extras < 2 && state.swipes >= 2 && (state.swipes - 2) % 3 == 0 && rows.size < ROW_COUNT) {
            when (((state.swipes - 2) / 3) % 3) {
                0 -> rows.add(rows.size / 2, eventRow(NoticeApp.Spam, state.swipes + 2))
                1 -> {
                    rows.add(rows.size / 2, eventRow(NoticeApp.Call))
                    pause = now + 5_000
                }
                2 -> if (state.level >= 3 && now >= event) {
                    rows.add(0, eventRow(NoticeApp.Drill))
                    event = now + EVENT_MS
                    taps = 0
                    lastTap = 0
                }
            }
        }
        val spam = rows.indexOfFirst { it.copyAfter != 0 && state.swipes >= it.copyAfter }
        if (spam >= 0 && rows.size < ROW_COUNT) {
            rows[spam] = rows[spam].copy(copyAfter = 0)
            rows.add(spam + 1, eventRow(NoticeApp.Spam))
        }

        val owned = NoticeApp.entries.filter { it in state.owned }
        val weight = weight(state.level)
        // Refill emptied space as an alternating sequence players can read at a glance.
        if (rows.size <= 2 && pending.isEmpty() && extras == 0) {
            val candidates = owned.filter { it != rows.firstOrNull()?.app }.toMutableList()
            val a = candidates.removeAt(pick(candidates.size))
            val b = candidates.removeAt(pick(candidates.size))
            val blocker = candidates[pick(candidates.size)]
            val pattern = listOf(a, b, blocker, b, a)
            rows.addAll(0, pattern.map { app -> Notice(id++, app, weight * valueOf(2), weight * valueOf(4), now) })
        }
        while (rows.size < ROW_COUNT) {
            val top = rows.firstOrNull()?.app
            val options = owned.filter { it != top }
            val second = rows.getOrNull(1)?.app
            val incoming = pending.indexOfFirst { it != top }
            val chargedNeighbor = rows.getOrNull(1)?.takeIf { it.app in options && state.pressure == 100 }
            val app = when {
                incoming >= 0 -> pending.removeAt(incoming)
                chargedNeighbor != null -> chargedNeighbor.app
                second != null && second in options && pick(100) < 57 -> second
                else -> {
                    val start = (state.swipes / 4) % owned.size
                    val wave = (0..2).map { owned[(start + it) % owned.size] }.filter { it in options }
                    val pool = if (wave.isNotEmpty() && pick(100) < 72) wave else options
                    pool[pick(pool.size)]
                }
            }
            val randomCount = weight * valueOf(listOf(1L, 2L, 2L, 3L)[pick(4)])
            val needed = if (chargedNeighbor?.app == app) chargedNeighbor.threshold - chargedNeighbor.count else ZERO
            val count = maxOf(randomCount, needed)
            rows.add(0, Notice(id++, app, count, weight * valueOf(4), now))
        }
        return state.copy(rows = rows, pendingApps = pending, nextId = id, random = seed, pauseUntil = pause,
            eventUntil = event, eventTaps = taps, lastTapAt = lastTap)
    }

    fun tapEvent(state: GameState, now: Long): GameState {
        if (now >= state.eventUntil || state.eventTaps >= 100 || (state.lastTapAt != 0L && now - state.lastTapAt < 200L)) return state
        return state.copy(eventTaps = state.eventTaps + 1, lastTapAt = now)
    }

    fun claimReward(state: GameState): GameState = if (state.rewardOffer <= ZERO) state
        else state.copy(points = state.points + state.rewardOffer, rewardOffer = ZERO)

    fun buy(state: GameState, app: NoticeApp): GameState {
        if (app.isEvent || app in state.owned || state.level < app.requiredLevel || state.points < valueOf(app.price.toLong())) return state
        return state.copy(points = state.points - valueOf(app.price.toLong()), owned = state.owned + app, pendingApps = state.pendingApps + app)
    }

    fun upgrade(state: GameState, app: NoticeApp): GameState {
        if (app !in state.owned) return state
        val price = upgradePrice(state, app)
        if (state.points < price) return state
        return state.copy(points = state.points - price, appLevels = state.appLevels + (app to (state.appLevels.getOrDefault(app, 1) + 1)))
    }
}
