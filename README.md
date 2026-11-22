# 알림 청소부

알림 한 줄을 밀어 같은 앱을 붙이고, 작은 충돌이 대기열과 상태표시줄을 집어삼키는 무한 증분 Android 게임입니다. 이 저장소의 **《알림 청소부 FX》는 손맛과 첫 연쇄를 검증하는 시연 앱**입니다. 본편의 최신 규칙과 남은 인터뷰는 [인터뷰 결정본](docs/notification-cleaner-interview-decisions.md)에서 시작합니다. 제품 목표, Android 설계, 개발 순서와 출시 검증은 [추가 개발계획서](docs/notification-cleaner-production-plan.md)를 함께 봅니다.

## 개발 인수인계

| 항목 | 본편에서 지킬 것 |
|---|---|
| 코어 | 저벨류 한 줄로 같은 앱 두 줄을 붙여 폭발시킵니다. 보충된 알림과 특수 블록도 진행 중인 연쇄에 합류합니다. |
| 성장 | 실제로 함께 터진 앱의 권역×카테고리 시너지를 계산합니다. 앱과 특수 슬롯은 초반 확장 후 상한을 두며, 번 재화로 장착하지 않은 후보도 강화해 주력 교체를 준비합니다. |
| 특수와 방치 | 재난경보는 직접 시작한 뒤 5초간 연타하고 연쇄를 방출합니다. 엮인 특수 블록은 각각 발동합니다. 자동 정리는 가벼운 정리 위주로 보상과 경험치를 쌓습니다. |
| UI와 손맛 | 기본 화면은 Android 알림창처럼 읽히게, 충돌과 결과는 히트스톱·셰이크·섬광·반동·파티클·SFX·햅틱을 같은 박자에 적용. 움직임 줄이기와 독립적인 소리·진동 옵션 제공 |

1. [인터뷰 결정본](docs/notification-cleaner-interview-decisions.md)의 확정 규칙과 검증 가안을 구분해 봅니다. 이전 문서와 상충하는 게임 규칙은 인터뷰 결정본을 우선합니다. 다음 인터뷰는 첫 주력 교체 시점과 투자 비용부터 이어갑니다.
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
- [초기 마스터플랜](docs/notification-cleaner-master-plan.md) · [파쇄 콘셉트 보드](docs/notification-cleaner-concept-board.png) · [자동 연쇄와 배율 콘셉트 보드](docs/notification-cleaner-score-concept-board.png)
