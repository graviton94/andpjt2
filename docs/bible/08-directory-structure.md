# 08 · 디렉토리 구조

## 현재 (2026-11-23)

```text
andpjt2/
├── README.md                         저장소 입구: 바이블 링크, 빌드 방법
├── build.gradle.kts · settings.gradle.kts · gradle/ · gradlew   Android 빌드 (app 모듈, 시연 앱)
├── .github/workflows/android.yml     CI: core 테스트 → 시연 앱 테스트·린트·APK → 에뮬레이터 스모크
│
├── core/                             ★ 본편 판정 엔진 (순수 Kotlin, 독립 Gradle 빌드, 테스트 53개)
│   └── src/main/kotlin/com/graviton94/notificationcleaner/core/
│       ├── Content.kt                권역·카테고리, 앱 60종 + 업데이트 팩, 카드, 백업·클라우드 상점
│       ├── Rules.kt                  RuleSet: 모든 계수 (레벨 곡선, 가격, 시너지, 환생, 수익화, 출석판)
│       ├── Engine.kt                 상태·박자·스와이프·고유 효과 58종·돌발·오프라인·초기화·동기화
│       ├── Monetization.kt           데이터(유료재화)·프리미엄 ★·광고 지면·데이터 상점·월간/터보/울트라 패스·30일 출석판
│       ├── ContentDoc.kt · Examples.kt   참조표 생성, K/M/B 숫자 표기(short)
│
├── tools/
│   ├── economy-sim/                  경제 시뮬레이터 (core 소스 그대로 컴파일, 4코어 병렬)
│   │   └── src/main/kotlin/Daily.kt  일상 플레이 · 과금 프로필 5종 · 광고 분리 실험 · SIM_RULES 덮어쓰기
│   ├── icons/                        앱 아이콘 코드 SVG · 발주 PNG 반입
│   ├── catalog/                      콘텐츠 카탈로그 생성
│   ├── commissions/                  에셋 발주 보드 생성 · 시트 칸별 자르기(slice_sheet.py)
│   └── sound/                        시연 합성음 → OGG 렌더러
│
├── assets-src/sfx/                   효과음 1차본 59개 (OGG, 시연 합성음 렌더)
│
├── docs/
│   ├── bible/                        ★ 게임 바이블 00~20
│   ├── content/                      app-texts.json, l10n/(en.json, store-listing.md), 카탈로그
│   └── design/
│       ├── tokens.json · design-system.md
│       ├── app-icons/                앱 아이콘 66종
│       ├── ui-assets/                재화·부스트·등급 배지·메타·상점 아트 39개 (+ _sheets 원본)
│       ├── commissions.json · ui-commission.md   에셋 발주 정본
│       ├── fx-demo/                  타격감 시연 페이지 소스
│       └── screens-v1/               화면 시안
│
├── app/                              시연 앱 《알림 청소부 FX》 (그레이박스에서 교체 예정)
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
│       ├── platform/       Ads(보상형), Billing(데이터 팩·구독), Analytics, RemoteRules
│       └── format/         Notation (K/M/B/T·aa, core short() 사용)
├── tools/economy-sim/
└── docs/bible/
```

규칙: `core`는 Android를 모른다. `app/ui`는 `core`의 상태를 읽기만 하고, 변경은 `GameViewModel`을 거쳐 `Engine` 호출로만.
