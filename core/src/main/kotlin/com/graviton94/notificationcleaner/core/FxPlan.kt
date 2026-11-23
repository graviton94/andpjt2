package com.graviton94.notificationcleaner.core

/**
 * Turns a swipe's beats into a timed script for the FX director (19 game-feel, 20 sound). The engine has already
 * decided everything; this only says *when* and *how hard* to show it, so it is pure and testable.
 * Values mirror docs/design/tokens.json → fx and the demo (docs/design/fx-demo).
 */
enum class CueKind { Swipe, Merge, HitStop, Shatter, Shake, Ripple, ScreenCrack, Sound, Haptic, ChipAdd, MultUp, Wave, Payoff, Overheat, LevelUp, DecorCollapse, SlowMo, Flash }

data class Cue(val atMs: Long, val kind: CueKind, val value: Double = 0.0, val sound: String? = null, val chain: Int = 0, val rowId: Long = 0)

data class LadderStep(val name: String, val hitStopMs: Long, val shakeDp: Double, val shreds: Int, val ripple: Double)

object FxPlan {
    /** 확대의 사다리 (tokens fx.ladder). */
    fun step(chain: Int): LadderStep = when {
        chain <= 1 -> LadderStep("팡", 28, 1.5, 12, 0.014)
        chain <= 3 -> LadderStep("연쇄", 36, 2.0, 20, 0.018)
        chain <= 5 -> LadderStep("폭주", 48, 2.5, 32, 0.022)
        chain <= 8 -> LadderStep("대폭주", 60, 3.0, 48, 0.028)
        else -> LadderStep("알림 해일", 80, 3.0, 64, 0.04)
    }

    /** Gap between consecutive bursts of one chain: a beat that speeds up as the chain grows (demo timing). */
    fun burstGap(index: Int): Long = maxOf(100L, 190L - 11L * index)

    /** Arrival ping for a new row. */
    fun arrivalSound(r: Row): String = when (r.kind) {
        RowKind.Spam -> "notify.spam"
        RowKind.Call -> "phone.buzz.ring"
        RowKind.Event -> "notify.event"
        RowKind.Alert -> "phone.buzz"
        else -> when (r.app?.let { Catalog.app(it).category }) {
            Category.Social -> "notify.social"; Category.Work -> "notify.work"; Category.Daily -> "notify.daily"
            Category.Media -> "notify.media"; Category.Money -> "notify.money"; null -> "notify.stock"
        }
    }

    fun plan(o: SwipeOutcome, reducedMotion: Boolean = false): List<Cue> {
        val cues = mutableListOf<Cue>()
        var t = 0L
        val shrink = if (reducedMotion) 0.3 else 1.0
        fun add(c: Cue) { cues += c }
        var k = 0
        for (b in o.beats) when (b) {
            is Beat.Swiped -> { add(Cue(t, CueKind.Swipe, rowId = b.row.id)); add(Cue(t, CueKind.Sound, sound = "swipe")); t += 110 }
            is Beat.Merged -> { add(Cue(t, CueKind.Merge, rowId = b.into.id)); add(Cue(t, CueKind.Sound, sound = "merge")); t += 60 }
            is Beat.Burst -> {
                k++
                val s = step(b.chain)
                add(Cue(t, CueKind.HitStop, s.hitStopMs.toDouble(), chain = b.chain))
                add(Cue(t, CueKind.Shatter, s.shreds * shrink, chain = b.chain, rowId = b.row.id))
                if (!reducedMotion) { add(Cue(t, CueKind.Shake, s.shakeDp, chain = b.chain)); add(Cue(t, CueKind.Ripple, s.ripple, chain = b.chain)) }
                add(Cue(t, CueKind.Sound, sound = "pop.${minOf(16, b.chain)}", chain = b.chain))
                add(Cue(t, CueKind.Haptic, minOf(40.0, 8.0 + 3 * b.chain)))
                add(Cue(t + 300, CueKind.ChipAdd, chain = b.chain)); add(Cue(t + 300, CueKind.Sound, sound = "chip.${listOf(1, 4, 8, 12).last { it <= maxOf(1, b.chain) }}"))
                add(Cue(t, CueKind.MultUp, (1 + b.chain).toDouble(), chain = b.chain))
                if (b.chain >= 9) {
                    if (!reducedMotion) { add(Cue(t, CueKind.SlowMo, 200.0)); add(Cue(t, CueKind.Flash)); add(Cue(t, CueKind.ScreenCrack, 0.85)) }
                }
                t += s.hitStopMs + burstGap(k)
            }
            is Beat.Wave -> {
                add(Cue(t, CueKind.HitStop, 40.0)); add(Cue(t, CueKind.Wave, b.index.toDouble()))
                add(Cue(t, CueKind.Sound, sound = "wave.${minOf(3, b.index)}")); t += 450
            }
            is Beat.Capped -> add(Cue(t + 900, CueKind.Overheat))
            is Beat.LevelUp -> { add(Cue(t, CueKind.LevelUp, b.level.toDouble())); add(Cue(t, CueKind.Sound, sound = "ui.levelup")) }
            // New rows arrive like real phone notifications: a different ping per app category (CC0 layer, 20 sound).
            is Beat.Spawned -> add(Cue(t + 40, CueKind.Sound, sound = arrivalSound(b.row)))
            else -> {}
        }
        if (o.chain >= 1) {
            // 정산: merge → count-up (620 ms) → slam; big chains also crack the screen glass and collapse the decor.
            val pay = t + 380
            add(Cue(pay, CueKind.Sound, sound = "whoosh"))
            val slam = pay + 160 + 620
            add(Cue(slam, CueKind.Payoff, o.chain.toDouble()))
            add(Cue(slam, CueKind.Sound, sound = "payoff.${listOf(1, 3, 5, 8).last { it <= maxOf(1, o.chain) }}"))
            if (!reducedMotion) { add(Cue(slam, CueKind.ScreenCrack, 1.0)); add(Cue(slam, CueKind.Ripple, 0.045)) }
            if (o.chain >= 8 || o.overload) add(Cue(slam + 30, CueKind.DecorCollapse, 1.0 + minOf(o.chain, 12) * 0.04))
        }
        return cues.sortedBy { it.atMs }
    }
}
