package com.graviton94.notificationcleaner.core

import java.math.BigInteger

/*
 * Generates docs/bible/05-content-reference.md from the engine's own data, so the item lists in the
 * bible can never drift from the rules.
 *   ./gradlew -p core run --args="content" > docs/bible/05-content-reference.md
 */

private val rules = RuleSet()

/** 12,345 · 1.2만 · 3.4억 · 5.6조 · 7.8경 · 9.1해 · beyond: 1.2×10^30. */
fun kor(n: BigInteger): String {
    if (n < BigInteger.valueOf(10_000)) return "%,d".format(n)
    val digits = n.toString().length
    if (digits > 24) return "%s×10^%d".format(n.toString().let { "${it[0]}.${it[1]}" }, digits - 1)
    val units = listOf(20 to "해", 16 to "경", 12 to "조", 8 to "억", 4 to "만")
    val (exp, unit) = units.first { digits > it.first }
    val scaled = n.toBigDecimal().movePointLeft(exp)
    val text = if (scaled >= java.math.BigDecimal(1_000)) "%,d".format(scaled.toBigInteger())
        else scaled.setScale(if (scaled < java.math.BigDecimal(100)) 1 else 0, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
    return "$text$unit"
}

private val cardText = mapOf(
    EventCard.Update to "밀면 덱의 앱 하나 강화 +1 (무료)",
    EventCard.AllHands to "다음 새 줄 5개를 A·B·A·B·A로 예약 (이중 샌드위치)",
    EventCard.Otp to "3스와이프 안에 밀면 직전 스와이프 P × 10, 지나면 사라짐",
    EventCard.Sale to "다음에 사는 앱 가격 −50%",
    EventCard.GroupText to "판의 모든 앱 줄 수량 +1",
    EventCard.Firmware to "덱의 모든 앱 강화 +1",
    EventCard.Lotto to "절반 확률로 직전 스와이프 P × 1~60",
)

private val assistText = mapOf(
    Assist.Overload to "과부하 파동 +1",
    Assist.Sandwich to "A·X·A 보충 확률 +3%p",
    Assist.Board to "판 +1줄",
    Assist.Wild to "와일드 줄 확률 +2%p",
)

private fun steps(s: List<Pair<Int, Int>>, unit: String = "") = s.joinToString(" → ") { "Lv${it.first} ${it.second}$unit" }

fun contentDoc(): String = buildString {
    appendLine("# 05 · 콘텐츠 참조표 (자동 생성)")
    appendLine()
    appendLine("> 이 문서는 `./gradlew -p core run --args=\"content\"`가 엔진 데이터(`Content.kt`, `Rules.kt`)에서 만든다. 손으로 고치지 말고 코드를 고친 뒤 다시 생성한다. 규칙 버전 ${rules.version}.")
    appendLine()
    appendLine("## 1. 알림 앱 24종")
    appendLine()
    appendLine("| # | ID | 이름 | 티어 | 권역 | 카테고리 | 해금 | 초기화 조건 | 가격 | 알림 1개 가치 | 프로 해금 | 프로 가격 |")
    appendLine("|---:|---|---|---:|---|---|---:|---:|---:|---:|---:|---:|")
    Catalog.apps.forEachIndexed { i, a ->
        val start = a.id in Catalog.starter
        appendLine("| ${i + 1} | `${a.id}` | ${a.name} | ${a.tier} | ${a.region.label} | ${a.category.label} | Lv${a.unlockLevel} | " +
            "${if (a.minResets > 0) "${a.minResets}회" else "—"} | ${if (start) "시작" else kor(rules.appPrice(a)) + " P"} | ${kor(BigInteger.TEN.pow(a.tier - 1))} | " +
            "Lv${Catalog.proLevel.getValue(a.id)} | ${kor(rules.proPrice(a.id))} P |")
    }
    appendLine()
    appendLine("- 프로 버전: 그 앱의 알림 가치 ×${rules.proMultiplier}. 가격 = 프로 해금 레벨에 필요한 누적 P × ${rules.proPricePermille / 10.0}%.")
    appendLine("- 강화 L→L+1: `${rules.upgradeBase} × ${rules.appPriceTierRatio}^(티어−1) × ${rules.upgradeGrowthPermille / 1000.0}^(L−1)`. 마일스톤 ${rules.levelMilestones.joinToString("·")}단계마다 가치 ×2.")
    appendLine()
    appendLine("## 2. 권역 · 카테고리")
    appendLine()
    appendLine("| 권역 | 앱 | 카테고리 | 앱 |")
    appendLine("|---|---|---|---|")
    val rows = maxOf(Region.entries.size, Category.entries.size)
    for (i in 0 until rows) {
        val r = Region.entries.getOrNull(i)
        val c = Category.entries.getOrNull(i)
        appendLine("| ${r?.label ?: ""} | ${r?.let { x -> Catalog.apps.filter { it.region == x }.joinToString(", ") { it.name } } ?: ""} | " +
            "${c?.label ?: ""} | ${c?.let { x -> Catalog.apps.filter { it.category == x }.joinToString(", ") { it.name } } ?: ""} |")
    }
    appendLine()
    appendLine("시너지(덱 안 같은 수): 카테고리 ${rules.categorySynergySteps.joinToString(" · ") { "${it.first}개 +${it.second}%" }} / 권역 ${rules.regionSynergySteps.joinToString(" · ") { "${it.first}개 +${it.second}%" }}. 2개 이상 모인 세트가 하나 늘 때마다 모든 가치 ×${rules.diversityPermille / 1000.0}. 누적 처리량 자릿수마다 권역 +${rules.regionDigitPercent}%, 카테고리 +${rules.categoryDigitPercent}%.")
    appendLine()
    appendLine("## 3. 이벤트 카드")
    appendLine()
    appendLine("| 카드 | 해금 | 가격 | 등장 주기 | 효과 |")
    appendLine("|---|---:|---:|---:|---|")
    EventCard.entries.forEach { c ->
        appendLine("| ${c.label} | Lv${c.unlockLevel} | ${kor(rules.cardPrice(c))} P | ${c.everySwipes}스와이프 | ${cardText.getValue(c)} |")
    }
    appendLine()
    appendLine("이벤트 슬롯: ${steps(rules.eventSlotSteps, "칸")}, 백업 상점 +1.")
    appendLine()
    appendLine("## 4. 기본 돌발 알림")
    appendLine()
    appendLine("| 알림 | 등장 | 규칙 |")
    appendLine("|---|---|---|")
    appendLine("| 스팸 | ${rules.spamEverySwipes}스와이프마다 | 합쳐지지 않음. ${rules.spamCopyAfterSwipes}스와이프 안에 안 지우면 복제 |")
    appendLine("| 전화 | Lv${rules.callUnlockLevel}부터 ${rules.callEverySwipes}스와이프마다 | ${rules.callPauseMs / 1000}초 동안 자동 정리 정지, 밀면 재개 |")
    appendLine()
    appendLine("## 5. 판 강화 (P, 회차마다 초기화)")
    appendLine()
    appendLine("| 강화 | 해금 | 최대 단계 (레벨별) | 가격 | 효과 |")
    appendLine("|---|---:|---|---|---|")
    Assist.entries.forEach { a ->
        appendLine("| ${a.label} | Lv${a.unlockLevel} | ${steps(a.caps)} | `${kor(BigInteger.valueOf(a.base))} × ${a.growth}ⁿ` | ${assistText.getValue(a)} |")
    }
    appendLine()
    appendLine("## 6. 덱")
    appendLine()
    appendLine("- 앱 슬롯 최대: ${steps(rules.maxSlotSteps)} (백업 상점 +2)")
    appendLine("- 앱 슬롯 최소: ${steps(rules.minSlotSteps)}")
    appendLine("- 판 줄 수: 기본 ${rules.baseRows}, 덱 앱 4개 초과 ${rules.appsPerExtraRow}개마다 +1, 최대 ${rules.maxRows}")
    appendLine()
    appendLine("## 7. 백업 상점 (백업파일, 초기화에도 유지)")
    appendLine()
    appendLine("| 항목 | 열림 | 최대 | 1·2·3단계 가격 |")
    appendLine("|---|---:|---:|---|")
    BackupItem.entries.forEach { b ->
        val n = if (b.max == Int.MAX_VALUE) 3 else minOf(3, b.max)
        appendLine("| ${b.label} | 초기화 ${b.minResets}회 | ${if (b.max == Int.MAX_VALUE) "무한" else b.max} | ${(0 until n).joinToString(" · ") { rules.backupPrice(b, it).toString() }} |")
    }
    appendLine()
    appendLine("백업파일 총량 = `⌊10 × (최고 회차 P ÷ Lv${rules.resetLevel} 기준 P)^${rules.backupExponentPermille / 1000.0}⌋` (기준의 10^${rules.backupSoftcapDigits}배를 넘으면 지수 절반), 초기화 때는 **지난 최고 기록을 넘긴 만큼만** 지급. Lv${rules.resetLevel}부터. 패시브 = `(1 + ${rules.lifetimeFilePercent}% × 평생 백업파일)^${rules.lifetimeExponentPermille / 1000.0}`. 백업파일을 받은 초기화만 초기화 횟수로 센다.")
    appendLine()
    appendLine("## 8. 레벨 기준 누적 P")
    appendLine()
    appendLine("| 레벨 | 필요 누적 P | 레벨 | 필요 누적 P | 레벨 | 필요 누적 P |")
    appendLine("|---:|---:|---:|---:|---:|---:|")
    val ls = listOf(2, 5, 10, 15, 20, 25, 30, 36, 42, 48, 55, 64, 72, 80, 88)
    ls.chunked(3).forEach { row -> appendLine("| " + row.joinToString(" | ") { "Lv$it | ${kor(rules.levelP(it))}" } + " |") }
    appendLine()
    appendLine("`Lv L = ${rules.levelPBase} × ${rules.levelRatioPermille / 1000.0}^(L−2)`, Lv${rules.levelLateFrom}부터 배수가 레벨마다 +${rules.levelRatioStepPermille / 1000.0}. 오프라인: 자동 정리 기준 속도(직접의 ${rules.offlineAutoPermille / 10}%) × 효율 ${rules.offlineEfficiencyPercent}%, 상한 ${steps(rules.offlineCapSteps, "시간")}.")
}
