package com.graviton94.notificationcleaner.core

/** Lifetime counters behind achievements. */
enum class Stat { Bursts, Swipes, SpamCleared, CrashRestarts, Upgrades, Resets, Syncs, BestChain, BestWave }

/** One daily mission: a counter to reach today. */
enum class Mission(val label: String, val stat: Stat, val goal: Long) {
    Bursts("알림 30번 터뜨리기", Stat.Bursts, 30),
    Swipes("알림 150번 밀기", Stat.Swipes, 150),
    Chain("5연쇄 한 번", Stat.BestChain, 5),
    Spam("스팸 10개 치우기", Stat.SpamCleared, 10),
    Upgrades("앱 강화 20번", Stat.Upgrades, 20),
    Waves("과부하 파동 3번", Stat.BestWave, 3),
}

/** Achievements → titles (18 meta-and-ops). Rewards are 꾸미기 조각 only, never progress. */
enum class Achievement(val title: String, val pieces: Int) {
    FirstReset("초기화 입문", 1), FirstSync("클라우드 이민자", 2), LastApp("알림 없는 사람", 3),
    Chain10("연쇄 반응", 2), Wave9("알림 해일", 2), Spam1000("스팸 사냥꾼", 2), Crash100("재부팅 장인", 2),
    Pro10("프로 정리왕", 2), Streak30("알림 개근왕", 3),
}

/**
 * Meta progress: daily missions and achievements, fed by swipe outcomes and lifecycle events.
 * Pure like the engine: the game calls [record] after every swipe and [reset]/[sync] instead of the engine's.
 */
class Meta(private val engine: Engine) {
    private fun stat(s: GameState, k: Stat) = s.account.stats[k.name] ?: 0L

    /** Today's three missions: a fixed daily pick, the same for everyone that day. */
    fun missionsFor(now: Long): List<Mission> {
        val all = Mission.entries
        val start = Math.floorMod(dayOf(now) * 5, all.size.toLong()).toInt()
        // Three consecutive entries from a day-dependent start: a different mix every day.
        return (0 until 3).map { all[(start + it * 2) % all.size] }.distinct().let { if (it.size == 3) it else (it + all).distinct().take(3) }
    }

    fun missionProgress(s: GameState, m: Mission, now: Long): Long =
        if (s.account.missionProgressDay != dayOf(now)) 0 else s.account.missionProgress[m.stat.name] ?: 0

    fun missionsDone(s: GameState, now: Long) = missionsFor(now).all { missionProgress(s, it, now) >= it.goal }

    /** Daily mission reward: the 데이터 payout once all of today's missions are done. */
    fun claimMissions(shop: Shop, s: GameState, now: Long): GameState? = if (missionsDone(s, now)) shop.claimMissions(s, now) else null

    /** Count what a swipe did; unlock achievements. */
    fun record(s: GameState, o: SwipeOutcome, now: Long): GameState {
        val bursts = o.beats.count { it is Beat.Burst }.toLong()
        val waves = o.beats.filterIsInstance<Beat.Wave>().maxOfOrNull { it.index }?.toLong() ?: 0
        val spam = o.beats.count { it is Beat.Swiped && it.row.kind == RowKind.Spam }.toLong()
        val crash = o.beats.count { it is Beat.Swiped && it.row.alert == Alert.Crash }.toLong()
        return bump(o.state, now, mapOf(Stat.Bursts to bursts, Stat.Swipes to 1, Stat.SpamCleared to spam, Stat.CrashRestarts to crash),
            maxes = mapOf(Stat.BestChain to o.chain.toLong(), Stat.BestWave to waves))
    }

    fun upgraded(s: GameState, levels: Int, now: Long) = bump(s, now, mapOf(Stat.Upgrades to levels.toLong()))

    fun reset(s: GameState, now: Long): GameState? = engine.factoryReset(s, now)?.let { bump(it, now, mapOf(Stat.Resets to 1)) }

    fun sync(s: GameState, now: Long): GameState? = engine.sync(s, now)?.let { bump(it, now, mapOf(Stat.Syncs to 1)) }

