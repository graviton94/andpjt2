# 알림 청소부

알림 한 줄을 밀어 같은 앱을 붙이고, 작은 충돌이 대기열과 상태표시줄을 집어삼키는 무한 증분 Android 게임입니다. 이 저장소의 **《알림 청소부 FX》는 손맛과 첫 연쇄를 검증하는 시연 앱**입니다. 본편의 제품 목표, 공식 가안, 개발 순서와 통과 조건은 [추가 개발계획서](docs/notification-cleaner-production-plan.md)에 있습니다.

## 본편 설계 정본 (2026-11-23)

본편은 시연 앱을 잇지 않고 처음부터 설계합니다. 현재 [개발 순서](docs/core-rules-v3.md#0-개발-순서)의 1단계입니다.

- [핵심 로직 방향 결정](docs/core-direction-decisions.md) · 덱·슬롯·시너지·점화제·초기화에 대한 기획 인터뷰 결과
- [코어 규칙 v3](docs/core-rules-v3.md) · 판정 규칙 정본. 기준 구현은 순수 Kotlin 모듈 [`core/`](core) (`./gradlew -p core test`)
- [디자인 시스템 v1](docs/design/design-system.md) · 데이라이트 기본, 다크 모드 앰버 골드, 글래스모피즘. 값의 원본은 [`tokens.json`](docs/design/tokens.json)
- [콘텐츠 카탈로그](docs/content/notification-catalog.html) · 알림 앱 24종, 이벤트 카드·기본 돌발, 덱·시너지, 판 강화, 백업 상점, 레벨링 곡선 (브라우저로 열기)
- [경제 설계](docs/notification-cleaner-p1-economy-design.md) · 시연 엔진 실측과 결정 사항. 기하급수 배율 모델 v0.2가 다음 단계

## 개발 인수인계

| 항목 | 본편에서 지킬 것 |
|---|---|
| 코어 | 한 손 스와이프 → 같은 앱 묶음 → 실물 폭발 → 과부하 → 알림창 붕괴·자동 복구. 플레이어의 다음 입력은 같은 알림창에서 이어짐 |
| 성장 | 점수는 과부하 파동에서 자릿수가 뛰고, 앱 강화·권역·카테고리·이벤트가 곱해짐. 앱 고유값은 앱별 한 번 가산. P·XP·행동력의 진행 목표는 각각 관리 |
| 조작과 보상 | 직접·자동 스와이프, 경험치 기반 무한 진행, P로 앱 구매·강화. 광고 두 배 보상은 선택형이며 완료 거래에서 P만 지급. 경쟁 기록은 동일 조건으로 분리 |
| UI와 손맛 | 기본 화면은 Android 알림창처럼 읽히게, 충돌과 결과는 히트스톱·셰이크·섬광·반동·파티클·SFX·햅틱을 같은 박자에 적용. 움직임 줄이기와 독립적인 소리·진동 옵션 제공 |

1. [개발계획서 2·3장](docs/notification-cleaner-production-plan.md#2-첫-3분과-오래-남는-루프)을 읽고 첫 3분과 점수 공식을 티켓에 반영합니다. 결정이 필요한 성장값은 코드에 흩어 두기 전에 같은 시드의 경제 시뮬레이션으로 비교합니다. P 지급·가격·레벨 곡선 v0.1과 P1 작업 분해는 [P1 경제 설계](docs/notification-cleaner-p1-economy-design.md)에 있고, `./gradlew -p tools/economy-sim run`으로 다시 잴 수 있습니다.
2. [개발계획서 5~7장](docs/notification-cleaner-production-plan.md#5-화면-타격감과-활용-스킬)의 타격감 계약, Android 경계와 통과 조건을 기준으로 작업을 나눕니다. 다음 구현 순서는 P0 실기기 타격감 검증, P1 10분 분량과 P·XP 분리, P2 첫 이용자 테스트입니다.
3. [GameEngine.kt](app/src/main/java/com/graviton94/notificationcleaner/GameEngine.kt)와 [GameSession.kt](app/src/main/java/com/graviton94/notificationcleaner/GameSession.kt)에서 판정·저장·재생을 확인합니다. 화면과 피드백은 [NotificationShade.kt](app/src/main/java/com/graviton94/notificationcleaner/NotificationShade.kt), [ImpactEffects.kt](app/src/main/java/com/graviton94/notificationcleaner/ImpactEffects.kt), [ImpactFeedback.kt](app/src/main/java/com/graviton94/notificationcleaner/ImpactFeedback.kt)에 있습니다.
4. 변경마다 고정 시드 판정 테스트와 앱 스모크를 실행합니다. 연출 작업은 실제 기기에서 손을 뗀 순간부터 복구까지 확인하고, 샘플 전용 첫 연쇄 수치를 본편의 밸런스 목표와 구분합니다.

## 시연 앱 빌드와 첫 플레이

JDK 17, Android SDK Platform 36, Build Tools 35.0.0을 사용합니다. Android 8.0 이상에서 기존 테스트 앱과 함께 설치할 수 있습니다. 앱 이름은 《알림 청소부 FX》입니다.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

생성 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다. [Android test build](https://github.com/graviton94/andpjt2/actions/workflows/android.yml)에는 APK와 에뮬레이터 스모크 결과가 남습니다.

1. 첫 화면의 **문앞왔**을 옆으로 밀면 같은 앱 세 쌍이 충돌해 과부하가 이어집니다. 시연판 기준 **7연쇄 · 14,967개 처리 · 117,645 P**입니다.
2. **자동 꺼짐**을 눌러 자동 정리를 켜거나, **앱 상점**에서 새 앱을 사고 강화합니다. **최고점 기록**에서 연쇄와 권역·카테고리 배율을 확인합니다.
3. **모의 재난문자**에서는 60초 동안 탭해 배율을 올립니다. 시연판의 두 배 보상은 체험 버튼이고, 최고점은 기기에 저장됩니다.

네트워크·기기 연결·손전등은 앱 안에서 재현합니다. 밝기 슬라이더는 앱 화면 밝기를 바꿉니다. 설정에서 **움직임 줄이기**, 효과음과 진동을 각각 조절합니다.

## 레퍼런스와 이전 기획

- [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)로 Compose 화면 정보 계층과 접근성을 검토합니다. [pixelfx](https://github.com/patch-gg/pixelfx) 타임라인을 참고하고 시연 앱의 Hit spark·Critical hit 스프라이트를 활용합니다.
- [Kenney Game Icons](https://kenney.nl/assets/game-icons)는 CC0입니다. 시연판 스프라이트와 아이콘의 출처는 [APK 에셋 라이선스](app/src/main/assets/licenses)에 있습니다.
- [UI 테마 시안 3종](docs/design/ui-theme-candidates.md) · 본편 UI의 정본 후보
- [초기 마스터플랜](docs/notification-cleaner-master-plan.md) · [파쇄 콘셉트 보드](docs/notification-cleaner-concept-board.png) · [자동 연쇄와 배율 콘셉트 보드](docs/notification-cleaner-score-concept-board.png)
