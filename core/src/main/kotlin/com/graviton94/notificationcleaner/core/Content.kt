package com.graviton94.notificationcleaner.core

/** Home market. Region synergy counts apps of the same region in the deck; Global (stock apps) gets none. */
enum class Region(val label: String) { Domestic("국내권"), Global("기본 앱"), NorthAmerica("북미권"), Europe("유럽권"), Asia("아시아권") }

/** App category. Category synergy counts apps of the same category in the deck. */
enum class Category(val label: String) { Social("소통"), Work("업무"), Daily("생활"), Media("미디어"), Money("금융") }

/**
 * One parody app (docs/content/notification-catalog.html). Tiers follow real-world reach: Android's stock apps at
 * tiers 1-2, the world's biggest platforms at tier 10. Names and icons parody the real app; never identical. [order] is its place inside the tier and
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
        // 1티어
        app("alarm", "오분만", 1, 0, GL, Category.Work, 1), // 기본 시계·알람 패러디
        app("weather", "맑음예보", 1, 1, GL, Category.Daily, 1), // 기본 날씨 패러디
        app("memo", "메모장끝", 1, 2, GL, Category.Work, 1), // 기본 메모 패러디
        app("flash", "번쩍등", 1, 3, GL, Category.Daily, 2), // 기본 손전등 패러디
        app("calc", "계산했산", 1, 4, GL, Category.Money, 3), // 기본 계산기 패러디
        app("recorder", "녹음했음", 1, 5, GL, Category.Media, 4), // 기본 녹음기 패러디
        // 2티어
        app("phone", "전화왔숑", 2, 0, GL, Category.Social, 5), // 기본 전화 패러디
        app("sms", "문자왔숑", 2, 1, GL, Category.Social, 6), // 기본 메시지 패러디
        app("camera", "찰칵찰칵", 2, 2, GL, Category.Media, 7), // 기본 카메라 패러디
        app("gallery", "추억소환", 2, 3, GL, Category.Media, 8), // 기본 갤러리 패러디
        app("contacts", "누구세요", 2, 4, GL, Category.Social, 9), // 기본 연락처 패러디
        app("settings", "설정했셈", 2, 5, GL, Category.Daily, 11), // 기본 설정 패러디
        // 3티어
        app("mail", "쥐메일", 3, 0, NA, Category.Work, 12), // Gmail 패러디
        app("translate", "딥에루", 3, 1, EU, Category.Work, 13), // DeepL 패러디
        app("transfer", "위트랜스뻐", 3, 2, EU, Category.Work, 14), // WeTransfer 패러디
        app("shopee", "쇼피피", 3, 3, AS, Category.Money, 16), // Shopee 패러디
        app("cloud", "드랍박스", 3, 4, NA, Category.Work, 17), // Dropbox 패러디
        app("calendar", "타임트리리", 3, 5, AS, Category.Daily, 19), // TimeTree 패러디
        // 4티어
        app("pay", "토스트", 4, 0, KR, Category.Money, 20), // Toss 패러디
        app("taxi", "코코아T", 4, 1, KR, Category.Daily, 22), // Kakao T 패러디
        app("music", "메롱", 4, 2, KR, Category.Media, 23), // Melon 패러디
        app("caller", "트루콜라", 4, 3, EU, Category.Social, 26), // Truecaller 패러디
        app("bolt", "볼트볼트", 4, 4, EU, Category.Daily, 28), // Bolt 패러디
        app("browser", "오페라라", 4, 5, EU, Category.Work, 29), // Opera 패러디
        // 5티어
        app("food", "배다른민족", 5, 0, KR, Category.Daily, 32), // 배달의민족 패러디
        app("webtoon", "나베르웹툰", 5, 1, KR, Category.Media, 35), // 네이버웹툰 패러디
        app("cafe", "맘카페짱", 5, 2, KR, Category.Social, 38), // 지역 맘카페 패러디
        app("wise", "와이저", 5, 3, EU, Category.Money, 42), // Wise 패러디
        app("carpool", "블라블라카풀", 5, 4, EU, Category.Daily, 45), // BlaBlaCar 패러디
        app("home", "샤오미미", 5, 5, AS, Category.Daily, 48), // Xiaomi Home 패러디
        // 6티어
        app("meet", "주움", 6, 0, NA, Category.Work, 51), // Zoom 패러디
        app("match", "바두두", 6, 1, EU, Category.Social, 54), // Badoo 패러디
        app("coin", "업빝", 6, 2, KR, Category.Money, 57), // Upbit 패러디
        app("used", "양파마켓", 6, 3, KR, Category.Money, 60), // 당근마켓 패러디
        app("booking", "북킹닷콤", 6, 4, EU, Category.Daily, 64), // Booking.com 패러디
        app("reward", "원신신", 6, 5, AS, Category.Media, 67), // 원신 패러디
        // 7티어
        app("ott", "넷플러그", 7, 0, NA, Category.Media, 70), // Netflix 패러디
        app("comment", "디스인사이드", 7, 1, KR, Category.Social, 74), // 디시인사이드 패러디
        app("beat", "스팟티파이", 7, 2, EU, Category.Media, 78), // Spotify 패러디
        app("revolut", "레볼루투", 7, 3, EU, Category.Money, 82), // Revolut 패러디
        app("line", "라잉", 7, 4, AS, Category.Social, 86), // LINE 패러디
        app("grab", "그래브", 7, 5, AS, Category.Daily, 90), // Grab 패러디
        // 8티어
        app("portal", "나베르", 8, 0, KR, Category.Work, 94), // Naver 패러디
        app("mall", "쿠빵", 8, 1, KR, Category.Money, 98), // 쿠팡 패러디
        app("career", "링크드아웃", 8, 2, NA, Category.Work, 102), // LinkedIn 패러디
        app("ride", "우바", 8, 3, NA, Category.Daily, 106), // Uber 패러디
        app("cheap", "태무", 8, 4, AS, Category.Money, 110), // Temu 패러디
        app("edit", "컵컷", 8, 5, AS, Category.Media, 114), // CapCut 패러디
        // 9티어
        app("secret", "텔레그림", 9, 0, EU, Category.Social, 118), // Telegram 패러디
        app("photo", "인싸그램", 9, 1, NA, Category.Media, 122), // Instagram 패러디
        app("ai", "챗GTP", 9, 2, NA, Category.Work, 126), // ChatGPT 패러디
        app("search", "구굴", 9, 3, NA, Category.Work, 130), // Google 패러디
        app("mega", "아마좀", 9, 4, NA, Category.Money, 134), // Amazon 패러디
        app("superapp", "위잿", 9, 5, AS, Category.Money, 138), // WeChat 패러디
        // 10티어
        app("chat", "코코아톡", 10, 0, KR, Category.Social, 142), // 카카오톡 패러디
        app("tube", "너튜브", 10, 1, NA, Category.Media, 146), // YouTube 패러디
        app("feed", "테이크북", 10, 2, NA, Category.Social, 150), // Facebook 패러디
        app("green", "왔썹", 10, 3, AS, Category.Social, 155), // WhatsApp 패러디
        app("dance", "틱특", 10, 4, AS, Category.Media, 160), // TikTok 패러디
        app("shop", "알리바바바", 10, 5, AS, Category.Money, 166), // AliExpress 패러디
    )
    val starter = listOf("alarm", "memo", "weather")

    /** Level at which each app's pro version (×2 value) opens: 25 levels after the app itself. */
    val proLevel: Map<String, Int> = mapOf(
        "alarm" to 26, "weather" to 26, "memo" to 26, "flash" to 27, "calc" to 28, "recorder" to 29,
        "phone" to 30, "sms" to 31, "camera" to 32, "gallery" to 33, "contacts" to 34, "settings" to 36,
        "mail" to 37, "translate" to 38, "transfer" to 39, "shopee" to 41, "cloud" to 42, "calendar" to 44,
        "pay" to 45, "taxi" to 47, "music" to 48, "caller" to 51, "bolt" to 53, "browser" to 54,
        "food" to 57, "webtoon" to 60, "cafe" to 63, "wise" to 67, "carpool" to 70, "home" to 73,
        "meet" to 76, "match" to 79, "coin" to 82, "used" to 85, "booking" to 89, "reward" to 92,
        "ott" to 95, "comment" to 99, "beat" to 103, "revolut" to 107, "line" to 111, "grab" to 115,
        "portal" to 119, "mall" to 123, "career" to 127, "ride" to 131, "cheap" to 135, "edit" to 139,
        "secret" to 143, "photo" to 147, "ai" to 151, "search" to 155, "mega" to 159, "superapp" to 163,
        "chat" to 167, "tube" to 171, "feed" to 175, "green" to 180, "dance" to 185, "shop" to 191,
    )
    fun app(id: String): AppDef = apps.first { it.id == id }
}
