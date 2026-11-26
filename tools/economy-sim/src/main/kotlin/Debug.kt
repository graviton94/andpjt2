import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.Catalog

fun debugRun() {
    val e = Engine()
    var s = chooseDeck(e.newGame(1_000_000L, 1), DeckPolicy.Synergy)
    var t = 1_000_000L
    for (day in 0 until 3) for (session in 0 until 4) {
        val begin = 1_000_000L + day * 86_400_000L + session * 4 * 3_600_000L
        if (t < begin) { val (n, p) = e.settleOffline(s, begin - t, begin); println("  오프라인 +$p (rate ${s.earnRate})"); s = n; t = begin }
        val start = s.runEarned; var swipes = 0; var nulls = 0
        val end = t + 15 * 60_000L
        while (t < end) {
            t += e.chargeMs(s); s = e.tick(s, t)
            val target = e.bestTarget(s, t); if (target == null) { nulls++; continue }
            val o = e.swipe(s, target, t); if (o == null) { nulls++; continue }
            s = spend(o.state, DeckPolicy.Synergy).first; s = chooseDeck(s, DeckPolicy.Synergy); swipes++
        }
        println("day $day s$session lv ${s.level} swipes $swipes nulls $nulls energy ${s.energy} next ${s.nextChargeAt - t} earned ${s.runEarned - start} wallet ${s.wallet.toString().length}자리 deck ${s.active.map { Catalog.app(it).name }}")
    }
}
