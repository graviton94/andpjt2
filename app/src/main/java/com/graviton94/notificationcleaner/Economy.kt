package com.graviton94.notificationcleaner

import java.math.BigInteger
import java.math.BigInteger.ZERO
import java.math.BigInteger.valueOf
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

enum class Product(val label: String, val won: Int) {
    SMALL("유료재화 100", 500), LARGE("유료재화 600", 2900),
    MASTERY("영구 마스터리", 2900), THEME("야간 테마", 1900),
    PASS("월간 정리 패스", 4900), STARTER("스타터 팩", 3900)
}

data class TestReceipt(val product: Product, val app: NoticeApp?, val id: String)

data class Account(
    val credits: Long = 0, val boosts: Int = 0, val armed: Boolean = false,
    val masteries: Set<NoticeApp> = emptySet(), val focus: NoticeApp? = null,
    val trials: Int = 0, val trialUses: Int = 0, val decor: Int = 0, val equipped: Boolean = false,
    val theme: Boolean = false, val starter: Boolean = false, val passUntil: Long = 0,
    val overloads: Int = 0, val passClaims: Int = 0, val month: String = "", val day: String = "",
    val attendance: Int = 0, val adDay: String = "", val receipts: Set<String> = emptySet(),
    val tokens: Long = 0, val recovery: BigInteger = ZERO, val personalBest: BigInteger = ZERO,
    val legacyBest: BigInteger = ZERO, val mark: Long? = null, val won: Long = 0,
    val previews: Int = 0, val cards: Int = 0, val savedFocus: NoticeApp? = null,
    val lastClock: Long = 0, val challengeOpen: Boolean = false, val challengeBest: BigInteger = ZERO, val lastPurchase: TestReceipt? = null,
)

object EconomyRules {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val tenThousand = valueOf(10_000)
    fun p(score: Score): BigInteger {
        val digits = valueOf(score.points.toString().length - 1L)
        return valueOf(8) + valueOf(3) * digits.pow(2) + valueOf(5) * valueOf(kotlin.math.sqrt(score.combo.toDouble()).toLong())
    }
    private fun date(now: Long) = Instant.ofEpochMilli(now).atZone(seoul).toLocalDate()
    fun login(state: GameState, now: Long): GameState {
        val a = state.account
        if (now < a.lastClock) return state
        val day = date(now)
        if (day.toString() <= a.day) return state
        val month = YearMonth.from(day).toString()
        val step = if (month == a.month) (a.attendance + 1).coerceAtMost(20) else 1
        val current = a.copy(month = month, day = day.toString(), attendance = step, lastClock = now)
        if (month == a.month && a.attendance == 20) return state.copy(account = current)
        val points = when (step) { 2 -> 30; 4 -> 40; 7, 11 -> 50; 14, 15 -> 60; 18 -> 70; else -> 0 }
        val boosts = when (step) { 3, 6, 9, 12, 16, 19 -> 1; 10 -> 2; 20 -> 4; else -> 0 }
        val decor = when (step) { 1, 13 -> 20; 5 -> 60; else -> 0 }
        return state.copy(points = state.points + valueOf(points.toLong()), account = current.copy(
            boosts = a.boosts + boosts, trials = a.trials + when (step) { 10 -> 1; 20 -> 2; else -> 0 },
            decor = maxOf(a.decor, decor), previews = a.previews + if (step in listOf(8, 17)) 1 else 0,
            cards = a.cards + if (step == 15) 1 else 0))
    }

    private fun claimPass(a: Account): Account {
        val tier = listOf(5, 15, 30).count { a.overloads >= it }
        return a.copy(passClaims = maxOf(a.passClaims, tier), decor = maxOf(a.decor, tier * 40).coerceAtMost(100))
    }

    fun purchase(state: GameState, product: Product, id: String, now: Long, app: NoticeApp? = null): GameState {
        val a = state.account
        if (id.isBlank() || id in a.receipts || now < a.lastClock) return state
        val changed = when (product) {
            Product.SMALL -> a.copy(credits = a.credits + 100)
            Product.LARGE -> a.copy(credits = a.credits + 600)
            Product.MASTERY, Product.STARTER -> {
                if (app == null || app !in state.owned || app in a.masteries ||
                    app == a.focus && a.trialUses > 0 || product == Product.STARTER && a.starter) return state
                a.copy(masteries = a.masteries + app, focus = if (a.trialUses == 0) app else a.focus,
                    credits = a.credits + if (product == Product.STARTER) 300 else 0,
                    starter = a.starter || product == Product.STARTER)
            }
            Product.THEME -> if (a.theme) return state else a.copy(theme = true, decor = 100, equipped = true)
            Product.PASS -> claimPass(a.copy(passUntil = Instant.ofEpochMilli(maxOf(now, a.passUntil))
                .atZone(seoul).plusMonths(1).toInstant().toEpochMilli()))
        }
        return state.copy(account = changed.copy(receipts = a.receipts + id, won = a.won + product.won, lastClock = now, lastPurchase = TestReceipt(product, app, id)))
    }

