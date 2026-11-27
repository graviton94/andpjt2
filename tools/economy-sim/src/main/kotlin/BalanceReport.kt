import com.graviton94.notificationcleaner.core.Catalog
import com.graviton94.notificationcleaner.core.CreditItem
import com.graviton94.notificationcleaner.core.SpeedPass
import com.graviton94.notificationcleaner.core.premiumCost
import java.io.File
import java.util.Locale

private data class Scenario(val payer: Payer, val low: Double, val high: Double)
private data class Sample(val scenario: Scenario, val seed: Long, val trace: Timeline)
private val scenarios = listOf(
    Scenario(Payer.Free, 66.0, 84.0), Scenario(Payer.Ads, 49.0, 63.0),
    Scenario(Payer.Pass, 35.0, 49.0), Scenario(Payer.Spend30, 24.0, 32.0),
    Scenario(Payer.Spend100, 11.0, 17.0),
)
private fun fmt(n: Double?) = n?.let { String.format(Locale.ROOT, "%.2f", it) }.orEmpty()
private fun completeMean(values: List<Double?>) = if (values.any { it == null }) null else values.filterNotNull().average()
internal fun csv(file: File, fields: List<String>, rows: Sequence<List<Any?>>) {
    file.bufferedWriter().use { out ->
        out.appendLine(fields.joinToString(","))
        rows.forEach { row ->
            check(row.size == fields.size)
            out.appendLine(row.joinToString(",") { raw ->
                val value = raw?.toString().orEmpty()
                if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
            })
        }
    }
}

