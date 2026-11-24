import com.graviton94.notificationcleaner.core.AdSlot
import com.graviton94.notificationcleaner.core.Catalog
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.WebSession
import com.graviton94.notificationcleaner.core.account
import java.io.File

private const val DAY_MS = 86_400_000L
private const val START_MS = 1000 * DAY_MS

private data class OfferArm(val label: String, val cash: Int, val order: (WebSession, Long) -> Unit = { _, _ -> })

fun balanceCommerce(output: File) {
    output.mkdirs()
    val engine = Engine(simRules())
    val rules = engine.rules
    val offers = rules.commerce
    val small = offers.packs.first()
    val large = offers.packs.last()
    val best = offers.packs.first { it.id == "data_1200" }
    val arms = listOf(
        OfferArm("free", 0),
        OfferArm("ads", 0),
        OfferArm("small", small.won) { w, t -> w.buyData(small.id, "pack", t) },
        OfferArm("large", large.won) { w, t -> w.buyData(large.id, "pack", t) },
        OfferArm("month", offers.passWon) { w, t -> w.buyPass("month", "pass", t) },
        OfferArm("starter", offers.starterWon) { w, t -> w.buyStarter("starter", t) },
        OfferArm("rookie", offers.rookieWon) { w, t -> w.buyRookie("rookie", t) },
        OfferArm("turbo", com.graviton94.notificationcleaner.core.SpeedPass.Turbo.won) { w, t -> w.buyPass("Turbo", "pass", t) },
        OfferArm("ultra", com.graviton94.notificationcleaner.core.SpeedPass.Ultra.won) { w, t -> w.buyPass("Ultra", "pass", t) },
        OfferArm("month+starter+1200", offers.passWon + offers.starterWon + best.won) { w, t ->
            w.buyPass("month", "pass", t)
            w.buyStarter("starter", t)
            w.buyData(best.id, "pack", t)
        },
    )
    val schedules = linkedMapOf("consecutive" to (0..19).toList(), "spread" to (0..19).map { it * 29 / 19 })
    val cases = arms.asSequence().flatMap { arm -> schedules.asSequence().flatMap { (schedule, visits) ->
        (1L..2L).asSequence().map { seed ->
            val w = WebSession(engine, engine.newGame(START_MS, seed), START_MS)
            arm.order(w, START_MS)
            val first = w.state.account
            arm.order(w, START_MS)
            check(w.state.account == first) { "Duplicate grant for ${arm.label}" }
            var lastVisit = START_MS
            var resetDay: Int? = null
            var bestStreak = 0
            for (day in visits) {
                var now = START_MS + day * DAY_MS
                if (day > 0) w.offline(now - lastVisit, now) else w.attend(now)
                bestStreak = maxOf(bestStreak, w.state.account.streak)
                w.claimRookie(now)
                if (arm.label == "ads" || w.shop.passActive(w.state, now)) {
                    for (slot in listOf(AdSlot.Boost, AdSlot.CreditBox, AdSlot.Overclock)) {
                        val receipt = "ad-$day-${slot.name}"
                        w.watchAd(slot.name, receipt, now)
                        val paid = w.state.account
                        w.watchAd(slot.name, receipt, now)
                        check(w.state.account == paid)
                    }
                }
                val app = w.state.active.last()
                if (w.state.account.starVouchers > 0) w.premium(app, true, now)
                w.premium(app, false, now)
                repeat(20) {
                    now += engine.chargeMs(w.state, now)
                    engine.bestTarget(w.state, now)?.let { w.swipe(it, now) }
                }
                w.claimMissions(now)
                Catalog.apps.firstOrNull { engine.canBuy(w.state, it.id) }?.let { w.buy(it.id, now) }
                w.upgradeN(app, "all", now)
                if (resetDay == null && engine.backupFilesNow(w.state) > 0) {
                    w.reset(now)
                    resetDay = day + 1
                }
                lastVisit = now
            }
            val s = w.state
            val a = s.account
            check(a.credits == a.creditsFree + a.creditsBought - a.creditsSpent)
            listOf(arm.label, schedule, seed, arm.cash, maxOf(s.level, s.backup.bestLevel), bestStreak,
                a.creditsFree, a.creditsBought, a.creditsSpent, a.credits, a.premium.values.sum(),
                s.energy, resetDay, s.backup.files)
        }
    } }
    csv(File(output, "commerce-scenarios.csv"),
        listOf("offer", "calendar", "seed", "won", "best_level", "best_attendance_streak", "free_data",
            "paid_data", "spent_data", "data_balance", "stars", "energy", "first_reset_day", "backup_files"), cases)

    val balances = rules.attendanceBoard.runningFold(listOf(0L, 0L, 0L)) { total, entry ->
        listOf(total[0] + entry.data, total[1] + entry.tickets.entries.sumOf { (item, n) -> item.price * n },
            total[2] + entry.starVouchers)
    }
    csv(File(output, "attendance-values.csv"),
        listOf("day", "data", "ticket_data_equivalent", "vouchers", "data_to_date", "ticket_data_to_date", "vouchers_to_date"),
        rules.attendanceBoard.asSequence().mapIndexed { i, reward ->
            listOf(i + 1, reward.data, reward.tickets.entries.sumOf { (item, n) -> item.price * n }, reward.starVouchers) + balances[i + 1]
        })
}
