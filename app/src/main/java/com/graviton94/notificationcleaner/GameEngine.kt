package com.graviton94.notificationcleaner

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
    Inbox("밀린메일", "전부 읽기까지 1,024통 남았어요.", Category.Social, Region.Domestic, 2, 30, 2),
    Music("비트서랍", "한 곡만 더 듣는 중이에요.", Category.Media, Region.NorthAmerica, 3, 30, 3),
    Payment("페이한칸", "결제 기억은 나중에 떠오릅니다.", Category.Daily, Region.NorthAmerica, 4, 45, 4),
    Spam("스팸", "광고 알림 · 다른 알림을 두 번 밀면 복제", null, null),
    Call("전화", "발신번호 표시 제한 · 자동 정리 잠시 대기", null, null),
    Drill("모의 재난문자", "훈련 알림 · 60초 동안 탭해서 배율을 올리세요.", null, null);

    val isEvent: Boolean get() = category == null
}

data class Notice(
    val id: Long,
    val app: NoticeApp,
    val count: Int = 1,
    val threshold: Int = 4,
    val arrivedAt: Long = 0,
    val copyAfter: Int = 0,
)

data class AppScore(val app: NoticeApp, val points: Long)

data class Score(
    val points: Long,
    val xp: Long,
    val combo: Int,
    val appScores: List<AppScore>,
    val direct: Int,
    val eventPercent: Int,
)

data class GameState(
    val rows: List<Notice>,
    val owned: Set<NoticeApp>,
    val appLevels: Map<NoticeApp, Int> = emptyMap(),
    val regions: Map<Region, Long> = emptyMap(),
    val categories: Map<Category, Long> = emptyMap(),
    val pendingApps: List<NoticeApp> = emptyList(),
    val level: Int = 1,
    val xp: Long = 0,
    val points: Long = 0,
    val bestScore: Long = 0,
    val bestCombo: Int = 0,
    val lastScore: Score? = null,
    val rewardOffer: Long = 0,
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
)