    private fun bump(s: GameState, now: Long, adds: Map<Stat, Long>, maxes: Map<Stat, Long> = emptyMap()): GameState {
        val today = dayOf(now)
        var next = s.withAccount { a ->
            val stats = a.stats.toMutableMap()
            adds.forEach { (k, v) -> if (v != 0L) stats.merge(k.name, v, Long::plus) }
            maxes.forEach { (k, v) -> stats[k.name] = maxOf(stats[k.name] ?: 0L, v) }
            val prog = (if (a.missionProgressDay == today) a.missionProgress else emptyMap()).toMutableMap()
            adds.forEach { (k, v) -> if (v != 0L) prog.merge(k.name, v, Long::plus) }
            maxes.forEach { (k, v) -> prog[k.name] = maxOf(prog[k.name] ?: 0L, v) }
            a.copy(stats = stats, missionProgress = prog, missionProgressDay = today)
        }
        for (ach in earned(next)) next = next.withAccount { it.copy(titles = it.titles + ach.title, pieces = it.pieces + ach.pieces) }
        return next
    }

    /** Achievements met now and not yet held. */
    fun earned(s: GameState): List<Achievement> = Achievement.entries.filter { it.title !in s.account.titles }.filter { a ->
        when (a) {
            Achievement.FirstReset -> stat(s, Stat.Resets) >= 1
            Achievement.FirstSync -> stat(s, Stat.Syncs) >= 1
            Achievement.LastApp -> Catalog.apps.maxBy { it.unlockLevel }.id in s.appLevels
            Achievement.Chain10 -> stat(s, Stat.BestChain) >= 10
            Achievement.Wave9 -> stat(s, Stat.BestWave) >= 9
            Achievement.Spam1000 -> stat(s, Stat.SpamCleared) >= 1_000
            Achievement.Crash100 -> stat(s, Stat.CrashRestarts) >= 100
            Achievement.Pro10 -> s.pro.size >= 10
            Achievement.Streak30 -> s.account.streak >= 30
        }
    }

    /** 꾸미기 조각 → one free-line cosmetic. */
    fun redeemPieces(s: GameState): GameState? = if (s.account.pieces < 10) null else s.withAccount { it.copy(pieces = it.pieces - 10) }

    // 일일 도전 (18 meta-and-ops §1) ------------------------------------------------------------

    /** Keys a day: 2, 3 from Lv52 (best level ever). Never sold. */
    fun challengeKeys(s: GameState, now: Long): Int {
        val cap = if (maxOf(s.backup.bestLevel, s.level) >= 52) 3 else 2
        return if (s.account.challengeDay != dayOf(now)) cap else cap - s.account.challengeKeys
    }

    /** Today's board: the same seed and the same six-app deck for everyone, fixed levels, no account bonuses. */
    fun challengeBoard(now: Long): GameState {
        val day = dayOf(now)
        val pool = Catalog.apps.filter { it.tier in 3..8 }
        val deck = (0 until pool.size).map { pool[Math.floorMod(day * 31 + it * 17, pool.size.toLong()).toInt()].id }.distinct().take(6)
        val g = engine.newGame(now, 0x5EED0000L + day)
        return g.copy(level = 40, appLevels = deck.associateWith { 30 }, active = deck, energy = 60)
    }

    /** Uses a key: returns the board to play for 60 seconds, or null with no keys left. */
    fun startChallenge(s: GameState, now: Long): Pair<GameState, GameState>? {
        if (challengeKeys(s, now) <= 0) return null
        val used = s.withAccount { a -> a.copy(challengeDay = dayOf(now), challengeKeys = if (a.challengeDay == dayOf(now)) a.challengeKeys + 1 else 1) }
        return used to challengeBoard(now)
    }

    /** Finishes a run: score = P earned in the window; a new best pays 2 pieces, a finish 1. */
    fun finishChallenge(s: GameState, board: GameState): GameState {
        val score = board.runEarned
        val best = java.math.BigInteger(s.account.challengeBest)
        return s.withAccount { a -> a.copy(pieces = a.pieces + if (score > best) 2 else 1, challengeBest = score.max(best).toString()) }
    }
}
