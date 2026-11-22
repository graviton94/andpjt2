# 08 · 디렉토리 구조

## 현재 (2026-11-23)

```text
andpjt2/
├── README.md                         저장소 입구: 바이블 링크, 시연 앱 빌드 방법
├── build.gradle.kts · settings.gradle.kts · gradle/ · gradlew   Android 빌드 (app 모듈)
├── .github/workflows/android.yml     CI: core 테스트 → 시연 앱 테스트·린트·APK → 에뮬레이터 스모크
│
├── core/                             ★ 본편 판정 엔진 (순수 Kotlin, 독립 Gradle 빌드)
│   ├── build.gradle.kts              JVM 17, `run` = 예시 출력 / `run --args=content` = 참조표 생성
│   └── src/
│       ├── main/kotlin/com/graviton94/notificationcleaner/core/
│       │   ├── Content.kt            권역·카테고리, 앱 24종, 이벤트 카드, 판 강화, 백업 상점, 프로 레벨
│       │   ├── Rules.kt              RuleSet: 모든 계수·공식 (레벨 P, 가격, 시너지, 백업파일)
│       │   ├── Engine.kt             상태·박자·스와이프·충전·오프라인·상점·덱·초기화
│       │   ├── Examples.kt           문서 예시 출력 (고정 시드)
│       │   └── ContentDoc.kt         바이블 05 참조표 생성기
│       └── test/kotlin/.../CoreRulesTest.kt   예시 고정 테스트
│
├── tools/
│   └── economy-sim/                  경제 시뮬레이터 (core 소스를 그대로 컴파일)
│       ├── build.gradle.kts
│       └── src/main/kotlin/
│           ├── EconomySim.kt         회차 시뮬레이션, 구매·덱·백업 정책
│           ├── Daily.kt              8주 일상 플레이(하루 4×15분 + 오프라인), 해금 밀도
│           └── Debug.kt              세션 추적
│
├── docs/
│   ├── bible/                        ★ 게임 바이블 (이 폴더)
│   ├── core-rules-v3.md              판정 규칙 정본
│   ├── core-direction-decisions.md   핵심 로직 인터뷰 결정
│   ├── economy-v0.2.md · economy-v0.3.md   경제 설계 기록
│   ├── notification-cleaner-*.md / *.png   시연 앱 시절 기획 (기록용)
│   ├── content/notification-catalog.html   콘텐츠 카탈로그 (아티팩트 원본)
│   └── design/
│       ├── design-system.md · tokens.json · design-system-{light,dark}.png
│       ├── ui-theme-candidates.md · ui-theme-{amber,mint,day}.png
│       └── theme-source/             시안·시트 렌더러 (Node + Playwright)
│
├── app/                              시연 앱 《알림 청소부 FX》 (그레이박스에서 교체 예정)
│   └── src/main/java/com/graviton94/notificationcleaner/
│       ├── GameEngine.kt · GameSession.kt         시연 엔진·세션 (폐기 예정)
│       ├── NotificationShade.kt · ShadeSheets.kt  시연 화면
│       └── ImpactEffects.kt · ImpactFeedback.kt   연출·햅틱 (참고용으로 이관 검토)
└── scripts/                          시연 앱 스모크·오디오 합성
```

## 목표 (그레이박스 · 3단계 이후)

```text
andpjt2/
├── settings.gradle.kts               include(":core", ":app") — core를 Android 빌드에도 포함
├── core/                             (현재 그대로, 판정·데이터)
│   └── src/main/resources/rules/rules-v3.json     직렬화한 RuleSet·Catalog (원격 설정 기본값)
├── app/
│   └── src/main/java/com/graviton94/notificationcleaner/
│       ├── MainActivity.kt
│       ├── state/          GameViewModel, SaveStore(DataStore), Clock(단조 시계)
│       ├── ui/theme/       tokens.json → Compose 테마 (Color, Type, Shape, Glass)
│       ├── ui/hud/         StatusBarHud, QuickTiles, Header
│       ├── ui/board/       NotificationBoard, NotificationCard, SwipeGesture
│       ├── ui/shade/       PullDownShade, Sliders, MediaCard
│       ├── ui/sheets/      Shop, Upgrade(Bulk), DeckEditor, Assists, FactoryReset, BackupShop, Records, Daily
│       ├── fx/             Timeline(Beat 재생), HitStop, Shake, Particles, Haptics, Sfx
│       ├── platform/       Ads(보상형), Billing, Analytics, RemoteRules
│       └── format/         KoreanNumber, Notation
├── tools/economy-sim/
└── docs/bible/
```

규칙: `core`는 Android를 모른다. `app/ui`는 `core`의 상태를 읽기만 하고, 변경은 `GameViewModel`을 거쳐 `Engine` 호출로만.
