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
    /** Factory resets needed before the shop offers it: late apps are run-count goals as well as level goals. */
    val minResets: Int = 0,
)

/** Event cards are bought with P and equipped in their own slots. Only equipped cards appear on the board. */
enum class EventCard(val label: String, val unlockLevel: Int, val price: Long, val everySwipes: Int) {
    /** Swiping it upgrades one active app by one level for free. */
    Update("업데이트", 4, 800, 40),

    /** The next five arriving rows are A·B·A·B·A, a double sandwich. */
    AllHands("@모두 공지", 14, 30_000, 30),

    /** Swiping it within [RuleSet.otpLifeSwipes] pays 10× the previous swipe; then it expires. */
    Otp("인증번호", 16, 60_000, 25),

    /** The next app bought costs half. Price 0 = priced from the level curve (RuleSet.cardPrice). */
    Sale("앱스토어 세일", 22, 0, 35),

    /** Every app row on the board gets one more notification: many rows jump to 3/4 or burst on the next touch. */
    GroupText("단체 문자", 27, 0, 30),

    /** Every app in the deck gains one upgrade level. */
    Firmware("펌웨어 업데이트", 37, 0, 60),

    /** Pays the previous swipe × 0 to 60 (half the time nothing): a gamble. */
    Lotto("로또 알림", 61, 0, 45),
}

/**
 * Board assists bought with P inside a run. [caps] raises the number of steps by level: later levels open
 * higher caps as content. Backup items raise their starting values.
 */
enum class Assist(val label: String, val unlockLevel: Int, val caps: List<Pair<Int, Int>>, val base: Long, val growth: Long) {
    Overload("과부하 깊이", 8, listOf(8 to 6, 19 to 7, 54 to 8, 83 to 9), 2_000, 400),
    Sandwich("샌드위치", 10, listOf(10 to 5, 24 to 6, 47 to 8), 20_000, 15),
    Board("넓은 알림창", 12, listOf(12 to 2, 39 to 3), 500_000, 200),
    Wild("와일드 알림", 20, listOf(20 to 5, 69 to 8), 1_000_000, 30),
    ;

    fun max(level: Int): Int = caps.lastOrNull { level >= it.first }?.second ?: 0
}

/** Backup shop. Bought with backup files from factory resets; never reset. */
/**
 * Backup shop. Bought with backup files from factory resets; never reset. [minResets] spreads the shop over
 * the first resets so every reset opens something new.
 */
enum class BackupItem(val label: String, val max: Int, val base: Long, val growthPermille: Int, val minResets: Int) {
    Value("알림 가치 ×1.25", 50, 3, 1_450, 1),
    FastBoot("빠른 부팅 · 레벨 기준 P −10%", 20, 2, 1_500, 1),
    StartFunds("시작 자금", 10, 1, 1_600, 1),
    Offline("오프라인 정산 +2%p · +1시간", 10, 4, 1_500, 1),
    Sandwich("샌드위치 +2%p", 10, 3, 1_500, 2),
    ChainBoost("연쇄 증폭 +0.1", 10, 8, 1_800, 2),
    AutoClean("자동 정리 −0.3초", 10, 4, 1_500, 3),
    Synergy("시너지 +10%", 10, 6, 1_600, 3),
    Milestone("마일스톤 +0.1", 10, 10, 1_900, 4),
    RememberedApps("기억된 앱", 8, 5, 2_000, 4),
    Battery("배터리 +2 · 충전 −0.2초", 5, 5, 2_000, 5),
    DeepOverload("시작 과부하 깊이 +1", 3, 20, 3_000, 6),
    ExtraSlot("앱 슬롯 +1", 2, 15, 3_000, 8),
    Board("넓은 알림창 +1", 2, 12, 3_000, 10),
    Wild("와일드 알림 +1%p", 10, 4, 1_500, 12),
    EventSlot("이벤트 슬롯 +1", 1, 25, 1_000, 15),
}

/**
 * Cloud shop (2nd prestige, 클라우드 동기화). Bought with GB from syncs; survives everything. [minSyncs] spreads it
 * over the first syncs so every sync opens something new.
 */
enum class CloudItem(val label: String, val max: Int, val base: Long, val growthPermille: Int, val minSyncs: Int) {
    Compression("압축 백업 · 백업파일 ×1.5", 30, 1, 2_000, 1),
    SyncValue("동기화 가치 · 알림 가치 ×2", 30, 2, 2_200, 1),
    /** Raises the 알림 가치 backup item's max by 10: each sync moves the first layer's ceiling up. */
    ValueCap("알림 가치 한도 +10", 20, 2, 1_600, 1),
    Discount("백업 상점 −10%", 5, 3, 2_000, 2),
    KeepShop("백업 상점 단계 유지 +3", 5, 2, 2_500, 2),
    CloudSlot("앱 슬롯 +1", 1, 10, 1_000, 3),
    CloudOffline("오프라인 상한 +1시간", 4, 3, 2_000, 4),
    CloudEventSlot("이벤트 슬롯 +1", 1, 20, 1_000, 5),
    CloudAuto("자동 정리 −0.5초", 3, 8, 2_000, 6),
    CloudChain("연쇄 증폭 +0.2", 5, 10, 2_000, 8),
}

object Catalog {
    private fun app(id: String, name: String, tier: Int, order: Int, r: Region, c: Category, lv: Int, resets: Int = 0) = AppDef(id, name, tier, order, r, c, lv, resets)
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
        app("pay", "페이한칸", 2, 2, NA, Category.Money, 6),
        app("shorts", "숏츠한편", 2, 3, AS, Category.Media, 8),
        app("music", "비트서랍", 3, 0, NA, Category.Media, 10),
        app("inbox", "밀린메일", 3, 1, KR, Category.Work, 12),
        app("map", "길막지도", 3, 2, KR, Category.Daily, 14),
        app("point", "적립했냥", 3, 3, KR, Category.Money, 17),
        app("reward", "접속보상", 4, 0, GL, Category.Media, 20),
        app("bank", "잔액알림", 4, 1, KR, Category.Money, 23),
        app("cloud", "용량부족", 4, 2, GL, Category.Work, 26),
        app("translate", "번역해줘", 4, 3, EU, Category.Social, 29),
        app("meet", "카메라켜요", 5, 0, NA, Category.Work, 36),
        app("coin", "떡상알림", 5, 1, AS, Category.Money, 42),
        app("match", "매칭됨", 5, 2, EU, Category.Social, 48),
        app("home", "문열림", 5, 3, EU, Category.Daily, 55),
        app("live", "지금방송중", 6, 0, AS, Category.Media, 64),
        app("stock", "장마감", 6, 1, NA, Category.Money, 70),
        app("comment", "댓글폭탄", 6, 2, KR, Category.Social, 76),
        app("minutes", "회의록봇", 6, 3, EU, Category.Work, 80),
    )
    val starter = listOf("chat", "mail", "delivery")

    /** Level at which each app's pro version (×2 value) opens: tier by tier, Lv31-86. */
    val proLevel: Map<String, Int> = mapOf(
        "chat" to 31, "mail" to 32, "delivery" to 33, "steps" to 34,
        "feed" to 43, "calendar" to 44, "pay" to 45, "shorts" to 46,
        "music" to 56, "inbox" to 57, "map" to 58, "point" to 59,
        "reward" to 65, "bank" to 66, "cloud" to 67, "translate" to 68,
        "meet" to 76, "coin" to 77, "match" to 78, "home" to 79,
        "live" to 81, "stock" to 82, "comment" to 84, "minutes" to 86,
    )
    fun app(id: String): AppDef = apps.first { it.id == id }
}