data class ChainFrame(
    val rows: List<Notice>,
    val merged: NoticeApp? = null,
    val burst: Int = 0,
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
    private val appPercents = listOf(100, 125, 155, 190, 230)
    private val upgradePrices = listOf(30, 60, 120, 240)

    private data class Burst(val app: NoticeApp, val count: Int)
    private data class Collapse(val rows: List<Notice>, val bursts: List<Burst>, val frames: List<ChainFrame>)

    fun initial(now: Long, seed: Long = now): GameState {
        val rows = listOf(
            NoticeApp.Calendar to 1, NoticeApp.Mail to 2, NoticeApp.Chat to 2,
            NoticeApp.Delivery to 1, NoticeApp.Chat to 2, NoticeApp.Mail to 2, NoticeApp.Calendar to 1,
        ).mapIndexed { i, (app, count) ->
            Notice(i + 1L, app, count, arrivedAt = now - (i + 1L) * 27_000)
        }
        return GameState(rows = rows, owned = starter, nextPassiveAt = now + PASSIVE_MS, random = seed)
    }

    fun weight(level: Int): Int = 1 shl ((level - 1) / 3).coerceIn(0, 18)

    fun goal(level: Int): Long = 20L * weight(level) + 6L * (level - 1)

    fun appPercent(state: GameState, app: NoticeApp): Int =
        appPercents[(state.appLevels[app] ?: 1).coerceIn(1, appPercents.size) - 1]

    fun upgradePrice(state: GameState, app: NoticeApp): Int? =
        upgradePrices.getOrNull((state.appLevels[app] ?: 1) - 1)

    fun regionPercent(total: Long): Int = when {
        total >= 12 -> 150
        total >= 8 -> 125
        total >= 4 -> 110
        else -> 100
    }

    fun categoryPercent(total: Long): Int = when {
        total >= 28 -> 135
        total >= 12 -> 120
        total >= 4 -> 108
        else -> 100
    }

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
        rows.removeAt(index)
        val frames = mutableListOf(ChainFrame(rows.toList()))
        val bursts = mutableListOf<Burst>()
        while (true) {
            val left = rows.zipWithNext().indexOfFirst { (a, b) -> !a.app.isEvent && a.app == b.app }
            if (left < 0) break
            val a = rows[left]
            val b = rows.removeAt(left + 1)
            val merged = a.copy(count = a.count + b.count, threshold = minOf(a.threshold, b.threshold))
            rows[left] = merged
            frames += ChainFrame(rows.toList(), merged = a.app)
            if (merged.count >= merged.threshold) {
                rows.removeAt(left)
                bursts += Burst(merged.app, merged.count)
                frames += ChainFrame(rows.toList(), burst = bursts.size)
            }
        }
        return Collapse(rows.toList(), bursts.toList(), frames.toList())
    }

    fun autoTarget(state: GameState): Long? {
        val middle = state.rows.size / 2
        return state.rows.indices.maxWithOrNull(
            compareBy<Int> { collapse(state.rows, it).bursts.size }.thenBy { -kotlin.math.abs(it - middle) }
        )?.let { state.rows[it].id }
    }

    fun swipe(state: GameState, rowId: Long, now: Long): SwipeResult? {
        val current = advance(state, now)
        if (current.energy == 0) return null
        val index = current.rows.indexOfFirst { it.id == rowId }
        if (index < 0) return null
        val removed = current.rows[index]
        val collapsed = collapse(current.rows, index)
        val report = score(current, removed, collapsed.bursts, now)
        val regions = current.regions.toMutableMap()
        val categories = current.categories.toMutableMap()
        collapsed.bursts.forEach { burst ->
            burst.app.region?.let { regions[it] = regions.getOrDefault(it, 0L) + burst.count }
            burst.app.category?.let { categories[it] = categories.getOrDefault(it, 0L) + burst.count }
        }
        val progressed = gainXp(
            current.copy(
                rows = collapsed.rows,
                energy = current.energy - 1,
                nextChargeAt = if (current.nextChargeAt == 0L) now + CHARGE_MS else current.nextChargeAt,
                points = current.points + report.points,
                lastScore = report,
                bestScore = maxOf(current.bestScore, report.points),
                bestCombo = maxOf(current.bestCombo, report.combo),
                rewardOffer = if (report.combo >= 2) report.points else current.rewardOffer,
                swipes = current.swipes + 1,
                pauseUntil = if (removed.app == NoticeApp.Call) 0 else current.pauseUntil,
                regions = regions,
                categories = categories,
            ), report.xp,
        )
        val next = fill(progressed, now)
        return SwipeResult(next, collapsed.frames + ChainFrame(next.rows))
    }

    private fun score(state: GameState, removed: Notice, bursts: List<Burst>, now: Long): Score {
        val values = linkedMapOf<NoticeApp, Long>()
        bursts.forEachIndexed { i, burst ->
            values[burst.app] = values.getOrDefault(burst.app, 0) + burst.count.toLong() * (i + 2)
        }
        val frenzy = eventPercent(state, now)
        val points = values.map { (app, base) ->
            val bonus = if (state.level >= 3) app.uniqueBonus.toLong() * weight(state.level) else 0
            val appValue = base * appPercent(state, app) + bonus * 100
            val region = regionPercent(state.regions[app.region] ?: 0)
            val category = categoryPercent(state.categories[app.category] ?: 0)
            AppScore(app, appValue * region * category * frenzy / 100_000_000L)
        }
        return Score(
            points = removed.count + points.sumOf { it.points },
            xp = removed.count + values.values.sum(),
            combo = bursts.size,
            appScores = points,
            direct = removed.count,
            eventPercent = frenzy,
        )
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
            Notice(id++, app, threshold = 0, arrivedAt = now, copyAfter = copyAfter)

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
        while (rows.size < ROW_COUNT) {
            val top = rows.firstOrNull()?.app
            val options = owned.filter { it != top }
            val second = rows.getOrNull(1)?.app
            val incoming = pending.indexOfFirst { it != top }
            val app = when {
                incoming >= 0 -> pending.removeAt(incoming)
                second != null && second in options && pick(100) < 52 -> second
                else -> {
                    val start = (state.swipes / 4) % owned.size
                    val wave = (0..2).map { owned[(start + it) % owned.size] }.filter { it in options }
                    val pool = if (wave.isNotEmpty() && pick(100) < 72) wave else options
                    pool[pick(pool.size)]
                }
            }
            val count = listOf(1, 2, 2, 3)[pick(4)] * weight
            rows.add(0, Notice(id++, app, count, 4 * weight, now))
        }
        return state.copy(rows = rows, pendingApps = pending, nextId = id, random = seed, pauseUntil = pause,
            eventUntil = event, eventTaps = taps, lastTapAt = lastTap)
    }

    fun tapEvent(state: GameState, now: Long): GameState {
        if (now >= state.eventUntil || state.eventTaps >= 100 || (state.lastTapAt != 0L && now - state.lastTapAt < 200L)) return state
        return state.copy(eventTaps = state.eventTaps + 1, lastTapAt = now)
    }

    fun claimReward(state: GameState): GameState = if (state.rewardOffer <= 0) state
        else state.copy(points = state.points + state.rewardOffer, rewardOffer = 0)

    fun buy(state: GameState, app: NoticeApp): GameState {
        if (app.isEvent || app in state.owned || state.level < app.requiredLevel || state.points < app.price) return state
        return state.copy(points = state.points - app.price, owned = state.owned + app, pendingApps = state.pendingApps + app)
    }

    fun upgrade(state: GameState, app: NoticeApp): GameState {
        if (app !in state.owned) return state
        val price = upgradePrice(state, app) ?: return state
        if (state.points < price) return state
        return state.copy(points = state.points - price, appLevels = state.appLevels + (app to (state.appLevels.getOrDefault(app, 1) + 1)))
    }
}
