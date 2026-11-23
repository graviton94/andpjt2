package com.graviton94.notificationcleaner.core

/**
 * Reach, in layers: stock apps, three regional markets, then the global giants. Region synergy counts apps of the same
 * region in the deck; stock apps get none. Tiers follow the layers: stock 1-2, regional 3-8, global 9-10.
 */
enum class Region(val label: String) { Stock("기본 앱"), Asia("아시아권"), SoutheastAsia("동남아권"), West("서양권"), Global("글로벌") }

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
    Compression("압축 백업 · 백업파일 ×1.5", 30, 1, 2_300, 1),
    SyncValue("동기화 가치 · 알림 가치 ×2", 30, 2, 2_600, 1),
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
    private val ST = Region.Stock
    private val AS = Region.Asia
    private val SE = Region.SoutheastAsia
    private val WE = Region.West
    private val GL = Region.Global

    val apps = listOf(
        // 1티어
        app("alarm", "시계", 1, 0, ST, Category.Work, 1), // 기본 시계
        app("weather", "날씨", 1, 1, ST, Category.Daily, 1), // 기본 날씨
        app("memo", "메모", 1, 2, ST, Category.Work, 1), // 기본 메모
        app("flash", "손전등", 1, 3, ST, Category.Daily, 2), // 기본 손전등
        app("calc", "계산기", 1, 4, ST, Category.Money, 3), // 기본 계산기
        app("recorder", "음성 녹음", 1, 5, ST, Category.Daily, 4), // 기본 녹음기
        // 2티어
        app("phone", "전화", 2, 0, ST, Category.Social, 5), // 기본 전화
        app("sms", "메시지", 2, 1, ST, Category.Social, 6), // 기본 메시지
        app("camera", "카메라", 2, 2, ST, Category.Media, 7), // 기본 카메라
        app("gallery", "갤러리", 2, 3, ST, Category.Media, 8), // 기본 갤러리
        app("contacts", "연락처", 2, 4, ST, Category.Social, 9), // 기본 연락처
        app("settings", "설정", 2, 5, ST, Category.Daily, 11), // 기본 설정
        // 3티어
        app("music", "메롱", 3, 0, AS, Category.Media, 12), // Melon 패러디
        app("papago", "마마고", 3, 1, AS, Category.Work, 13), // Papago 패러디
        app("zalo", "잘로로", 3, 2, SE, Category.Social, 14), // Zalo 패러디
        app("momo", "모모모", 3, 3, SE, Category.Money, 16), // MoMo 패러디
        app("airbnb", "에어방앤방", 3, 4, WE, Category.Daily, 17), // Airbnb 패러디
        app("duo", "듀오링구", 3, 5, WE, Category.Work, 19), // Duolingo 패러디
        // 4티어
        app("ctaxi", "코코아T", 4, 0, AS, Category.Daily, 20), // Kakao T 패러디
        app("pay", "토스트", 4, 1, AS, Category.Money, 22), // Toss 패러디
        app("glints", "글린츠츠", 4, 2, SE, Category.Work, 23), // Glints 패러디
        app("bigo", "비고고", 4, 3, SE, Category.Media, 26), // Bigo Live 패러디
        app("pin", "핀트레스트", 4, 4, WE, Category.Media, 28), // Pinterest 패러디
        app("meet", "주움", 4, 5, WE, Category.Work, 29), // Zoom 패러디
        // 5티어
        app("food", "배다른민족", 5, 0, AS, Category.Daily, 31), // 배달의민족 패러디
        app("edit", "컵컷", 5, 1, AS, Category.Media, 33), // CapCut 패러디
        app("travel", "트래블로까", 5, 2, SE, Category.Daily, 35), // Traveloka 패러디
        app("joox", "쥬욱스", 5, 3, SE, Category.Media, 37), // JOOX 패러디
        app("reddit", "래딧", 5, 4, WE, Category.Social, 39), // Reddit 패러디
        app("career", "링크드아웃", 5, 5, WE, Category.Work, 41), // LinkedIn 패러디
        // 6티어
        app("mall", "쿠빵", 6, 0, AS, Category.Money, 43), // 쿠팡 패러디
        app("portal", "메이버", 6, 1, AS, Category.Work, 45), // Naver 패러디
        app("michat", "미챗챗", 6, 2, SE, Category.Social, 47), // MiChat 패러디
        app("agoda", "아고다다", 6, 3, SE, Category.Daily, 49), // Agoda 패러디
        app("paypal", "페이팔팔", 6, 4, WE, Category.Money, 51), // PayPal 패러디
        app("match", "튄더", 6, 5, WE, Category.Social, 53), // Tinder 패러디
        // 7티어
        app("superapp", "위잿", 7, 0, AS, Category.Money, 55), // WeChat 패러디
        app("line", "라잉", 7, 1, AS, Category.Social, 57), // LINE 패러디
        app("jobs", "잡스트리트", 7, 2, SE, Category.Work, 59), // JobStreet 패러디
        app("lazada", "라자다다", 7, 3, SE, Category.Money, 61), // Lazada 패러디
        app("ride", "우바", 7, 4, WE, Category.Daily, 63), // Uber 패러디
        app("robin", "로빈훗훗", 7, 5, WE, Category.Money, 65), // Robinhood 패러디
        // 8티어
        app("dance", "틱특", 8, 0, AS, Category.Media, 67), // TikTok 패러디
        app("chat", "코코아톡", 8, 1, AS, Category.Social, 69), // 카카오톡 패러디
        app("grab", "그래브", 8, 2, SE, Category.Daily, 71), // Grab 패러디
        app("shopee", "쇼피피", 8, 3, SE, Category.Money, 73), // Shopee 패러디
        app("beat", "스팟티파이", 8, 4, WE, Category.Media, 75), // Spotify 패러디
        app("why", "와이", 8, 5, WE, Category.Social, 77), // X 패러디
        // 9티어
        app("mega", "아마좀", 9, 0, GL, Category.Money, 82), // Amazon 패러디
        app("ott", "넷플러그", 9, 1, GL, Category.Media, 85), // Netflix 패러디
        app("secret", "텔레그림", 9, 2, GL, Category.Money, 88), // Telegram 패러디
        app("mail", "쥐메일", 9, 3, GL, Category.Work, 91), // Gmail 패러디
        app("maps", "큐글맵스", 9, 4, GL, Category.Daily, 94), // Google Maps 패러디
        app("store", "큐글플레이", 9, 5, GL, Category.Money, 97), // Google Play 패러디
        // 10티어
        app("tube", "너튜브", 10, 0, GL, Category.Media, 100), // YouTube 패러디
        app("photo", "인싸그램", 10, 1, GL, Category.Media, 104), // Instagram 패러디
        app("feed", "테이크북", 10, 2, GL, Category.Social, 108), // Facebook 패러디
        app("green", "왔썹", 10, 3, GL, Category.Social, 112), // WhatsApp 패러디
        app("search", "큐글", 10, 4, GL, Category.Work, 116), // Google 패러디 · 영문명 Qoogle
        app("ai", "챗GTP", 10, 5, GL, Category.Work, 120), // ChatGPT 패러디
    )
    val starter = listOf("alarm", "memo", "weather")

    /** Level at which each app's pro version (×2 value) opens: 25 levels after the app itself. */
    val proLevel: Map<String, Int> = mapOf(
        "alarm" to 26, "weather" to 26, "memo" to 26, "flash" to 27, "calc" to 28, "recorder" to 29,
        "phone" to 30, "sms" to 31, "camera" to 32, "gallery" to 33, "contacts" to 34, "settings" to 36,
        "music" to 37, "papago" to 38, "zalo" to 39, "momo" to 41, "airbnb" to 42, "duo" to 44,
        "ctaxi" to 45, "pay" to 47, "glints" to 48, "bigo" to 51, "pin" to 53, "meet" to 54,
        "food" to 56, "edit" to 58, "travel" to 60, "joox" to 62, "reddit" to 64, "career" to 66,
        "mall" to 68, "portal" to 70, "michat" to 72, "agoda" to 74, "paypal" to 76, "match" to 78,
        "superapp" to 80, "line" to 82, "jobs" to 84, "lazada" to 86, "ride" to 88, "robin" to 90,
        "dance" to 92, "chat" to 94, "grab" to 96, "shopee" to 98, "beat" to 100, "why" to 102,
        "mega" to 107, "ott" to 110, "secret" to 113, "mail" to 116, "maps" to 119, "store" to 122,
        "tube" to 125, "photo" to 129, "feed" to 133, "green" to 137, "search" to 141, "ai" to 145,
    )
    fun app(id: String): AppDef = apps.first { it.id == id }
}