fun balanceDetail(out: File) {
    out.mkdirs()
    val rules = simRules()
    val profiles = scenarios.flatMap { scenario -> (1L..2L).map { scenario to it } }
    val samples = profiles.parallelStream().map { (scenario, seed) ->
        val timeline = playDays(seed, 84, DayPlan(), true, payer = scenario.payer)
        println("${scenario.payer.name} 시드 $seed 완료 · Lv124 ${fmt(timeline.level124Day).ifEmpty { "미도달" }}")
        Sample(scenario, seed, timeline)
    }.toList()
    fun rows(s: Scenario) = samples.filter { it.scenario == s }
    fun levels(s: Scenario) = completeMean(rows(s).map { it.trace.level124Day })
    fun offline(t: Timeline): Double {
        val total = t.activeP + t.offlineP
        if (total.signum() == 0) return 0.0
        return t.offlineP.toBigDecimal().divide(total.toBigDecimal(), java.math.MathContext.DECIMAL64).toDouble()
    }
    csv(File(out, "profiles.csv"),
        listOf("profile", "seed", "lv124_day", "last_app_buy_day", "first_reset_day", "first_sync_day",
            "free_data", "purchased_data", "spent_data", "data_balance", "earned_vouchers", "unused_vouchers", "reward_claims", "offline_p_share",
            "resets", "syncs"),
        samples.asSequence().map { (s, seed, t) -> listOf(s.payer.name, seed, fmt(t.level124Day), fmt(t.lastAppDay),
            fmt(t.resetDays.firstOrNull()), fmt(t.syncDays.firstOrNull()), t.credits, t.bought, t.dataSpent, t.dataBalance,
            t.vouchersEarned, t.vouchers, t.ads, fmt(offline(t)), t.resetDays.size, t.syncDays.size) })
    csv(File(out, "weekly.csv"), listOf("profile", "seed", "week", "best_level", "score_digits", "lifetime_files"),
        samples.asSequence().flatMap { (s, seed, t) -> t.bestLevelByWeek.asSequence().map { (week, level) ->
            listOf(s.payer.name, seed, week, level, t.digitsByWeek[week], t.lifetimeByWeek[week])
        } })
    csv(File(out, "apps.csv"), listOf("profile", "seed", "app_id", "app", "tier", "unlock_level", "unlock_day",
        "buy_day", "lv1_day", "lv3_day", "lv5_day", "lv8_day", "lv12_day", "premium_stars"),
        samples.asSequence().flatMap { (s, seed, t) -> Catalog.apps.asSequence().map { app ->
            val unlock = t.levelDays[app.unlockLevel]
            listOf(s.payer.name, seed, app.id, app.name, app.tier, app.unlockLevel, fmt(unlock), fmt(t.appBuys[app.id])) +
                listOf(1, 3, 5, 8, 12).map { fmt(t.appLevels[app.id]?.get(it)) } + (t.premiumByApp[app.id] ?: 0)
        } })
    csv(File(out, "app-costs.csv"), listOf("app_id", "app", "tier", "base_buy_p", "upgrade_lv", "base_upgrade_p",
        "star1", "star2", "star3", "star4", "star5"), Catalog.apps.asSequence().flatMap { app ->
        (1..12).asSequence().map { level ->
            listOf(app.id, app.name, app.tier, rules.appPrice(app), level, rules.upgradePrice(app, level)) +
                (1..rules.premiumMaxStars).map { rules.premiumCost(app, it) }
        }
    })

    val offers = rules.commerce
    val sku = offers.packs.map { listOf(it.id, it.won, it.data, offers.dataReceived(it, true)) } + listOf(
        listOf("starter_pack", offers.starterWon, offers.starterValue(rules), offers.starterValue(rules)),
        listOf("rookie_pass", offers.rookieWon, offers.rookieValue(rules), offers.rookieValue(rules)),
        listOf("pass_month", offers.passWon, rules.passInstantCredits + 30L * rules.passDailyCredits +
            30L * rules.passOverclockMinutes * CreditItem.Overclock.price / 60, null),
    ) + SpeedPass.entries.map {
        listOf(it.name, it.won, rules.passInstantCredits + 30L *
            (rules.passDailyCredits + it.dailyData + rules.passOverclockMinutes * CreditItem.Overclock.price / 60), null)
    }
    val skuValues = sku.map { row ->
        val price = (row[1] as Number).toDouble()
        row + listOf(fmt((row[2] as Number).toDouble() * offers.baseWonPerData / price),
            fmt((row[3] as Number?)?.toDouble()?.times(offers.baseWonPerData)?.div(price)))
    }
    csv(File(out, "sku-values.csv"), listOf("sku", "won", "data_equivalent", "first_data_equivalent", "normal_x", "first_x"),
        skuValues.asSequence())

    val report = StringBuilder("# 2시드 상세 리포트\n\n")
    fun line(s: String = "") { report.appendLine(s) }
    line("엔진 RuleSet ${rules.version}, 레벨 비율 ${rules.levelRatioPermille}‰. 84일·시드 1/2·하루 4회 × 15분.")
    line("Lv124 최초 도달과 마지막 앱 구매를 분리했다. 최종 진행 판정은 balance-report의 10시드 결과를 사용한다.")
    line()
    line("| 프로필 | 시드 1 / 2 Lv124 | 평균 | 첫 초기화 평균 | 마지막 앱 구매 평균 |")
    line("|---|---:|---:|---:|---:|")
    for (scenario in scenarios) {
        val series = rows(scenario).map { it.trace }
        val avg = levels(scenario)
        line("| ${scenario.payer.label} | ${series.joinToString(" / ") { fmt(it.level124Day).ifEmpty { "미도달" } }} | " +
            "${fmt(avg).ifEmpty { "미도달" }} | " +
            "${fmt(completeMean(series.map { it.resetDays.firstOrNull() }))} | ${fmt(completeMean(series.map { it.lastAppDay }))} |")
    }
    line()
    for (scenario in scenarios) for ((_, seed, trace) in rows(scenario)) {
        val day = trace.level124Day ?: continue
        if (day > scenario.high || day < scenario.low) {
            line("- 개별 시드 범위 이탈 · ${scenario.payer.name} 시드 $seed · ${fmt(day)}일 · 목표 ${scenario.low.toInt()}~${scenario.high.toInt()}일")
        }
    }
    for ((slower, faster) in scenarios.zipWithNext()) {
        val a = levels(slower)
        val b = levels(faster)
        val gap = if (a != null && b != null) a - b else null
        val reversed = rows(slower).zip(rows(faster)).filter { (x, y) ->
            val slow = x.trace.level124Day
            val fast = y.trace.level124Day
            slow != null && fast != null && slow <= fast
        }.map { it.first.seed }
        line("- ${slower.payer.name} → ${faster.payer.name} 평균 격차 ${fmt(gap).ifEmpty { "미관측" }}일" +
            if (reversed.isEmpty()) "" else " · 순서 역전 시드 $reversed")
    }
    val reset = completeMean(rows(scenarios.first()).map { it.trace.resetDays.firstOrNull() })
    line("- 무과금 첫 초기화 평균 ${fmt(reset).ifEmpty { "미도달" }}일")
    line()
    line("## 무과금 ★5 장바구니")
    line()
    line("실제로 쓴 데이터와 별개로, 84일 무료 데이터 총수입에서 특정 티어 ★5 묶음의 구매 가능성을 계산한다.")
    line("무료로 획득한 선택권은 가상 장바구니의 ★1·★2에만 쓴다.")
    line()
    val targets = listOf(10 to 1, 5 to 2, 1 to 4)
    for (sample in rows(scenarios.first())) {
        val t = sample.trace
        for ((tier, quantity) in targets) {
            val app = Catalog.apps.first { it.tier == tier }
            val prices = (1..5).map { rules.premiumCost(app, it) }
            val cover = List(quantity) { prices.take(rules.voucherMaxStars) }.flatten()
                .sortedDescending().take(t.vouchersEarned).sum()
            val needed = quantity * prices.sum() - cover
            val unlocked = Catalog.apps.count { it.tier == tier && it.unlockLevel in t.levelDays }
            line("- 시드 ${sample.seed} · ${tier}티어 ★5 ×$quantity · 필요 $needed / 무료 ${t.credits} 데이터 · " +
                if (t.credits >= needed && unlocked >= quantity) "가능" else "미달")
        }
    }
    line()
    line("## 상품별 지급과 기준가 대비 효율")
    line()
    line("| 상품 | 가격 ₩ | 데이터 환산 | 첫 팩 환산 | 기본 배율 | 첫 팩 배율 |")
    line("|---|---:|---:|---:|---:|---:|")
    for (row in skuValues) {
        line(row.joinToString(separator = " | ", prefix = "| ", postfix = " |") { it?.toString()?.ifEmpty { "—" } ?: "—" })
    }
    line()
    line("속도 구독의 데이터 환산은 확정 지급분과 오버클럭만 계산한다. 속도·초기화·동기화 배율은 프로필 표에서 판정한다.")
    line()
    line("출석 가치는 [검산식](standards.md)과 [출석 CSV](attendance-values.csv)를 참고한다. [상품·출석 40개 케이스](commerce-scenarios.csv)는 84일 성장 프로필과 별도다.")
    File(out, "DETAIL.md").writeText(report.toString())
}
