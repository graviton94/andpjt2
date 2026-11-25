import com.graviton94.notificationcleaner.core.Catalog
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.GameState
import com.graviton94.notificationcleaner.core.Meta
import com.graviton94.notificationcleaner.core.account
import com.graviton94.notificationcleaner.core.withAccount
import java.io.File
import java.math.BigInteger

private fun playMinute(engine: Engine, board: GameState, now: Long): GameState {
    var state = board
    var time = now
    val limit = now + 60_000
    while (time + engine.chargeMs(state, time) <= limit) {
        time += engine.chargeMs(state, time)
        val row = engine.bestTarget(state, time) ?: continue
        state = engine.swipe(state, row, time)?.state ?: state
    }
    return state
}

fun challengeStarAudit(output: File) {
    val engine = Engine()
    val meta = Meta(engine)
    val rows = (20_000L..20_006L).asSequence().map { day ->
        val now = day * 86_400_000L
        val account = engine.newGame(now, 17)
        val starMap = Catalog.apps.associate { it.id to engine.rules.premiumMaxStars }
        val starred = account.withAccount { it.copy(premium = starMap) }
        val (plainUsed, plainBoard) = checkNotNull(meta.startChallenge(account, now))
        val (starredUsed, starredBoard) = checkNotNull(meta.startChallenge(starred, now))
        check(plainBoard == starredBoard && starredBoard.account.premium.isEmpty())
        check(starredUsed.account.premium == starMap)
        val plain = meta.finishChallenge(plainUsed, playMinute(engine, plainBoard, now)).account.challengeBest
        val paid = meta.finishChallenge(starredUsed, playMinute(engine, starredBoard, now)).account.challengeBest
        check(plain == paid)
        val app = plainBoard.active.first()
        val injectStars = plainBoard.withAccount { it.copy(premium = starMap) }
        val witnessBase = engine.earn(plainBoard, app, BigInteger.TEN, 0)
        val witnessWithStars = engine.earn(injectStars, app, BigInteger.TEN, 0)
        check(witnessWithStars > witnessBase)
        listOf(day, starredBoard.account.premium.size, plain, paid, app, witnessBase, witnessWithStars)
    }
    csv(output, listOf("utc_epoch_day", "challenge_apps_with_stars", "plain_account_score", "starred_account_score",
        "positive_control_app", "positive_control_no_stars_p", "positive_control_injected_stars_p"), rows)
}