    fun ad(state: GameState, id: String, now: Long): GameState {
        val a = state.account
        val day = date(now).toString()
        if (state.level < 35 || day <= a.adDay || id.isBlank() || id in a.receipts || now < a.lastClock) return state
        return state.copy(account = a.copy(adDay = day, boosts = a.boosts + 1, receipts = a.receipts + id, lastClock = now))
    }

    fun redeem(state: GameState, trial: Boolean): GameState {
        val a = state.account
        val cost = if (trial) 200L else 100L
        if (a.credits < cost) return state
        return state.copy(account = a.copy(credits = a.credits - cost,
            trials = a.trials + if (trial) 1 else 0, boosts = a.boosts + if (trial) 0 else 1))
    }

    fun equip(state: GameState, app: NoticeApp, useTrial: Boolean): GameState {
        val a = state.account
        if (app !in state.owned) return state
        if (!useTrial && app in a.masteries && a.trialUses == 0) return state.copy(account = a.copy(focus = app))
        if (!useTrial || a.trials == 0 || a.trialUses != 0 || app in a.masteries) return state
        return state.copy(account = a.copy(focus = app, trials = a.trials - 1, trialUses = 3))
    }

    fun exchange(state: GameState): GameState {
        val a = state.account
        if (a.trials == 0 || a.masteries.isEmpty()) return state
        return state.copy(account = a.copy(trials = a.trials - 1, boosts = a.boosts + 2))
    }

    private fun part(amount: BigInteger, bp: Int) = amount * valueOf(bp.toLong()) / tenThousand

    fun masteryBonus(contribution: BigInteger) = part(contribution, 200)

    fun personalScore(base: BigInteger, totalBonus: BigInteger) =
        base + minOf(part(base, 800), totalBonus)

    fun settle(state: GameState, after: GameState, score: Score, frames: List<ChainFrame>, now: Long): Pair<Account, Score> {
        val a = state.account
        val overload = frames.any { it.kind == ImpactKind.Overflow }
        val contribution = score.appScores.firstOrNull { it.app == a.focus }?.points ?: ZERO
        val trial = contribution > ZERO && a.trialUses > 0 && a.focus !in a.masteries
        val boost = overload && a.armed && a.boosts > 0
        var marker = a.mark
        var special = false
        for (frame in frames) {
            if (marker != null && frame.kind == ImpactKind.Merge &&
                (frame.target.id == marker || frame.partnerId == marker)) marker = frame.target.id
            if (marker != null && frame.kind == ImpactKind.Burst && frame.target.id == marker) {
                special = true
                marker = null
            }
        }
        marker = marker?.takeIf { id -> after.rows.any { it.id == id } }
        if (marker == null && now in a.lastClock until a.passUntil) {
            marker = after.rows.firstOrNull { it.id >= state.nextId && it.id % 11 == 0L && !it.app.isEvent }?.id
        }
        val sources = linkedMapOf(
            "마스터리" to if (trial || a.focus in a.masteries) masteryBonus(contribution) else ZERO,
            "증폭기" to if (boost) part(score.points, 300) else ZERO,
            "중계 알림" to if (special) part(score.points, 200) else ZERO,
            "꾸미기" to if (a.equipped) part(score.points, a.decor) else ZERO,
        ).filterValues { it > ZERO }
        val personal = personalScore(score.points, sources.values.fold(ZERO, BigInteger::add))
        val changed = a.copy(boosts = a.boosts - if (boost) 1 else 0, armed = a.armed && !boost,
            trialUses = a.trialUses - if (trial) 1 else 0, mark = marker,
            overloads = a.overloads + if (overload) 1 else 0, personalBest = maxOf(a.personalBest, personal))
        return (if (now in a.lastClock until a.passUntil && overload) claimPass(changed) else changed) to
            score.copy(personal = personal, bonuses = sources)
    }

    fun enterChallenge(state: GameState, now: Long): GameState {
        val a = state.account
        if (a.challengeOpen || now < a.lastClock || now >= a.passUntil && a.previews == 0) return state
        return state.copy(account = a.copy(challengeOpen = true,
            previews = a.previews - if (now < a.passUntil) 0 else 1))
    }

    fun challengeBoard(now: Long): GameState {
        val board = GameEngine.initial(now, 35)
        return board.copy(account = Account(mark = 5))
    }

    fun playChallenge(state: GameState, row: Long, now: Long): GameState {
        val a = state.account
        if (!a.challengeOpen) return state
        val score = GameEngine.swipe(challengeBoard(now), row, now)?.state?.lastScore ?: return state
        return state.copy(account = a.copy(challengeOpen = false, challengeBest = maxOf(a.challengeBest, score.personal)))
    }

    fun canRebirth(state: GameState) = state.level >= 70 && state.bestScore >= state.account.recovery

    fun rebirth(state: GameState, now: Long): GameState {
        if (!canRebirth(state)) return state
        val a = state.account
        return GameEngine.initial(now, state.random xor now).copy(level = 45, account = a.copy(
            tokens = a.tokens + 1 + (state.level - 70) / 10, recovery = state.bestScore, mark = null))
    }
}
