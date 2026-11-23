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
        // 1티어
        app("alarm", "오분만", 1, 0, EU, Category.Work, 1), // 알람·스누즈
        app("weather", "맑음예보", 1, 1, GL, Category.Daily, 1), // 날씨
        app("memo", "메모장끝", 1, 2, NA, Category.Work, 1), // 메모
        app("flash", "번쩍등", 1, 3, AS, Category.Daily, 2), // 손전등
        app("calc", "계산했산", 1, 4, EU, Category.Money, 3), // 계산기
        app("steps", "만보기왕", 1, 5, AS, Category.Social, 4), // 만보기·걷기 랭킹
        // 2티어
        app("calendar", "일정찍", 2, 0, AS, Category.Work, 5), // 캘린더
        app("mail", "메일소포", 2, 1, GL, Category.Work, 6), // 메일
        app("delivery", "문앞왔", 2, 2, KR, Category.Daily, 7), // 택배 조회
        app("point", "적립했냥", 2, 3, EU, Category.Money, 8), // 포인트·적립
        app("gallery", "추억소환", 2, 4, AS, Category.Media, 9), // 사진첩·추억
        app("podcast", "듣다잠듦", 2, 5, EU, Category.Media, 11), // 팟캐스트
        // 3티어
        app("map", "길막지도", 3, 0, KR, Category.Daily, 12), // 지도·내비
        app("pay", "페이한칸", 3, 1, NA, Category.Money, 13), // 간편결제
        app("inbox", "밀린메일", 3, 2, NA, Category.Work, 14), // 업무 메일함
        app("translate", "번역해줘", 3, 3, EU, Category.Social, 16), // 번역·언어학습
        app("cloud", "용량부족", 3, 4, GL, Category.Work, 17), // 클라우드 저장
        app("taxi", "지금잡혀", 3, 5, AS, Category.Daily, 19), // 택시 호출
        // 4티어
        app("food", "배달왔쪄", 4, 0, KR, Category.Daily, 20), // 음식 배달
        app("bank", "잔액알림", 4, 1, KR, Category.Money, 22), // 은행
        app("music", "비트서랍", 4, 2, NA, Category.Media, 23), // 음악 스트리밍
        app("reward", "접속보상", 4, 3, GL, Category.Media, 26), // 모바일 게임
        app("home", "문열림", 4, 4, EU, Category.Daily, 28), // 스마트홈
        app("fit", "운동하셈", 4, 5, NA, Category.Social, 29), // 운동 기록·챌린지
        // 5티어
        app("meet", "카메라켜요", 5, 0, NA, Category.Work, 32), // 화상회의
        app("coin", "떡상알림", 5, 1, AS, Category.Money, 35), // 코인 거래
        app("match", "매칭됨", 5, 2, EU, Category.Social, 38), // 데이팅
        app("cafe", "맘카페짱", 5, 3, KR, Category.Social, 42), // 지역 커뮤니티
        app("webtoon", "다음화보기", 5, 4, KR, Category.Media, 45), // 웹툰
        app("stay", "숙소특가", 5, 5, EU, Category.Daily, 48), // 여행·숙소
        // 6티어
        app("stock", "장마감", 6, 0, NA, Category.Money, 51), // 주식
        app("comment", "댓글폭탄", 6, 1, KR, Category.Social, 54), // 댓글 커뮤니티
        app("minutes", "회의록봇", 6, 2, EU, Category.Work, 57), // AI 회의록
        app("live", "지금방송중", 6, 3, AS, Category.Media, 60), // 라이브 방송
        app("used", "중고줄게", 6, 4, KR, Category.Money, 64), // 중고거래
        app("workchat", "업무톡방", 6, 5, GL, Category.Work, 67), // 업무 메신저
        // 7티어
        app("ott", "정주행각", 7, 0, NA, Category.Media, 70), // OTT
        app("cheap", "직구특가", 7, 1, AS, Category.Money, 74), // 초저가 직구 쇼핑
        app("career", "경력직구함", 7, 2, NA, Category.Social, 78), // 구인·인맥
        app("edit", "컷편집기", 7, 3, AS, Category.Media, 82), // 영상 편집
        app("portal", "실검왔다", 7, 4, KR, Category.Daily, 86), // 국내 포털
        app("ride", "같이타요", 7, 5, EU, Category.Social, 90), // 차량 공유
        // 8티어
        app("mall", "새벽도착", 8, 0, KR, Category.Money, 94), // 새벽배송 쇼핑
        app("secret", "비밀말풍선", 8, 1, EU, Category.Social, 98), // 보안 메신저
        app("photo", "사진한장", 8, 2, NA, Category.Media, 102), // 사진 SNS
        app("feed", "피드또봐", 8, 3, GL, Category.Social, 106), // 페이스형 SNS
        app("ai", "물어봐봇", 8, 4, NA, Category.Work, 110), // AI 챗봇
        app("superpay", "전국페이", 8, 5, KR, Category.Money, 114), // 국민 간편결제
        // 9티어
        app("shorts", "숏츠한편", 9, 0, AS, Category.Media, 118), // 숏폼 영상
        app("chat", "톡탁톡", 9, 1, KR, Category.Social, 122), // 국민 메신저
        app("search", "검색해봐", 9, 2, GL, Category.Work, 126), // 검색 엔진
        app("superapp", "다되는앱", 9, 3, AS, Category.Money, 130), // 슈퍼앱
        app("store", "앱깔아", 9, 4, GL, Category.Daily, 134), // 앱 마켓
        app("navi", "길안내양", 9, 5, GL, Category.Daily, 138), // 글로벌 지도
        // 10티어
        app("tube", "영상한바다", 10, 0, GL, Category.Media, 142), // 세계 최대 동영상
        app("green", "세계말풍선", 10, 1, GL, Category.Social, 146), // 세계 1위 메신저
        app("os", "시스템업뎃", 10, 2, GL, Category.Work, 150), // OS 시스템 알림
        app("mega", "전세계쇼핑", 10, 3, NA, Category.Money, 155), // 세계 최대 쇼핑몰
        app("dance", "춤추는영상", 10, 4, AS, Category.Media, 160), // 글로벌 숏폼 1위
        app("assist", "만능비서", 10, 5, EU, Category.Daily, 166), // AI 비서
    )
    val starter = listOf("alarm", "memo", "weather")

    /** Level at which each app's pro version (×2 value) opens: 25 levels after the app itself. */
    val proLevel: Map<String, Int> = mapOf(
        "alarm" to 26, "weather" to 26, "memo" to 26, "flash" to 27, "calc" to 28, "steps" to 29,
        "calendar" to 30, "mail" to 31, "delivery" to 32, "point" to 33, "gallery" to 34, "podcast" to 36,
        "map" to 37, "pay" to 38, "inbox" to 39, "translate" to 41, "cloud" to 42, "taxi" to 44,
        "food" to 45, "bank" to 47, "music" to 48, "reward" to 51, "home" to 53, "fit" to 54,
        "meet" to 57, "coin" to 60, "match" to 63, "cafe" to 67, "webtoon" to 70, "stay" to 73,
        "stock" to 76, "comment" to 79, "minutes" to 82, "live" to 85, "used" to 89, "workchat" to 92,
        "ott" to 95, "cheap" to 99, "career" to 103, "edit" to 107, "portal" to 111, "ride" to 115,
        "mall" to 119, "secret" to 123, "photo" to 127, "feed" to 131, "ai" to 135, "superpay" to 139,
        "shorts" to 143, "chat" to 147, "search" to 151, "superapp" to 155, "store" to 159, "navi" to 163,
        "tube" to 167, "green" to 171, "os" to 175, "mega" to 180, "dance" to 185, "assist" to 191,
    )
    fun app(id: String): AppDef = apps.first { it.id == id }
}
