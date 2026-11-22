package com.graviton94.notificationcleaner.core

/** Virtual market. Region synergy counts apps of the same region in the deck. */
enum class Region(val label: String) { Domestic("국내권"), Global("글로벌"), NorthAmerica("북미권"), Europe("유럽권"), Asia("아시아권") }

/** App category. Category synergy counts apps of the same category in the deck. */
enum class Category(val label: String) { Social("소통"), Work("업무"), Daily("생활"), Media("미디어"), Money("금융") }

/**
 * One fictional app (docs/content/notification-catalog.html). [order] is its place inside the tier and
 * sets its price step. Unique effects are catalogued but not part of rules v3 (planned for v3.1).
 */
data class AppDef(
    val id: String,
    val name: String,
    val tier: Int,
    val order: Int,
    val region: Region,
    val category: Category,
    val unlockLevel: Int,
)

/** Event cards are bought with P and equipped in their own slots. Only equipped cards appear on the board. */
enum class EventCard(val label: String, val unlockLevel: Int, val price: Long, val everySwipes: Int) {
    /** Swiping it upgrades one active app by one level for free. */
    Update("업데이트", 4, 800, 40),

    /** The next five arriving rows are A·B·A·B·A, a double sandwich. */
    AllHands("@모두 공지", 14, 30_000, 30),

    /** Swiping it within [RuleSet.otpLifeSwipes] pays 10× the previous swipe; then it expires. */
    Otp("인증번호", 16, 60_000, 25),
}

/** Board assists bought with P inside a run. Backup items raise their starting values. */
enum class Assist(val label: String, val unlockLevel: Int, val max: Int, val base: Long, val growth: Long) {
    Overload("과부하 깊이", 8, 6, 2_000, 400),
    Sandwich("샌드위치", 10, 5, 20_000, 15),
    Board("넓은 알림창", 12, 2, 500_000, 200),
    Wild("와일드 알림", 20, 5, 1_000_000, 30),
}

/** Backup shop. Bought with backup files from factory resets; never reset. */
enum class BackupItem(val label: String, val max: Int, val base: Long, val growthPermille: Int) {
    FastBoot("빠른 부팅 · XP +25%", 20, 2, 1_500),
    RememberedApps("기억된 앱", 8, 5, 2_000),
    StartFunds("시작 자금", 10, 1, 1_600),
    Value("알림 가치 ×1.1", Int.MAX_VALUE, 3, 1_350),
    ChainBoost("연쇄 증폭 +0.1", 10, 8, 1_800),
    Milestone("마일스톤 +0.1", 10, 10, 1_900),
    ExtraSlot("앱 슬롯 +1", 2, 15, 3_000),
    EventSlot("이벤트 슬롯 +1", 1, 25, 1_000),
    Synergy("시너지 +10%", 10, 6, 1_600),
    Board("넓은 알림창 +1", 2, 12, 3_000),
    Sandwich("샌드위치 +2%p", 10, 3, 1_500),
    Wild("와일드 알림 +1%p", 10, 4, 1_500),
    DeepOverload("시작 과부하 깊이 +1", 3, 20, 3_000),
    AutoClean("자동 정리 −0.3초", 10, 4, 1_500),
    Battery("배터리 +2 · 충전 −0.2초", 5, 5, 2_000),
}

object Catalog {
    private fun app(id: String, name: String, tier: Int, order: Int, r: Region, c: Category, lv: Int) = AppDef(id, name, tier, order, r, c, lv)
    private val KR = Region.Domestic
    private val GL = Region.Global
    private val NA = Region.NorthAmerica
    private val EU = Region.Europe
    private val AS = Region.Asia

    val apps = listOf(
        app("chat", "톡탁톡", 1, 0, KR, Category.Social, 1),
        app("mail", "메일소포", 1, 1, GL, Category.Work, 1),
        app("delivery", "문앞왔", 1, 2, KR, Category.Daily, 1),
        app("steps", "만보기왕", 1, 3, AS, Category.Daily, 2),
        app("feed", "피드또봐", 2, 0, GL, Category.Media, 3),
        app("calendar", "일정찍", 2, 1, GL, Category.Work, 4),
        app("pay", "페이한칸", 2, 2, NA, Category.Money, 5),
        app("shorts", "숏츠한편", 2, 3, AS, Category.Media, 7),
        app("music", "비트서랍", 3, 0, NA, Category.Media, 8),
        app("inbox", "밀린메일", 3, 1, KR, Category.Work, 10),
        app("map", "길막지도", 3, 2, KR, Category.Daily, 12),
        app("point", "적립했냥", 3, 3, KR, Category.Money, 14),
        app("reward", "접속보상", 4, 0, GL, Category.Media, 15),
        app("bank", "잔액알림", 4, 1, KR, Category.Money, 17),
        app("cloud", "용량부족", 4, 2, GL, Category.Work, 19),
        app("translate", "번역해줘", 4, 3, EU, Category.Social, 22),
        app("meet", "카메라켜요", 5, 0, NA, Category.Work, 24),
        app("coin", "떡상알림", 5, 1, AS, Category.Money, 27),
        app("match", "매칭됨", 5, 2, EU, Category.Social, 30),
        app("home", "문열림", 5, 3, EU, Category.Daily, 34),
        app("live", "지금방송중", 6, 0, AS, Category.Media, 36),
        app("stock", "장마감", 6, 1, NA, Category.Money, 40),
        app("comment", "댓글폭탄", 6, 2, KR, Category.Social, 45),
        app("minutes", "회의록봇", 6, 3, EU, Category.Work, 50),
    )
    val starter = listOf("chat", "mail", "delivery")
    fun app(id: String): AppDef = apps.first { it.id == id }
}
